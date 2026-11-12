package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// WgConfig.kt
//
// Parser for standard WireGuard .conf files.
//
// Supported format:
//
//   [Interface]
//   PrivateKey = <base64>
//   Address    = 10.8.0.2/24
//   DNS        = 1.1.1.1, 8.8.8.8
//   MTU        = 1420   (optional)
//
//   [Peer]
//   PublicKey           = <base64>
//   PresharedKey        = <base64>   (optional)
//   Endpoint            = vpn.example.com:51820
//   AllowedIPs          = 0.0.0.0/0, ::/0
//   PersistentKeepalive = 25          (optional)
//
// ─────────────────────────────────────────────────────────────────────────────

import okio.ByteString.Companion.decodeBase64
import java.net.Inet4Address
import java.net.InetAddress

data class WgInterfaceConfig(
    val privateKey: String,
    val addresses:  List<String>,
    val dns:        List<String>,
    val mtu:        Int = 1420,
)

data class WgPeerConfig(
    val publicKey:           String,
    val presharedKey:        String? = null,
    val endpoint:            String,
    val allowedIps:          List<String>,
    val persistentKeepalive: Int = 25,
)

data class WgConfig(
    val iface: WgInterfaceConfig,
    val peer:  WgPeerConfig,
) {
    companion object {
        /**
         * Parse a WireGuard .conf file text into a [WgConfig].
         * Returns null if the config is missing required fields or cannot be parsed.
         */
        fun parse(text: String): WgConfig? {
            return try {
                doParse(text)
            } catch (_: Exception) {
                null
            }
        }

        private fun doParse(text: String): WgConfig? {
            var section = ""
            // Interface fields
            var privateKey = ""
            val addresses  = mutableListOf<String>()
            val dns        = mutableListOf<String>()
            var mtu        = 1420
            // Peer fields
            var publicKey           = ""
            var presharedKey: String? = null
            var endpoint            = ""
            val allowedIps          = mutableListOf<String>()
            var persistentKeepalive = 25

            for (raw in text.lines()) {
                val line = raw.trim()
                when {
                    line.startsWith("[Interface]", ignoreCase = true) -> section = "interface"
                    line.startsWith("[Peer]", ignoreCase = true)      -> section = "peer"
                    line.isBlank() || line.startsWith("#")            -> { /* skip */ }
                    else -> {
                        val eq = line.indexOf('=')
                        if (eq < 0) continue
                        val key   = line.substring(0, eq).trim().lowercase()
                        val value = line.substring(eq + 1).trim()
                        when (section) {
                            "interface" -> when (key) {
                                "privatekey" -> privateKey = value
                                "address"    -> addresses.addAll(value.split(',').map { it.trim() })
                                "dns"        -> dns.addAll(value.split(',').map { it.trim() })
                                "mtu"        -> mtu = value.toIntOrNull() ?: 1420
                            }
                            "peer" -> when (key) {
                                "publickey"           -> publicKey = value
                                "presharedkey"        -> presharedKey = value
                                "endpoint"            -> endpoint = value
                                "allowedips"          -> allowedIps.addAll(value.split(',').map { it.trim() })
                                "persistentkeepalive" -> persistentKeepalive = value.toIntOrNull() ?: 25
                            }
                        }
                    }
                }
            }

            if (privateKey.isBlank() || publicKey.isBlank() || endpoint.isBlank()) return null
            if (addresses.isEmpty()) return null

            return WgConfig(
                iface = WgInterfaceConfig(
                    privateKey = privateKey,
                    addresses  = addresses,
                    dns        = dns,
                    mtu        = mtu,
                ),
                peer = WgPeerConfig(
                    publicKey           = publicKey,
                    presharedKey        = presharedKey,
                    endpoint            = endpoint,
                    allowedIps          = if (allowedIps.isEmpty()) listOf("0.0.0.0/0") else allowedIps,
                    persistentKeepalive = persistentKeepalive,
                ),
            )
        }
    }

    /** Comma separated bare interface addresses (prefix stripped) for netstack. */
    fun interfaceAddressesCsv(): String =
        iface.addresses.joinToString(",") { it.substringBefore('/').trim() }

    /** Comma separated bare DNS servers (prefix stripped) for netstack. */
    fun dnsCsv(): String =
        iface.dns.joinToString(",") { it.substringBefore('/').trim() }

    /**
     * Build the wireguard-go UAPI text. Keys are converted from base64 to hex and
     * [endpoint] must already be resolved to an IP (host name resolution happens
     * outside the tunnel so bootstrap DNS does not depend on the tunnel).
     */
    fun toIpc(endpoint: WgEndpoint): String = buildString {
        append("private_key=").append(WgKeys.base64ToHex(iface.privateKey)).append('\n')
        append("listen_port=0\n")
        append("replace_peers=true\n")
        append("public_key=").append(WgKeys.base64ToHex(peer.publicKey)).append('\n')
        peer.presharedKey?.takeIf { it.isNotBlank() }?.let {
            append("preshared_key=").append(WgKeys.base64ToHex(it)).append('\n')
        }
        append("endpoint=").append(endpoint.format()).append('\n')
        if (peer.persistentKeepalive > 0) {
            append("persistent_keepalive_interval=").append(peer.persistentKeepalive).append('\n')
        }
        append("replace_allowed_ips=true\n")
        val ips = if (peer.allowedIps.isEmpty()) listOf("0.0.0.0/0", "::/0") else peer.allowedIps
        for (ip in ips) {
            append("allowed_ip=").append(normalizeAllowedIp(ip)).append('\n')
        }
    }

    private fun normalizeAllowedIp(raw: String): String {
        val ip = raw.trim()
        if (ip.contains('/')) return ip
        return if (ip.contains(':')) "$ip/128" else "$ip/32"
    }
}

/** Parsed `host:port` endpoint. IPv6 hosts are stored without brackets. */
data class WgEndpoint(val host: String, val port: Int) {
    fun format(): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    /** True when [host] is an IP literal that needs no bootstrap DNS lookup. */
    fun isIpLiteral(): Boolean =
        host.contains(':') || IPV4.matches(host)

    /**
     * Resolve a host-name endpoint using the device's normal DNS (outside the
     * tunnel). Returns null if resolution fails. IPv4 is preferred so the
     * endpoint is reachable even on IPv4-only networks.
     */
    fun resolveHost(): WgEndpoint? {
        if (isIpLiteral()) return this
        return try {
            val all = InetAddress.getAllByName(host)
            val addr = all.firstOrNull { it is Inet4Address } ?: all.firstOrNull() ?: return null
            val literal = addr.hostAddress ?: return null
            copy(host = literal.substringBefore('%'))
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        fun parse(raw: String): WgEndpoint? {
            val value = raw.trim()
            if (value.isEmpty()) return null
            return if (value.startsWith("[")) {
                val close = value.indexOf(']')
                if (close <= 1) return null
                val host = value.substring(1, close)
                val rest = value.substring(close + 1)
                if (!rest.startsWith(":")) return null
                portOf(rest.substring(1))?.let { WgEndpoint(host, it) }
            } else {
                val idx = value.lastIndexOf(':')
                if (idx <= 0 || idx == value.lastIndex) return null
                val host = value.substring(0, idx)
                portOf(value.substring(idx + 1))?.let { WgEndpoint(host, it) }
            }
        }

        private fun portOf(raw: String): Int? =
            raw.toIntOrNull()?.takeIf { it in 1..65535 }
    }
}

/** WireGuard key helpers. WireGuard .conf files use base64; the UAPI uses hex. */
object WgKeys {
    private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")

    fun base64ToHex(value: String): String {
        val key = value.trim()
        if (HEX_64.matches(key)) return key.lowercase()
        val bytes = key.decodeBase64()
            ?: throw IllegalArgumentException("key is not valid base64")
        require(bytes.size == 32) { "WireGuard key must be 32 bytes, got ${bytes.size}" }
        return bytes.hex()
    }
}
