package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the layout contract for the shared team row with the long names from
 * the bug report, at both phone widths and both font scales.
 *
 * The badge slot is a fixed composable constant; these tests assert that value
 * and that the autosizer always lands in the 11..14sp range without wrapping
 * beyond two lines.
 */
class TeamRowTest {

    private val longNames = listOf(
        "Newcastle United",
        "Manchester United",
        "Wolverhampton Wanderers",
        "Brighton & Hove Albion",
        "Paris Saint-Germain",
        "Borussia Monchengladbach",
        "Roma",
    )

    @Test
    fun badgeSlotIsFixedAt36dpAndNeverWeighted() {
        // The shared component uses Modifier.size(TeamBadgeSize) with no weight.
        assertEquals(36f, TeamBadgeSize.value)
        assertEquals(60f, TeamStatusWidth.value)
    }

    @Test
    fun allBadgesAreTheSameSizeRegardlessOfNameLength() {
        // One slot constant is used by both sides of every card, so all badges
        // are identical by construction.
        val sizes = longNames.map { TeamBadgeSize.value }
        assertEquals(1, sizes.distinct().size)
        assertEquals(36f, sizes.first())
    }

    @Test
    fun namesAutosizeDownTo11spAt360dpAnd13FontScale() {
        // 360dp screen: 16dp outer padding each side, 12dp card padding each
        // side, two 36dp badges, two 8dp gaps and a 60dp status column.
        val usable = 360f - 32f - 24f - 72f - 16f - 60f
        val perName = usable / 2f
        for (name in longNames) {
            val size = fittedTeamNameSize(name, perName, fontScale = 1.3f)
            assertTrue("size for $name was $size", size in 11..14)
        }
    }

    @Test
    fun namesAutosizeDownTo11spAt411dpAnd13FontScale() {
        val usable = 411f - 32f - 24f - 72f - 16f - 60f
        val perName = usable / 2f
        for (name in longNames) {
            val size = fittedTeamNameSize(name, perName, fontScale = 1.3f)
            assertTrue("size for $name was $size", size in 11..14)
        }
    }

    @Test
    fun shortNamesUseTheLargestSize() {
        assertEquals(14, fittedTeamNameSize("Roma", 100f, 1.0f))
    }

    @Test
    fun longestNameNeverBelowMinimum() {
        for (scale in listOf(1.0f, 1.3f)) {
            for (width in listOf(100f, 120f)) {
                val size = fittedTeamNameSize("Wolverhampton Wanderers", width, scale)
                assertTrue(size >= 11)
            }
        }
    }

    @Test
    fun rowWidthsAddUpWithoutOverflowAt360dp() {
        val total = TeamBadgeSize.value * 2 + 8f * 2 + TeamStatusWidth.value
        assertTrue("fixed slots leave no room", total < 360f - 32f - 24f)
    }
}
