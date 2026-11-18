package com.linkstreamz.app

enum class MainSection(val label: String) {
    HOME("Home"),
    SPORTS("Sports"),
    MOVIES("Movies"),
    CHANNELS("Channels"),
}

enum class SportsTab(val label: String) {
    LIVE("Live"),
    UPCOMING("Upcoming"),
    FINISHED("Finished"),
    HIGHLIGHTS("Highlights"),
}

object AppRoutes {
    const val MAIN = "main"
    const val DOWNLOADS = "downloads"
    const val PLAYER = "player"
    const val SETTINGS = "settings"

    fun notificationSection(target: String?): MainSection? = when (target) {
        "home" -> MainSection.HOME
        "movies" -> MainSection.MOVIES
        "events" -> MainSection.SPORTS
        "channels" -> MainSection.CHANNELS
        else -> null
    }
}
