package herdr.dev.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import herdr.dev.app.MainActivity
import herdr.dev.app.R
import herdr.dev.app.data.models.AgentState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HerdrNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        val channel = NotificationChannel(
            CHANNEL_AGENT_UPDATES,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundMonitoringNotification(
        activeCount: Int = 0,
        attentionCount: Int = 0,
        doneCount: Int = 0,
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val summaryText = when {
            attentionCount > 0 -> "⚠️ $attentionCount waiting for approval · $activeCount active"
            doneCount > 0 -> "✅ $doneCount tasks finished · $activeCount active"
            activeCount > 0 -> "⚡ $activeCount agent${if (activeCount > 1) "s" else ""} running"
            else -> "All agents idle"
        }

        return NotificationCompat.Builder(context, CHANNEL_AGENT_UPDATES)
            .setContentTitle("Herdr Live · Fleets")
            .setContentText(summaryText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun notifyAttentionNeeded(
        paneId: String,
        currentState: AgentState,
        agentName: String = "Agent",
        titleExcerpt: String = "",
    ) {
        val title = when (currentState) {
            AgentState.BLOCKED -> "⚠️ Action Required: $agentName"
            AgentState.DONE -> "✅ Completed: $agentName"
            AgentState.WORKING -> "⚡ Running: $agentName"
            AgentState.IDLE -> "⏸️ Idle: $agentName"
        }
        val body = if (titleExcerpt.isNotBlank()) titleExcerpt else "Open pane $paneId to review output."

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_PANE_ID, paneId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            paneId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_AGENT_UPDATES)
            .setSmallIcon(if (currentState == AgentState.BLOCKED) android.R.drawable.stat_notify_error else android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (currentState == AgentState.BLOCKED) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingOpenIntent)
            .setAutoCancel(true)

        if (currentState == AgentState.BLOCKED) {
            val notifId = paneId.hashCode()

            // Helper to build quick reply pending intent
            fun makeReplyIntent(response: String, requestCodeOffset: Int): PendingIntent {
                val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_QUICK_REPLY
                    putExtra(NotificationActionReceiver.EXTRA_PANE_ID, paneId)
                    putExtra(NotificationActionReceiver.EXTRA_RESPONSE, response)
                    putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                }
                return PendingIntent.getBroadcast(
                    context,
                    notifId + requestCodeOffset,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            builder.addAction(
                android.R.drawable.checkbox_on_background,
                "✓ Approve (y)",
                makeReplyIntent("y", 1),
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "✕ Deny (n)",
                makeReplyIntent("n", 2),
            )
            builder.addAction(
                android.R.drawable.ic_menu_save,
                "♾ Remember (p)",
                makeReplyIntent("p", 3),
            )
        }

        NotificationManagerCompat.from(context).notify(paneId.hashCode(), builder.build())
    }

    companion object {
        const val CHANNEL_AGENT_UPDATES = "herdr-agent-updates"
    }
}

