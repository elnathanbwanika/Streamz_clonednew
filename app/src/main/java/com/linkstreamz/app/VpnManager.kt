package com.linkstreamz.app

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.security.MessageDigest
import kotlin.math.min

/**
 * Manages the state of VPN configuration fetching and connection.
 */
class VpnManager(
    private val configSource: VpnConfigSource,
    private val engine: VpnEngine,
    private val appContext: android.content.Context
) {
    private var lastConfigId: String? = null 
    private var lastConfigSha256: String? = null
    private var lastConfigBytes: ByteArray? = null
    private var lastExpiry: String? = null
    
    // Constants for storage and scheduling
    companion object {
        private const val PREFS_NAME = "vpn_prefs"
        private const val KEY_LAST_CONFIG_ID = "last_config_id"
        private const val KEY_LAST_CONFIG_SHA256 = "last_config_sha256" 
        private const val KEY_LAST_CONFIG_BYTES = "last_config_bytes"
        private const val KEY_LAST_EXPIRY = "last_expiry"
        private const val MAX_BACKOFF_SECONDS = 15 * 60 // 15 minutes
        private const val INITIAL_BACKOFF_SECONDS = 30
    }
    
    /**
     * Refreshes the VPN configuration from the private repo.
     * This is the main method that should be called on app start, foreground,
     * and periodically via WorkManager job.
     */
    suspend fun refreshConfig() {
        try {
            // Fetch manifest
            val manifest = configSource.fetchManifest() ?: return
            
            // Check if we have an active config
            val activeFile = manifest.active?.let { id ->
                manifest.files.find { it.id == id }
            } ?: return

            // Validate expiry and check if file has changed
            val now = System.currentTimeMillis()
            
            if (activeFile.exp != null) {
                // Parse expiry timestamp if needed - we'll validate in our fetch method
            }
            
            // Try to fetch the config file content
            val configBytes = configSource.fetchConfig(activeFile.id) ?: return
            
            // Verify SHA256 if present
            if (activeFile.sha256 != null && activeFile.sha256.isNotEmpty()) {
                val computedSha256 = computeSha256(configBytes)
                if (computedSha256 != activeFile.sha256) {
                    AppLogger.d("VpnManager", "SHA256 mismatch for VPN config")
                    return
                }
            }
            
            // Check if config is different from previous
            val currentId = activeFile.id
            
            if (currentId != lastConfigId || !configBytes.contentEquals(lastConfigBytes ?: ByteArray(0))) {
                // Config changed - we'll start the engine with new config
                AppLogger.d("VpnManager", "VPN config changed, will reload")
                
                // Store new config to persistent storage
                saveConfigToStorage(currentId, configBytes, activeFile.exp)
                
                // Start or restart VPN engine with new config
                restartEngineWithConfig(configBytes)
            }
            
        } catch (e: Exception) {
            // Silently ignore errors - keep last good config if exists
            AppLogger.d("VpnManager", "Failed to refresh VPN config: ${e.message}")
        }
    }
    
    /**
     * Initialize VPN manager and perform initial setup.
     */
    suspend fun initialize() {
        // Restore last configuration from storage if it exists
        restoreConfigFromStorage()
    }
    
    /**
     * Stops the VPN engine and cleans up resources.
     */
    suspend fun shutdown() {
        engine.stop()
        // Clear state
        lastConfigId = null 
        lastConfigSha256 = null
        lastConfigBytes = null
    }
    
    /**
     * Get current proxy endpoint for routing traffic.
     * Returns null if VPN is not connected.
     */
    fun getProxyEndpoint(): String? {
        return if (engine.state() == VpnState.CONNECTED) 
            engine.localProxyEndpoint() 
        else null
    }
    
    /**
     * Restarts the VPN engine with a new configuration.
     */
    private suspend fun restartEngineWithConfig(configBytes: ByteArray) {
        // Stop any existing connection
        engine.stop()
        
        // Start with new config
        val success = engine.start(configBytes)
        if (!success) {
            AppLogger.d("VpnManager", "Failed to start VPN engine with new configuration")
        }
    }
    
    /**
     * Save the VPN configuration to persistent storage.
     */
    private fun saveConfigToStorage(id: String, configBytes: ByteArray, expiry: String?) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save ID and expiry
        editor.putString(KEY_LAST_CONFIG_ID, id)
        editor.putString(KEY_LAST_EXPIRY, expiry)
        
        // Store config bytes in file system (we'll store it as base64 encoded string)
        try {
            val base64Bytes = android.util.Base64.encodeToString(configBytes, android.util.Base64.DEFAULT)
            // Write to a file
            val configFile = File(appContext.filesDir, "vpn_config_${id}")
            configFile.writeText(base64Bytes)
            
            // Also store the reference in preferences
            editor.putString(KEY_LAST_CONFIG_SHA256, computeSha256(configBytes))
        } catch (e: Exception) {
            AppLogger.e("VpnManager", "Failed to save config", e)
        }
        
        editor.apply()
    }
    
    /**
     * Restore configuration from persistent storage.
     */
    private fun restoreConfigFromStorage() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_LAST_CONFIG_ID, null)
        val expiry = prefs.getString(KEY_LAST_EXPIRY, null)
        
        if (id != null) {
            try {
                val configFile = File(appContext.filesDir, "vpn_config_${id}")
                if (configFile.exists()) {
                    val base64String = configFile.readText()
                    val configBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                    lastConfigId = id
                    lastConfigSha256 = computeSha256(configBytes)
                    lastConfigBytes = configBytes
                    lastExpiry = expiry
                    
                    // Try to reestablish connection with stored config
                    AppLogger.d("VpnManager", "Restored VPN configuration from storage")
                }
            } catch (e: Exception) {
                AppLogger.e("VpnManager", "Failed to restore config", e)
            }
        }
    }
    
    /**
     * Computes SHA256 hash of byte array.
     */
    private fun computeSha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}