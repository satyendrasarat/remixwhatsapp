package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM pdf_documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isEnabled = 1 AND indexingStatus = 'READY'")
    suspend fun getActiveDocuments(): List<PdfDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: PdfDocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET isEnabled = :enabled WHERE id = :docId")
    suspend fun setDocumentEnabled(docId: Long, enabled: Boolean)

    @Query("DELETE FROM pdf_documents WHERE id = :docId")
    suspend fun deleteDocument(docId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<KnowledgeChunkEntity>)

    @Query("DELETE FROM knowledge_chunks WHERE documentId = :docId")
    suspend fun deleteChunksForDocument(docId: Long)

    @Query("SELECT * FROM knowledge_chunks WHERE documentId = :docId ORDER BY pageNumber ASC, id ASC")
    fun observeChunksForDocument(docId: Long): Flow<List<KnowledgeChunkEntity>>

    @Query(
        """
        SELECT kc.* FROM knowledge_chunks kc
        INNER JOIN pdf_documents pd ON kc.documentId = pd.id
        WHERE pd.isEnabled = 1 AND pd.indexingStatus = 'READY'
        """
    )
    suspend fun getAllActiveChunks(): List<KnowledgeChunkEntity>
}

@Dao
interface FlowConfigDao {
    @Query("SELECT * FROM flow_config WHERE id = 1")
    fun observeConfig(): Flow<FlowConfigEntity?>

    @Query("SELECT * FROM flow_config WHERE id = 1")
    suspend fun getConfig(): FlowConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: FlowConfigEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun observeConversation(conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET status = :status, unreadEscalation = :unreadEscalation, handoffReason = :reason, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateStatus(
        conversationId: String,
        status: String,
        unreadEscalation: Boolean,
        reason: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int = 8): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT COUNT(*) FROM chat_messages WHERE senderType = 'AI_BOT'")
    fun observeAiReplyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE guardrailTriggered = 1")
    fun observeGuardrailTransferCount(): Flow<Int>
}
