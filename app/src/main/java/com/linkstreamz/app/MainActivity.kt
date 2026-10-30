package com.linkstreamz.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.widget.FrameLayout
import android.view.ViewGroup
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TOKEN: Initialise browser-identity token minting stack (must be before setContent)
        TokenEngine.init(this)
        // Attach the hidden 1×1 WebView to the window's decor view so it is part of
        // the real view hierarchy. A WebView without a window root will not fire
        // shouldInterceptRequest or execute JavaScript on many Android versions.
        val webHost = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
        }
        window.addContentView(webHost, ViewGroup.LayoutParams(1, 1))
        TokenEngine.minter.attach(webHost)

        // YT-EXTRACT: Initialize NewPipeExtractor with our OkHttp downloader (Step 2)
        try {
            val okClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            org.schabi.newpipe.extractor.NewPipe.init(NewPipeDownloader(okClient))
            org.schabi.newpipe.extractor.ServiceList.YouTube // force-load service
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to init NewPipe", e)
        }
        
        val prefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            prefs.edit().putString("crash_log", sw.toString()).commit()
            defaultHandler?.uncaughtException(thread, exception)
        }

        setContent {
            LinkStreamzTheme {
                var crashLog by remember { mutableStateOf(prefs.getString("crash_log", null)) }

                if (crashLog != null) {
                    AlertDialog(
                        onDismissRequest = { 
                            prefs.edit().remove("crash_log").apply()
                            crashLog = null 
                        },
                        title = { Text("App Crashed Last Time") },
                        text = { 
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text(crashLog ?: "")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Crash Log", crashLog)
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(this@MainActivity, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copy Log")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                prefs.edit().remove("crash_log").apply()
                                crashLog = null 
                            }) {
                                Text("Dismiss")
                            }
                        }
                    )
                }

                val navController = rememberNavController()
                val repository = remember { EventsRepository() }
                val moviesRepo = remember { MoviesRepository() }
                val channelsRepo = remember { ChannelsRepository() }
                val settingsRepository = remember { SettingsRepository(applicationContext) }
                
                // Using custom factory to pass the repository
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return SettingsViewModel(settingsRepository) as T
                    }
                }
                val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
                val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
                
                var target by remember { mutableStateOf<PlayerTarget?>(null) }

                LaunchedEffect(repository) { repository.start(this) }
                LaunchedEffect(moviesRepo)  { moviesRepo.start(this) }
                LaunchedEffect(channelsRepo) { channelsRepo.start(this) }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                repository = repository,
                                moviesRepo = moviesRepo,
                                channelsRepo = channelsRepo,
                                settings = settings,
                                onSettings = { navController.navigate("settings") },
                                onPlayer = {
                                    target = it
                                    navController.navigate("player")
                                },
                            )
                        }
                        composable("player") {
                            target?.let {
                                PlayerScreen(
                                    target = it,
                                    settings = settings,
                                    repository = repository,
                                    moviesRepo = moviesRepo,
                                    channelsRepo = channelsRepo,
                                    onBack = { navController.popBackStack() },
                                    onTarget = { newTarget ->
                                        if (newTarget.servers.isEmpty()) {
                                            android.widget.Toast.makeText(applicationContext, "No streams available", android.widget.Toast.LENGTH_SHORT).show()
                                        } else if (newTarget.servers.size == 1) {
                                            target = newTarget.withServer(newTarget.servers.first())
                                        } else {
                                            target = newTarget
                                        }
                                    }
                                )
                            }
                        }
                        composable("settings") {
                            SettingsScreen(
                                uiState = settings,
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                                onRefresh = { repository.load() },
                                onCustomStream = { url, audioUrl ->
                                    val server = if (audioUrl.isNotBlank() && url.isNotBlank()) {
                                        StreamServer("Custom Split", videoUrl = url, audioUrl = audioUrl)
                                    } else if (audioUrl.isNotBlank()) {
                                        StreamServer("Custom Audio", url = null, audioUrl = audioUrl)
                                    } else {
                                        StreamServer("Custom", url = url)
                                    }
                                    target = CustomTarget(server)
                                    navController.navigate("player")
                                },
                            )
                        }
                    }

                    // Floating Logs Button — hidden when player is in fullscreen
                    var showLogs by remember { mutableStateOf(false) }
                    val currentLogs by AppLogger.logs.collectAsStateWithLifecycle()
                    val isPlayerFullscreen by PlayerFullscreenState.isFullscreen

                    if (!isPlayerFullscreen) {
                        androidx.compose.material3.SmallFloatingActionButton(
                            onClick = { showLogs = true },
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Text("Logs")
                        }
                    }

                    if (showLogs) {
                        AlertDialog(
                            onDismissRequest = { showLogs = false },
                            title = { Text("App Logs") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    Text(
                                        text = currentLogs.joinToString("\n\n"),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("App Logs", currentLogs.joinToString("\n"))
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(this@MainActivity, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Copy Logs")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogs = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
