package com.linkstreamz.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import java.io.File

object DownloadConditions {
    const val MOBILE_DATA_MESSAGE =
        "You are on mobile data. This download may use a lot of data."
    const val LOW_STORAGE_MESSAGE =
        "Not enough free storage to start this download. Free up space and try again."

    private const val MIN_FREE_BYTES = 300L * 1024L * 1024L

    fun isMetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun hasLowStorage(context: Context): Boolean {
        val available = runCatching {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory() ?: File(context.filesDir, "..")
            StatFs(root.absolutePath).availableBytes
        }.getOrNull() ?: return false
        return available in 1 until MIN_FREE_BYTES
    }
}
