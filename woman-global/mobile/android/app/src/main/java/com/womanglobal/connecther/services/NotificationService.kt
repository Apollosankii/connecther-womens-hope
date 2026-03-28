package com.womanglobal.connecther.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.womanglobal.connecther.ChatActivity
import com.womanglobal.connecther.HomeActivity
import com.womanglobal.connecther.R
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: --------------------> " + remoteMessage.from)

        val payload = remoteMessage.data
        if (payload.isNotEmpty()) Log.d(TAG, "Message data payload: $payload")
        val title = payload["title"] ?: remoteMessage.notification?.title ?: "ConnectHer"
        val message = payload["body"] ?: remoteMessage.notification?.body ?: "You have a new notification."
        val type = payload["type"] ?: ""
        sendNotification(title, message, type, payload)
    }

    override fun onNewToken(refreshedToken: String) {
        super.onNewToken(refreshedToken)
        val token = refreshedToken.trim()
        if (token.isBlank()) return
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = SupabaseData.upsertFcmToken(token, deviceId)
            Log.d(TAG, "FCM token sync result: $ok")
        }
    }

    private fun sendNotification(
        title: String?,
        message: String?,
        messageType: String?,
        payload: Map<String, String> = emptyMap(),
    ) {
        Log.d(TAG, "Sending notification: Title = $title, Message = $message")

        val notificationManager = NotificationManagerCompat.from(this)

        if (!notificationManager.areNotificationsEnabled()) {
            Log.d(TAG, "Notifications are disabled. Skipping notification.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Notification permission not granted. Skipping notification.")
            return
        }

        createNotificationChannel()

        val data = payload
        val chatCode = data["chat_code"].orEmpty()
        val quoteId = data["quote_id"].orEmpty()
        val resolvedType = data["type"].orEmpty().ifBlank { messageType.orEmpty() }
        val intent = if ((resolvedType == "message" || resolvedType == "booking_accepted") && chatCode.isNotBlank()) {
            Intent(this, ChatActivity::class.java).apply {
                putExtra("chat_code", chatCode)
                putExtra("quote_id", quoteId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        } else {
            Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                resolvedType.takeIf { it.isNotBlank() }?.let { putExtra("fragment_to_open", it) }
            }
        }

        // Creating a PendingIntent to trigger when the notification is clicked
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "FCM_CHANNEL_ID"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(0, notificationBuilder.build())
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "FCM_CHANNEL_ID",
                "FCM Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "NotificationService"
    }
}
