package com.linkstreamz.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Periodic refresh of the VPN manifest/ config. Runs in the background and only
 * persists a valid config; it never starts the tunnel (the tunnel lives only as
 * long as the app process and is started by [WgManager.init] / [WgManager.refresh]).
 */
class TunnelConfigWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result =
        if (WgManager.refreshBlocking(applicationContext)) Result.success() else Result.retry()
}
