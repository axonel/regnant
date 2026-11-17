import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import net from 'node:net';
import { dbManager } from './db.js';
import { notifier } from './notifier.js';

export function getOmarchySocketPath() {
  const runtimeDir = process.env.XDG_RUNTIME_DIR || `/run/user/${process.getuid ? process.getuid() : 1000}`;
  if (fs.existsSync(runtimeDir)) {
    try {
      const files = fs.readdirSync(runtimeDir);
      const sockFile = files.find(f => f.startsWith('omarchy-shell-') && f.endsWith('.sock'));
      if (sockFile) {
        return `${runtimeDir}/${sockFile}`;
      }
    } catch {}
  }
  return null;
}

export function sendOmarchyIpc(target, method, args = [], timeoutMs = 2000) {
  return new Promise((resolve, reject) => {
    const socketPath = getOmarchySocketPath();
    if (!socketPath) {
      return reject(new Error('Omarchy shell socket not found'));
    }

    const payload = [target, method, ...args].join('\x1f') + '\x1e';
    let response = '';
    const socket = net.createConnection(socketPath, () => {
      socket.write(payload);
    });

    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error('Omarchy shell IPC timeout'));
    }, timeoutMs);

    socket.on('data', chunk => {
      response += chunk.toString('utf8');
      if (response.includes('\x1e')) {
        clearTimeout(timer);
        socket.end();
      }
    });

    socket.on('end', () => {
      clearTimeout(timer);
      if (response.startsWith('OK\x1f')) {
        const out = response.slice(3).replace(/\x1e.*$/, '');
        resolve(out);
      } else if (response.startsWith('SKIP')) {
        reject(new Error('Target or function not found in Omarchy shell'));
      } else {
        resolve(response.replace(/\x1e.*$/, ''));
      }
    });

    socket.on('error', err => {
      clearTimeout(timer);
      reject(err);
    });
  });
}

export class PowerManager {
  async lockSession() {
    return new Promise((resolve, reject) => {
      // Check for Omarchy lock script
      const omarchyLockPath = '/usr/share/omarchy/bin/omarchy-system-lock';
      const useOmarchy = fs.existsSync(omarchyLockPath);
      const bin = useOmarchy ? omarchyLockPath : '/usr/bin/loginctl';
      const args = useOmarchy ? [] : ['lock-session'];

      const child = spawn(bin, args, {
        detached: true,
        stdio: 'ignore',
      });
      child.on('close', code => {
        if (code === 0) {
          notifier.notify('Regnant', 'Workstation session locked');
          resolve({ success: true, message: 'Workstation session locked successfully', state: 'LOCKED' });
        } else {
          // If omarchy-system-lock failed, attempt loginctl fallback
          if (useOmarchy) {
            const fallback = spawn('/usr/bin/loginctl', ['lock-session'], { detached: true, stdio: 'ignore' });
            fallback.on('close', fCode => {
              if (fCode === 0) {
                notifier.notify('Regnant', 'Workstation session locked');
                resolve({ success: true, message: 'Workstation session locked successfully', state: 'LOCKED' });
              } else {
                reject(new Error(`Session lock failed with code ${fCode}`));
              }
            });
            fallback.on('error', reject);
          } else {
            reject(new Error(`loginctl lock-session failed with code ${code}`));
          }
        }
      });
      child.on('error', err => {
        // Fallback to loginctl if spawn failed
        if (useOmarchy) {
          const fallback = spawn('/usr/bin/loginctl', ['lock-session'], { detached: true, stdio: 'ignore' });
          fallback.on('close', fCode => {
            if (fCode === 0) {
              resolve({ success: true, message: 'Workstation session locked successfully', state: 'LOCKED' });
            } else {
              reject(err);
            }
          });
          fallback.on('error', reject);
        } else {
          reject(err);
        }
      });
    });
  }

  getLockStatus() {
    // 1. Try omarchy-shell lock isLocked
    try {
      const out = execFileSync('omarchy-shell', ['lock', 'isLocked'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 1500
      }).trim();
      if (out === 'true') return { locked: true, state: 'LOCKED' };
      if (out === 'false') return { locked: false, state: 'UNLOCKED' };
    } catch {
      // ignore
    }

    // 2. Try loginctl LockedHint
    try {
      const sessionList = execFileSync('loginctl', ['list-sessions', '--no-legend'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 1500
      });
      const lines = sessionList.trim().split('\n');
      for (const line of lines) {
        const parts = line.trim().split(/\s+/);
        if (parts.length >= 3 && parts[2] === (process.env.USER || 'roonakyadav')) {
          const sessId = parts[0];
          const hint = execFileSync('loginctl', ['show-session', sessId, '-p', 'LockedHint'], {
            encoding: 'utf8',
            stdio: ['ignore', 'pipe', 'ignore'],
            timeout: 1000
          }).trim();
          if (hint.includes('LockedHint=yes')) return { locked: true, state: 'LOCKED' };
          if (hint.includes('LockedHint=no')) return { locked: false, state: 'UNLOCKED' };
        }
      }
    } catch {
      // ignore
    }

    return { locked: null, state: 'UNKNOWN' };
  }

  async unlockSession(password) {
    if (!password || typeof password !== 'string' || password.length === 0) {
      return { success: false, status: 'INVALID_PASSWORD', message: 'Password cannot be empty' };
    }

    // 1. Verify lock status first
    const currentLock = this.getLockStatus();
    if (currentLock.locked === false) {
      return { success: false, status: 'NOT_LOCKED', message: 'Laptop is not currently locked' };
    }

    let initResult = null;
    try {
      initResult = await sendOmarchyIpc('lock', 'unlockWithPassword', [password]);
    } catch (err) {
      // If direct socket fails, try omarchy-shell fallback
      try {
        initResult = execFileSync('omarchy-shell', ['lock', 'unlockWithPassword', password], {
          encoding: 'utf8',
          stdio: ['ignore', 'pipe', 'ignore'],
          timeout: 2000
        }).trim();
      } catch (fallbackErr) {
        return { success: false, status: 'HOST_UNAVAILABLE', message: `Omarchy shell lock IPC failed: ${err.message}` };
      }
    }

    if (initResult === 'not-locked') {
      return { success: false, status: 'NOT_LOCKED', message: 'Laptop is not currently locked' };
    }
    if (initResult === 'empty-password') {
      return { success: false, status: 'INVALID_PASSWORD', message: 'Password cannot be empty' };
    }
    if (initResult === 'busy') {
      return { success: false, status: 'BUSY', message: 'Authentication attempt already in progress' };
    }
    if (initResult !== 'initiated') {
      return { success: false, status: 'HOST_UNAVAILABLE', message: `Unexpected lock IPC response: ${initResult}` };
    }

    // 2. Poll unlock status for PAM result (up to 4 seconds)
    const startTime = Date.now();
    const timeoutMs = 4000;

    while (Date.now() - startTime < timeoutMs) {
      await new Promise(r => setTimeout(r, 120));
      try {
        const authStatus = await sendOmarchyIpc('lock', 'unlockStatus', [], 1000);
        if (authStatus === 'success') {
          notifier.notify('Regnant', 'Workstation session unlocked');
          return { success: true, status: 'UNLOCKED', message: 'Workstation session unlocked successfully' };
        }
        if (authStatus === 'failure') {
          return { success: false, status: 'INVALID_PASSWORD', message: 'Authentication failed: invalid password' };
        }
      } catch {
        // Check lock status directly as fallback
        const pollStatus = this.getLockStatus();
        if (pollStatus.locked === false) {
          notifier.notify('Regnant', 'Workstation session unlocked');
          return { success: true, status: 'UNLOCKED', message: 'Workstation session unlocked successfully' };
        }
      }
    }

    // Final check
    const finalStatus = this.getLockStatus();
    if (finalStatus.locked === false) {
      notifier.notify('Regnant', 'Workstation session unlocked');
      return { success: true, status: 'UNLOCKED', message: 'Workstation session unlocked successfully' };
    }

    return { success: false, status: 'INVALID_PASSWORD', message: 'Authentication timed out or failed' };
  }

  async suspendSystem() {
    return new Promise((resolve, reject) => {
      notifier.notify('Regnant', 'Suspending workstation...');
      const child = spawn('/usr/bin/systemctl', ['suspend'], {
        detached: true,
        stdio: 'ignore',
      });
      child.on('close', code => {
        if (code === 0) {
          resolve({ success: true, message: 'Workstation suspended' });
        } else {
          reject(new Error(`systemctl suspend failed with code ${code}`));
        }
      });
      child.on('error', reject);
    });
  }

  async rebootSystem(approvalId) {
    if (!approvalId) {
      throw new Error('Reboot requires an explicit approval confirmation token');
    }

    const approval = dbManager.getApproval(approvalId);
    if (!approval || approval.status !== 'APPROVED' || approval.action !== 'power_reboot') {
      throw new Error('Invalid or unapproved confirmation token for reboot');
    }

    notifier.notify('Regnant', 'Rebooting workstation now...');
    const child = spawn('/usr/bin/systemctl', ['reboot'], {
      detached: true,
      stdio: 'ignore',
    });
    child.unref();

    return { success: true, message: 'Reboot command dispatched to systemd' };
  }

  async shutdownSystem(approvalId) {
    if (!approvalId) {
      throw new Error('Shutdown requires an explicit approval confirmation token');
    }

    const approval = dbManager.getApproval(approvalId);
    if (!approval || approval.status !== 'APPROVED' || approval.action !== 'power_shutdown') {
      throw new Error('Invalid or unapproved confirmation token for shutdown');
    }

    notifier.notify('Regnant', 'Shutting down workstation now...');
    const child = spawn('/usr/bin/systemctl', ['poweroff'], {
      detached: true,
      stdio: 'ignore',
    });
    child.unref();

    return { success: true, message: 'Shutdown command dispatched to systemd' };
  }
}

export const powerManager = new PowerManager();
