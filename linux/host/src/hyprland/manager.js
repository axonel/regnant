import { execFileSync } from 'node:child_process';
import { EventEmitter } from 'node:events';
import net from 'node:net';
import path from 'node:path';

export class HyprlandManager extends EventEmitter {
  constructor() {
    super();
    this.socketClient = null;
    this.reconnectTimer = null;
    this._isListening = false;
  }

  isAvailable() {
    try {
      const out = execFileSync('hyprctl', ['version'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 2000
      });
      return out.includes('Hyprland');
    } catch {
      return false;
    }
  }

  getSocketPath() {
    const xdg = process.env.XDG_RUNTIME_DIR || '/run/user/1000';
    const sig = process.env.HYPRLAND_INSTANCE_SIGNATURE;
    if (!sig) return null;
    return path.join(xdg, 'hypr', sig, '.socket2.sock');
  }

  startEventListener() {
    if (this._isListening) return;
    this._isListening = true;
    this._connectSocket();
  }

  _connectSocket() {
    if (!this._isListening) return;
    const socketPath = this.getSocketPath();
    if (!socketPath) return;

    try {
      this.socketClient = net.createConnection(socketPath);

      let buffer = '';
      this.socketClient.on('data', chunk => {
        buffer += chunk.toString();
        const lines = buffer.split('\n');
        buffer = lines.pop(); // keep last incomplete line

        let wsChanged = false;
        for (const line of lines) {
          if (!line) continue;
          if (line.startsWith('workspace>>') || line.startsWith('workspacev2>>') || line.startsWith('focusedmon>>')) {
            wsChanged = true;
          }
        }

        if (wsChanged) {
          this._notifyWorkspaceChanged();
        }
      });

      this.socketClient.on('error', () => {
        this._scheduleReconnect();
      });

      this.socketClient.on('close', () => {
        this._scheduleReconnect();
      });
    } catch {
      this._scheduleReconnect();
    }
  }

  _scheduleReconnect() {
    if (this.socketClient) {
      try { this.socketClient.destroy(); } catch {}
      this.socketClient = null;
    }
    if (!this._isListening) return;
    if (this.reconnectTimer) return;

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this._connectSocket();
    }, 2000);
  }

  stopEventListener() {
    this._isListening = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.socketClient) {
      try { this.socketClient.destroy(); } catch {}
      this.socketClient = null;
    }
  }

  _notifyWorkspaceChanged() {
    try {
      const activeWs = this.getActiveWorkspace();
      const activeMon = this.getActiveMonitor();
      this.emit('workspace', {
        workspaceId: activeWs.id,
        workspaceName: activeWs.name,
        monitorName: activeWs.monitor || activeMon?.name || 'default',
        activeSlot: activeWs.id,
        timestamp: Date.now()
      });
    } catch {
      // ignore
    }
  }

  getActiveWorkspace() {
    try {
      const out = execFileSync('hyprctl', ['-j', 'activeworkspace'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 2000
      });
      const ws = JSON.parse(out);
      return {
        id: ws.id,
        name: ws.name,
        monitor: ws.monitor,
        monitorId: ws.monitorID,
        windowsCount: ws.windows || 0
      };
    } catch (err) {
      return { id: 1, name: '1', monitor: 'default', monitorId: 0, windowsCount: 0 };
    }
  }

  getActiveMonitor() {
    try {
      const out = execFileSync('hyprctl', ['-j', 'monitors'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 2000
      });
      const mons = JSON.parse(out);
      const focused = mons.find(m => m.focused) || mons[0] || null;
      return focused ? {
        id: focused.id,
        name: focused.name,
        activeWorkspaceId: focused.activeWorkspace?.id || 1,
        activeWorkspaceName: focused.activeWorkspace?.name || '1'
      } : null;
    } catch {
      return null;
    }
  }

  getClients() {
    try {
      const out = execFileSync('hyprctl', ['-j', 'clients'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 3000
      });
      const clients = JSON.parse(out);
      return clients.map(c => ({
        address: c.address,
        title: c.title || 'Untitled',
        className: c.initialClass || c.class || 'Window',
        workspaceId: c.workspace?.id || 1,
        workspaceName: c.workspace?.name || '1',
        pid: c.pid,
        visible: !!c.visible
      }));
    } catch (err) {
      if (!this.isAvailable()) {
        return [];
      }
      throw new Error(`Failed to get clients: ${err.message}`);
    }
  }

  getWorkspaces() {
    try {
      const out = execFileSync('hyprctl', ['-j', 'workspaces'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 3000
      });
      const workspaces = JSON.parse(out);
      return workspaces.map(w => ({
        id: w.id,
        name: w.name,
        monitor: w.monitor,
        windowsCount: w.windows || 0
      })).sort((a, b) => a.id - b.id);
    } catch (err) {
      if (!this.isAvailable()) {
        return [];
      }
      throw new Error(`Failed to get workspaces: ${err.message}`);
    }
  }

  focusWindow(address) {
    if (!/^0x[0-9a-fA-F]+$/.test(address)) {
      throw new Error('Invalid window address');
    }
    execFileSync('hyprctl', ['dispatch', 'focuswindow', `address:${address}`], {
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 3000
    });
    return true;
  }

  closeWindow(address) {
    if (!/^0x[0-9a-fA-F]+$/.test(address)) {
      throw new Error('Invalid window address');
    }
    execFileSync('hyprctl', ['dispatch', 'closewindow', `address:${address}`], {
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 3000
    });
    return true;
  }

  moveToWorkspace(address, workspaceId) {
    if (!/^0x[0-9a-fA-F]+$/.test(address)) {
      throw new Error('Invalid window address');
    }
    const safeWs = parseInt(workspaceId, 10);
    if (isNaN(safeWs)) throw new Error('Invalid workspace ID');

    execFileSync('hyprctl', ['dispatch', 'movetoworkspacesilent', `${safeWs},address:${address}`], {
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 3000
    });
    return true;
  }

  switchWorkspace(workspaceId, monitorName = null) {
    const safeWs = parseInt(workspaceId, 10);
    if (isNaN(safeWs) || safeWs < 1 || safeWs > 5) {
      throw new Error('Workspace ID must be an integer between 1 and 5');
    }

    // Method 1: Try Omarchy-native workspace switcher
    try {
      const monArg = monitorName ? `, "${monitorName}"` : '';
      execFileSync('hyprctl', ['dispatch', `o.workspaces.focus(${safeWs}${monArg})`], {
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 3000
      });
      return this.getActiveWorkspace();
    } catch (omarchyErr) {
      // Method 2: Fall back to native Hyprland Lua dispatcher
      try {
        execFileSync('hyprctl', ['dispatch', `hl.dsp.focus({ workspace = "${safeWs}" })`], {
          stdio: ['ignore', 'pipe', 'pipe'],
          timeout: 3000
        });
        return this.getActiveWorkspace();
      } catch {
        // Method 3: Fall back to standard hyprctl workspace command
        execFileSync('hyprctl', ['dispatch', 'workspace', String(safeWs)], {
          stdio: ['ignore', 'pipe', 'pipe'],
          timeout: 3000
        });
        return this.getActiveWorkspace();
      }
    }
  }
}

let _hyprlandManagerInstance = null;
export const hyprlandManager = new Proxy({}, {
  get(target, prop) {
    if (!_hyprlandManagerInstance) {
      _hyprlandManagerInstance = new HyprlandManager();
    }
    const val = _hyprlandManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_hyprlandManagerInstance) : val;
  }
});
