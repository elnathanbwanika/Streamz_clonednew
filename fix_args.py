import re

with open('app/src/main/java/com/linkstreamz/app/MainActivity.kt', 'r') as f:
    main_kt = f.read()

main_kt = main_kt.replace(
    """                                channelsRepo = channelsRepo,
                                onSettings = { navController.navigate("settings") },""",
    """                                channelsRepo = channelsRepo,
                                settings = settings,
                                onSettings = { navController.navigate("settings") },"""
)
with open('app/src/main/java/com/linkstreamz/app/MainActivity.kt', 'w') as f:
    f.write(main_kt)

with open('app/src/main/java/com/linkstreamz/app/Preview.kt', 'r') as f:
    preview_kt = f.read()

preview_kt = preview_kt.replace(
    """            channelsRepo = remember { ChannelsRepository() },
            onSettings = {},""",
    """            channelsRepo = remember { ChannelsRepository() },
            settings = SettingsUiState(),
            onSettings = {},"""
)
with open('app/src/main/java/com/linkstreamz/app/Preview.kt', 'w') as f:
    f.write(preview_kt)

