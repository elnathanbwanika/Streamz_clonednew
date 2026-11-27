package com.linkstreamz.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground notification for the mandatory update download. It only observes
 * [AppUpdateDownloadRepository]; progress is posted at most once per second
 * while terminal states are posted immediately.
 */
class AppUpdateNotificationService : Service() {
    companion object {
        const val ACTION_CANCEL = "com.linkstreamz.app.update.CANCEL"
        const val ACTION_INSTALL = "com.linkstreamz.app.update.INSTALL"
        const val EXTRA_FILE = "update_file"

        private const val PROGRESS_ID = 7201
        private const val INSTALL_ID = 7202
        private const val MIN_INTERVAL_MS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, AppUpdateNotificationService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun showTapToInstall(context: Context, file: File) {
            NotificationChannels.ensure(context)
            val install = PendingIntent.getService(
                context,
                INSTALL_ID,
                Intent(context, AppUpdateNotificationService::class.java).apply {
                    action = ACTION_INSTALL
                    putExtra(EXTRA_FILE, file.absolutePath)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Download complete")
                .setContentText("Tap to install")
                .setContentIntent(install)
                .addAction(0, "Install", install)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(INSTALL_ID, notification) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    private var foregroundStarted = false
    private var lastPostedAt = 0L

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                AppUpdateDownloadRepository.requestCancel()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_INSTALL -> {
                intent.getStringExtra(EXTRA_FILE)?.let { path ->
                    AppUpdateCompletionController.launchFromUi(this, File(path))
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (collectJob == null) {
            collectJob = scope.launch {
                AppUpdateDownloadRepository.state.collect { state -> render(state) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun render(state: AppUpdateDownloadState) {
        when (state) {
            AppUpdateDownloadState.Idle,
            is AppUpdateDownloadState.Failed -> {
                dismissProgress()
                stopSelf()
            }
            is AppUpdateDownloadState.Connecting,
            is AppUpdateDownloadState.Downloading -> postProgress(state, throttle = true)
            is AppUpdateDownloadState.Completed -> {
                if (AppUpdateForegroundTracker.isForeground) {
                    dismissProgress()
                } else {
                    showTapToInstall(this, state.file)
                    dismissProgress()
                }
                stopSelf()
            }
        }
    }

    private fun postProgress(state: AppUpdateDownloadState, throttle: Boolean) {
        val now = System.currentTimeMillis()
        if (throttle && now - lastPostedAt < MIN_INTERVAL_MS) return
        lastPostedAt = now
        val notification = buildProgressNotification(state)
        runCatching {
            if (!foregroundStarted) {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                ServiceCompat.startForeground(this, PROGRESS_ID, notification, type)
                foregroundStarted = true
            } else {
                NotificationManagerCompat.from(this).notify(PROGRESS_ID, notification)
            }
        }.onFailure { foregroundStarted = false }
    }

    private fun dismissProgress() {
        runCatching { NotificationManagerCompat.from(this).cancel(PROGRESS_ID) }
        if (foregroundStarted) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            foregroundStarted = false
        }
    }

    private fun buildProgressNotification(state: AppUpdateDownloadState): Notification {
        val open = PendingIntent.getActivity(
            this,
            PROGRESS_ID,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            PROGRESS_ID + 1,
            Intent(this, AppUpdateNotificationService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, NotificationChannels.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Cancel", cancel)

        when (state) {
            is AppUpdateDownloadState.Connecting -> builder
                .setContentTitle("Connecting...")
                .setContentText("Preparing the update")
                .setProgress(100, 0, true)
            is AppUpdateDownloadState.Downloading -> {
                val progress = state.progress
                builder
                    .setContentTitle(
                        if (progress.totalBytes == null) {
                            "Downloading update"
                        } else {
                            "Downloading update ${progress.percent}%"
                        },
                    )
                    .setContentText("${AppUpdateProgressText.size(progress)} · ${AppUpdateProgressText.detail(progress)}")
                    .setProgress(100, progress.percent, progress.isIndeterminate)
            }
            else -> builder
                .setContentTitle("Downloading update")
                .setContentText("Preparing the update")
                .setProgress(100, 0, true)
        }
        return builder.build()
    }
}
