import re

with open('app/src/main/java/com/linkstreamz/app/MainActivity.kt', 'r') as f:
    main_kt = f.read()

# Add repositories to remember block
repo_block = """val repository = remember { EventsRepository() }
                val moviesRepo = remember { MoviesRepository() }
                val channelsRepo = remember { ChannelsRepository() }"""

main_kt = main_kt.replace("val repository = remember { EventsRepository() }", repo_block)

home_screen_call = """HomeScreen(
                                repository = repository,
                                moviesRepo = moviesRepo,
                                channelsRepo = channelsRepo,
                                onSettings = { navController.navigate("settings") },"""

main_kt = main_kt.replace("""HomeScreen(
                                repository = repository,
                                onSettings = { navController.navigate("settings") },""", home_screen_call)

with open('app/src/main/java/com/linkstreamz/app/MainActivity.kt', 'w') as f:
    f.write(main_kt)

