# Regnant — Environment Audit

Conducted on: 2026-11-17
Host OS: Omarchy Linux 4.5.0 (Arch-based Linux, rolling release)
Kernel: Linux 7.2.15-1-omarchy x86_64

## 1. Operating System and Session

- **Distribution**: Omarchy 4.5.0 (`ID=omarchy`, `ID_LIKE=arch`)
- **Desktop Environment**: Hyprland (Wayland compositor)
- **Session Type**: `wayland`
- **Display Server**: `WAYLAND_DISPLAY=wayland-1`
- **Session Signature**: `HYPRLAND_INSTANCE_SIGNATURE` active
- **Shell / Compositor UI**: Quickshell (`/usr/share/omarchy/shell`)
- **Notification Daemon**: Native desktop notifications handled via `notify-send` / quickshell OSD

## 2. Desktop Applications Audit

Do not assume generic desktop tools; these are the actual verified installed binaries and desktop entries on this system:

- **Terminal**: `foot` (`/usr/bin/foot`, desktop entry: `/usr/share/applications/foot.desktop`)
- **Web Browser**: `brave` (`/usr/bin/brave`, desktop entry: `/usr/share/applications/brave-browser.desktop`, default handler: `omarchy-browser.desktop` -> `omarchy-launch-browser`)
- **File Manager**: `nautilus` / GNOME Files (`/usr/bin/nautilus`, desktop entry: `org.gnome.Nautilus.desktop`)
- **Calculator**: `omacalc` (`/home/roonakyadav/.local/bin/omacalc`, desktop entry: `~/.local/share/applications/omacalc.desktop`)
- **Folder Shortcuts**:
  - Downloads: `~/Downloads` (`xdg-open /home/roonakyadav/Downloads`)
  - Home: `~` (`xdg-open /home/roonakyadav`)

## 3. Runtimes and Development Tooling

- **Node.js**: `v26.13.1` (managed via mise at `~/.local/share/mise/installs/node/latest/bin/node`)
- **npm**: `11.20.0`
- **Rust**: `rustc 1.100.0` / `cargo 1.100.0`
- **Java Development Kit**: Eclipse Temurin OpenJDK `21.0.13+9-LTS` (Java 21)
- **Gradle**: `8.11.1` (managed via mise)
- **Electron**: System Electron 42 (`/usr/bin/electron42`, `v42.11.18`)
- **Qt / QMake**: `qmake6` (`/usr/bin/qmake6`), Qt 6.12 modules installed

## 4. Android SDK and Connected Device

- **Android SDK Path**: `/home/roonakyadav/Android/Sdk`
- **Installed Platforms**: `android-36` (Android 16 / Baklava)
- **Installed Build Tools**: `36.0.0`
- **Platform Tools**: `/home/roonakyadav/Android/Sdk/platform-tools`
- **ADB**: `/usr/bin/adb` (version 35.0.2)
- **Connected Hardware Device**:
  - Manufacturer: vivo
  - Model: I2407
  - Android Version: 16 (API Level 36)
  - Connection State: `device` (Authorized over USB ADB)

## 5. Networking Assessment

- **Laptop Interface**: `wlp0s20f3` — IP `100.128.163.153/20`
- **Android Interface**: `wlan0` — IP `100.128.171.185/20`
- **Subnet Observations**: The IP addresses belong to a shared CGNAT/Enterprise subnet (`100.128.0.0/20`). Wireless access point client-to-client isolation is active on this network, dropping peer-to-peer ICMP and direct cross-client TCP packets.
- **Connectivity Architecture**:
  - When devices are on an unisolated local Wi-Fi LAN (e.g., home network), standard mDNS (`_regnant._tcp`) and direct TLS communication operate seamlessly.
  - When client isolation or corporate network policies block direct station-to-station traffic, Regnant provides:
    1. Direct manual IP:port fallback configuration.
    2. Zero-friction ADB reverse tunneling (`adb reverse tcp:8765 tcp:8765` mapping phone `127.0.0.1:8765` to host `127.0.0.1:8765`) while tethered over USB, ensuring 100% reliable local development and debugging without external relay servers.
