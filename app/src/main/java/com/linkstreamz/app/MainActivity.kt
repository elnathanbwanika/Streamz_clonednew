package com.linkstreamz.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.PrintWriter
import java.io.StringWriter
import coil.Coil
import coil.ImageLoader

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.getStringExtra("notification_target")?.let(NotificationRouter::route)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)

        // YT-EXTRACT: Initialize NewPipeExtractor with the shared tunnel-aware client.
        try {
            val okClient = HttpClients.newBuilder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            org.schabi.newpipe.extractor.NewPipe.init(NewPipeDownloader(okClient))
            org.schabi.newpipe.extractor.ServiceList.YouTube // force-load service
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to init NewPipe", e)
        }

        // Route Coil image requests through the same tunnel-aware client.
        try {
            Coil.setImageLoader(
                ImageLoader.Builder(this)
                    .okHttpClient(HttpClients.newBuilder().build())
                    .build()
            )
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to init image loader", e)
        }

        // VPN: in-process userspace tunnel; fails open and never blocks playback.
        WgManager.init(this)

        // APP-UPDATE: Load the last known update requirement before first composition.
        AppUpdateStore.init(this, BuildConfig.VERSION_NAME)
        AppUpdateDownloader.init(this)
        
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
                val updateRequirement by AppUpdateStore.required.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current
                var resumeTick by remember { mutableIntStateOf(0) }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            AppUpdateStore.refreshRequirement()
                            resumeTick++
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(updateRequirement) {
                    AppUpdateDownloader.onAppStart(this@MainActivity, updateRequirement)
                }

                fun closeApp() {
                    finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    Runtime.getRuntime().exit(0)
                }

                var crashLog by remember { mutableStateOf(prefs.getString("crash_log", null)) }

                if (crashLog != null && updateRequirement == null) {
                    AlertDialog(
                        onDismissRequest = { 
                            prefs.edit().remove("crash_log").apply()
                            crashLog = null 
                        },
                        title = { Text("App Crashed Last Time", color = LsColor.Text) },
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
                var mainSection by rememberSaveable { mutableStateOf(MainSection.HOME) }
                var focusLimited by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    DownloadManager.resumePendingIfNeeded(this@MainActivity)
                }

                val pendingNotification by NotificationRouter.pendingTarget.collectAsStateWithLifecycle()
                LaunchedEffect(pendingNotification, navController) {
                    val destination = pendingNotification ?: return@LaunchedEffect
                    NotificationRouter.consume()
                    if (destination == "downloads") {
                        navController.navigate(AppRoutes.DOWNLOADS) { launchSingleTop = true }
                    } else {
                        mainSection = AppRoutes.notificationSection(destination) ?: MainSection.HOME
                        navController.navigate(AppRoutes.MAIN) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }

                val onboardingPrefs = remember { getSharedPreferences("permissions_onboarding", Context.MODE_PRIVATE) }
                fun requiredPermissions(): Array<String> = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        this@MainActivity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) add(android.Manifest.permission.POST_NOTIFICATIONS)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        this@MainActivity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }.toTypedArray()
                var showOnboarding by remember {
                    mutableStateOf(
                        !onboardingPrefs.getBoolean("seen", false) && requiredPermissions().isNotEmpty()
                    )
                }
                val onboardingLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { }
                if (showOnboarding && updateRequirement == null) {
                    AlertDialog(
                        onDismissRequest = {
                            onboardingPrefs.edit().putBoolean("seen", true).apply()
                            showOnboarding = false
                        },
                        shape = LsShape.Dialog,
                        containerColor = LsColor.Card,
                        tonalElevation = 0.dp,
                        iconContentColor = LinkColors.OrangePrimary,
                        titleContentColor = LinkColors.PrimaryText,
                        textContentColor = LinkColors.MutedText,
                        icon = {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(LinkColors.OrangePrimary.copy(alpha = 0.14f))
                                    .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = LinkColors.OrangePrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                        title = {
                            Text(
                                "A quick setup",
                                fontFamily = DisplayFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                letterSpacing = 0.4.sp,
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    "Allow notifications so we can alert you about new streams and show download progress. " +
                                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
                                            "Storage access lets downloads save to your Downloads folder."
                                        else "You can change these later in Android settings.",
                                    fontFamily = BodyFont,
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .background(LinkColors.OrangePrimary.copy(alpha = 0.08f), RoundedCornerShape(9.dp))
                                        .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        "You're in control — change these anytime in Settings.",
                                        color = LinkColors.MutedText,
                                        fontFamily = MonoFont,
                                        fontSize = 9.5.sp,
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onboardingPrefs.edit().putBoolean("seen", true).apply()
                                    showOnboarding = false
                                    val missing = requiredPermissions()
                                    if (missing.isNotEmpty()) onboardingLauncher.launch(missing)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LinkColors.OrangePrimary,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Continue", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                onboardingPrefs.edit().putBoolean("seen", true).apply()
                                showOnboarding = false
                            }) { Text("Not now", color = LinkColors.MutedText, fontSize = 12.sp) }
                        },
                    )
                }

                LaunchedEffect(repository) { repository.start(this) }
                LaunchedEffect(moviesRepo)  { moviesRepo.start(this) }
                LaunchedEffect(channelsRepo) { channelsRepo.start(this) }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController = navController, startDestination = AppRoutes.MAIN) {
                        composable(AppRoutes.MAIN) {
                            HomeScreen(
                                repository = repository,
                                moviesRepo = moviesRepo,
                                channelsRepo = channelsRepo,
                                settings = settings,
                                section = mainSection,
                                onSectionChange = { mainSection = it },
                                onOpenLimited = {
                                    focusLimited = true
                                    mainSection = MainSection.MOVIES
                                },
                                focusLimited = focusLimited,
                                onFocusConsumed = { focusLimited = false },
                                onSettings = { navController.navigate(AppRoutes.SETTINGS) },
                                onDownloads = { navController.navigate(AppRoutes.DOWNLOADS) { launchSingleTop = true } },
                                onPlayer = {
                                    target = it
                                    navController.navigate(AppRoutes.PLAYER)
                                },
                            )
                        }
                        composable(AppRoutes.DOWNLOADS) {
                            DownloadsScreen(
                                onBack = { navController.popBackStack() },
                                onPlay = { item ->
                                    val uri = item.fileUri
                                    if (uri.isNullOrBlank()) {
                                        android.widget.Toast.makeText(applicationContext, "File is not available yet", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        target = DownloadedTarget(
                                            localUri = uri,
                                            title = item.title,
                                            subtitle = listOfNotNull(item.seriesTitle, item.episodeTitle)
                                                .joinToString(" · ")
                                                .ifBlank { "Downloaded" },
                                            tvBadge = item.tvBadge,
                                        )
                                        navController.navigate(AppRoutes.PLAYER)
                                    }
                                },
                            )
                        }
                        composable(AppRoutes.PLAYER) {
                            target?.let {
                                PlayerScreen(
                                    target = it,
                                    settings = settings,
                                    repository = repository,
                                    moviesRepo = moviesRepo,
                                    channelsRepo = channelsRepo,
                                    onBack = { navController.popBackStack() },
                                    onOpenDownloads = {
                                        navController.navigate(AppRoutes.DOWNLOADS) { launchSingleTop = true }
                                    },
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
                        composable(AppRoutes.SETTINGS) {
                            SettingsScreen(
                                uiState = settings,
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() },
                                onRefresh = { repository.load() },
                                onCustomStream = { url, audioUrl, referer, origin, rawHeaders ->
                                    // Parse "Key: Value" lines into a map; merge with referer/origin
                                    val extraHeaders = buildMap<String, String> {
                                        rawHeaders.lines()
                                            .map { it.trim() }
                                            .filter { it.contains(':') }
                                            .forEach { line ->
                                                val k = line.substringBefore(':').trim()
                                                val v = line.substringAfter(':').trim()
                                                if (k.isNotEmpty()) put(k, v)
                                            }
                                        if (referer.isNotBlank()) put("Referer", referer)
                                        if (origin.isNotBlank()) put("Origin", origin)
                                    }
                                    val server = if (audioUrl.isNotBlank() && url.isNotBlank()) {
                                        StreamServer("Custom Split", videoUrl = url, audioUrl = audioUrl, headers = extraHeaders)
                                    } else if (audioUrl.isNotBlank()) {
                                        StreamServer("Custom Audio", url = null, audioUrl = audioUrl, headers = extraHeaders)
                                    } else {
                                        StreamServer("Custom", url = url, headers = extraHeaders)
                                    }
                                    target = CustomTarget(server)
                                    navController.navigate(AppRoutes.PLAYER)
                                },
                            )
                        }
                    }

                    updateRequirement?.let { requirement ->
                        AppUpdateOverlay(
                            requirement = requirement,
                            resumeTick = resumeTick,
                            onCloseApp = { closeApp() },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Foreground refresh: pick up manifest/config changes promptly.
        WgManager.refresh(this)
    }
}
