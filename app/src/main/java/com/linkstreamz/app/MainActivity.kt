package com.linkstreamz.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.PictureInPictureUiState
import androidx.annotation.RequiresApi
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.media3.common.Player
import androidx.compose.runtime.mutableStateOf
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

    /** Set while the player screen is mounted and showing video. */
    private var pipController: PipController? = null

    /** The on-screen video view, used for the PiP source rect hint. */
    private var videoViewRef: android.view.View? = null

    internal fun setPipVideoView(view: android.view.View?) {
        videoViewRef = view
    }

    /** Read-only accessor for the PiP diagnostics log. */
    internal fun videoViewForPip(): android.view.View? = videoViewRef

    /** True while this activity is hosted in a PiP window. */
    private val inPipState = mutableStateOf(false)

    /**
     * Live PiP state. Exposed as [State] (not a plain Boolean) so Compose
     * subscribes and recomposes when the window enters/leaves PiP.
     */
    val isInPipState: androidx.compose.runtime.State<Boolean> get() = inPipState

    /**
     * Android 13+ transition flag (`isTransitioningToPip`). It is true only
     * briefly, *before* [isInPictureInPictureMode] flips — that early window is
     * the whole point: the first small frame must already be video-only, or the
     * system draws the squeezed normal page for a frame or two.
     */
    private val pipTransitioningState = mutableStateOf(false)

    val isPipTransitioningState: androidx.compose.runtime.State<Boolean>
        get() = pipTransitioningState

    /**
     * True while the window is in PiP *or* about to shrink into it. Read live
     * (non-Compose callers use this); the Compose mirror is
     * [isPipTransitioningState].
     */
    internal val isInPipNow: Boolean
        get() = isInPictureInPictureMode || pipTransitioningState.value

    /**
     * Single source of truth for the layout mode.
     *
     * The platform flag [isInPictureInPictureMode] is authoritative and always
     * current; [inPipState] only mirrors it so Compose can observe it. Every
     * lifecycle callback and every PiP transition calls this, so the layout can
     * never be left in "normal" mode by a stale cached flag — and it is
     * deliberately independent of playback state (playing/paused/buffering/
     * ended/error), of touches and of controller visibility.
     */
    private fun syncPipState(trigger: String) {
        val inPip = isInPictureInPictureMode
        if (inPipState.value != inPip) {
            inPipState.value = inPip
            pipController?.onPipModeChanged(inPip)
        }
        AppLogger.d(
            "Pip",
            "layoutMode=" + (if (inPip) "videoOnly" else "normal") +
                ", pip=" + inPip + ", trigger=" + trigger,
        )
    }

    internal fun attachPip(player: Player) {
        val existing = pipController
        if (existing != null && existing.player === player) return
        val controller = PipController(this, player)
        controller.onPipChanged = { inPip -> inPipState.value = inPip }
        pipController = controller
        controller.register()
    }

    internal fun setPipLive(live: Boolean) = pipController?.setLiveContent(live)

    internal fun setPipVideoSize(width: Int, height: Int) = pipController?.setVideoSize(width, height)

    internal fun setPipPlaying(playing: Boolean) = pipController?.setPlaying(playing)

    internal fun setPipTitle(title: String, subtitle: String) = pipController?.setTitle(title, subtitle)

    internal fun setPipCloseHandler(onClose: () -> Unit) {
        pipController?.onCloseRequested = onClose
    }

    /**
     * Android 12+: let the system enter PiP automatically when the user swipes
     * home, with the correct ratio and actions already attached. Pre-12 falls
     * back to [onUserLeaveHint].
     */
    internal fun enableAutoEnter() = updateAutoEnter(true)

    internal fun disableAutoEnter() = updateAutoEnter(false)

    private fun updateAutoEnter(enabled: Boolean) {
        val controller = pipController ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            // On Android 12+ the close button is the system's own; wiring it here
            // means dismissing the window pauses playback instead of leaving audio
            // running behind a finished activity.
            controller.applyCloseAction()
            setPictureInPictureParams(controller.buildParams(autoEnter = enabled))
        } catch (_: IllegalArgumentException) {
            AppLogger.e("Pip", "setAutoEnterEnabled rejected")
        } catch (_: IllegalStateException) {
            AppLogger.e("Pip", "setAutoEnterEnabled after window closed")
        }
    }

    /** Called by the player's "Mini player" button. */
    internal fun enterPip(): Boolean {
        val controller = pipController ?: return false
        // Source rect hint: the video view's on-screen bounds, so the system can
        // animate into/out of the PiP window smoothly instead of a hard cut.
        val hint = videoViewRef?.let { view ->
            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
                null
            } else {
                // getGlobalVisibleRect gives the view's visible bounds in window
                // coordinates — exactly what the system animates from/to. Using
                // screen coords would be off by the status bar inset.
                val r = Rect()
                if (view.getGlobalVisibleRect(r)) r else null
            }
        }
        return controller.enter(hint)
    }

    internal fun detachPip() {
        pipController?.unregister()
        pipController = null
        // Do NOT clear the PiP flag here. detachPip runs when the player effect
        // restarts (e.g. the user pauses from the PiP window), which is not the
        // same thing as leaving PiP. The layout mode is owned by the platform
        // state and re-derived through syncPipState().
    }

    /** Re-derive the layout mode from the platform. Safe to call any time. */
    internal fun refreshPipState(trigger: String) = syncPipState(trigger)

    /**
     * Called by the player when the user leaves the app (Home gesture/button)
     * while playing. Android 12+ auto-enters via setAutoEnterEnabled, so this
     * is the pre-12 fallback.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            pipController?.enter()
        }
        syncPipState("onUserLeaveHint")
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // We have arrived (or left): the transition flag has done its job.
        pipTransitioningState.value = false
        // Final authority: the platform flag is already current here.
        syncPipState("onPictureInPictureModeChanged")
    }

    /**
     * Android 13+ (API 33): fires when the PiP transition *starts*, before the
     * window has shrunk, and again when the system overlay is shown/hidden.
     * [onPictureInPictureModeChanged] stays the final authority; this only
     * re-derives the layout mode so the first small frame is already video-only.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        // Publish the transition flag FIRST: it is the only signal available
        // before the window shrinks, and the next frame must be video-only.
        pipTransitioningState.value = pipState.isTransitioningToPip
        if (pipState.isTransitioningToPip) {
            pipController?.onPipModeChanged(true)
        }
        syncPipState("onPictureInPictureUiStateChanged")
    }

    /**
     * Every one of these can be the event that would otherwise let the normal
     * page reappear inside the PiP window (e.g. after the player effect restarts
     * on a pause). Re-derive the layout mode from the platform each time — the
     * mode depends ONLY on the PiP state, never on playback state.
     */
    override fun onResume() {
        super.onResume()
        syncPipState("onResume")
    }

    override fun onPause() {
        super.onPause()
        syncPipState("onPause")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // PiP window resize / rotation / screen off-on: keep the video-only mode.
        syncPipState("onConfigurationChanged")
    }

    override fun onStop() {
        super.onStop()
        // While in PiP the activity is only paused, but if a device does deliver
        // ON_STOP we must keep the action receiver registered, otherwise the
        // window's play/pause buttons would go dead. isInPipNow also covers the
        // Android 13 transition window, so a stop mid-transition cannot unregister.
        val pipActive = isInPipNow
        if (!pipActive) {
            pipController?.unregister()
        }
        // The PiP window was dismissed while we were in it: stop playback so no
        // audio survives the closed window (pre-Android-12 fallback path).
        if (pipController?.isInPip == true && !pipActive) {
            pipController?.onPipWindowClosed()
        }
        syncPipState("onStop")
    }

    override fun onStart() {
        super.onStart()
        // Foreground refresh: pick up manifest/config changes promptly.
        WgManager.refresh(this)
        pipController?.register()
        // Re-registering must not be mistaken for leaving PiP.
        syncPipState("onStart")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        // A notification tap can re-deliver an intent while we are in PiP.
        syncPipState("onNewIntent")
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
}
