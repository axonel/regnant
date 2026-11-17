import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import { dbManager } from './db.js';
import { notifier } from './notifier.js';

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
