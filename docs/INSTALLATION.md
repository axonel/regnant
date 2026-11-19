# Installation

## Supported target

The primary integration target is Omarchy Linux with Hyprland and Wayland. Other Linux distributions may run parts of the Node.js host, but desktop-specific capabilities require compatible session utilities.

## Host prerequisites

- Node.js 26 and npm.
- systemd user session.
- Wayland session for desktop controls.
- Optional utilities for specific features: `hyprctl`, `grim`, `wpctl`, `busctl`, `wl-copy`, `wl-paste` and Omarchy/Quickshell.
- Network access between the Android device and laptop for direct local control.
- Optional: GitHub CLI (`gh`) installed and authenticated with `gh auth login` for remote pull-request and workflow operations.

## Install the host

```bash
git clone https://github.com/axonel/regnant.git
cd regnant
bash linux/scripts/install.sh
```

The current installer writes a user-level service and desktop launcher. Review `linux/scripts/install.sh` before running it on a machine; it contains environment assumptions that may need adjustment outside the developer's Omarchy setup.

Verify service status:

```bash
systemctl --user status regnant-host.service
journalctl --user -u regnant-host.service -f
```

## Build Android

Install JDK 21 and Android SDK API 36, then:

```bash
cd android
./gradlew assembleDebug
```

Install the resulting APK using Android Studio or ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pairing

1. Start the host service.
2. Open the host-side Regnant interface and obtain the current pairing code.
3. On Android, choose the host and enter the code.
4. Confirm the connection status before issuing commands.

Pairing codes are short-lived and single-use. Do not share them or include them in issue reports.

## Omarchy remote unlock

Remote unlock is specific to the Omarchy lock plugin and its PAM configuration. Read [remote unlock implementation details](remote-unlock.md). Do not assume this feature works on a generic Linux lock screen.

## Uninstall

The repository includes `linux/scripts/uninstall.sh`. Inspect the script before execution and verify that the user service is removed as expected.
