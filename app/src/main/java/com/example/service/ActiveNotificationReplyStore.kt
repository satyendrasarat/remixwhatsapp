package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ReplyStore"

data class LiveReplyHandle(
    val conversationId: String,
    val contactName: String,
    val packageName: String,
    val actionIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>,
    val receivedAt: Long = System.currentTimeMillis()
)

/**
 * Holds active WhatsApp notification RemoteInput reply actions in memory so both
 * automated AI replies and manual Human Agent replies can dispatch back into WhatsApp.
 */
object ActiveNotificationReplyStore {
    private val activeHandles = ConcurrentHashMap<String, LiveReplyHandle>()

    fun register(
        conversationId: String,
        contactName: String,
        packageName: String,
        action: Notification.Action
    ) {
        val pendingIntent = action.actionIntent ?: return
        val inputs = action.remoteInputs ?: return
        if (inputs.isEmpty()) return

        activeHandles[conversationId] = LiveReplyHandle(
            conversationId = conversationId,
            contactName = contactName,
            packageName = packageName,
            actionIntent = pendingIntent,
            remoteInputs = inputs
        )
    }

    fun hasActiveHandle(conversationId: String): Boolean = activeHandles.containsKey(conversationId)

    fun sendReply(context: Context, conversationId: String, messageText: String): Boolean {
        val handle = activeHandles[conversationId] ?: return false
        return try {
            val fillInIntent = Intent()
            val resultsBundle = Bundle()
            for (input in handle.remoteInputs) {
                resultsBundle.putCharSequence(input.resultKey, messageText)
            }
            RemoteInput.addResultsToIntent(handle.remoteInputs, fillInIntent, resultsBundle)
            handle.actionIntent.send(context, 0, fillInIntent)
            Log.d(TAG, "Dispatched WhatsApp RemoteInput reply to ${handle.contactName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch WhatsApp RemoteInput reply", e)
            activeHandles.remove(conversationId)
            false
        }
    }
}
