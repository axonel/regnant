#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== Installing Regnant Desktop & Host Service ==="

BIN_DIR="$HOME/.local/bin"
ICON_DIR="$HOME/.local/share/icons/hicolor/128x128/apps"
APP_DIR="$HOME/.local/share/applications"
SYSTEMD_DIR="$HOME/.config/systemd/user"

mkdir -p "$BIN_DIR" "$ICON_DIR" "$APP_DIR" "$SYSTEMD_DIR"

# 1. Install desktop icon
cp "$PROJECT_ROOT/assets/icon-128.png" "$ICON_DIR/regnant.png"

# 2. Create host runner script
cat << EOF > "$BIN_DIR/regnant-host"
#!/usr/bin/env bash
exec /usr/bin/node "$PROJECT_ROOT/linux/host/src/index.js" "\$@"
EOF
chmod +x "$BIN_DIR/regnant-host"

# 3. Create desktop GUI runner script
cat << EOF > "$BIN_DIR/regnant-desktop"
#!/usr/bin/env bash
exec /usr/bin/electron42 "$PROJECT_ROOT/linux/desktop/src/main.js" --ozone-platform=wayland --disable-gpu --disable-gpu-sandbox "\$@"
EOF
chmod +x "$BIN_DIR/regnant-desktop"

# 4. Install desktop entry
cp "$PROJECT_ROOT/linux/regnant.desktop" "$APP_DIR/regnant.desktop"
if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$APP_DIR" || true
fi

# 5. Create systemd user service for background daemon
cat << EOF > "$SYSTEMD_DIR/regnant-host.service"
[Unit]
Description=Regnant Linux Host Background Service
After=network.target

[Service]
Type=simple
ExecStart=$BIN_DIR/regnant-host
Restart=on-failure
RestartSec=3
Environment=WAYLAND_DISPLAY=wayland-1
Environment=XDG_CURRENT_DESKTOP=Hyprland
Environment=XDG_SESSION_TYPE=wayland
Environment=PATH=/home/roonakyadav/.local/bin:/usr/local/bin:/usr/bin:/bin

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable regnant-host.service
systemctl --user restart regnant-host.service

echo "✓ Regnant Host service installed and started (user-level systemd)"
echo "✓ Regnant Desktop installed to $BIN_DIR/regnant-desktop"
echo "✓ Desktop launcher installed to $APP_DIR/regnant.desktop"
echo "=== Installation complete ==="
