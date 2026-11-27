package com.linkstreamz.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Process-wide foreground/background signal used by the update hand-off. */
object AppUpdateForegroundTracker {
    private val lock = Any()
    private var startedCount = 0

    val isForeground: Boolean get() = synchronized(lock) { startedCount > 0 }

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                synchronized(lock) { startedCount++ }
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                synchronized(lock) {
                    if (startedCount > 0) startedCount--
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
