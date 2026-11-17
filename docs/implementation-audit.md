# Regnant — Comprehensive Implementation & Architecture Audit
Date: 2026-11-17
Auditor: Lead Systems & Security Engineer
Branch: `feature/expansion-and-wifi`
Baseline Commit: `3f6ae57` (`feat: baseline v1.0.0 implementation of Regnant`)

---

## 1. Executive Summary

Regnant is a local-first mobile control plane designed for Omarchy Linux (Hyprland / Wayland) paired securely with an Android 16 device (`vivo I2407`). This audit inspects the v1.0.0 baseline implementation, evaluates code health, tests existing features, analyzes real-world Wi-Fi network topology, diagnoses network/firewall constraints, and defines the roadmap for feature expansion.

---

## 2. Source Tree & Repository State

- **Root Directory:** `/home/roonakyadav/Projects/regnant`
- **Git Repository:** Initialized with baseline commit `3f6ae57` on branch `feature/expansion-and-wifi`.
- **Tree Structure:**
  - `android/`: Native Kotlin 2.0.21 / Jetpack Compose Android 16 application.
  - `linux/host/`: Node.js 26 background service managed by `systemd --user`.
  - `linux/desktop/`: Electron 42 desktop companion and system tray interface.
  - `shared/protocol.js`: Single source of truth for protocol constants, schemas, and actions.
  - `docs/`: Architecture documentation and technical specifications.
  - `assets/`: App icons (SVG, PNG) and visual artifacts.

---

## 3. Component Audits

### 3.1 Linux Host Daemon (`linux/host`)
- **Runtime:** Node.js v26.13.1 (via mise).
- **Service Management:** `systemd --user` unit `regnant-host.service` active and enabled.
- **Database:** Built-in `node:sqlite` (`DatabaseSync`) at `~/.local/share/regnant/regnant.db`. Clean relational schema with `devices` and `history` tables.
- **Security & Cryptography:**
  - Self-signed TLS 1.3 certificate generated via `openssl req` at initialization with 10-year validity.
  - Public certificate SHA-256 fingerprint displayed on host and verified by Android via TLS certificate pinning.
  - Ephemeral single-use 6-character pairing codes (`RG-XXXX`) with 5-minute TTL and rate-limiting (3 failed attempts per 15 minutes).
  - High-entropy 32-byte cryptographic bearer tokens stored as SHA-256 hashes on the host.
- **Parser & Executor:**
  - Deterministic natural language tokenizer; rejects arbitrary shell tokens (`rm`, `sudo`, `curl`, `bash`, etc.).
  - Allow-list application execution via `child_process.spawn` with detached lifecycle and explicit Wayland environment discovery (`WAYLAND_DISPLAY`, `HYPRLAND_INSTANCE_SIGNATURE`).
- **Tests:**
  - Unit tests: `test/host.test.js` (7 passed).
  - Integration tests: `test/integration.test.js` (1 passed).

### 3.2 Linux Desktop GUI (`linux/desktop`)
- **Runtime:** Electron 42 (`/usr/bin/electron42`).
- **Features:** System tray integration, live pairing code display with countdown timer, paired devices table with revocation buttons, recent command audit log, and host service status controls.
- **Security:** Strict `contextIsolation: true`, `nodeIntegration: false`, communication through preload script and local IPC.

### 3.3 Android Application (`android/app`)
- **Runtime & Tools:** Kotlin 2.0.21, Gradle 8.11.1, Java 21 Temurin, Target SDK 36 (Android 16), Min SDK 29.
- **UI Architecture:** Jetpack Compose, Material 3, Omarchy color palette (Candle `#EFE9D7`, Coral `#7D2826`, Leaf `#898861`, Juniper `#343723`, Surface `#0C0F14`).
- **Storage:** `androidx.security.crypto.EncryptedSharedPreferences` backed by Android Keystore (AES-256-GCM / AES-256-SIV).
- **Networking:** OkHttp 4.12.0 TLS WebSocket client, custom `X509TrustManager` enforcing exact SHA-256 certificate pinning.
- **Discovery:** Android `NsdManager` DNS-SD client listening for `_regnant._tcp`.
- **Background Service:** Foreground service with `connectedDevice` type and status bar notification.

---

## 4. P0 Networking & Wi-Fi Acceptance Audit

### 4.1 Network Environment Findings
- **Wi-Fi SSID:** `Uniworld-1` (BSSID `fc:11:65:e1:43:60`, 5 GHz Wi-Fi 6 / 802.11ax).
- **Subnet:** `100.128.160.0/20` (RFC 6598 Carrier-Grade NAT / Shared Address Space).
- **Laptop IP:** `100.128.163.153` (IPv4), `fe80::3c7a:ad75:6037:5f3c` (IPv6 link-local on `wlp0s20f3`).
- **Phone IP:** `100.128.171.185` (IPv4), `fe80::4709:3197:7cbf:99a2` (IPv6 link-local on `wlan0`).

### 4.2 Diagnostic Results: Why Initial Direct Connections Failed
1. **Host Firewall (UFW):**
   - Active on Omarchy Linux (`systemctl is-active ufw` -> `active`).
   - Default incoming policy: DROP.
   - Allowed ports in `/etc/ufw/user.rules` and `user6.rules`: Only port `53317` (TCP/UDP) and port `53`.
   - Port `8765` was **blocked** by UFW for both IPv4 and IPv6 incoming traffic.
   - Since passwordless `sudo` is not permitted, changing UFW rules via `ufw allow` is unavailable. However, port `53317` is **pre-authorized and permanently open** in UFW rules!
2. **Access Point Client Isolation (AP Isolation):**
   - `Uniworld-1` drops or filters peer-to-peer IPv4 packets between wireless stations on `100.128.160.0/20` (100% packet loss on IPv4 ping).
   - However, **IPv6 link-local neighbor discovery, ICMPv6, and TCP traffic are permitted** across stations by the AP.
   - Diagnostic test: Pinging `fe80::3c7a:ad75:6037:5f3c%wlan0` from Android yielded **0% packet loss** (average RTT 98ms).
3. **Dual-Stack Socket Binding:**
   - Host daemon was listening on `0.0.0.0` (IPv4 only) instead of `::` (dual stack IPv4/IPv6).
   - When configured to listen on `::` port `53317`, Android curl tests over Wi-Fi succeeded immediately:
     `curl -k --interface wlan0 "https://[fe80::3c7a:ad75:6037:5f3c]:53317/"` returned `200 OK` with TLS 1.3 encryption.
4. **Android Client Defect in `NsdDiscovery.kt`:**
   - Line 78 of `NsdDiscovery.kt` contained `if (host !is java.net.Inet4Address) return`.
   - This intentionally discarded all IPv6 addresses discovered via mDNS, making connection impossible on networks where IPv4 peer-to-peer is isolated.
5. **Android Client Defect in `RegnantApp.kt`:**
   - Line 45 contained `if (pairedHost.ip != "127.0.0.1")`, which prevented the app from updating its target IP when previously paired over ADB reverse loopback.

---

## 5. Architectural Action Plan

1. **Networking & Discovery Hardening:**
   - Update `DEFAULT_PORT` to `53317` across `shared/protocol.js`, `linux/host`, `linux/desktop`, and Android.
   - Configure host `https.createServer` to bind to `::` (dual-stack) on port `53317`.
   - Update Avahi service publication in `discovery.js` to announce port `53317`.
   - Update `NsdDiscovery.kt` and `RegnantClient.kt` to support both IPv4 and IPv6 link-local addresses, binding OkHttp sockets to the active Wi-Fi network.
2. **Quick Actions Update:**
   - Remove Calculator from default quick action chips in `HomeScreen.kt` and `QuickActionChips.kt`.
   - Add **Projects** quick action, launching the new Project Launcher screen.
3. **Database Schema Migrations (v1.1.0):**
   - Add tables: `projects`, `approved_roots`, `transfers`, `routines`, `permissions`, `approvals`.
4. **Feature Implementations:**
   - Project Launcher (`projects.js` + `ProjectsScreen.kt`).
   - Laptop Status Dashboard (`metrics.js` + `DashboardScreen.kt`).
   - Remote File Browser (`filebrowser.js` + `FilesScreen.kt`).
   - Bidirectional File Transfer (`transfer.js`).
   - Safe Web / Search launcher (`web.js`).
   - Manual Clipboard Sharing (`clipboard.js`).
   - Power & Lock Controls (`power.js`).
   - Running Application Manager (`appmanager.js`).
   - Saved Routines engine (`routines.js`).
   - Host Granular Permissions matrix (`permissions.js`).
5. **Real-Device Verification & Acceptance:**
   - Verify connection, pairing, and command execution strictly over ordinary Wi-Fi without ADB reverse.
