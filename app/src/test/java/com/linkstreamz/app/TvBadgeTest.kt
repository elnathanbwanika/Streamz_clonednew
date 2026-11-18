package com.linkstreamz.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBadgeTest {

    private fun parse(json: String) = TvBadgeParser.parse(Json.parseToJsonElement(json))

    private fun assertPlacement(
        expectedX: Float,
        expectedY: Float,
        expectedW: Float,
        actual: TvBadgePlacement?,
    ) {
        assertNotNull(actual)
        assertEquals(expectedX, actual!!.x, 0.001f)
        assertEquals(expectedY, actual.y, 0.001f)
        assertEquals(expectedW, actual.w, 0.001f)
    }

    @Test
    fun objectParsesCardAndPlayer() {
        val badge = parse(
            """{"card":{"x":12,"y":34,"w":25},"player":{"x":60,"y":7,"w":20}}"""
        )
        assertPlacement(12f, 34f, 25f, badge?.card)
        assertPlacement(60f, 7f, 20f, badge?.player)
    }

    @Test
    fun trueUsesDefaultsForBothPlacements() {
        val badge = parse("true")
        assertPlacement(4f, 6f, 30f, badge?.card)
        assertPlacement(79f, 5f, 18f, badge?.player)
    }

    @Test
    fun falseMeansNoBadge() {
        assertNull(parse("false"))
    }

    @Test
    fun nullMeansNoBadge() {
        assertNull(TvBadgeParser.parse(JsonNull))
    }

    @Test
    fun missingMeansNoBadge() {
        assertNull(TvBadgeParser.parse(null))
    }

    @Test
    fun partialUsesDefaultsForMissingNumbers() {
        val badge = parse("""{"card":{"w":20}}""")
        assertPlacement(4f, 6f, 20f, badge?.card)
        assertNull(badge?.player)
    }

    @Test
    fun truePlacementUsesDefaults() {
        val badge = parse("""{"card":true,"player":false}""")
        assertPlacement(4f, 6f, 30f, badge?.card)
        assertNull(badge?.player)
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val badge = parse(
            """{"card":{"x":-50,"y":200,"w":200},"player":{"x":500,"y":-10,"w":1}}"""
        )
        assertPlacement(0f, 100f, 60f, badge?.card)
        assertPlacement(92f, 0f, 8f, badge?.player)
    }

    @Test
    fun invalidNumbersFallBackToDefaults() {
        val badge = parse(
            """{"card":{"x":"oops","y":null,"w":"NaN"},"player":{"x":[],"y":{},"w":"Infinity"}}"""
        )
        assertPlacement(4f, 6f, 30f, badge?.card)
        assertPlacement(79f, 5f, 18f, badge?.player)
    }

    @Test
    fun resolvedRectAlwaysStaysInsideFrame() {
        val placement = TvBadgeParser.DefaultPlayer.copy(x = 99f, y = 99f, w = 60f)
        val rect = placement.resolve(frameWidth = 640f, frameHeight = 360f)

        assertTrue(rect.left >= 0f)
        assertTrue(rect.top >= 0f)
        assertTrue(rect.left + rect.width <= 640.001f)
        assertTrue(rect.top + rect.height <= 360.001f)
    }

    @Test
    fun invalidBadgeShapesDoNotCrash() {
        assertNull(parse("[]"))
        assertNull(parse("\"badge\""))
        assertNull(parse("123"))
        assertFalse(parse("{}") != null)
    }
}
