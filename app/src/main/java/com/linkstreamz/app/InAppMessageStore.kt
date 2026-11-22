package com.linkstreamz.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Local state for in-app messages: the last good `messages` array (for offline
 * launches) and per-id seen markers. Mirrors the way AppUpdateStore is initialised
 * from MainActivity.onCreate.
 */
object InAppMessageStore : InAppMessageSeenState {
    private const val PREFS = "in_app_messages"
    private const val KEY_CACHE = "cached_messages"
    private const val KEY_ONCE = "once_"
    private const val KEY_DAILY = "daily_"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var prefs: SharedPreferences? = null

    private val _messages = MutableStateFlow<List<InAppMessage>>(emptyList())
    val messages: StateFlow<List<InAppMessage>> = _messages.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Seed from the last good cache so an offline launch can still show messages.
        _messages.value = cachedMessages()
    }

    /**
     * Called from the content feed parser after a successful content.json load.
     * Only a well-formed `messages` array replaces the cache, so a bad response
     * can never wipe the last good list.
     */
    fun ingest(root: JsonObject) {
        val array = root["messages"] as? JsonArray ?: return
        val parsed = InAppMessageParser.parseArrayElement(array)
        prefs?.edit()?.putString(KEY_CACHE, array.toString())?.apply()
        _messages.value = parsed
    }

    /** Parses the locally cached array; any corruption yields an empty list. */
    fun cachedMessages(): List<InAppMessage> {
        val raw = prefs?.getString(KEY_CACHE, null) ?: return emptyList()
        return runCatching {
            InAppMessageParser.parseArrayElement(json.parseToJsonElement(raw))
        }.getOrDefault(emptyList())
    }

    /** Live list plus cached list, de-duplicated by id and newest first. */
    fun candidates(): List<InAppMessage> =
        (_messages.value + cachedMessages())
            .associateBy { it.id }
            .values
            .sortedByDescending { it.id }

    override fun seenOnce(id: Long): Boolean =
        prefs?.getBoolean(KEY_ONCE + id, false) == true

    override fun lastDailyDay(id: Long): Long? {
        val value = prefs?.getLong(KEY_DAILY + id, Long.MIN_VALUE) ?: return null
        return value.takeUnless { it == Long.MIN_VALUE }
    }

    /** Records a dismissal or button tap. "always" is tracked per session instead. */
    fun markSeen(message: InAppMessage, nowMs: Long) {
        val p = prefs ?: return
        p.edit().apply {
            when (message.frequency) {
                InAppMessageFrequency.ONCE -> putBoolean(KEY_ONCE + message.id, true)
                InAppMessageFrequency.DAILY ->
                    putLong(KEY_DAILY + message.id, InAppMessageTime.eatDay(nowMs))
                InAppMessageFrequency.ALWAYS -> Unit
            }
        }.apply()
    }
}
