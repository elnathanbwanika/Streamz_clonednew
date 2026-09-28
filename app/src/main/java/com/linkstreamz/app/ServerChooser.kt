package com.linkstreamz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerChooserSheet(target: PlayerTarget, onDismiss: () -> Unit, onPick: (StreamServer) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A).copy(alpha = 0.98f),
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Muted) }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("CHOOSE A SERVER", color = Text, fontFamily = DisplayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(target.subtitle, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                target.servers.forEachIndexed { index, server ->
                    val isRecommended = index == 0
                    val pingColor = when (index) {
                        0 -> Color(0xFF4CAF50)
                        1 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                    
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color(0xFF2C2C2C), RoundedCornerShape(16.dp))
                            .clickable { onPick(server) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(Modifier.size(10.dp).background(pingColor, CircleShape))
                        
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(server.name, color = Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                if (isRecommended) {
                                    Text("RECOMMENDED", color = Accent, fontFamily = MonoFont, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Accent.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            server.quality?.let { Text(it, color = Muted, fontFamily = MonoFont, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                        }
                        
                        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
