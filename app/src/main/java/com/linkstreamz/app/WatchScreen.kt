package com.linkstreamz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
fun WatchScreen(
    item: Any,
    settings: SettingsUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedUrl by remember { mutableStateOf("") }
    
    val baseClient = remember(settings.customProxyUrl) {
        val builder = okhttp3.OkHttpClient.Builder()
        builder.proxy(java.net.Proxy.NO_PROXY)
        builder.build()
    }

    val engine = remember {
        PlayerEngine(
            context = context,
            baseClient = baseClient,
            globalDelaySeconds = settings.streamDelaySeconds,
        ) {
            // on fallback
        }
    }
    
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                engine.player.playWhenReady = false
            } else if (event == Lifecycle.Event.ON_RESUME) {
                engine.player.playWhenReady = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(item) {
        selectedUrl = when (item) {
            is Movie -> item.videoUrl
            is Channel -> item.videoUrl
            is Series -> item.seasons.firstOrNull()?.episodes?.firstOrNull()?.videoUrl ?: ""
            else -> ""
        }
    }
    
    LaunchedEffect(selectedUrl) {
        if (selectedUrl.isNotEmpty()) {
            val isChannel = item is Channel
            engine.loadStream(StreamServer("Inline", url = selectedUrl))
            engine.player.repeatMode = if (isChannel) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
            engine.player.playWhenReady = true
        }
    }

    Column(Modifier.fillMaxSize().background(LinkColors.ScreenBg)) {
        // Player section (top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            val exoPlayer = engine.player
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = exoPlayer
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )
            // Back button overlaid on video
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }

        // Details list below
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                when (item) {
                    is Movie -> {
                        Text(item.title, color = LinkColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFont)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${item.genre} • ${item.year} • ${item.duration}", color = LinkColors.MutedText, fontSize = 14.sp)
                    }
                    is Channel -> {
                        Text(item.title, color = LinkColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFont)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(item.program, color = LinkColors.MutedText, fontSize = 14.sp)
                    }
                    is Series -> {
                        Text(item.title, color = LinkColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFont)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${item.genre} • ${item.year}", color = LinkColors.MutedText, fontSize = 14.sp)
                    }
                }
            }
            
            if (item is Series) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Episodes", color = LinkColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFont)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                item.seasons.forEach { season ->
                    item {
                        Text("Season ${season.number}", color = LinkColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(season.episodes) { episode ->
                        val isPlaying = selectedUrl == episode.videoUrl
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isPlaying) LinkColors.OrangePrimary.copy(alpha = 0.1f) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .clickable { selectedUrl = episode.videoUrl }
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = episode.thumbUrl,
                                contentDescription = null,
                                modifier = Modifier.width(120.dp).aspectRatio(16f/9f).background(LinkColors.CardBg)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(episode.title, color = if (isPlaying) LinkColors.OrangePrimary else LinkColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(episode.duration, color = LinkColors.MutedText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
