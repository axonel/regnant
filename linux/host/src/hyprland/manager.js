import { execFileSync } from 'node:child_process';

export class HyprlandManager {
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

  switchWorkspace(workspaceId) {
    const safeWs = parseInt(workspaceId, 10);
    if (isNaN(safeWs)) throw new Error('Invalid workspace ID');

    execFileSync('hyprctl', ['dispatch', 'workspace', String(safeWs)], {
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 3000
    });
    return true;
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
