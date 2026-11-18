package com.linkstreamz.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val ANNOUNCEMENTS = "link_streamz_announcements"
    const val DOWNLOADS = "link_streamz_downloads"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                ANNOUNCEMENTS,
                "Announcements",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "New movies, events and important Link Streamz updates"
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                DOWNLOADS,
                "Download progress",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Silent progress updates for downloads"
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
