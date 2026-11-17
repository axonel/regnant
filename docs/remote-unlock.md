# REGNANT — Remote Workstation Unlock (v1.2.2)

## 1. Architectural Overview

Regnant v1.2.2 introduces authenticated remote workstation unlocking from the Regnant Android application to the user's Omarchy Linux laptop.

Rather than implementing a custom password database, shadow file reader, or synthetic key injector (e.g. `ydotool` / `wtype`), Regnant delegates authentication directly to Omarchy's native Pluggable Authentication Modules (PAM) pipeline (`/etc/pam.d/omarchy-lock-password`) via Quickshell's inter-process communication (IPC) interface.

```
+-------------------------------------------------------------------------+
|                        REGNANT ANDROID CLIENT                           |
|  - Stateful Lock / Unlock Buttons                                       |
|  - Masked Password Modal (PasswordVisualTransformation)                 |
|  - Immediate in-memory wiping post-submission                           |
+-------------------------------------------------------------------------+
                                    │
                       POST /api/v1/power/unlock
                       TLS 1.3 / Bearer Token
                       Capability: power_controls
                                    │
                                    ▼
+-------------------------------------------------------------------------+
|                         REGNANT LINUX DAEMON                            |
|  - Validates session is actively locked                                 |
|  - Rejects empty passwords                                              |
|  - Wipes reference immediately after dispatch                           |
+-------------------------------------------------------------------------+
                                    │
                        UNIX Domain Socket IPC
                 /run/user/1000/omarchy-shell-*.sock
                   (0x1f argument / 0x1e message)
                                    │
                                    ▼
+-------------------------------------------------------------------------+
|                       OMARCHY SHELL (QUICKSHELL)                        |
|  - User-space Plugin: ~/.config/omarchy/plugins/roonakyadav.lock        |
|  - Target: "lock" (unlockWithPassword, unlockStatus)                    |
|  - PamContext: "omarchy-lock-password"                                  |
|  - Successful: finishUnlock()                                           |
|  - Failure: handlePasswordFailure()                                     |
+-------------------------------------------------------------------------+
                                    │
                                    ▼
+-------------------------------------------------------------------------+
|                         LINUX PAM SUBSYSTEM                             |
|  /etc/pam.d/omarchy-lock-password                                       |
+-------------------------------------------------------------------------+
```

---

## 2. Security Model & Credential Hygiene

### 2.1 Zero Password Persistence
At no point is the user's Linux password persisted:
- **No SQLite storage**: Neither the host database (`regnant.db`) nor the Android room database touches the password.
- **No Keystore or Preferences**: `SecurityStore` and Android `SharedPreferences` only manage the device pairing certificates and ephemeral tokens.
- **No Command-Line Leaks (`ps aux`)**: The host does not spawn `su`, `sudo`, `login`, `chpasswd`, or any external process. All communication with Quickshell is direct byte-stream I/O over a private local UNIX domain socket owned by UID 1000 with permissions `0600`.
- **Immediate Memory Wiping**:
  - Android: `password = ""` is executed inside the coroutine block immediately upon capturing the string reference.
  - Host: The request body reference is discarded and nulled immediately after socket dispatch.

### 2.2 Transport & Capability Authorization
- **Mutual Authentication**: Transmitted strictly over TLS 1.3 with pinned SHA-256 host certificate verification.
- **Capability Matrix**: Gated by `CAPABILITIES.POWER_CONTROLS` (`power_controls`). If this capability is revoked for a paired device, the unlock endpoint immediately returns `403 Forbidden`.
- **Local Network Scope**: The endpoint is only accessible on the local link; no public relay or cloud intermediary exists.

---

## 3. Omarchy Shell PAM Integration

### 3.1 User-Space Plugin Architecture
Omarchy loads system plugins from `/usr/share/omarchy/shell/plugins/` (root-owned) and user overrides from `~/.config/omarchy/plugins/`.

Using `omarchy plugin clone omarchy.lock`, the lock plugin was cloned into:
`~/.config/omarchy/plugins/roonakyadav.lock/`

Because the plugin retains `clonedFrom: "omarchy.lock"`, it inherits host capabilities (including `"authentication"`) and overrides the default `ShellIpc` target `"lock"`.

### 3.2 IPC Method Extensions in `Service.qml`
Two IPC methods were added to the `ShellIpc` block:

```qml
ShellIpc {
  target: "lock"

  function unlockWithPassword(password: string): string {
    if (!root.currentlyLocked()) return "not-locked"
    if (!password || password.length === 0) return "empty-password"
    if (root.authenticatingPassword) return "busy"
    root.lastRemoteAuthResult = "pending"
    root.submitPassword(password)
    return "initiated"
  }

  function unlockStatus(): string {
    return root.lastRemoteAuthResult
  }
}
```

### 3.3 Asynchronous PAM Completion
When `submitPassword()` is called:
1. `passwordPam.start()` initiates PAM conversation with `/etc/pam.d/omarchy-lock-password`.
2. When PAM requests credentials, `respondToPasswordPrompt()` supplies `pendingPassword`.
3. In `passwordPam.onCompleted`:
   - If `result === PamResult.Success`: sets `lastRemoteAuthResult = "success"` and calls `finishUnlock()`.
   - If `result !== PamResult.Success`: sets `lastRemoteAuthResult = "failure"` and calls `handlePasswordFailure()`.
4. The Regnant daemon polls `unlockStatus()` every 120ms (timeout: 4.0s). Once a terminal status (`success` or `failure`) is observed, the daemon returns the HTTP response to Android.

---

## 4. UNIX Domain Socket IPC Protocol

Quickshell implements a lightweight byte-oriented framing protocol over `/run/user/1000/omarchy-shell-*.sock`:
- **Argument Separator**: `0x1F` (ASCII Unit Separator `\x1f`).
- **Message Terminator**: `0x1E` (ASCII Record Separator `\x1e`).

To execute `omarchy-shell lock unlockWithPassword <password>`:
```
\x1f + target + \x1f + method + \x1f + arg1 + ... + \x1e
```

The response from the socket is returned as a plain string terminated by `\n`.

---

## 5. Android Interface Implementation

### 5.1 Stateful Controls
On the Android Home screen:
- **Laptop Unlocked**: Displays `[ 🔒 Lock Laptop ]` button.
- **Laptop Locked**: Displays `[ 🔒 LOCKED ]` status badge alongside a prominent `[ 🔓 Unlock Laptop ]` action button.

### 5.2 Password Input Dialog
Tapping **Unlock Laptop** opens `UnlockPasswordDialog`:
- Prompts: *"Enter your Linux login password to authenticate and unlock your Omarchy workstation via PAM."*
- Masked input with `PasswordVisualTransformation` and visibility toggle.
- IME configured with `ImeAction.Done` and `keyboardActions(onDone = { ... })` for single-tap or keyboard submission.
- Real-time states:
  - `UNLOCKING` (Amber spinner + "Verifying PAM credentials...")
  - `UNLOCKED` (Green check + "Workstation unlocked")
  - `INVALID PASSWORD` (Red alert + "Authentication failed")
  - `HOST UNAVAILABLE` (Amber warning + connection error)

---

## 6. Recovery & Emergency Procedures

If a remote unlock fails or becomes unresponsive:

1. **Physical Unlock via Laptop Screen**:
   The laptop screen lock remains fully operational. The user can type their password on the laptop keyboard or use the fingerprint reader at any time.

2. **Omarchy Shell Restart**:
   If Quickshell's lock plugin hangs or needs reloading:
   ```bash
   omarchy restart shell
   ```

3. **Reverting to Stock Lock Plugin**:
   To disable the remote unlock extension and return strictly to the factory lock plugin:
   ```bash
   rm -rf ~/.config/omarchy/plugins/roonakyadav.lock
   omarchy restart shell
   ```

4. **TTY Recovery**:
   If the GUI compositor lock is unresponsive:
   - Switch to TTY: `Ctrl + Alt + F3`
   - Log in with your Linux credentials
   - Kill or restart the lock session:
     ```bash
     loginctl unlock-session
     ```
