package com.linkstreamz.app

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.ZoneOffset

/** Visual variant of an in-app popup. */
enum class InAppMessageStyle { INFO, ANNOUNCE, PROMO, WARN }

/** How often a message may be shown to the same user. */
enum class InAppMessageFrequency { ONCE, DAILY, ALWAYS }

/** What the popup's primary button does. */
sealed interface InAppMessageAction {
    data object None : InAppMessageAction
    data class Screen(val section: String) : InAppMessageAction
    data class Link(val url: String) : InAppMessageAction
}

/**
 * One server-controlled popup. Pure data: no Android or Compose dependencies so
 * parsing, eligibility and frequency can be unit-tested on the JVM.
 */
data class InAppMessage(
    val id: Long,
    val title: String,
    val body: String,
    val style: InAppMessageStyle,
    val image: String?,
    val buttonLabel: String?,
    val action: InAppMessageAction,
    val frequency: InAppMessageFrequency,
    val startAtMs: Long?,
    val endAtMs: Long?,
    val active: Boolean,
)

/** EAT calendar-day helpers. The server's timestamps are all in East Africa Time. */
object InAppMessageTime {
    private val eat = ZoneOffset.ofHours(3)

    /** Epoch day of the EAT calendar date containing [epochMs]. */
    fun eatDay(epochMs: Long): Long =
        Instant.ofEpochMilli(epochMs).atOffset(eat).toLocalDate().toEpochDay()
}

/** Read-only view of what this user has already seen, used by the pure rules. */
interface InAppMessageSeenState {
    fun seenOnce(id: Long): Boolean
    fun lastDailyDay(id: Long): Long?
}

/** Tolerance-first parser. Any bad entry is dropped; it never throws. */
object InAppMessageParser {
    private val sections = setOf("home", "sports", "movies", "channels", "downloads")

    /** Parses the top-level `messages` array of a freshly loaded content.json. */
    fun parse(root: JsonObject): List<InAppMessage> = parseArray(root["messages"])

    /** Parses a cached `messages` array string. */
    fun parseArrayElement(element: JsonElement?): List<InAppMessage> = parseArray(element)

    private fun parseArray(element: JsonElement?): List<InAppMessage> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull(::parseOne)
    }

    private fun parseOne(element: JsonElement): InAppMessage? {
        val obj = element as? JsonObject ?: return null

        // An id is required: frequency is tracked per id, so an unidentifiable
        // message cannot be honoured safely.
        val id = obj.primitive("id")?.longOrNull ?: return null
        val title = obj.text("title") ?: return null
        val body = obj.text("body") ?: return null
        if (obj.primitive("active")?.booleanOrNull != true) return null

        val button = obj["button"] as? JsonObject
        return InAppMessage(
            id = id,
            title = title,
            body = body,
            style = styleOf(obj.text("style")),
            image = obj.text("image"),
            buttonLabel = button?.text("label"),
            action = actionOf(button?.get("action")),
            frequency = frequencyOf(obj.text("frequency")),
            startAtMs = TimeLimitedParser.parseInstantMs(obj.text("startAt")),
            endAtMs = TimeLimitedParser.parseInstantMs(obj.text("endAt")),
            active = true,
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun styleOf(raw: String?): InAppMessageStyle = when (raw?.lowercase()) {
        "announce" -> InAppMessageStyle.ANNOUNCE
        "promo" -> InAppMessageStyle.PROMO
        "warn" -> InAppMessageStyle.WARN
        else -> InAppMessageStyle.INFO
    }

    private fun frequencyOf(raw: String?): InAppMessageFrequency = when (raw?.lowercase()) {
        "daily" -> InAppMessageFrequency.DAILY
        "always" -> InAppMessageFrequency.ALWAYS
        else -> InAppMessageFrequency.ONCE
    }

    private fun actionOf(element: JsonElement?): InAppMessageAction {
        val obj = element as? JsonObject ?: return InAppMessageAction.None
        val value = obj.text("value")
        return when (obj.text("type")?.lowercase()) {
            "screen" -> if (value != null && value.lowercase() in sections) {
                InAppMessageAction.Screen(value.lowercase())
            } else {
                InAppMessageAction.None
            }
            "link" -> if (value != null && value.startsWith("https://", ignoreCase = true)) {
                InAppMessageAction.Link(value)
            } else {
                InAppMessageAction.None
            }
            else -> InAppMessageAction.None
        }
    }
}

/** Time-window eligibility, using the caller's clock (the server-adjusted one in app). */
fun InAppMessage.isEligible(nowMs: Long): Boolean =
    active && (startAtMs == null || nowMs >= startAtMs) && (endAtMs == null || nowMs < endAtMs)

/** Frequency eligibility for one message at [nowMs]. */
fun shouldShowByFrequency(
    message: InAppMessage,
    seen: InAppMessageSeenState,
    nowMs: Long,
): Boolean = when (message.frequency) {
    InAppMessageFrequency.ONCE -> !seen.seenOnce(message.id)
    InAppMessageFrequency.DAILY -> seen.lastDailyDay(message.id) != InAppMessageTime.eatDay(nowMs)
    InAppMessageFrequency.ALWAYS -> true
}

/** Pure selection of the next popup: newest first, one at a time, capped per session. */
object InAppMessageQueue {
    const val MAX_PER_SESSION = 3

    fun next(
        messages: List<InAppMessage>,
        seen: InAppMessageSeenState,
        nowMs: Long,
        shownThisSession: Set<Long>,
        maxPerSession: Int = MAX_PER_SESSION,
    ): InAppMessage? {
        if (shownThisSession.size >= maxPerSession) return null
        return messages
            .filter { it.isEligible(nowMs) }
            .filter { it.id !in shownThisSession }
            .filter { shouldShowByFrequency(it, seen, nowMs) }
            .sortedByDescending { it.id }
            .firstOrNull()
    }
}
