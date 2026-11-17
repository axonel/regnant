# Regnant Linux Application & Host Service

## 1. Overview

Regnant Linux consists of two tightly coupled components:
1. **Regnant Host Daemon (`regnant-host`)**: A lightweight background daemon managing local TLS HTTPS/WSS servers, mDNS advertisement, cryptographic token verification, deterministic command parsing, and non-shell desktop execution.
2. **Regnant Desktop (`regnant-desktop`)**: An Electron 42 desktop GUI providing a visual pairing interface, paired device inspection, command audit history, and service controls.

Both run entirely under user privileges (never root/sudo) and are deeply integrated with the Omarchy Linux Hyprland/Wayland desktop environment.

## 2. Desktop Environment Integration

### 2.1 Wayland & Hyprland Session Propagation
Background services on Linux do not always inherit user session display variables by default. Regnant explicitly resolves and propagates:
- `WAYLAND_DISPLAY` (e.g. `wayland-1`)
- `XDG_CURRENT_DESKTOP=Hyprland`
- `XDG_SESSION_TYPE=wayland`
- `HYPRLAND_INSTANCE_SIGNATURE`
- `PATH` and `HOME`

This ensures that applications spawned by Regnant render natively in the active Hyprland graphical session without requiring X11 emulation.

### 2.2 Detected Applications Allowlist
Inspected on Omarchy Linux:
- **Terminal**: `foot` (`/usr/bin/foot`)
- **Web Browser**: `brave` (`/usr/bin/brave`)
- **File Manager**: `nautilus` (`/usr/bin/nautilus`)
- **Calculator**: `omacalc` (`/home/roonakyadav/.local/bin/omacalc`)
- **Downloads Folder**: `xdg-open /home/roonakyadav/Downloads`
- **Home Directory**: `xdg-open /home/roonakyadav`

### 2.3 Strict Command Execution
- Uses Node.js `child_process.spawn()` with exact arguments.
- `shell: false` is strictly enforced.
- Arbitrary command strings and shell pipelines are rejected immediately by the deterministic parser.

## 3. Background Service Architecture

The host daemon runs as a user-level systemd service:
`~/.config/systemd/user/regnant-host.service`

### Service Management Commands
```bash
# Check service status
systemctl --user status regnant-host.service

# View live service logs
journalctl --user -u regnant-host.service -f

# Restart host daemon
systemctl --user restart regnant-host.service

# Stop host daemon
systemctl --user stop regnant-host.service
```

## 4. Desktop Application & System Tray

The desktop GUI is built on Electron 42 (`/usr/bin/electron42`):
- Launch command: `regnant-desktop`
- Desktop entry: `~/.local/share/applications/regnant.desktop`
- Closing the window minimizes/hides to the system tray rather than killing the service.
- System tray context menu permits reopening the window, generating temporary pairing codes, or quitting.

## 5. Installation & Uninstallation

### Installation
Run the installer:
```bash
bash linux/scripts/install.sh
```
This registers the systemd user service, installs the desktop launcher, links icons, and enables auto-start at login.

### Uninstallation
Run the uninstaller:
```bash
bash linux/scripts/uninstall.sh
```
This stops the service, disables autostart, and removes only Regnant-created application launchers and binaries.
