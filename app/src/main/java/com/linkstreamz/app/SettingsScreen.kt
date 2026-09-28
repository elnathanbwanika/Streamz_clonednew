package com.linkstreamz.app

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: PlaybackSettings,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onRefresh: suspend () -> Unit,
    onCustomStream: (String) -> Unit,
) {
    var customUrl by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxWidth().padding(14.dp, 14.dp, 16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = .04f), RoundedCornerShape(12.dp))) {
                Icon(Icons.Default.Settings, "Back", tint = Text, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("SETTINGS", color = Text, fontFamily = DisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Text("LINK STREAMZ", color = Muted, fontFamily = MonoFont, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp), letterSpacing = .5.sp)
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { SettingsGroupTitle("Playback") }
            item {
                SettingsGroup {
                    SettingSwitch("Autoplay", "Start playing as soon as a match opens", Icons.Default.PlayArrow, settings.autoplay) {
                        scope.launch { settingsRepository.update { it.copy(autoplay = !it.autoplay) } }
                    }
                    SettingSwitch("Start muted", "Open every stream with sound turned off", Icons.Default.Bolt, settings.muted) {
                        scope.launch { settingsRepository.update { it.copy(muted = !it.muted) } }
                    }
                    SettingSegment("Skip length", "Used by the ± buttons and double-tap", Icons.Default.Loop, settings.skip, listOf(5, 10, 15, 30), "s") { value ->
                        scope.launch { settingsRepository.update { it.copy(skip = value) } }
                    }
                }
            }
            item { SettingsGroupTitle("Display") }
            item {
                SettingsGroup {
                    SettingSegment("Hide controls", "Overlay fades out after this idle time", Icons.Default.DisplaySettings, settings.hide, listOf(3, 5, 8, 12), "s") { value ->
                        scope.launch { settingsRepository.update { it.copy(hide = value) } }
                    }
                    SettingSwitch("Keep screen on", "Prevent the display from sleeping while playing", Icons.Default.DisplaySettings, settings.awake) {
                        scope.launch { settingsRepository.update { it.copy(awake = !it.awake) } }
                    }
                }
            }
            item { SettingsGroupTitle("Custom stream") }
            item {
                SettingsGroup {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingHeader("Play your own link", "Paste an HLS (.m3u8) or MP4 URL. It plays instantly and isn't saved.", Icons.Default.Link)
                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it },
                            placeholder = { Text("https://your-server/stream.m3u8", color = Color(0xFF4D4D4D)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = MonoFont, fontSize = 11.sp, color = Text),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val valid = Patterns.WEB_URL.matcher(customUrl.trim()).matches() && (customUrl.startsWith("http://") || customUrl.startsWith("https://"))
                                if (valid) onCustomStream(customUrl.trim()) else Toast.makeText(context, "Enter a valid http(s) URL", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Text("Play now", modifier = Modifier.padding(start = 9.dp), fontSize = 13.5.sp)
                        }
                    }
                }
            }
            item { SettingsGroupTitle("Maintenance") }
            item {
                SettingsGroup {
                    SettingsAction("Refresh content", "Fetch the latest matches and highlights", Icons.Default.Refresh, "Refresh", refreshing) {
                        refreshing = true
                        scope.launch { onRefresh(); refreshing = false }
                    }
                    SettingsAction("Reset settings", "Restore every option to its default value", Icons.Default.RestartAlt, "Reset", false) {
                        scope.launch { settingsRepository.reset() }
                    }
                }
            }
            item { Text("LINK STREAMZ · v1.0", color = Color(0xFF4D4D4D), fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.fillMaxWidth().padding(top = 22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(5.dp).background(Accent, RoundedCornerShape(2.dp)))
        Text(title.uppercase(), color = Muted, fontFamily = MonoFont, fontSize = 9.sp, letterSpacing = 1.3.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Card2, Card)), RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
private fun SettingHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp).background(AccentSoft, RoundedCornerShape(9.dp)).padding(3.dp))
        Column {
            Text(title, color = Text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChecked: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(10.dp, 10.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeader(title, subtitle, icon)
        Switch(checked = checked, onCheckedChange = { onChecked() }, modifier = Modifier.padding(start = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSegment(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Int, options: List<Int>, suffix: String, onSelected: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(10.dp, 10.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeader(title, subtitle, icon)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = Accent, activeContentColor = Color.Black),
                ) { Text("$value$suffix", fontFamily = MonoFont, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, loading: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(10.dp, 10.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeader(title, subtitle, icon)
        OutlinedButton(onClick = onClick, enabled = !loading, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(if (loading) "…" else label, color = Text, fontSize = 11.5.sp)
        }
    }
}