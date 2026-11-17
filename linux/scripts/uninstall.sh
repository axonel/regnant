#!/usr/bin/env bash
set -euo pipefail

echo "=== Uninstalling Regnant Desktop & Host Service ==="

BIN_DIR="$HOME/.local/bin"
ICON_DIR="$HOME/.local/share/icons/hicolor/128x128/apps"
APP_DIR="$HOME/.local/share/applications"
SYSTEMD_DIR="$HOME/.config/systemd/user"

# Stop and disable systemd user service
if systemctl --user is-active --quiet regnant-host.service 2>/dev/null; then
    systemctl --user stop regnant-host.service || true
fi
if systemctl --user is-enabled --quiet regnant-host.service 2>/dev/null; then
    systemctl --user disable regnant-host.service || true
fi

rm -f "$SYSTEMD_DIR/regnant-host.service"
systemctl --user daemon-reload || true

# Remove binaries and desktop files
rm -f "$BIN_DIR/regnant-host"
rm -f "$BIN_DIR/regnant-desktop"
rm -f "$APP_DIR/regnant.desktop"
rm -f "$ICON_DIR/regnant.png"

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$APP_DIR" || true
fi

echo "✓ Removed service, desktop launchers, and executables."
echo "Note: Configuration and database in ~/.config/regnant and ~/.local/share/regnant were preserved."
echo "=== Uninstall complete ==="
