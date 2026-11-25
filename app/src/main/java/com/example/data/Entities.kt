package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ConversationStatus(val label: String) {
    AI_ACTIVE("AI Active"),
    HANDOFF_REQUIRED("Needs Human"),
    HUMAN_ACTIVE("Human Agent"),
    RESOLVED("Resolved")
}

enum class MessageSender {
    CUSTOMER,
    AI_BOT,
    HUMAN_AGENT,
    SYSTEM_EVENT
}

enum class SentimentLevel(val label: String) {
    POSITIVE("Positive"),
    NEUTRAL("Neutral"),
    CONCERNED("Concerned"),
    FRUSTRATED("Frustrated"),
    ANGRY("Angry");

    companion object {
        fun fromRaw(raw: String?): SentimentLevel {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NEUTRAL
        }
    }
}

enum class ContactFilterMode(val label: String) {
    ALL("All Contacts"),
    ALLOWLIST("Allowlist Only"),
    BLOCKLIST("Blocklist")
}

enum class AssistantTone(val label: String, val instruction: String) {
    HELPFUL_WARM(
        "Helpful & Warm",
        "Be friendly, empathetic, and clear like a caring customer support specialist."
    ),
    CONCISE_DIRECT(
        "Concise & Direct",
        "Keep WhatsApp replies short, scannable, and straight to the point (2-4 sentences max)."
    ),
    FORMAL_SUPPORT(
        "Formal Support",
        "Maintain a polished, professional, courteous corporate support tone."
    )
}

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val localPath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val summary: String,
    val chunkCount: Int,
    val isEnabled: Boolean = true,
    val indexingStatus: String = "READY", // INDEXING, READY, ERROR
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val documentName: String,
    val sectionTitle: String,
    val pageNumber: Int,
    val content: String,
    val keywords: String
)

@Entity(tableName = "flow_config")
data class FlowConfigEntity(
    @PrimaryKey val id: Int = 1,
    val isAutomationEnabled: Boolean = true,
    val listenWhatsApp: Boolean = true,
    val listenWhatsAppBusiness: Boolean = true,
    val ignoreGroupChats: Boolean = true,
    val contactFilterMode: String = ContactFilterMode.ALL.name,
    val contactFilterList: String = "",
    val cooldownSeconds: Int = 5,
    val enableLiveAgentGuardrail: Boolean = true,
    val enableFrustrationGuardrail: Boolean = true,
    val frustrationThreshold: Int = 65,
    val customEscalationKeywords: String = "human, live agent, representative, real person, manager, supervisor, speak to someone",
    val handoffMessage: String = "I hear your concern and want to make sure you get the best help. I'm transferring this chat to a live human agent now—someone will reply shortly.",
    val strictRagOnly: Boolean = true,
    val topKChunks: Int = 4,
    val tone: String = AssistantTone.HELPFUL_WARM.name,
    val customSystemPrompt: String = "Answer WhatsApp customer inquiries accurately using only the provided PDF knowledge snippets. Never invent pricing, order statuses, or policies.",
    val appendBotSignature: Boolean = false
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val contactName: String,
    val packageName: String,
    val status: String = ConversationStatus.AI_ACTIVE.name,
    val lastMessage: String,
    val lastSentiment: String = SentimentLevel.NEUTRAL.name,
    val lastFrustrationScore: Int = 0,
    val handoffReason: String? = null,
    val unreadEscalation: Boolean = false,
    val isSimulated: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId"), Index("timestamp")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val senderType: String,
    val content: String,
    val sentiment: String? = null,
    val frustrationScore: Int? = null,
    val citedSources: String? = null,
    val guardrailTriggered: Boolean = false,
    val deliveryStatus: String = "DELIVERED",
    val timestamp: Long = System.currentTimeMillis()
)
