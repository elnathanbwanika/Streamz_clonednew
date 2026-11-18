package com.linkstreamz.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

object AppLogger {
    val logs = MutableStateFlow<List<String>>(emptyList())

    fun d(tag: String, msg: String) {
        val safe = LogRedactor.redact(msg)
        try {
            Log.d(tag, safe)
        } catch (_: Throwable) {
            // Local unit tests have no Android Log implementation.
        }
        addLog("D/$tag: $safe")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        val safe = LogRedactor.redact(msg)
        val stack = LogRedactor.redact(t?.stackTraceToString().orEmpty())
        try {
            Log.e(tag, if (stack.isBlank()) safe else "$safe\n$stack")
        } catch (_: Throwable) {
            // Local unit tests have no Android Log implementation.
        }
        addLog("E/$tag: $safe ${stack.take(200)}")
    }

    private fun addLog(msg: String) {
        val current = logs.value.toMutableList()
        current.add(0, msg) // Add to top
        if (current.size > 50) current.removeLast()
        logs.value = current
    }
}
