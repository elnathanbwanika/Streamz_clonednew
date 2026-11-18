package com.linkstreamz.app

import kotlinx.coroutines.flow.MutableStateFlow

object NotificationRouter {
    val pendingTarget = MutableStateFlow<String?>(null)

    fun route(value: String?) {
        val clean = value?.trim()?.lowercase()
        if (clean in setOf("home", "movies", "events", "channels", "downloads")) {
            pendingTarget.value = clean
        }
    }

    fun consume(): String? = pendingTarget.value.also { pendingTarget.value = null }
}
