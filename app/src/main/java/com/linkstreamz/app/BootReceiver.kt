package com.linkstreamz.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            DownloadStore.init(context)
            DownloadStore.recoverInterrupted()
            DownloadManager.init(context)
            if (DownloadManager.hasPending()) {
                DownloadManager.startService(context, DownloadService.ACTION_START)
            }
        }
    }
}
