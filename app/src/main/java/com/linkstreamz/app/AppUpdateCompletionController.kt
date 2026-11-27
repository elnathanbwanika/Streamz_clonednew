package com.linkstreamz.app

import android.content.Context
import java.io.File

/**
 * Single install hand-off for a completed update. When the app is in the
 * foreground the system installer opens straight away; when it is backgrounded
 * only the "Tap to install" notification is left. The handled marker prevents
 * rotation/recomposition from opening the installer twice.
 */
object AppUpdateCompletionController {
    private const val PREFS = "app_update_completion"
    private const val KEY_LAUNCHED_FILE = "launchedFile"

    fun ensureHandled(context: Context, state: AppUpdateDownloadState) {
        val completed = state as? AppUpdateDownloadState.Completed ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAUNCHED_FILE, null) == completed.file.absolutePath) return
        if (AppUpdateForegroundTracker.isForeground && AppUpdateInstaller.canInstall(context)) {
            if (launchFromUi(context, completed.file)) {
                AppLogger.d("UpdateProgress", "installer launched from completion")
            }
        } else {
            AppUpdateNotificationService.showTapToInstall(context, completed.file)
        }
    }

    fun launchFromUi(context: Context, file: File): Boolean {
        val launched = AppUpdateInstaller.launchInstaller(context, file)
        if (launched) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAUNCHED_FILE, file.absolutePath).apply()
        }
        return launched
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAUNCHED_FILE).apply()
    }
}
