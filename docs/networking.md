# Regnant — Networking & Discovery Specification

## 1. Network Topology

Regnant operates as a peer-to-peer mobile-to-workstation control plane on the local network.

```
+---------------------------+                +---------------------------+
|    Regnant Android        |                |      Regnant Host         |
|                           |   DNS-SD/mDNS  |    (Omarchy Linux)        |
|  [ NsdManager Discovery ] | -------------> |  [ mDNS Service Ad ]      |
|                           |                |  Service: _regnant._tcp   |
|                           |   TLS / WSS    |  Port: 8765               |
|  [ OkHttp WebSocket ]     | <============> |  [ HTTPS / WSS Server ]   |
|  Bearer Auth + TLS Pin    |                |  SQLite Auth & Replay DB  |
+---------------------------+                +---------------------------+
```

## 2. Service Discovery (mDNS / DNS-SD)

- **Service Type**: `_regnant._tcp`
- **Domain**: `local.`
- **Default Port**: `8765`
- **TXT Records**:
  - `version=1.0.0`
  - `hostId=<stable UUID>`
  - `name=Omarchy Laptop`
  - `certFingerprint=<SHA256 of host TLS certificate>`

### Reconnection Flow
1. Android registers an `NsdManager.DiscoveryListener` for `_regnant._tcp`.
2. When a service matching the saved `hostId` is resolved, Android extracts the IP address and port.
3. Android compares the certificate fingerprint against its Keystore-pinned value.
4. Android opens a secure WSS connection with `Authorization: Bearer <token>`.
5. Host validates the token against SQLite `devices` table and upgrades the connection to an active authenticated session.

## 3. Endpoints Specification

### 3.1 `GET /api/v1/health`
- **Purpose**: Check host readiness and fetch public metadata.
- **Auth**: None.
- **Response**:
  ```json
  {
    "status": "ok",
    "version": "1.0.0",
    "hostName": "Omarchy Laptop",
    "hostId": "123e4567-e89b-12d3-a456-426614174000",
    "isPaired": false
  }
  ```

### 3.2 `POST /api/v1/pair`
- **Purpose**: Exchange temporary 6-digit code for long-term bearer credentials.
- **Auth**: Temporary Pairing Code.
- **Request**:
  ```json
  {
    "deviceId": "android-vivo-i2407-uuid",
    "deviceName": "vivo I2407",
    "pairingCode": "RG-7K9P"
  }
  ```
- **Response**:
  ```json
  {
    "status": "PAIRED",
    "token": "4f9b8c...",
    "hostId": "123e4567-e89b-12d3-a456-426614174000",
    "hostName": "Omarchy Laptop",
    "certFingerprint": "A1:B2:C3..."
  }
  ```

### 3.3 `POST /api/v1/commands`
- **Purpose**: Submit a typed command over REST (alternative to WebSocket).
- **Auth**: `Bearer <token>`
- **Request**:
  ```json
  {
    "requestId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "timestamp": 1794910800000,
    "action": "launch_app",
    "target": "terminal"
  }
  ```
- **Response**:
  ```json
  {
    "requestId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "status": "COMPLETED",
    "action": "launch_app",
    "target": "terminal",
    "message": "Launched Foot Terminal",
    "executedAt": 1794910800120
  }
  ```

### 3.4 `GET /api/v1/devices`
- **Purpose**: Retrieve connected / registered devices.
- **Auth**: `Bearer <token>` or localhost internal.

### 3.5 `POST /api/v1/devices/revoke`
- **Purpose**: Revoke an existing paired device.
- **Auth**: `Bearer <token>` or localhost internal.

### 3.6 WebSocket (`/ws`)
- **Protocol**: `regnant-v1`
- **Events**:
  - `client -> server`:
    - `{"type": "COMMAND", "data": { ... }}`
    - `{"type": "PING"}`
  - `server -> client`:
    - `{"type": "PONG"}`
    - `{"type": "STATE_UPDATE", "data": { "connected": true, "pairedDevices": 1 }}`
    - `{"type": "COMMAND_RESULT", "data": { "requestId": "...", "status": "COMPLETED", "message": "..." }}`

## 4. Resilience and Fallbacks

1. **Station-to-Station Wi-Fi Isolation**:
   - In environments where routers block client-to-client traffic (such as university or corporate campus Wi-Fi), Regnant provides:
     - Manual IP:Port configuration in Android settings.
     - Port forwarding over USB via `adb reverse tcp:8765 tcp:8765`.
2. **Reconnection Backoff**:
   - Linear retry: 1s, 2s, 4s, 5s (capped at 5s intervals).
   - Network change broadcast receiver initiates instant re-discovery upon Wi-Fi reconnect.
