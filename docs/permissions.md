# REGNANT — Granular Permissions & Consequential Approval Matrix

## 1. Overview

Regnant rejects the paradigm of all-or-nothing remote execution. Every paired device is governed by a fine-grained capability matrix persisted in SQLite. Furthermore, high-consequence operations (such as system shutdown or reboot) require explicit, local user approval before execution.

---

## 2. Granular Capability Matrix

The capabilities defined in `shared/protocol.js` include:

| Capability Flag | Category | Allowed by Default | Description |
|---|---|---|---|
| `launch_apps` | Application Execution | Yes | Launch allowlisted apps (`foot`, `brave`, `nautilus`, `omacalc`) |
| `open_paths` | Path Inspection | Yes | Open approved directory paths in file manager |
| `projects` | Workspaces | Yes | Enumerate and launch development workspaces in IDEs |
| `view_metrics` | Telemetry | Yes | Stream CPU, RAM, Battery, Storage, and Uptime telemetry |
| `browse_files` | Filesystem | Yes | Browse directory contents and preview text files |
| `transfer_files`| File Transfer | Yes | Upload and download files to/from approved folders |
| `open_web` | Web / Browser | Yes | Safe web opening (`http:`, `https:` only) and search queries |
| `clipboard` | Clipboard Sync | Yes | Explicit user-triggered fetch / send of system clipboard |
| `power_controls`| Session Power | Restricted | Session locking (`loginctl lock-session`) and sleep (`suspend`) |
| `running_apps` | Window Control | Yes | Inspect open Hyprland window clients and close windows |
| `routines` | Automation | Yes | Trigger pre-defined sequenced multi-action routines |

---

## 3. Database Schema & Enforcement

Capabilities are stored on a per-device basis in the `permissions` table:

```sql
CREATE TABLE permissions (
  device_id TEXT NOT NULL,
  capability TEXT NOT NULL,
  allowed INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (device_id, capability),
  FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
);
```

### Server-Side Enforcement Point

Every API route and WebSocket action verifies permissions before processing:

```javascript
if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.PROJECTS)) {
  return this.sendJson(res, 403, { error: 'Permission denied: project launching is restricted' });
}
```

If a capability is revoked from the Linux desktop companion UI, the change takes effect immediately without requiring a service restart.

---

## 4. Two-Tier Power & Safety Controls

Consequential actions are split into non-destructive and destructive tiers:

### Tier 1: User-Confirmed Mobile Controls
- **Lock Session**: Invokes `/usr/bin/loginctl lock-session`. The phone UI presents a confirmation modal before dispatching.
- **Suspend / Sleep**: Invokes `/usr/bin/systemctl suspend`. The phone displays a warning that the network connection will disconnect while the workstation sleeps.

### Tier 2: Approval-Gated Destruction (Shutdown / Reboot)
Shutdown (`systemctl poweroff`) and Reboot (`systemctl reboot`) cannot be triggered autonomously by the mobile device.

1. Mobile client dispatches `POST /api/v1/power/shutdown`.
2. Host engine creates a pending approval record in SQLite:
   ```sql
   INSERT INTO approvals (approval_id, device_id, action, status, created_at, expires_at)
   VALUES (?, ?, 'shutdown', 'PENDING', ?, ?);
   ```
3. A desktop notification and Electron desktop approval dialog pop up on the Linux workstation screen:
   > **Regnant Security Alert**  
   > Paired phone `Vivo I2407` has requested a system SHUTDOWN.  
   > [ Approve ]  [ Deny ]
4. The operation executes **only** if the logged-in workstation user explicitly clicks **Approve** on the laptop. If unapproved after 60 seconds, the request automatically expires.

---

## 5. Device Revocation & Token Lifecycle

- When a device is forgotten on mobile or revoked on desktop, `db.deleteDevice(deviceId)` cascades:
  1. Deletes the device registry entry.
  2. Deletes all associated permissions.
  3. Cancels and terminates active WebSocket connections for that device token immediately.
- Tokens are stored as SHA-256 hashes (`token_hash`), ensuring that a raw database read cannot extract plaintext authentication credentials.
