package herdr.dev.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import herdr.dev.app.data.HerdrSocketRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: HerdrSocketRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val paneId = intent.getStringExtra(EXTRA_PANE_ID) ?: return
        val response = intent.getStringExtra(EXTRA_RESPONSE) ?: return

        if (action == ACTION_QUICK_REPLY) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, paneId.hashCode())
            // Dismiss the notification once action is clicked
            NotificationManagerCompat.from(context).cancel(notificationId)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.sendInput(paneId, response)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_QUICK_REPLY = "herdr.dev.app.action.QUICK_REPLY"
        const val EXTRA_PANE_ID = "extra_pane_id"
        const val EXTRA_RESPONSE = "extra_response"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
