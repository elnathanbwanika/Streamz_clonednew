package com.linkstreamz.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the architectural rule: the app must never use the Android system VPN
 * service or the old WireGuard Android tunnel backend again.
 */
class NoVpnServiceTest {

    private fun moduleDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/AndroidManifest.xml").isFile) return File(dir, "app")
            if (File(dir, "src/main/AndroidManifest.xml").isFile) return dir
            dir = dir.parentFile
        }
        error("cannot locate module directory (cwd=${File(".").absolutePath})")
    }

    private fun sourceFiles(): List<File> {
        val src = File(moduleDir(), "src/main/java")
        return src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private val forbidden = listOf(
        "VpnService",
        "BIND_VPN_SERVICE",
        "GoBackend",
        "wireguard.android",
        "com.wireguard.android",
    )

    @Test
    fun `source contains no system vpn or old wireguard backend references`() {
        val files = sourceFiles()
        assertTrue("no Kotlin sources found", files.isNotEmpty())
        for (file in files) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(
                    "forbidden reference '$token' in ${file.path}",
                    text.contains(token),
                )
            }
        }
    }

    @Test
    fun `app manifest declares no vpn permissions or foreground service`() {
        val manifest = File(moduleDir(), "src/main/AndroidManifest.xml")
        assertTrue("manifest missing", manifest.isFile)
        val text = manifest.readText()

        assertFalse(text.contains("android.net.VpnService"))
        assertFalse(text.contains("BIND_VPN_SERVICE"))
        assertFalse(text.contains("GoBackend"))
        assertFalse(text.contains("wireguard.android"))

        // Downloads are allowed to use a dataSync foreground service; VPN-backed
        // foreground services are still forbidden.
        assertFalse(text.contains("android:permission=\"android.permission.BIND_VPN_SERVICE\""))
        assertTrue(text.contains("android:foregroundServiceType=\"dataSync\""))
        assertFalse(text.contains("android:foregroundServiceType=\"specialUse\""))
    }
}
