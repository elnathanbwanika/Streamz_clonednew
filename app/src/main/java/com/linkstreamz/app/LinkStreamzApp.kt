package com.linkstreamz.app

import android.app.Application
import com.google.firebase.messaging.FirebaseMessaging

class LinkStreamzApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkBootstrap.init(this)
        NotificationChannels.ensure(this)
        AppUpdateForegroundTracker.install(this)
        DownloadManager.init(this)
        FirebaseMessaging.getInstance().subscribeToTopic("all")
    }
}
