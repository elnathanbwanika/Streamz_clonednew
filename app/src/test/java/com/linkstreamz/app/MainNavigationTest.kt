package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainNavigationTest {
    @Test
    fun `notification targets map to the four main tabs`() {
        assertEquals(MainSection.HOME, AppRoutes.notificationSection("home"))
        assertEquals(MainSection.SPORTS, AppRoutes.notificationSection("events"))
        assertEquals(MainSection.MOVIES, AppRoutes.notificationSection("movies"))
        assertEquals(MainSection.CHANNELS, AppRoutes.notificationSection("channels"))
        assertNull(AppRoutes.notificationSection("downloads"))
        assertNull(AppRoutes.notificationSection("unknown"))
    }

    @Test
    fun `main tabs do not include the old sports filters`() {
        assertEquals(
            listOf("Home", "Sports", "Movies", "Channels"),
            MainSection.entries.map { it.label },
        )
        assertEquals(
            listOf("Live", "Upcoming", "Finished", "Highlights"),
            SportsTab.entries.map { it.label },
        )
    }
}
