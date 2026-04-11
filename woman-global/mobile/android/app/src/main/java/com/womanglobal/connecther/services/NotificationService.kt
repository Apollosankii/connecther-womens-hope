package com.womanglobal.connecther.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.womanglobal.connecther.ChatActivity
import com.womanglobal.connecther.HomeActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.utils.PushRegistration
import kotlin.math.abs

class NotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        val payload = remoteMessage.data
        if (payload.isNotEmpty()) Log.d(TAG, "Message data payload: $payload")
        val title = payload["title"] ?: remoteMessage.notification?.title ?: "ConnectHer"
        val message = payload["body"] ?: remoteMessage.notification?.body ?: "You have a new notification."
        val type = (payload["type"] ?: "").trim()

        if (type == "provider_approved") {
            getSharedPreferences("user_session", MODE_PRIVATE).edit()
                .putBoolean("isProvider", true)
                .putBoolean("isProviderPending", false)
                .apply()
            Log.d(TAG, "Provider approved — isProvider synced from push")
        }

        sendNotification(title, message, type, payload)
    }

    override fun onNewToken(refreshedToken: String) {
        super.onNewToken(refreshedToken)
        PushRegistration.register(this)
    }

    private fun sendNotification(
        title: String?,
        message: String?,
        messageType: String?,
        payload: Map<String, String> = emptyMap(),
    ) {
        Log.d(TAG, "Showing notification: title=$title type=$messageType")

        val notificationManager = NotificationManagerCompat.from(this)

        if (!notificationManager.areNotificationsEnabled()) {
            Log.d(TAG, "Notifications disabled — skip")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "POST_NOTIFICATIONS not granted — skip")
            return
        }

        createNotificationChannel()

        val data = payload.mapValues { (_, v) -> v ?: "" }
        val resolvedType = data["type"].orEmpty().ifBlank { messageType.orEmpty() }
        val intent = buildTapIntent(resolvedType, data).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

        val notificationId = computeNotificationId(resolvedType, data, title, message)
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /** Routes notification tap to chat or the right home tab. */
    private fun buildTapIntent(type: String, payload: Map<String, String>): Intent {
        val chatCode = payload["chat_code"].orEmpty().trim()
        val quoteId = payload["quote_id"].orEmpty().trim()
        val openChat = type in CHAT_DEEP_LINK_TYPES && chatCode.isNotEmpty()
        if (openChat) {
            return Intent(this, ChatActivity::class.java).apply {
                putExtra("chat_code", chatCode)
                if (quoteId.isNotEmpty()) putExtra("quote_id", quoteId)
            }
        }

        val tab = HOME_TAB_BY_TYPE[type]
        return Intent(this, HomeActivity::class.java).apply {
            tab?.let { putExtra("fragment_to_open", it) }
        }
    }

    private fun computeNotificationId(
        type: String,
        payload: Map<String, String>,
        title: String?,
        body: String?,
    ): Int {
        val base = payload["request_id"]?.hashCode()
            ?: payload["job_id"]?.hashCode()
            ?: payload["chat_code"]?.hashCode()
            ?: (title ?: "").hashCode() * 31 + (body ?: "").hashCode()
        val mixed = base xor type.hashCode() xor (System.currentTimeMillis() % 1_000_000).toInt()
        return abs(mixed % Int.MAX_VALUE).coerceAtLeast(1)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.default_notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "NotificationService"

        private val CHAT_DEEP_LINK_TYPES = setOf(
            "message",
            // Booking accepted should not force-open Messages/Chat; user can choose.
        )

        /** FCM [type] → HomeActivity [fragment_to_open] key (see ViewPagerAdapter tab order). */
        private val HOME_TAB_BY_TYPE = mapOf(
            "booking_created" to "jobs",
            "booking_declined" to "jobs",
            "booking_expired" to "jobs",
            "booking_cancelled" to "jobs",
            "provider_approved" to "profile",
            "services" to "services",
            "messages" to "messages",
            "jobs" to "jobs",
            "profile" to "profile",
            "home" to "home",
            "general" to "home",
        )
    }
}
