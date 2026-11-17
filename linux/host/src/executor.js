import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { ACTIONS, TARGETS } from '../../../shared/protocol.js';

export class ActionExecutor {
  constructor() {
    this.appMap = this.detectInstalledApplications();
  }

  /**
   * Discovers and maps actual installed binaries on this system based on the environment audit.
   */
  detectInstalledApplications() {
    const homeDir = os.homedir();
    const map = {
      [TARGETS.TERMINAL]: null,
      [TARGETS.BRAVE]: null,
      [TARGETS.FILE_MANAGER]: null,
      [TARGETS.CALCULATOR]: null,
      [TARGETS.DOWNLOADS]: path.join(homeDir, 'Downloads'),
      [TARGETS.HOME]: homeDir,
    };

    // Terminal candidates
    const terminalCandidates = ['/usr/bin/foot', '/usr/bin/alacritty', '/usr/bin/kitty', '/usr/bin/ghostty'];
    for (const c of terminalCandidates) {
      if (fs.existsSync(c)) {
        map[TARGETS.TERMINAL] = { exec: c, args: [] };
        break;
      }
    }

    // Brave / Browser candidates
    const browserCandidates = ['/usr/bin/brave', '/usr/bin/brave-browser', '/usr/bin/chromium', '/usr/bin/firefox'];
    for (const c of browserCandidates) {
      if (fs.existsSync(c)) {
        map[TARGETS.BRAVE] = { exec: c, args: [] };
        break;
      }
    }

    // File manager candidates
    const fmCandidates = ['/usr/bin/nautilus', '/usr/bin/thunar', '/usr/bin/dolphin', '/usr/bin/pcmanfm'];
    for (const c of fmCandidates) {
      if (fs.existsSync(c)) {
        map[TARGETS.FILE_MANAGER] = { exec: c, args: [] };
        break;
      }
    }

    // Calculator candidates
    const calcCandidates = [
      path.join(homeDir, '.local/bin/omacalc'),
      '/usr/bin/gnome-calculator',
      '/usr/bin/kcalc',
      '/usr/bin/galculator'
    ];
    for (const c of calcCandidates) {
      if (fs.existsSync(c)) {
        map[TARGETS.CALCULATOR] = { exec: c, args: [] };
        break;
      }
    }

    return map;
  }

  /**
   * Resolves display session environment variables (Wayland/Hyprland)
   */
  resolveSessionEnvironment() {
    const env = { ...process.env };
    const uid = typeof process.getuid === 'function' ? process.getuid() : 1000;
    const runtimeDir = process.env.XDG_RUNTIME_DIR || `/run/user/${uid}`;

    // If WAYLAND_DISPLAY is missing from service environment, inspect /run/user/<uid>/wayland-*
    if (!env.WAYLAND_DISPLAY) {
      if (fs.existsSync(path.join(runtimeDir, 'wayland-1'))) {
        env.WAYLAND_DISPLAY = 'wayland-1';
      } else if (fs.existsSync(path.join(runtimeDir, 'wayland-0'))) {
        env.WAYLAND_DISPLAY = 'wayland-0';
      }
    }

    if (!env.XDG_CURRENT_DESKTOP) {
      env.XDG_CURRENT_DESKTOP = 'Hyprland';
    }

    if (!env.XDG_SESSION_TYPE) {
      env.XDG_SESSION_TYPE = 'wayland';
    }

    // If HYPRLAND_INSTANCE_SIGNATURE is missing, discover from socket directory
    if (!env.HYPRLAND_INSTANCE_SIGNATURE) {
      const hyprDir = path.join(runtimeDir, 'hypr');
      if (fs.existsSync(hyprDir)) {
        try {
          const entries = fs.readdirSync(hyprDir);
          const sig = entries.find(e => e.includes('_'));
          if (sig) {
            env.HYPRLAND_INSTANCE_SIGNATURE = sig;
          }
        } catch (e) {
          // ignore
        }
      }
    }

    return env;
  }

  /**
   * Executes a validated typed action.
   *
   * @param {string} action
   * @param {string} target
   * @returns {Promise<{ success: boolean, message: string, pid?: number, error?: string }>}
   */
  async execute(action, target) {
    const env = this.resolveSessionEnvironment();

    if (action === ACTIONS.LAUNCH_APP) {
      const app = this.appMap[target];
      if (!app || !app.exec) {
        return {
          success: false,
          error: `No installed application available for target "${target}".`,
        };
      }

      try {
        const child = spawn(app.exec, app.args, {
          detached: true,
          stdio: 'ignore',
          env,
        });
        child.unref();

        const baseName = path.basename(app.exec);
        return {
          success: true,
          message: `Launched ${baseName} successfully`,
          pid: child.pid,
        };
      } catch (err) {
        return {
          success: false,
          error: `Failed to launch application: ${err.message}`,
        };
      }
    }

    if (action === ACTIONS.OPEN_PATH) {
      const targetPath = this.appMap[target];
      if (!targetPath || !fs.existsSync(targetPath)) {
        return {
          success: false,
          error: `Target path does not exist on host: ${targetPath}`,
        };
      }

      try {
        const xdgOpen = '/usr/bin/xdg-open';
        const child = spawn(xdgOpen, [targetPath], {
          detached: true,
          stdio: 'ignore',
          env,
        });
        child.unref();

        return {
          success: true,
          message: `Opened ${path.basename(targetPath) || targetPath}`,
          pid: child.pid,
        };
      } catch (err) {
        return {
          success: false,
          error: `Failed to open path: ${err.message}`,
        };
      }
    }

    return {
      success: false,
      error: `Unsupported action type: ${action}`,
    };
  }

  getSupportedApplications() {
    return {
      terminal: this.appMap[TARGETS.TERMINAL]?.exec || 'Unavailable',
      brave: this.appMap[TARGETS.BRAVE]?.exec || 'Unavailable',
      fileManager: this.appMap[TARGETS.FILE_MANAGER]?.exec || 'Unavailable',
      calculator: this.appMap[TARGETS.CALCULATOR]?.exec || 'Unavailable',
      downloads: this.appMap[TARGETS.DOWNLOADS] || 'Unavailable',
      home: this.appMap[TARGETS.HOME] || 'Unavailable',
    };
  }
}

export const actionExecutor = new ActionExecutor();
