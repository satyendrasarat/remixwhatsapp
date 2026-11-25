package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ai.GeminiFlowEngine
import com.example.service.ActiveNotificationReplyStore
import com.example.service.HandoffNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "ReplyFlowRepo"

data class PipelineExecutionOutcome(
    val conversationId: String,
    val status: ConversationStatus,
    val sentReply: String?,
    val guardrailTriggered: Boolean,
    val guardrailReason: String?,
    val sentiment: SentimentLevel,
    val frustrationScore: Int,
    val citedSources: List<String>,
    val dispatchedToWhatsApp: Boolean
)

class ReplyFlowRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val geminiEngine: GeminiFlowEngine = GeminiFlowEngine()
) {
    private val knowledgeDao = database.knowledgeDao()
    private val flowConfigDao = database.flowConfigDao()
    private val conversationDao = database.conversationDao()

    val documentsFlow: Flow<List<PdfDocumentEntity>> = knowledgeDao.observeDocuments()

    val flowConfigFlow: Flow<FlowConfigEntity> = flowConfigDao.observeConfig().map {
        it ?: FlowConfigEntity()
    }

    val conversationsFlow: Flow<List<ConversationEntity>> = conversationDao.observeConversations()

    val aiReplyCountFlow: Flow<Int> = conversationDao.observeAiReplyCount()

    val guardrailTransferCountFlow: Flow<Int> = conversationDao.observeGuardrailTransferCount()

    fun observeConversation(conversationId: String): Flow<ConversationEntity?> =
        conversationDao.observeConversation(conversationId)

    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>> =
        conversationDao.observeMessages(conversationId)

    fun observeChunksForDocument(docId: Long): Flow<List<KnowledgeChunkEntity>> =
        knowledgeDao.observeChunksForDocument(docId)

    suspend fun getOrCreateConfig(): FlowConfigEntity {
        val existing = flowConfigDao.getConfig()
        if (existing != null) return existing
        val defaultConfig = FlowConfigEntity()
        flowConfigDao.upsertConfig(defaultConfig)
        return defaultConfig
    }

    suspend fun saveFlowConfig(config: FlowConfigEntity) {
        flowConfigDao.upsertConfig(config.copy(id = 1))
    }

    suspend fun setDocumentEnabled(docId: Long, enabled: Boolean) {
        knowledgeDao.setDocumentEnabled(docId, enabled)
    }

    suspend fun deleteDocument(doc: PdfDocumentEntity) {
        withContext(Dispatchers.IO) {
            PdfStorageManager.deleteLocalPdf(doc.localPath)
            knowledgeDao.deleteDocument(doc.id)
        }
    }

    suspend fun importPdfDocument(uri: Uri): PdfDocumentEntity {
        val stored = withContext(Dispatchers.IO) {
            PdfStorageManager.copyPdfToInternalStorage(context, uri)
        }
        return indexStoredPdf(stored)
    }

    suspend fun generateAndIndexSamplePdf(): PdfDocumentEntity {
        val stored = withContext(Dispatchers.IO) {
            PdfStorageManager.createSampleSupportGuidePdf(context)
        }
        return indexStoredPdf(stored)
    }

    private suspend fun indexStoredPdf(stored: StoredPdfInfo): PdfDocumentEntity {
        val placeholderId = knowledgeDao.insertDocument(
            PdfDocumentEntity(
                fileName = stored.fileName,
                localPath = stored.localPath,
                pageCount = stored.pageCount,
                fileSizeBytes = stored.fileSizeBytes,
                summary = "Extracting RAG knowledge chunks with Gemini…",
                chunkCount = 0,
                isEnabled = true,
                indexingStatus = "INDEXING"
            )
        )

        return try {
            val extracted = geminiEngine.extractPdfKnowledge(stored.bytes, stored.fileName)
            val chunkEntities = extracted.sections.map { section ->
                KnowledgeChunkEntity(
                    documentId = placeholderId,
                    documentName = stored.fileName,
                    sectionTitle = section.sectionTitle,
                    pageNumber = section.pageNumber.coerceIn(1, stored.pageCount.coerceAtLeast(1)),
                    content = section.content,
                    keywords = section.keywords
                )
            }
            knowledgeDao.deleteChunksForDocument(placeholderId)
            knowledgeDao.insertChunks(chunkEntities)

            val readyDoc = PdfDocumentEntity(
                id = placeholderId,
                fileName = stored.fileName,
                localPath = stored.localPath,
                pageCount = stored.pageCount,
                fileSizeBytes = stored.fileSizeBytes,
                summary = extracted.summary,
                chunkCount = chunkEntities.size,
                isEnabled = true,
                indexingStatus = "READY",
                errorMessage = null
            )
            knowledgeDao.updateDocument(readyDoc)
            readyDoc
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed indexing PDF ${stored.fileName}", e)
            val failedDoc = PdfDocumentEntity(
                id = placeholderId,
                fileName = stored.fileName,
                localPath = stored.localPath,
                pageCount = stored.pageCount,
                fileSizeBytes = stored.fileSizeBytes,
                summary = "Indexing failed. Tap retry or remove this document.",
                chunkCount = 0,
                isEnabled = false,
                indexingStatus = "ERROR",
                errorMessage = e.localizedMessage ?: "Failed to extract text from PDF."
            )
            knowledgeDao.updateDocument(failedDoc)
            throw e
        }
    }

    /**
     * Full Flow Manager execution pipeline for both live WhatsApp notifications and in-app Simulator tests.
     */
    suspend fun processIncomingMessage(
        contactName: String,
        messageText: String,
        packageName: String,
        isGroupChat: Boolean,
        isSimulated: Boolean
    ): PipelineExecutionOutcome {
        val config = getOrCreateConfig()
        val normalizedId = buildConversationId(contactName, packageName, isSimulated)
        val existingConversation = conversationDao.getConversation(normalizedId)
        val now = System.currentTimeMillis()

        // Gate 1: If this is a live notification, enforce master switch, app toggle, group filter & contact rules
        if (!isSimulated) {
            if (!config.isAutomationEnabled) {
                return PipelineExecutionOutcome(
                    conversationId = normalizedId,
                    status = ConversationStatus.AI_ACTIVE,
                    sentReply = null,
                    guardrailTriggered = false,
                    guardrailReason = "Automation paused",
                    sentiment = SentimentLevel.NEUTRAL,
                    frustrationScore = 0,
                    citedSources = emptyList(),
                    dispatchedToWhatsApp = false
                )
            }
            if (packageName == "com.whatsapp" && !config.listenWhatsApp) {
                return skippedOutcome(normalizedId, "WhatsApp Personal listener disabled")
            }
            if (packageName == "com.whatsapp.w4b" && !config.listenWhatsAppBusiness) {
                return skippedOutcome(normalizedId, "WhatsApp Business listener disabled")
            }
            if (isGroupChat && config.ignoreGroupChats) {
                return skippedOutcome(normalizedId, "Group chats ignored")
            }
            if (!passesContactFilter(contactName, config)) {
                return skippedOutcome(normalizedId, "Filtered by contact list rules")
            }
        }

        // Gate 2: Ensure conversation record exists and save incoming customer message
        val currentStatus = existingConversation?.status
            ?.let { raw -> ConversationStatus.entries.firstOrNull { it.name == raw } }
            ?: ConversationStatus.AI_ACTIVE

        val isLockedToHuman = currentStatus == ConversationStatus.HANDOFF_REQUIRED ||
            currentStatus == ConversationStatus.HUMAN_ACTIVE

        if (existingConversation == null) {
            conversationDao.upsertConversation(
                ConversationEntity(
                    id = normalizedId,
                    contactName = contactName,
                    packageName = packageName,
                    status = ConversationStatus.AI_ACTIVE.name,
                    lastMessage = messageText,
                    isSimulated = isSimulated,
                    updatedAt = now
                )
            )
        }

        // If already handed off to a human agent, record customer message and DO NOT let bot interrupt!
        if (isLockedToHuman) {
            conversationDao.insertMessage(
                ChatMessageEntity(
                    conversationId = normalizedId,
                    senderType = MessageSender.CUSTOMER.name,
                    content = messageText,
                    sentiment = existingConversation?.lastSentiment ?: SentimentLevel.CONCERNED.name,
                    frustrationScore = existingConversation?.lastFrustrationScore ?: 50,
                    timestamp = now
                )
            )
            conversationDao.upsertConversation(
                (existingConversation ?: ConversationEntity(
                    id = normalizedId,
                    contactName = contactName,
                    packageName = packageName,
                    lastMessage = messageText,
                    isSimulated = isSimulated
                )).copy(
                    lastMessage = messageText,
                    unreadEscalation = true,
                    updatedAt = now
                )
            )
            HandoffNotifier.notifyHandoffRequired(
                context = context,
                conversationId = normalizedId,
                contactName = contactName,
                reason = "New follow-up message while assigned to human agent",
                customerPreview = messageText
            )
            return PipelineExecutionOutcome(
                conversationId = normalizedId,
                status = currentStatus,
                sentReply = null,
                guardrailTriggered = true,
                guardrailReason = "Chat is currently locked to a human agent. Bot reply suppressed.",
                sentiment = SentimentLevel.fromRaw(existingConversation?.lastSentiment),
                frustrationScore = existingConversation?.lastFrustrationScore ?: 50,
                citedSources = emptyList(),
                dispatchedToWhatsApp = false
            )
        }

        // Gate 3: Retrieve relevant PDF RAG chunks from Room
        val recentHistory = conversationDao.getRecentMessages(normalizedId, limit = 8)
        val allActiveChunks = knowledgeDao.getAllActiveChunks()
        val combinedQuery = buildString {
            append(messageText)
            val previousCustomer = recentHistory.firstOrNull { it.senderType == MessageSender.CUSTOMER.name }
            if (previousCustomer != null) {
                append(" ")
                append(previousCustomer.content)
            }
        }
        val retrievedChunks = RagRetriever.retrieveTopChunks(
            query = combinedQuery,
            allChunks = allActiveChunks,
            topK = config.topKChunks
        )

        // Gate 4: Instant fast-path live-agent keyword guardrail (zero latency!)
        val matchedKeyword = if (config.enableLiveAgentGuardrail) {
            findMatchingEscalationKeyword(messageText, config.customEscalationKeywords)
        } else {
            null
        }

        if (matchedKeyword != null) {
            val fastReason = "Customer requested live representative (matched \"$matchedKeyword\")"
            conversationDao.insertMessage(
                ChatMessageEntity(
                    conversationId = normalizedId,
                    senderType = MessageSender.CUSTOMER.name,
                    content = messageText,
                    sentiment = SentimentLevel.CONCERNED.name,
                    frustrationScore = 60,
                    timestamp = now
                )
            )
            return executeHumanHandoff(
                conversationId = normalizedId,
                contactName = contactName,
                packageName = packageName,
                customerMessage = messageText,
                reason = fastReason,
                sentiment = SentimentLevel.CONCERNED,
                frustrationScore = 60,
                config = config,
                isSimulated = isSimulated,
                now = now
            )
        }

        val decision = geminiEngine.analyzeAndComposeReply(
            contactName = contactName,
            incomingMessage = messageText,
            recentHistory = recentHistory,
            retrievedChunks = retrievedChunks,
            config = config
        )

        // Save incoming customer message enriched with Gemini's sentiment telemetry
        conversationDao.insertMessage(
            ChatMessageEntity(
                conversationId = normalizedId,
                senderType = MessageSender.CUSTOMER.name,
                content = messageText,
                sentiment = decision.sentiment.name,
                frustrationScore = decision.frustrationScore,
                timestamp = now
            )
        )

        // Gate 5: Evaluate Flow Manager Guardrails
        val liveAgentTriggered = config.enableLiveAgentGuardrail && decision.wantsHumanAgent
        val frustrationTriggered = config.enableFrustrationGuardrail &&
            (decision.frustrationScore >= config.frustrationThreshold ||
                decision.sentiment == SentimentLevel.ANGRY)

        if (liveAgentTriggered || frustrationTriggered) {
            val reason = buildString {
                when {
                    liveAgentTriggered && frustrationTriggered -> {
                        append("Live agent requested & high frustration detected (${decision.frustrationScore}% >= ${config.frustrationThreshold}%)")
                    }
                    liveAgentTriggered -> {
                        append(
                            decision.escalationReason.ifBlank {
                                "Customer requested a live human representative"
                            }
                        )
                    }
                    else -> {
                        append("Frustration guardrail triggered (${decision.sentiment.label}, score ${decision.frustrationScore}% >= ${config.frustrationThreshold}%)")
                    }
                }
            }

            return executeHumanHandoff(
                conversationId = normalizedId,
                contactName = contactName,
                packageName = packageName,
                customerMessage = messageText,
                reason = reason,
                sentiment = decision.sentiment,
                frustrationScore = decision.frustrationScore,
                config = config,
                isSimulated = isSimulated,
                now = now
            )
        }

        // Gate 6: Normal Automated RAG Reply
        val finalReply = buildString {
            append(
                decision.replyText.ifBlank {
                    "Thanks for reaching out! Let me connect you with someone who can help further."
                }
            )
            if (config.appendBotSignature) {
                append("\n\n— ReplyFlow AI Assistant")
            }
        }

        val dispatched = if (!isSimulated) {
            ActiveNotificationReplyStore.sendReply(context, normalizedId, finalReply)
        } else {
            false
        }

        val effectiveCitations = decision.citedSources.ifEmpty {
            if (decision.groundedInKnowledge) retrievedChunks.take(2).map { it.citationLabel } else emptyList()
        }

        conversationDao.insertMessage(
            ChatMessageEntity(
                conversationId = normalizedId,
                senderType = MessageSender.AI_BOT.name,
                content = finalReply,
                citedSources = effectiveCitations.joinToString(" | ").ifBlank { null },
                guardrailTriggered = false,
                deliveryStatus = if (isSimulated) "SIMULATED" else if (dispatched) "SENT_WHATSAPP" else "LOGGED_ONLY",
                timestamp = now + 15
            )
        )

        conversationDao.upsertConversation(
            ConversationEntity(
                id = normalizedId,
                contactName = contactName,
                packageName = packageName,
                status = ConversationStatus.AI_ACTIVE.name,
                lastMessage = finalReply,
                lastSentiment = decision.sentiment.name,
                lastFrustrationScore = decision.frustrationScore,
                handoffReason = null,
                unreadEscalation = false,
                isSimulated = isSimulated,
                updatedAt = now + 15
            )
        )

        return PipelineExecutionOutcome(
            conversationId = normalizedId,
            status = ConversationStatus.AI_ACTIVE,
            sentReply = finalReply,
            guardrailTriggered = false,
            guardrailReason = null,
            sentiment = decision.sentiment,
            frustrationScore = decision.frustrationScore,
            citedSources = effectiveCitations,
            dispatchedToWhatsApp = dispatched
        )
    }

    private suspend fun executeHumanHandoff(
        conversationId: String,
        contactName: String,
        packageName: String,
        customerMessage: String,
        reason: String,
        sentiment: SentimentLevel,
        frustrationScore: Int,
        config: FlowConfigEntity,
        isSimulated: Boolean,
        now: Long
    ): PipelineExecutionOutcome {
        conversationDao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                senderType = MessageSender.SYSTEM_EVENT.name,
                content = "Guardrail Triggered — Transferred to Human Agent: $reason",
                sentiment = sentiment.name,
                frustrationScore = frustrationScore,
                guardrailTriggered = true,
                timestamp = now + 10
            )
        )

        val handoffNotice = config.handoffMessage.trim()
        var dispatched = false
        if (handoffNotice.isNotBlank()) {
            if (!isSimulated) {
                dispatched = ActiveNotificationReplyStore.sendReply(context, conversationId, handoffNotice)
            }
            conversationDao.insertMessage(
                ChatMessageEntity(
                    conversationId = conversationId,
                    senderType = MessageSender.AI_BOT.name,
                    content = handoffNotice,
                    guardrailTriggered = true,
                    deliveryStatus = if (isSimulated) "SIMULATED" else if (dispatched) "SENT_WHATSAPP" else "LOGGED_ONLY",
                    timestamp = now + 20
                )
            )
        }

        conversationDao.upsertConversation(
            ConversationEntity(
                id = conversationId,
                contactName = contactName,
                packageName = packageName,
                status = ConversationStatus.HANDOFF_REQUIRED.name,
                lastMessage = customerMessage,
                lastSentiment = sentiment.name,
                lastFrustrationScore = frustrationScore,
                handoffReason = reason,
                unreadEscalation = true,
                isSimulated = isSimulated,
                updatedAt = now + 20
            )
        )

        HandoffNotifier.notifyHandoffRequired(
            context = context,
            conversationId = conversationId,
            contactName = contactName,
            reason = reason,
            customerPreview = customerMessage
        )

        return PipelineExecutionOutcome(
            conversationId = conversationId,
            status = ConversationStatus.HANDOFF_REQUIRED,
            sentReply = handoffNotice.ifBlank { null },
            guardrailTriggered = true,
            guardrailReason = reason,
            sentiment = sentiment,
            frustrationScore = frustrationScore,
            citedSources = emptyList(),
            dispatchedToWhatsApp = dispatched
        )
    }

    suspend fun sendHumanAgentReply(conversationId: String, replyText: String): Boolean {
        val trimmed = replyText.trim()
        if (trimmed.isBlank()) return false
        val conv = conversationDao.getConversation(conversationId) ?: return false
        val now = System.currentTimeMillis()
        val dispatched = if (!conv.isSimulated) {
            ActiveNotificationReplyStore.sendReply(context, conversationId, trimmed)
        } else {
            false
        }

        conversationDao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                senderType = MessageSender.HUMAN_AGENT.name,
                content = trimmed,
                deliveryStatus = if (conv.isSimulated) "SIMULATED" else if (dispatched) "SENT_WHATSAPP" else "LOGGED_ONLY",
                timestamp = now
            )
        )
        conversationDao.upsertConversation(
            conv.copy(
                status = ConversationStatus.HUMAN_ACTIVE.name,
                lastMessage = trimmed,
                unreadEscalation = false,
                updatedAt = now
            )
        )
        return dispatched
    }

    suspend fun takeOverConversation(conversationId: String) {
        val now = System.currentTimeMillis()
        conversationDao.updateStatus(
            conversationId = conversationId,
            status = ConversationStatus.HUMAN_ACTIVE.name,
            unreadEscalation = false,
            reason = "Manually taken over by human agent",
            updatedAt = now
        )
        conversationDao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                senderType = MessageSender.SYSTEM_EVENT.name,
                content = "Human agent took over this conversation. Automated replies are paused.",
                timestamp = now
            )
        )
    }

    suspend fun resumeAiForConversation(conversationId: String) {
        val now = System.currentTimeMillis()
        conversationDao.updateStatus(
            conversationId = conversationId,
            status = ConversationStatus.AI_ACTIVE.name,
            unreadEscalation = false,
            reason = null,
            updatedAt = now
        )
        conversationDao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                senderType = MessageSender.SYSTEM_EVENT.name,
                content = "Conversation handed back to ReplyFlow AI automation.",
                timestamp = now
            )
        )
    }

    suspend fun resolveConversation(conversationId: String) {
        val now = System.currentTimeMillis()
        conversationDao.updateStatus(
            conversationId = conversationId,
            status = ConversationStatus.RESOLVED.name,
            unreadEscalation = false,
            reason = null,
            updatedAt = now
        )
        conversationDao.insertMessage(
            ChatMessageEntity(
                conversationId = conversationId,
                senderType = MessageSender.SYSTEM_EVENT.name,
                content = "Marked as resolved by human agent.",
                timestamp = now
            )
        )
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
    }

    private fun passesContactFilter(contactName: String, config: FlowConfigEntity): Boolean {
        val mode = ContactFilterMode.entries.firstOrNull { it.name == config.contactFilterMode }
            ?: ContactFilterMode.ALL
        if (mode == ContactFilterMode.ALL) return true

        val entries = config.contactFilterList
            .split(",", "\n")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        val target = contactName.trim().lowercase(Locale.ROOT)
        val matched = entries.any { entry -> target.contains(entry) || entry.contains(target) }
        return when (mode) {
            ContactFilterMode.ALLOWLIST -> matched
            ContactFilterMode.BLOCKLIST -> !matched
            ContactFilterMode.ALL -> true
        }
    }

    private fun findMatchingEscalationKeyword(message: String, keywordCsv: String): String? {
        val lower = message.lowercase(Locale.ROOT)
        return keywordCsv
            .split(",", "\n")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 3 }
            .firstOrNull { keyword -> lower.contains(keyword) }
    }

    private fun skippedOutcome(conversationId: String, reason: String) = PipelineExecutionOutcome(
        conversationId = conversationId,
        status = ConversationStatus.AI_ACTIVE,
        sentReply = null,
        guardrailTriggered = false,
        guardrailReason = reason,
        sentiment = SentimentLevel.NEUTRAL,
        frustrationScore = 0,
        citedSources = emptyList(),
        dispatchedToWhatsApp = false
    )

    companion object {
        fun buildConversationId(contactName: String, packageName: String, isSimulated: Boolean): String {
            val cleanContact = contactName.trim().lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "contact" }
            val prefix = if (isSimulated) "sim" else packageName.substringAfterLast('.')
            return "${prefix}_$cleanContact"
        }
    }
}
