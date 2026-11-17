import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { actionExecutor } from './executor.js';

const execFileAsync = promisify(execFile);

export class AppManager {
  async listRunningApps() {
    const env = actionExecutor.resolveSessionEnvironment();

    try {
      const { stdout } = await execFileAsync('/usr/bin/hyprctl', ['clients', '-j'], {
        env,
        timeout: 3000,
      });

      const clients = JSON.parse(stdout || '[]');
      return clients.map(c => ({
        address: c.address,
        title: c.title || 'Untitled Window',
        appClass: c.class || 'Unknown',
        pid: c.pid,
        workspaceId: c.workspace?.id,
        workspaceName: c.workspace?.name,
        floating: c.floating,
      })).filter(c => c.title && c.title !== '');
    } catch (err) {
      throw new Error(`Failed to query Hyprland clients: ${err.message}`);
    }
  }

  async closeWindow(address) {
    if (!address || typeof address !== 'string' || !address.startsWith('0x')) {
      throw new Error(`Invalid window address format: ${address}`);
    }

    const env = actionExecutor.resolveSessionEnvironment();

    try {
      await execFileAsync('/usr/bin/hyprctl', ['dispatch', 'closewindow', `address:${address}`], {
        env,
        timeout: 3000,
      });

      return {
        success: true,
        message: `Closed window at address ${address}`,
      };
    } catch (err) {
      throw new Error(`Failed to close window: ${err.message}`);
    }
  }
}

export const appManager = new AppManager();
