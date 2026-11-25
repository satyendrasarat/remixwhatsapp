package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.ReplyFlowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "WhatsAppListener"

class WhatsAppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ReplyFlowRepository

    // Deduplication and per-contact cooldown tracking
    private val recentFingerprints = ConcurrentHashMap<String, Long>()
    private val lastReplyTimestamps = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        repository = ReplyFlowRepository(applicationContext)
        HandoffNotifier.ensureChannel(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val pkg = statusBarNotification.packageName ?: return
        if (pkg !in SUPPORTED_PACKAGES) return

        val notification = statusBarNotification.notification ?: return
        // Ignore bundled summary notifications to prevent duplicate/malformed replies
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val rawText = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()

        if (rawTitle.isBlank() || rawText.isBlank()) return
        if (isSystemSummaryText(rawText)) return

        // Find the reply Notification.Action carrying RemoteInput
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        } ?: return

        val isGroupChat = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
            rawTitle.contains(":")
        val contactName = rawTitle.substringBefore(":").trim().ifBlank { rawTitle }
        val conversationId = ReplyFlowRepository.buildConversationId(
            contactName = contactName,
            packageName = pkg,
            isSimulated = false
        )

        // Register active reply action so AI or Human Agent can send reply back to WhatsApp
        ActiveNotificationReplyStore.register(
            conversationId = conversationId,
            contactName = contactName,
            packageName = pkg,
            action = replyAction
        )

        // Deduplicate identical notification updates within 10 seconds
        val now = System.currentTimeMillis()
        val fingerprint = "$conversationId|$rawText"
        val prevSeen = recentFingerprints[fingerprint]
        if (prevSeen != null && (now - prevSeen) < 10_000L) {
            return
        }
        recentFingerprints[fingerprint] = now
        pruneOldEntries(recentFingerprints, now, 60_000L)

        serviceScope.launch {
            try {
                val config = repository.getOrCreateConfig()
                val cooldownMs = (config.cooldownSeconds.coerceAtLeast(1)) * 1000L
                val lastReplied = lastReplyTimestamps[conversationId] ?: 0L
                if (now - lastReplied < cooldownMs) {
                    Log.d(TAG, "Skipping $contactName due to ${config.cooldownSeconds}s cooldown.")
                    return@launch
                }
                lastReplyTimestamps[conversationId] = now

                val outcome = repository.processIncomingMessage(
                    contactName = contactName,
                    messageText = rawText,
                    packageName = pkg,
                    isGroupChat = isGroupChat,
                    isSimulated = false
                )
                Log.d(
                    TAG,
                    "Processed WhatsApp msg from $contactName -> status=${outcome.status}, dispatched=${outcome.dispatchedToWhatsApp}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error handling WhatsApp notification for $contactName", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun isSystemSummaryText(text: String): Boolean {
        val lower = text.lowercase()
        return lower.endsWith("new messages") ||
            lower.contains("messages from") && lower.contains("chats") ||
            lower == "checking for new messages" ||
            lower == "incoming voice call" ||
            lower == "incoming video call"
    }

    private fun pruneOldEntries(map: ConcurrentHashMap<String, Long>, now: Long, maxAgeMs: Long) {
        if (map.size > 100) {
            val iterator = map.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > maxAgeMs) iterator.remove()
            }
        }
    }

    companion object {
        val SUPPORTED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
