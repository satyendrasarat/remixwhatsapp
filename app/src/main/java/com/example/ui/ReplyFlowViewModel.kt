package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatMessageEntity
import com.example.data.ConversationEntity
import com.example.data.ConversationStatus
import com.example.data.FlowConfigEntity
import com.example.data.KnowledgeChunkEntity
import com.example.data.PdfDocumentEntity
import com.example.data.PipelineExecutionOutcome
import com.example.data.ReplyFlowRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ReplyFlowVM"

enum class AppTab {
    DASHBOARD,
    KNOWLEDGE,
    FLOW_MANAGER,
    INBOX
}

enum class InboxFilter(val label: String) {
    ALL("All"),
    NEEDS_HUMAN("Needs Human"),
    AI_ACTIVE("AI Active"),
    RESOLVED("Resolved")
}

sealed interface OperationBanner {
    data object Idle : OperationBanner
    data class Loading(val message: String) : OperationBanner
    data class Success(val message: String) : OperationBanner
    data class Error(val message: String) : OperationBanner
}

data class DashboardMetrics(
    val activePdfCount: Int = 0,
    val totalChunkCount: Int = 0,
    val aiRepliesSent: Int = 0,
    val guardrailTransfers: Int = 0,
    val pendingHandoffs: Int = 0
)

class ReplyFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ReplyFlowRepository(application.applicationContext)

    private val _selectedTab = MutableStateFlow(AppTab.DASHBOARD)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    private val _inboxFilter = MutableStateFlow(InboxFilter.ALL)
    val inboxFilter: StateFlow<InboxFilter> = _inboxFilter.asStateFlow()

    private val _isNotificationAccessGranted = MutableStateFlow(checkNotificationAccess())
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    private val _pdfOperation = MutableStateFlow<OperationBanner>(OperationBanner.Idle)
    val pdfOperation: StateFlow<OperationBanner> = _pdfOperation.asStateFlow()

    private val _simulatorOperation = MutableStateFlow<OperationBanner>(OperationBanner.Idle)
    val simulatorOperation: StateFlow<OperationBanner> = _simulatorOperation.asStateFlow()

    private val _lastSimulatedOutcome = MutableStateFlow<PipelineExecutionOutcome?>(null)
    val lastSimulatedOutcome: StateFlow<PipelineExecutionOutcome?> = _lastSimulatedOutcome.asStateFlow()

    private val _showSimulatorSheet = MutableStateFlow(false)
    val showSimulatorSheet: StateFlow<Boolean> = _showSimulatorSheet.asStateFlow()

    val flowConfig: StateFlow<FlowConfigEntity> = repository.flowConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FlowConfigEntity()
    )

    val documents: StateFlow<List<PdfDocumentEntity>> = repository.documentsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val conversations: StateFlow<List<ConversationEntity>> = repository.conversationsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val metrics: StateFlow<DashboardMetrics> = combine(
        repository.documentsFlow,
        repository.conversationsFlow,
        repository.aiReplyCountFlow,
        repository.guardrailTransferCountFlow
    ) { docs, convs, aiCount, transferCount ->
        val readyDocs = docs.filter { it.isEnabled && it.indexingStatus == "READY" }
        DashboardMetrics(
            activePdfCount = readyDocs.size,
            totalChunkCount = readyDocs.sumOf { it.chunkCount },
            aiRepliesSent = aiCount,
            guardrailTransfers = transferCount,
            pendingHandoffs = convs.count { it.status == ConversationStatus.HANDOFF_REQUIRED.name }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardMetrics()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedConversation: StateFlow<ConversationEntity?> = _selectedConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeConversation(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMessages: StateFlow<List<ChatMessageEntity>> = _selectedConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.getOrCreateConfig()
        }
    }

    fun refreshNotificationAccessStatus() {
        _isNotificationAccessGranted.value = checkNotificationAccess()
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        if (tab != AppTab.INBOX) {
            _selectedConversationId.value = null
        }
    }

    fun openConversation(conversationId: String) {
        _selectedTab.value = AppTab.INBOX
        _selectedConversationId.value = conversationId
    }

    fun closeConversation() {
        _selectedConversationId.value = null
    }

    fun openHandoffQueue() {
        _inboxFilter.value = InboxFilter.NEEDS_HUMAN
        _selectedConversationId.value = null
        _selectedTab.value = AppTab.INBOX
    }

    fun setInboxFilter(filter: InboxFilter) {
        _inboxFilter.value = filter
    }

    fun setSimulatorSheetVisible(visible: Boolean) {
        _showSimulatorSheet.value = visible
        if (!visible) {
            _simulatorOperation.value = OperationBanner.Idle
        }
    }

    fun clearPdfBanner() {
        _pdfOperation.value = OperationBanner.Idle
    }

    fun clearSimulatorBanner() {
        _simulatorOperation.value = OperationBanner.Idle
    }

    fun observeChunksForDocument(docId: Long): Flow<List<KnowledgeChunkEntity>> =
        repository.observeChunksForDocument(docId)

    fun updateFlowConfig(transform: (FlowConfigEntity) -> FlowConfigEntity) {
        viewModelScope.launch {
            val current = repository.getOrCreateConfig()
            repository.saveFlowConfig(transform(current))
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _pdfOperation.value = OperationBanner.Loading("Reading PDF and indexing RAG chunks with Gemini…")
            try {
                val doc = repository.importPdfDocument(uri)
                _pdfOperation.value = OperationBanner.Success(
                    "Indexed \"${doc.fileName}\" (${doc.chunkCount} knowledge chunks ready)"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF import failed", e)
                _pdfOperation.value = OperationBanner.Error(
                    e.localizedMessage ?: "Unable to index PDF document."
                )
            }
        }
    }

    fun createSamplePdf() {
        viewModelScope.launch {
            _pdfOperation.value = OperationBanner.Loading("Generating sample policy PDF and indexing with Gemini…")
            try {
                val doc = repository.generateAndIndexSamplePdf()
                _pdfOperation.value = OperationBanner.Success(
                    "Indexed \"${doc.fileName}\" (${doc.chunkCount} chunks across ${doc.pageCount} pages)"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sample PDF generation/indexing failed", e)
                _pdfOperation.value = OperationBanner.Error(
                    e.localizedMessage ?: "Failed to index sample PDF with Gemini."
                )
            }
        }
    }

    fun toggleDocumentEnabled(docId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setDocumentEnabled(docId, enabled)
        }
    }

    fun deleteDocument(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
        }
    }

    fun simulateIncomingWhatsApp(contactName: String, messageText: String) {
        val cleanContact = contactName.trim().ifBlank { "Maya Lin" }
        val cleanMessage = messageText.trim()
        if (cleanMessage.isBlank()) return

        viewModelScope.launch {
            _simulatorOperation.value = OperationBanner.Loading(
                "Running Flow Manager: Sentiment Guardrail + PDF RAG + Gemini…"
            )
            try {
                val outcome = repository.processIncomingMessage(
                    contactName = cleanContact,
                    messageText = cleanMessage,
                    packageName = "com.whatsapp",
                    isGroupChat = false,
                    isSimulated = true
                )
                _lastSimulatedOutcome.value = outcome
                _simulatorOperation.value = if (outcome.guardrailTriggered) {
                    OperationBanner.Success("Guardrail triggered! Transferred to Human Agent queue.")
                } else {
                    OperationBanner.Success("Automated RAG reply composed and logged.")
                }
                _showSimulatorSheet.value = false
                openConversation(outcome.conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Simulation failed", e)
                _simulatorOperation.value = OperationBanner.Error(
                    e.localizedMessage ?: "Gemini flow evaluation failed."
                )
            }
        }
    }

    fun sendHumanReply(conversationId: String, text: String) {
        viewModelScope.launch {
            repository.sendHumanAgentReply(conversationId, text)
        }
    }

    fun takeOverChat(conversationId: String) {
        viewModelScope.launch {
            repository.takeOverConversation(conversationId)
        }
    }

    fun resumeAiChat(conversationId: String) {
        viewModelScope.launch {
            repository.resumeAiForConversation(conversationId)
        }
    }

    fun resolveChat(conversationId: String) {
        viewModelScope.launch {
            repository.resolveConversation(conversationId)
        }
    }

    fun deleteChat(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            if (_selectedConversationId.value == conversationId) {
                _selectedConversationId.value = null
            }
        }
    }

    private fun checkNotificationAccess(): Boolean {
        val context = getApplication<Application>().applicationContext
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }
}
