package com.linkstreamz.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

object AppLogger {
    val logs = MutableStateFlow<List<String>>(emptyList())

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        addLog("D/$tag: $msg")
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        val stack = t?.stackTraceToString()?.take(200) ?: ""
        addLog("E/$tag: $msg $stack")
    }

    private fun addLog(msg: String) {
        val current = logs.value.toMutableList()
        current.add(0, msg) // Add to top
        if (current.size > 50) current.removeLast()
        logs.value = current
    }
}
