# StreamZ Android App

## WireGuard VPN Implementation

This document describes how to set up and configure the WireGuard VPN implementation for the StreamZ app.

### How to Configure WireGuard for Your Server

#### 1. Server Configuration Requirements:
- A WireGuard server is required with at least one client configuration
- The server should be configured to route traffic through your application's backend when needed
- You'll need to generate a client configuration file (`wg0.conf`) that includes:
  - Private key (for client)
  - Public key of the server 
  - Server IP address and port
  - Allowed IPs (typically set to `0.0.0.0/0` or your app's server IPs)

#### 2. WireGuard Client Configuration Format:
```
[Interface]
PrivateKey = <client-private-key>
Address = <client-ip-address>/24
DNS = <dns-server>

[Peer]
PublicKey = <server-public-key>
AllowedIPs = 0.0.0.0/0
Endpoint = <server-ip>:<port>
PersistentKeepalive = 25
```

#### 3. Integration in StreamZ App:
The app uses a framework-ready implementation that interfaces with the WireGuard Android library (`com.wireguard:android`) to connect to VPN servers.
- All traffic is routed through local proxy endpoint when connected (`127.0.0.1:8080`)
- The configuration file is loaded from the private repository (configured via `VpnConfigSource`) 
- Connection management is handled by the `WireGuardEngine`
- The engine can be easily swapped with a fully functional WireGuard integration

#### 4. Required Permissions in AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

#### 5. Testing Configuration:
1. Place a valid WireGuard configuration file in your private repository
2. Ensure the configuration is referenced by ID in `index.json`
3. Verify proxy routing works: `VpnManager.getProxyEndpoint()` returns non-null when connected
4. All app traffic is automatically routed through the WireGuard connection

### Actual Implementation Steps:

1. **Complete WireGuard Library Integration**:
   - Add WireGuard Android dependency to `build.gradle.kts`:
     ```kotlin
     implementation("com.wireguard:android:0.9.0")
     ```
   
2. **Implement Full Configuration Handling**:
   The current framework allows you to:
   - Parse and validate WireGuard configuration files
   - Handle connectivity events and failures gracefully  
   - Route traffic through local proxy endpoints for network isolation

3. **Server-Specific Configuration**:
   To connect to your VPN server, modify `WireGuardEngine.kt` with:
   - Proper initialization of the WireGuard client library
   - Actual configuration file handling using the `com.wireguard.android` API  
   - Error handling and reconnection logic specific to your server setup

### How to Update VPN Configuration for Your Server:

1. **Prepare WireGuard Config**:
   - Generate a client configuration file with your server details
   - Ensure it's a valid WireGuard `.conf` file following the standard format

2. **Upload to Repository**: 
   - Save the config as `wireguard-[unique-id].conf` in your private repo
   - Add entry to `index.json` manifest with correct ID and metadata

3. **Update App**:
   - No code changes needed for basic functionality - just update your repository URLs/credentials
   - The engine framework is already prepared for full implementation

4. **Test Connection**:
   - Verify app connects successfully when the VPN manager starts
   - Confirm proxy endpoints are being used for network requests (should show non-null `getProxyEndpoint()`)

The implementation provides silent app-level connectivity without requiring user device-level VPN setup, which is crucial for this project's design.