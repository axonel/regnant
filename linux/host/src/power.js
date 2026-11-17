import { spawn } from 'node:child_process';
import { dbManager } from './db.js';
import { notifier } from './notifier.js';

export class PowerManager {
  async lockSession() {
    return new Promise((resolve, reject) => {
      const child = spawn('/usr/bin/loginctl', ['lock-session'], {
        detached: true,
        stdio: 'ignore',
      });
      child.on('close', code => {
        if (code === 0) {
          notifier.notify('Regnant', 'Workstation session locked');
          resolve({ success: true, message: 'Workstation session locked successfully' });
        } else {
          reject(new Error(`loginctl lock-session failed with code ${code}`));
        }
      });
      child.on('error', reject);
    });
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
