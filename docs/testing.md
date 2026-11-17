# Regnant — Verification & Testing Report

## 1. Automated Test Suites

### 1.1 Host Unit Tests (`linux/host/test/host.test.js`)
Executed via Node.js native test runner:
```bash
cd linux/host
node --test test/host.test.js
```
**Test Results: 7/7 Passing**
- `CommandParser — valid commands and aliases`: PASS
  - Verified aliases: "open terminal", "Launch Terminal", "start terminal!", "terminal", "open brave", "launch brave", "open browser", "open file manager", "open files", "open calculator", "calc", "open downloads", "show downloads", "open home".
- `CommandParser — rejection of unsupported or malicious inputs`: PASS
  - Verified rejection of: "delete all my files", "rm -rf /", "sudo rm -rf ~", "cat /etc/passwd", "bash -c whoami", "drop table users", and unknown phrases.
- `ActionExecutor — detects real desktop applications`: PASS
  - Verified detection of `foot`, `brave`, `nautilus`, `omacalc`, `Downloads`, and home directory.
- `CryptoManager — pairing code lifecycle, rate-limiting & single use`: PASS
  - Verified 6-character code format, active status, rate limit invalidation after 3 failures, and immediate single-use destruction upon successful pairing.
- `CryptoManager — bearer token generation, hashing and verification`: PASS
  - Verified 256-bit token entropy and constant-time SHA-256 verification.
- `DatabaseManager — device lifecycle, replay prevention & history`: PASS
  - Verified SQLite registration, listing, revocation, replay request ID deduplication, and historical record retrieval.

### 1.2 Integration Tests (`linux/host/test/integration.test.js`)
Executed via Node.js native test runner:
```bash
cd linux/host
node --test test/integration.test.js
```
**Test Results: 1/1 Suite Passing**
- Tested automated HTTPS server initialization on port 8799.
- Tested `/api/v1/health` endpoint.
- Tested temporary pairing code generation and verification via `/api/v1/pair`.
- Verified unauthenticated command rejection (HTTP 401).
- Verified authenticated command submission and successful foot execution.
- Verified duplicate `requestId` replay attack rejection (HTTP 400 with `Duplicate request ID`).
- Verified unsupported command rejection over HTTP.
- Verified authenticated WebSocket connection (`wss://127.0.0.1:8799/ws?token=...`) and Ping/Pong heartbeat.
- Verified device revocation (`/api/v1/devices/revoke`) and subsequent denial of access.

## 2. Physical Android Device End-to-End Validation

Tests executed live against connected hardware:
- **Device**: vivo I2407
- **Operating System**: Android 16 (API Level 36, Baklava)
- **Workstation**: Omarchy Linux 4.5.0, Hyprland (Wayland session `wayland-1`)

| Test Scenario | Action Performed | Result | Verification Proof |
|---|---|---|---|
| **1. APK Compilation & Installation** | Built debug APK and streamed via ADB | PASSED | `adb install -r app-debug.apk` exited with `Success` |
| **2. Initial App Launch** | Started `org.regnant.android/.MainActivity` | PASSED | App opened with custom dark palette and geometric crown icon |
| **3. Temporary Code Pairing** | Host generated `RG-BN5F`; entered in app | PASSED | Toast: "Paired successfully with electron (Omarchy)!"; code immediately consumed |
| **4. Pinned TLS & Token Storage** | Verified Android Keystore persistence | PASSED | Token and SHA-256 fingerprint saved in `regnant_secure_prefs` |
| **5. Bi-directional WebSocket** | Upgraded connection to `wss://` | PASSED | Live status indicator turned green `CONNECTED` |
| **6. Launch Foot Terminal** | Tapped "Terminal" chip on phone | PASSED | Workstation spawned `/usr/bin/foot` (PID 12248); Android showed `COMPLETED` |
| **7. Launch Brave Browser** | Tapped "Brave" chip on phone | PASSED | Workstation spawned `/usr/bin/brave`; Android showed `COMPLETED` |
| **8. Launch Nautilus Files** | Tapped "Files" chip on phone | PASSED | Workstation spawned `/usr/bin/nautilus` (PID 12610); Android showed `COMPLETED` |
| **9. Launch Omacalc Calculator** | Tapped "Calc" chip on phone | PASSED | Workstation spawned `/home/roonakyadav/.local/bin/omacalc` (PID 12727); Android showed `COMPLETED` |
| **10. Open Downloads Folder** | Typed "open downloads" and tapped Send | PASSED | Workstation opened Downloads directory via `xdg-open`; Android showed `COMPLETED` |
| **11. Reject Destructive Command** | Typed "delete all my files" and tapped Send | PASSED | Rejected with error: `Command rejected: arbitrary shell and destructive commands are not permitted.`; status `REJECTED` |
| **12. Automatic Reconnection** | Force-stopped Android app and relaunched | PASSED | Reconnected automatically without re-entering pairing code; `connectedClientsCount=1` |
| **13. Workstation Service Restart** | Restarted `regnant-host.service` | PASSED | Android auto-reconnect backoff re-established session; `connectedClientsCount=1` |
| **14. Desktop Notification** | Executed commands on phone | PASSED | Native desktop notifications posted via `notify-send` |
| **15. Idle Resource Use** | Measured background daemon memory & CPU | PASSED | Daemon uses ~29.4MB RAM and 0% idle CPU |
