package com.linkstreamz.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PushMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("all")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: "Link Streamz"
        val body = data["body"].orEmpty()
        val format = data["format"]?.lowercase() ?: "std"
        val target = data["target"] ?: "home"
        val actionLabel = (data["buttonLabel"] ?: data["button"])?.takeIf { it.isNotBlank() }
        val actionUrl = data["buttonUrl"]?.takeIf { it.isNotBlank() }
        val imageUrl = data["image"]?.takeIf { format == "pic" && it.isNotBlank() }
        val id = (data["sentAt"] ?: System.currentTimeMillis().toString()).hashCode()

        scope.launch {
            val image = imageUrl?.let { loadImage(it) }
            post(id, title, body, format, target, actionLabel, actionUrl, image)
        }
    }

    private fun post(
        id: Int,
        title: String,
        body: String,
        format: String,
        target: String,
        actionLabel: String?,
        actionUrl: String?,
        image: Bitmap?,
    ) {
        NotificationChannels.ensure(this)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_target", target)
        }
        val open = PendingIntent.getActivity(
            this,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, NotificationChannels.ANNOUNCEMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        when {
            format == "pic" && image != null -> builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(image)
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
            else -> if (format == "big" || body.length > 80) {
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
        }

        if (actionLabel != null) {
            val actionIntent = if (actionUrl != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                openIntent
            }
            val actionPending = PendingIntent.getActivity(
                this,
                id + 1,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, actionLabel, actionPending)
        }

        try {
            NotificationManagerCompat.from(this).notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; the app remains usable.
        }
    }

    private fun loadImage(url: String): Bitmap? = runCatching {
        val client = HttpClients.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        client.newCall(okhttp3.Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()
}
