import path from 'node:path';
import fs from 'node:fs';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ICON_PATH = path.resolve(__dirname, '../../../assets/icon-48.png');

export class DesktopNotifier {
  constructor({ spawnProcess = spawn } = {}) {
    this.spawnProcess = spawnProcess;
    this.lastNotifications = new Map(); // key -> timestamp
    this.debounceWindowMs = 5000; // Deduplicate within 5 seconds
  }

  notify(title, message, urgency = 'normal') {
    const key = `${title}:${message}`;
    const now = Date.now();
    const lastTime = this.lastNotifications.get(key) || 0;

    if (now - lastTime < this.debounceWindowMs) {
      return; // Deduplicated
    }

    this.lastNotifications.set(key, now);

    const args = [
      title,
      message,
      '--app-name=Regnant',
      `--urgency=${urgency}`,
    ];

    if (fs.existsSync(ICON_PATH)) {
      args.push(`--icon=${ICON_PATH}`);
    }

    try {
      const child = this.spawnProcess('/usr/bin/notify-send', args, {
        detached: true,
        stdio: 'ignore',
      });
      // Child-process launch failures are emitted asynchronously. A missing
      // desktop notification daemon must never crash the host service.
      child.on?.('error', () => {});
      child.unref?.();
    } catch (e) {
      // Non-fatal if notify-send fails
    }
  }

  phoneConnected(deviceName) {
    this.notify('Regnant', `Phone connected: ${deviceName || 'Android device'}`);
  }

  phoneDisconnected(deviceName) {
    this.notify('Regnant', `Phone disconnected: ${deviceName || 'Android device'}`);
  }

  phoneReconnected(deviceName) {
    this.notify('Regnant', `Phone reconnected: ${deviceName || 'Android device'}`);
  }

  pairingCompleted(deviceName) {
    this.notify('Regnant', `Pairing completed with ${deviceName || 'Android phone'}`);
  }

  commandExecuted(commandName) {
    this.notify('Regnant', `Executed command: ${commandName}`);
  }

  commandFailed(commandName, reason) {
    this.notify('Regnant', `Failed command: ${commandName} (${reason})`, 'critical');
  }
}

export const notifier = new DesktopNotifier();
