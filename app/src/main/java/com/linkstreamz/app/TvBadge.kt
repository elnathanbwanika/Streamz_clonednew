package com.linkstreamz.app

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class TvBadgePlacement(
    val x: Float,
    val y: Float,
    val w: Float,
) {
    fun resolve(frameWidth: Float, frameHeight: Float): TvBadgeRect {
        if (frameWidth <= 0f || frameHeight <= 0f) return TvBadgeRect(0f, 0f, 0f, 0f)
        val width = frameWidth * (w / 100f)
        val height = width * (96f / 340f)
        val left = (frameWidth * (x / 100f)).coerceIn(0f, (frameWidth - width).coerceAtLeast(0f))
        val top = (frameHeight * (y / 100f)).coerceIn(0f, (frameHeight - height).coerceAtLeast(0f))
        return TvBadgeRect(left, top, width, height)
    }
}

data class TvBadgeRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class TvBadge(
    val card: TvBadgePlacement? = null,
    val player: TvBadgePlacement? = null,
)

object TvBadgeParser {
    val DefaultCard = TvBadgePlacement(x = 4f, y = 6f, w = 30f)
    val DefaultPlayer = TvBadgePlacement(x = 79f, y = 5f, w = 18f)

    private const val MIN_W = 8f
    private const val MAX_W = 60f

    fun parse(element: JsonElement?): TvBadge? {
        if (element == null || element is JsonNull) return null
        if (element is JsonObject) {
            val card = parsePlacement(element["card"], DefaultCard)
            val player = parsePlacement(element["player"], DefaultPlayer)
            return if (card == null && player == null) null else TvBadge(card, player)
        }
        if (element is JsonPrimitive && element.asBooleanOrNull() == true) {
            return TvBadge(DefaultCard, DefaultPlayer)
        }
        return null
    }

    private fun parsePlacement(element: JsonElement?, default: TvBadgePlacement): TvBadgePlacement? {
        if (element == null || element is JsonNull) return null
        if (element is JsonPrimitive) {
            return if (element.asBooleanOrNull() == true) default else null
        }
        val obj = element as? JsonObject ?: return null
        val w = obj.float("w")?.takeIf { it.isFinite() }?.coerceIn(MIN_W, MAX_W) ?: default.w
        val rawX = obj.float("x")?.takeIf { it.isFinite() } ?: default.x
        val rawY = obj.float("y")?.takeIf { it.isFinite() } ?: default.y
        return TvBadgePlacement(
            x = rawX.coerceIn(0f, (100f - w).coerceAtLeast(0f)),
            y = rawY.coerceIn(0f, 100f),
            w = w,
        )
    }

    private fun JsonObject.float(key: String): Float? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.trim()?.toFloatOrNull()
    }

    private fun JsonPrimitive.asBooleanOrNull(): Boolean? =
        booleanOrNull ?: when (contentOrNull?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
}

internal fun TvBadgePlacement.storageValue(): String = "$x,$y,$w"

internal fun parseStoredPlacement(value: String?): TvBadgePlacement? {
    if (value.isNullOrBlank()) return null
    val parts = value.split(',')
    if (parts.size != 3) return null
    val x = parts[0].trim().toFloatOrNull() ?: return null
    val y = parts[1].trim().toFloatOrNull() ?: return null
    val w = parts[2].trim().toFloatOrNull() ?: return null
    if (!x.isFinite() || !y.isFinite() || !w.isFinite()) return null
    return TvBadgePlacement(x = x, y = y, w = w)
}
