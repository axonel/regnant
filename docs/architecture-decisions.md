# Regnant — Architectural Decision Record (ADR)

## 1. Overview & Vision

Regnant is a personal mobile control plane for a Linux computer, connecting an Android phone (Android 16 / API 36) to an Omarchy Linux workstation running Hyprland over the local network. It operates with zero cloud infrastructure, zero runtime AI dependencies, zero public relays, and strict local-only authentication.

## 2. Technology Stack Selection

### 2.1 Android Application
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Material 3 with custom Regnant design tokens)
- **Minimum SDK**: API 29 (Android 10) | **Target SDK**: API 36 (Android 16 Baklava)
- **Local Service Discovery**: Native Android `android.net.nsd.NsdManager` for DNS-SD (`_regnant._tcp`)
- **Secure Storage**: Android Keystore (`EncryptedSharedPreferences` backed by Keystore AES-256-GCM)
- **Networking**: OkHttp 4.x with TLS Certificate Pinning and WebSocket client
- **Voice Input**: Android `SpeechRecognizer` (preferring on-device recognition engine)
- **Background Persistence**: Android Foreground Service (`connectedDevice` type) with notification channels
- **Rationale**: Android platform APIs provide robust hardware-backed Keystore security, low-latency multicast DNS handling through `NsdManager`, and native speech recognition without external dependencies.

### 2.2 Linux Host Daemon (`regnant-host`)
- **Runtime**: Node.js v26.13.1 (already managed and activated via mise)
- **Protocol Server**: Native Node.js `https` + `ws` (RFC 6455 WebSocket)
- **Cryptographic Engine**: Node.js native `node:crypto` (TLS 1.3, SHA-256, HMAC, constant-time comparisons)
- **Database / Persistence**: Native `node:sqlite` (built into modern Node.js) for paired device registry, replay token cache, and activity audit log
- **Service Discovery**: DNS-SD responder advertising `_regnant._tcp.local.`
- **Execution Mechanism**: Strict non-shell execution (`child_process.execFile` and `spawn` with argument arrays, never `shell: true`)
- **Session Environment Handling**: Explicit propagation of `WAYLAND_DISPLAY`, `XDG_CURRENT_DESKTOP`, `XDG_SESSION_TYPE`, `HYPRLAND_INSTANCE_SIGNATURE`, and `PATH` into spawned processes
- **Rationale**: Node.js 26 delivers immediate start time, zero compile delay, memory safety, built-in SQLite, enterprise-grade TLS/crypto, and straightforward systemd user-service integration.

### 2.3 Linux Desktop UI (`regnant-desktop`)
- **Runtime**: Electron 42 (`/usr/bin/electron42` already installed on the Omarchy workstation)
- **Window Management**: Native Wayland client with Wayland Ozone flags (`--ozone-platform=wayland`)
- **Tray & Background Lifecycle**: Minimized to system tray; closing the window hides it without terminating either the GUI or the background daemon
- **Rationale**: Leverages the existing preinstalled system Electron 42 binary without downloading bulky bundled runtimes. Communicates directly with the `regnant-host` daemon via local authenticated IPC/loopback.

## 3. Pairing and Authentication Lifecycle

Pairing eliminates QR codes entirely in favor of an ergonomic 6-character alphanumeric code:

1. **Host Generation**: When uncoupled or requested, `regnant-host` generates a cryptographically random 6-character code (e.g., `RG-7K9P`) using `crypto.randomBytes()`.
2. **Time-To-Live**: The code expires strictly in 5 minutes (300 seconds). Single-use only.
3. **Rate Limiting**: Maximum 3 failed attempts before the active code is invalidated.
4. **Exchange**:
   - The phone connects to `https://<host-ip>:<port>/api/v1/pair`.
   - The phone submits `{ deviceId, deviceName, pairingCode, clientPublicKey }`.
   - The host verifies the code using constant-time comparison (`crypto.timingSafeEqual`).
   - If valid, the host generates a device-specific high-entropy 256-bit bearer token and records the phone in its SQLite device store.
   - The host returns the bearer token and its TLS certificate SHA-256 fingerprint.
5. **Post-Pairing**:
   - The phone securely stores the host's TLS certificate fingerprint and bearer token in the Android Keystore.
   - All subsequent HTTP and WebSocket requests require `Authorization: Bearer <token>` and strict TLS certificate verification.
   - The pairing code is destroyed from host memory immediately.

## 4. Local Network Discovery & Automatic Reconnection

- **DNS-SD Advertisement**: `regnant-host` announces service type `_regnant._tcp` on local network interfaces.
  - TXT records include `protocol=1.0.0`, `hostId=<stable-host-uuid>`, `name=Omarchy Laptop`.
- **Phone Discovery**: `NsdManager` listens for `_regnant._tcp`. When a host with a matching paired `hostId` is found, Android initiates immediate TLS connection.
- **Heartbeat & Keepalive**: WebSocket sends periodic ping frames every 15 seconds. If missed after 3 cycles (45s), connection enters `RECONNECTING` state and automatically initiates re-discovery.
- **Client-Isolation & Fallback Support**:
  - If mDNS or peer-to-peer traffic is blocked by enterprise/router client-isolation, Regnant displays an alert on Android and provides a manual host IP/port input option.
  - In development and USB tethering modes, `adb reverse tcp:8765 tcp:8765` connects the phone's `localhost:8765` directly to the host.

## 5. Command Parsing and Execution Allowlist

Regnant explicitly bans arbitrary shell execution and model generation at runtime.

### 5.1 Deterministic Command Mapping
The command parser maps natural language phrases into structured typed actions:
- `open terminal` / `start terminal` / `launch terminal` -> `{ action: "launch_app", target: "terminal" }`
- `open brave` / `launch brave` / `open browser` -> `{ action: "launch_app", target: "brave" }`
- `open file manager` / `open files` -> `{ action: "launch_app", target: "file_manager" }`
- `open calculator` / `calculator` -> `{ action: "launch_app", target: "calculator" }`
- `open downloads` / `show downloads` -> `{ action: "open_path", target: "downloads" }`
- `open home folder` / `open home` -> `{ action: "open_path", target: "home" }`

### 5.2 Safe Application Invocation
- Target executables are resolved through an inspected system map:
  - `terminal` -> `/usr/bin/foot`
  - `brave` -> `/usr/bin/brave`
  - `file_manager` -> `/usr/bin/nautilus`
  - `calculator` -> `/home/roonakyadav/.local/bin/omacalc`
  - `downloads` -> `/usr/bin/xdg-open /home/roonakyadav/Downloads`
  - `home` -> `/usr/bin/xdg-open /home/roonakyadav`
- Execution is executed via `child_process.execFile` or `spawn` directly with argument arrays.
- `shell: true` is forbidden.
- Unknown commands return `{ status: "REJECTED", reason: "Unsupported command" }`.
- Request IDs (`requestId`) are cached in SQLite to prevent replay attacks.
