# REGNANT

> Personal mobile control plane for Omarchy Linux workstation.

Regnant connects an Android smartphone directly to a Linux laptop over the local network. It operates without external cloud dependencies, without AI models at runtime, without public relays, and without QR codes.

```
       +----------------------------+
       |       REGNANT ANDROID      |
       |  Native Kotlin + Compose   |
       |  Android Keystore AES-GCM  |
       +----------------------------+
                     │
         TLS 1.3 / Authenticated WSS
       Local Wi-Fi mDNS (_regnant._tcp)
                     │
       +----------------------------+
       |        REGNANT HOST        |
       |  Node.js Daemon (port 8765)|
       |  SQLite + Strict Executor  |
       +----------------------------+
                     │
         Wayland Session IPC
       +----------------------------+
       |       LINUX WORKSTATION    |
       |  Foot, Brave, Nautilus,    |
       |  Omacalc, Hyprland Desktop |
       +----------------------------+
```

---

## Features

- **Local Wi-Fi Peer-to-Peer**: Complete privacy. Zero cloud relays, zero external telemetry.
- **Single-Use 6-Character Pairing**: No QR codes. A temporary, rate-limited alphanumeric code pairs devices once.
- **Automatic Reconnection**: Discover each other automatically via DNS-SD / mDNS (`_regnant._tcp`) after pairing.
- **Hardware-Backed Cryptography**: Android Keystore encryption for tokens; TLS 1.3 with SHA-256 certificate fingerprint pinning.
- **Deterministic Action Parser**: Translates natural phrases ("open terminal", "open brave", "open files", "open calculator", "open downloads") without running an LLM.
- **Strict Non-Shell Execution**: Absolute path resolution to inspected binaries (`foot`, `brave`, `nautilus`, `omacalc`). Shell injection and arbitrary commands are rejected.
- **Dual Client Architecture**: Lightweight user-level systemd background host daemon + Electron 42 desktop GUI with system tray integration.
- **Minimal Resource Footprint**: Idle memory ~29MB RAM, 0% CPU, 0% GPU.

---

## Color Palette & Visual Design

Designed strictly following the sovereign command aesthetic:

| Color Name | Hexadecimal | Usage |
|---|---|---|
| **Main Background** | `#0C0F14` | Window background & surface base |
| **Candle** | `#EFE9D7` | Primary text and icons |
| **Coral** | `#7D2826` | Primary action buttons & badges |
| **Leaf** | `#898861` | Secondary accents & labels |
| **Juniper** | `#343723` | Cards, chips & subtle containers |

---

## Project Structure

```
regnant/
├── android/                         # Native Android Application (Kotlin, Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/org/regnant/android/
│   │   │   ├── MainActivity.kt      # Main Compose Activity
│   │   │   ├── RegnantApp.kt        # Application lifecycle & background listener
│   │   │   ├── data/                # SecurityStore (Keystore), Models
│   │   │   ├── network/             # RegnantClient (OkHttp TLS/WSS), NsdDiscovery
│   │   │   ├── service/             # RegnantForegroundService (connectedDevice)
│   │   │   └── ui/                  # Theme, Screens (Home, Devices, Activity, Settings)
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── linux/
│   ├── host/                        # Background Host Daemon (Node.js 26)
│   │   ├── src/
│   │   │   ├── index.js             # CLI Daemon Entry
│   │   │   ├── server.js            # HTTPS / WSS Server & Session Router
│   │   │   ├── parser.js            # Deterministic natural language command parser
│   │   │   ├── executor.js          # Non-shell application executor (Foot, Brave, Nautilus, Omacalc)
│   │   │   ├── crypto.js            # TLS certs, 6-char pairing code, token hashing
│   │   │   ├── db.js                # SQLite device registry & replay prevention
│   │   │   ├── discovery.js         # DNS-SD / Avahi service announcement
│   │   │   └── notifier.js          # Native desktop notifications via notify-send
│   │   └── test/                    # Unit and integration test suites
│   ├── desktop/                     # Electron 42 Desktop GUI
│   │   └── src/                     # main.js, preload.js, index.html, styles.css, renderer.js
│   ├── scripts/
│   │   ├── install.sh               # User-level systemd service & desktop launcher installer
│   │   └── uninstall.sh             # Clean uninstaller
│   └── regnant.desktop              # Freedesktop application entry
├── assets/                          # Original SVG and rasterized PNG icon assets
└── docs/                            # In-depth architectural & testing documentation
    ├── environment-audit.md         # Full audit of Omarchy, Hyprland & Android device
    ├── architecture-decisions.md    # ADR documenting stack selection & rationale
    ├── security.md                  # Security model, threat analysis & token lifecycle
    ├── networking.md                # DNS-SD, WSS protocol, endpoints & fallbacks
    ├── android.md                   # Android implementation & Keystore details
    ├── linux.md                     # Linux host daemon & Wayland session integration
    ├── testing.md                   # Automated test results & live device verification
    └── known-limitations.md         # Router client isolation & fallback mitigations
```

---

## Quick Start Guide

### 1. Start the Linux Host & Desktop
```bash
# Run the installer (registers and starts systemd user service + desktop icon)
bash linux/scripts/install.sh

# Open the desktop interface
regnant-desktop
```

### 2. Install & Open Android Application
The pre-compiled debug APK is located at:
`android/app/build/outputs/apk/debug/app-debug.apk`

```bash
# Install onto connected phone via ADB
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n org.regnant.android/.MainActivity
```

### 3. Pair Devices Once
1. On the Linux laptop, Regnant displays a 6-character code (e.g. `RG-BN5F`).
2. On Android, tap **Enter Pairing Code** and input the code.
3. Both devices immediately exchange cryptographic credentials and pin the host's TLS certificate.
4. The status pill turns green **CONNECTED**.

### 4. Control Your Workstation
- Tap **Terminal** to launch `foot`.
- Tap **Brave** to open Brave browser.
- Tap **Files** to open Nautilus file manager.
- Tap **Calc** to open Omacalc calculator.
- Say or type `"open downloads"` to open the Downloads folder.
- Destructive commands like `"delete all my files"` are strictly rejected.

---

## Testing

```bash
# Run host unit tests
cd linux/host
node --test test/host.test.js

# Run integration tests
node --test test/integration.test.js
```
