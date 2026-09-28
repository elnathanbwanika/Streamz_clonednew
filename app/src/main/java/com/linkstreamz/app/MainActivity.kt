package com.linkstreamz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkStreamzTheme {
                val navController = rememberNavController()
                val repository = remember { EventsRepository() }
                val settingsRepository = remember { SettingsRepository(applicationContext) }
                val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = PlaybackSettings())
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                var target by remember { mutableStateOf<PlayerTarget?>(null) }

                LaunchedEffect(repository) { repository.start(this) }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            repository = repository,
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
                                onBack = { navController.popBackStack() },
                                onTarget = { newTarget ->
                                    if (newTarget.servers.isEmpty()) {
                                        android.widget.Toast.makeText(applicationContext, "No streams available", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (newTarget.servers.size == 1) {
                                        target = newTarget.withServer(newTarget.servers.first())
                                    } else {
                                        target = newTarget // Will trigger server chooser in player or we can just navigate
                                    }
                                }
                            )
                        }
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            settingsRepository = settingsRepository,
                            scope = scope,
                            onBack = { navController.popBackStack() },
                            onRefresh = { repository.load() },
                            onCustomStream = {
                                target = CustomTarget(StreamServer("Custom", it))
                                navController.navigate("player")
                            },
                        )
                    }
                }
            }
        }
    }
}