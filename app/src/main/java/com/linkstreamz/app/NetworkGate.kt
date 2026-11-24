package com.linkstreamz.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Current device connectivity plus an explicit way to re-check it. */
data class NetworkStatus(val isOnline: Boolean, val refresh: () -> Unit)

/** True when the device has an active network that can reach the Internet. */
internal fun deviceOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Observes connectivity for the lifetime of the composition. [NetworkStatus.refresh]
 * forces an immediate re-check, which the Retry button uses.
 */
@Composable
fun rememberNetworkStatus(): NetworkStatus {
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(deviceOnline(context)) }
    var refreshToken by remember { mutableIntStateOf(0) }

    DisposableEffect(context, refreshToken) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
        if (manager == null) {
            isOnline = false
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isOnline = deviceOnline(context)
                }

                override fun onLost(network: Network) {
                    isOnline = deviceOnline(context)
                }

                override fun onUnavailable() {
                    isOnline = false
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    isOnline = network == manager.activeNetwork &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }
            isOnline = deviceOnline(context)
            manager.registerDefaultNetworkCallback(callback)
            onDispose { manager.unregisterNetworkCallback(callback) }
        }
    }

    return NetworkStatus(isOnline) { refreshToken++ }
}

/**
 * Place as the last child of the root Box. Renders the offline notice over all
 * app content whenever the device has no Internet connectivity.
 */
@Composable
fun OfflineGate() {
    val status = rememberNetworkStatus()
    if (!status.isOnline) {
        OfflineScreen(onRetry = status.refresh)
    }
}

@Composable
private fun OfflineScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LsColor.Bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(LsColor.AccentDim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = LsColor.Accent,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                "No Internet connection",
                color = LsColor.Text,
                fontFamily = DisplayFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Seems you're not connected to the Internet. " +
                    "Please check your Internet and try again",
                color = LsColor.TextDim,
                fontFamily = BodyFont,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            AppButton(
                label = "Retry",
                onClick = onRetry,
                variant = AppButtonVariant.Primary,
            )
        }
    }
}
