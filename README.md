# REGNANT (v1.2.1)

> Personal mobile control plane for Omarchy Linux workstation.

Regnant connects an Android smartphone directly to a Linux laptop over the local network. It operates without external cloud dependencies, without AI models at runtime, without public relays, and without QR codes.

```
       +------------------------------------+
       |          REGNANT ANDROID           |
       |  Native Kotlin + Jetpack Compose   |
       |  Android Keystore AES-GCM          |
       |  Scoped Wi-Fi DNS (RegnantDns)     |
       +------------------------------------+
                         │
             TLS 1.3 / Authenticated WSS
          Local Wi-Fi mDNS (_regnant._tcp)
               Dual-Stack Port 53317
                         │
       +------------------------------------+
       |            REGNANT HOST            |
       |  Node.js Daemon (port 53317)       |
       |  SQLite + Strict Non-Shell Engine  |
       |  Hardware Telemetry Streamer       |
       +------------------------------------+
                         │
             Wayland Session IPC & Sockets
       +------------------------------------+
       |          LINUX WORKSTATION         |
       |  Foot, Brave, Nautilus, Hyprland,  |
       |  VS Code, Antigravity, Systemd     |
       +------------------------------------+
```

---

## What's New in v1.2.1

- **Animated Hyprland Workspace Selector**: Prominently placed on Android Home screen (`1 — 2 — 3 — 4 — 5`) with smooth spring-based sliding indicator, supporting touch taps and horizontal drag gestures. Event-driven bi-directional synchronization directly bound to Hyprland's UNIX domain event socket (`.socket2.sock`) and Omarchy Lua dispatchers (`o.workspaces.focus`). Out-of-range workspaces (e.g. > 5) are cleanly flagged without falsely highlighting slot 5.
- **Dedicated "Lock Laptop" Control**: Replaced ambiguous lock controls with a prominent, high-contrast "Lock Laptop" button on Android. Tapping opens an explicit confirmation dialog identifying the target machine (`Lock electron (Omarchy)?`) and stating the physical password/fingerprint requirement. Executes Omarchy's native `/usr/share/omarchy/bin/omarchy-system-lock` and reports `"Laptop locked"` status.
- **Strict Security Boundary**: Regnant never stores, transmits, or accepts login passwords. Remote locking is strictly one-way; unlocking always requires physical entry on the workstation.

---

## What's New in v1.1.0

- **P0 Real Wi-Fi & AP-Isolation Bypass**: Permanent removal of ADB reverse. Resolved AP client isolation using dual-stack IPv6 link-local multicast and `RegnantDns` scoped resolution.
- **Project Launcher**: Replaced Calculator quick action with a full development workspace launcher for `~/Projects` with automatic IDE detection (`code`, `antigravity`, `foot -D`).
- **Live Hardware Telemetry**: Real-time streaming of laptop Battery %, CPU utilization (`/proc/stat`), RAM allocation (`/proc/meminfo`), Home Storage, and Uptime.
- **Remote File Browser & Text Preview**: Browse authorized directories (`~/Projects`, `~/Downloads`) with in-app syntax preview and directory traversal sandboxing.
- **Manual Clipboard Sync**: User-initiated fetch and push of Wayland clipboard via `wl-paste` and `wl-copy`. Zero background polling.
- **Hardware & Power Safety**: One-tap session locking (`loginctl lock-session`) and suspend (`systemctl suspend`) with mobile confirmation alerts; approval-gated shutdown/reboot.
- **Granular Permissions Matrix**: SQLite-backed capability permissions allowing fine-grained revocation per device.

---

## Color Palette & Sovereign Command Aesthetic

Designed strictly following the Sovereign Command palette:

| Color Name | Hexadecimal | Usage |
|---|---|---|
| **Main Background** | `#0C0F14` | Window background & surface base |
| **Candle** | `#EFE9D7` | Primary text, titles, and icons |
| **Coral** | `#7D2826` | Primary action buttons & badges |
| **Leaf** | `#898861` | Secondary accents, labels, and borders |
| **Juniper** | `#343723` | Cards, chips & subtle containers |
| **Status Green** | `#4E8E5F` | Active connection indicators |
| **Status Red** | `#A33C3A` | Warnings, errors, and revocations |

---

## Project Structure

```
regnant/
├── android/                         # Native Android Application (Kotlin, Jetpack Compose)
│   ├── app/src/main/java/org/regnant/android/
│   │   ├── MainActivity.kt          # 5-Tab Scaffold (Home, Projects, Status, Files, Devices)
│   │   ├── RegnantApp.kt            # Application lifecycle & background notifications
│   │   ├── data/                    # SecurityStore (Keystore), Models
│   │   ├── network/                 # RegnantClient (OkHttp TLS/WSS, RegnantDns), NsdDiscovery
│   │   └── ui/                      # Screens (Home, Projects, Dashboard, Files, Devices)
│   └── build.gradle.kts
├── linux/
│   ├── host/                        # Background Host Daemon (Node.js 26)
│   │   ├── src/
│   │   │   ├── index.js             # CLI Daemon Entrypoint
│   │   │   ├── server.js            # HTTPS / WSS Server (Port 53317, Dual-Stack)
│   │   │   ├── parser.js            # Deterministic natural language command parser
│   │   │   ├── executor.js          # Non-shell application executor (Foot, Brave, Nautilus)
│   │   │   ├── projects.js          # Workspace detection & IDE launcher
│   │   │   ├── metrics.js           # Non-blocking hardware telemetry collector
│   │   │   ├── filebrowser.js       # Directory traversal sandboxing & text preview
│   │   │   ├── transfer.js          # Chunked streaming upload/download
│   │   │   ├── clipboard.js         # Wayland clipboard bridge (wl-paste, wl-copy)
│   │   │   ├── power.js             # Session lock, suspend, approval-gated shutdown
│   │   │   ├── appmanager.js        # Hyprland window client manager
│   │   │   ├── routines.js          # Pre-packaged multi-step automation routines
│   │   │   ├── crypto.js            # TLS certs, 6-char pairing code, SHA-256 tokens
│   │   │   ├── db.js                # SQLite device registry, history & permissions
│   │   │   ├── discovery.js         # Avahi / DNS-SD service announcement
│   │   │   └── notifier.js          # Native desktop notifications via notify-send
│   │   └── test/                    # Unit, expansion, and integration test suites
│   ├── desktop/                     # Electron 42 Desktop GUI
│   │   └── src/                     # main.js, preload.js, index.html, styles.css, renderer.js
│   └── scripts/
│       ├── install.sh               # User-level systemd service & desktop launcher installer
│       └── uninstall.sh             # Clean uninstaller
├── assets/                          # Original SVG, rasterized PNG icons, and UI screenshots
└── docs/                            # In-depth architectural & testing documentation
    ├── environment-audit.md         # Full audit of Omarchy, Hyprland & Android device
    ├── implementation-audit.md      # Wi-Fi diagnostics, firewall analysis & architecture
    ├── networking.md                # Dual-stack mDNS, UFW port 53317, AP isolation & RegnantDns
    ├── projects.md                  # Project launcher & workspace execution specification
    ├── file-transfers.md            # File browser, sandboxing & chunked transfer protocol
    ├── permissions.md               # Capability matrix & consequential approval workflow
    ├── security.md                  # Cryptographic model, Keystore storage & token lifecycle
    └── testing.md                   # Full test results, physical device verification & evidence
```

---

## Quick Start Guide

### 1. Install & Start the Linux Host
```bash
# Run installer (creates systemd user service + desktop icon)
bash linux/scripts/install.sh

# Open the desktop companion
regnant-desktop
```

### 2. Install & Open Android Application
The debug APK is compiled at:
`android/app/build/outputs/apk/debug/app-debug.apk`

```bash
# Install to device via ADB
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n org.regnant.android/.MainActivity
```

### 3. Pair Devices Once
1. On the Linux laptop, Regnant displays a 6-character code (e.g. `RG-XC3M`).
2. On Android, tap **Enter Pairing Code** and input the code.
3. Both devices exchange cryptographic credentials and pin the host's TLS certificate.
4. The status pill turns green **CONNECTED**.

### 4. Control Your Workstation
- Tap **Terminal** to launch `foot`.
- Tap **Brave** to open Brave browser.
- Tap **Files** to open Nautilus file manager.
- Tap **Projects** to view and launch workspaces (`MAD`, `WrongStack`, etc.) into VS Code or Foot.
- Say or type `"open downloads"` or `"open brave"`.
- Check live battery, CPU, RAM, and home storage on the **Status** tab.
- Browse and preview workstation code files on the **Files** tab.

---

## Testing & Quality Assurance

```bash
# Run all host unit, expansion, and integration test suites
cd linux/host
node --test test/host.test.js
node --test test/expansion.test.js
node --test test/integration.test.js
```
