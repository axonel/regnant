# REGNANT — Testing & Verification Report

## 1. Test Overview

This report details the comprehensive verification of **Regnant v1.1.0** across unit testing, integration testing, real-device Wi-Fi validation, and UI screenshot evidence.

---

## 2. Automated Test Results

### 1. Host Engine & Parser Tests (`test/host.test.js`)
```
✔ CommandParser — valid commands and aliases (0.81ms)
✔ CommandParser — rejection of unsupported or malicious inputs (0.18ms)
✔ ActionExecutor — detects real desktop applications (0.10ms)
✔ CryptoManager — pairing code lifecycle, rate-limiting & single use (7.92ms)
✔ CryptoManager — pairing code rate limit invalidation (6.98ms)
✔ CryptoManager — bearer token generation, hashing and verification (6.89ms)
✔ DatabaseManager — device lifecycle, replay prevention & history (1.80ms)

Tests: 7 passed, 0 failed, duration: 81.7ms
```

### 2. Feature Expansion Engine Tests (`test/expansion.test.js`)
```
✔ ProjectManager — IDE detection and project listing (0.63ms)
✔ MetricsCollector — System snapshot structure and validity (0.60ms)
✔ FileBrowser — Directory traversal protection and approved roots (0.63ms)
✔ WebManager — URL validation rejects non-HTTP protocols (0.18ms)
✔ RoutineManager — Pre-seeded routines and step structure (0.15ms)
✔ DatabaseManager — Granular permissions matrix (9.79ms)

Tests: 6 passed, 0 failed, duration: 63.2ms
```

### 3. End-to-End Integration Suite (`test/integration.test.js`)
```
✔ Integration — Server startup, pairing, authenticated commands, replay & revocation (293.95ms)

Tests: 1 passed, 0 failed, duration: 360.0ms
```

**Overall Automated Test Status: 14 / 14 Passed (100% Success Rate)**

---

## 3. Real Physical Device Verification (Vivo I2407, Android 16)

All physical device tests were performed with **ADB reverse tunnels permanently removed** (`adb reverse --remove-all`), communicating strictly over genuine local Wi-Fi.

| Test Case | Method | Observed Result | Status |
|---|---|---|---|
| **Network Discovery** | Android NSD / Avahi mDNS | App discovered `Regnant - electron (Omarchy)` on port `53317` with IPv6 link-local address | **PASS** |
| **Initial Pairing** | Single-use 6-character code (`RG-XC3M`) | Cryptographic exchange completed over TLS; device stored token in Android Keystore | **PASS** |
| **Persistent WebSocket** | WSS connection over Wi-Fi | Handshake completed; status switched to `CONNECTED` (green badge) | **PASS** |
| **Live Telemetry** | Background /proc streaming | Battery (34%), CPU usage (8.0%), and RAM consumption (63.2%) update live | **PASS** |
| **Quick Action: Terminal** | Tap "Terminal" chip on Home screen | Dispatched via WebSocket; launched `foot` on Hyprland desktop; `COMPLETED` confirmation arrived | **PASS** |
| **Quick Action: Brave** | Type "open brave" + Send | Launched `/opt/brave-bin/brave`; status badge `COMPLETED` | **PASS** |
| **Quick Action: Projects** | Tap "Projects" chip (replaces Calc) | Navigated to Workspaces screen; displayed `MAD`, `cml-kaggle`, `concord`, `WrongStack` | **PASS** |
| **Workspace IDE Launch** | Tap "Terminal" on `MAD` project card | Spawned `/usr/bin/foot -D /home/roonakyadav/Projects/MAD` directly into directory | **PASS** |
| **Status Dashboard** | Navigate to Status tab | Rendered hardware gauges: Battery, CPU, RAM, Home Storage (35.5% used), Uptime | **PASS** |
| **Remote File Browser** | Tap Files tab -> `Projects` -> `regnant` | Traversed `/home/roonakyadav/Projects/regnant`; listed subdirectories & files | **PASS** |
| **In-App Text Preview** | Tap `README.md` (6 KB) in File Browser | Read file content over TLS and displayed syntax-styled markdown preview modal | **PASS** |
| **Session Lock Confirmation** | Tap "Lock Screen" on Dashboard | Displayed confirmation alert explaining `loginctl lock-session`; safely cancellable | **PASS** |

---

## 4. UI Screenshot Evidence

The following captures have been recorded directly from the physical Android device:

1. `assets/home_screen.png`: Home screen showing connected workstation, live hardware telemetry (Battery, CPU, RAM), and Quick Action chips (Terminal, Brave, Files, Projects).
2. `assets/home_recent_activity.png`: Home screen showing recent activity feed with `Launched foot successfully` and `Launched brave successfully` with `COMPLETED` badges.
3. `assets/projects_screen.png`: Workspaces screen listing discovered development repositories (`MAD`, `WrongStack`, `cml-kaggle`, `concord`) with IDE launch triggers.
4. `assets/status_screen.png`: Workstation hardware status dashboard displaying battery power, CPU utilization, memory allocation, storage capacity, and uptime.
5. `assets/files_screen.png`: Remote file browser root screen showing authorized directories (`Downloads`, `Projects`).
6. `assets/file_browser_listing.png`: File browser directory listing inside `/home/roonakyadav/Projects`.
7. `assets/file_preview_modal.png`: In-app file viewer modal displaying `README.md` contents fetched over TLS.

---

## 5. Host Resource Consumption Benchmarks

Measured under active background operation:
- **CPU Utilization**: `0.0%`
- **Resident Set Size (RSS)**: `74.5 MB`
- **Memory Percentage**: `0.4%` of system RAM
- **Active Threads**: 8 threads (Node.js event loop + worker pool)
