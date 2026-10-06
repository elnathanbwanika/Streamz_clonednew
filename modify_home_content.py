import re

with open('app/src/main/java/com/linkstreamz/app/HomeScreen.kt', 'r') as f:
    home_kt = f.read()

content_old = """            when (val current = state) {
                FeedState.Loading -> LoadingList()
                FeedState.Error -> if (minDelayPassed && showError) ErrorState { scope.launch { repository.load() } } else LoadingList()
                is FeedState.Ready -> if (!minDelayPassed) LoadingList() else {
                    val items = when (filter) {
                        HomeFilter.HIGHLIGHTS -> current.feed.highlights.map { it as Any }
                        else -> current.feed.events.filter {
                            filter == HomeFilter.ALL || effectiveStatus(it.startMs, it.status, nowMs) == filter.name.lowercase()
                        }.map { it as Any }
                    }
                    if (items.isEmpty()) {
                        EmptyState(if (filter == HomeFilter.HIGHLIGHTS) "No highlights yet." else "No events here yet.")
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 44.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items, key = { item -> if (item is Event) "event-${item.id}" else "highlight-${(item as Highlight).id}" }) { item ->
                                when (item) {
                                    is Event -> EventCard(item, nowMs, {
                                        selectTarget(EventTarget(item), onPlayer, { dialogTarget = it }, context)
                                    })
                                    is Highlight -> HighlightCard(item, {
                                        selectTarget(HighlightTarget(item), onPlayer, { dialogTarget = it }, context)
                                    })
                                }
                            }
                        }
                    }
                }
            }"""

content_new = """            if (filter == HomeFilter.MOVIES) {
                MoviesScreen(moviesRepo, onWatch = { item ->
                    // TODO: open WatchScreen
                    val url = if (item is Movie) item.videoUrl else if (item is Series) item.seasons[0].episodes[0].videoUrl else ""
                    onPlayer(CustomTarget(StreamServer("Movies", url = url)))
                })
            } else if (filter == HomeFilter.CHANNELS) {
                ChannelsScreen(channelsRepo, onWatch = { channel ->
                    onPlayer(CustomTarget(StreamServer("Channels", url = channel.videoUrl)))
                })
            } else {
                when (val current = state) {
                    FeedState.Loading -> LoadingList()
                    FeedState.Error -> if (minDelayPassed && showError) ErrorState { scope.launch { repository.load() } } else LoadingList()
                    is FeedState.Ready -> if (!minDelayPassed) LoadingList() else {
                        val items = when (filter) {
                            HomeFilter.HIGHLIGHTS -> current.feed.highlights.map { it as Any }
                            else -> current.feed.events.filter {
                                filter == HomeFilter.ALL || effectiveStatus(it.startMs, it.status, nowMs) == filter.name.lowercase()
                            }.map { it as Any }
                        }
                        if (items.isEmpty()) {
                            EmptyState(if (filter == HomeFilter.HIGHLIGHTS) "No highlights yet." else "No events here yet.")
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 44.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(items, key = { item -> if (item is Event) "event-${item.id}" else "highlight-${(item as Highlight).id}" }) { item ->
                                    when (item) {
                                        is Event -> EventCard(item, nowMs, {
                                            selectTarget(EventTarget(item), onPlayer, { dialogTarget = it }, context)
                                        })
                                        is Highlight -> HighlightCard(item, {
                                            selectTarget(HighlightTarget(item), onPlayer, { dialogTarget = it }, context)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }"""

home_kt = home_kt.replace(content_old, content_new)

with open('app/src/main/java/com/linkstreamz/app/HomeScreen.kt', 'w') as f:
    f.write(home_kt)

