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
}
