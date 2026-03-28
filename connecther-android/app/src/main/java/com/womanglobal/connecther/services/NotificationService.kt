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
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.womanglobal.connecther.HomeActivity
import com.womanglobal.connecther.R

class NotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}, data: ${remoteMessage.data}")

        // Prefer data payload for type-aware routing; fall back to notification payload
        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"] ?: ""
            val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title
            val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body
            val fragment = when (type) {
                "message", "chat" -> "messages"
                "approval", "provider_approved" -> "profile"
                "job", "jobs" -> "jobs"
                else -> ""
            }
            val (displayTitle, displayBody) = when (type) {
                "approval", "provider_approved" -> Pair(
                    title?.ifBlank { null } ?: "Application approved",
                    body?.ifBlank { null } ?: "Your provider application has been approved. You can now offer services."
                )
                "message", "chat" -> Pair(
                    title?.ifBlank { null } ?: "New message",
                    body?.ifBlank { null } ?: "You have a new chat message."
                )
                "job" -> Pair(
                    title?.ifBlank { null } ?: "Job update",
                    body?.ifBlank { null } ?: "There is an update on your job."
                )
                else -> Pair(
                    title?.ifBlank { null } ?: "New Score Update",
                    body?.ifBlank { null } ?: (remoteMessage.data["score"]?.let { "Score: $it" } ?: remoteMessage.data["time"] ?: "Notification")
                )
            }
            sendNotification(displayTitle, displayBody, fragment, remoteMessage.data)
            return
        }

        if (remoteMessage.notification != null) {
            sendNotification(
                remoteMessage.notification!!.title,
                remoteMessage.notification!!.body,
                "",
                emptyMap()
            )
        }
    }

    override fun onNewToken(refreshedToken: String) {
        super.onNewToken(refreshedToken)
        Log.d(TAG, "-----------------------> New FCM token: $refreshedToken")
        // Upload to Supabase when using Clerk/Supabase so backend can send push
        if (SupabaseData.isConfigured()) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val deviceId = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.ID}"
            scope.launch {
                runCatching {
                    SupabaseData.upsertFcmToken(refreshedToken, deviceId)
                }.onFailure { e -> Log.e(TAG, "Failed to upsert FCM token to Supabase", e) }
            }
        }
    }

    private fun sendNotification(
        title: String?,
        message: String?,
        messageType: String?,
        data: Map<String, String> = emptyMap()
    ) {
        val t = title?.ifBlank { null } ?: "ConnectHer"
        val m = message?.ifBlank { null } ?: "New notification"
        Log.d(TAG, "Sending notification: Title = $t, Message = $m, type = $messageType")

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

        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            messageType?.takeIf { it.isNotBlank() }?.let { putExtra("fragment_to_open", it) }
            data["chat_code"]?.let { putExtra("chat_code", it) }
            data["quote_id"]?.let { putExtra("quote_id", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (messageType?.hashCode() ?: 0) and 0x7FFFFFFF,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "FCM_CHANNEL_ID"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(t)
            .setContentText(m)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notifId = (t.hashCode() xor m.hashCode()) and 0x7FFFFFFF
        notificationManager.notify(notifId, notificationBuilder.build())
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
