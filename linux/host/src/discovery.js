import fs from 'node:fs';
import { spawn } from 'node:child_process';
import { config } from './config.js';
import { cryptoManager } from './crypto.js';

export class ServiceDiscovery {
  constructor() {
    this.bonjour = null;
    this.service = null;
    this.avahiProcess = null;
    this.isAdvertising = false;
  }

  start(port = config.port) {
    if (this.isAdvertising) return;

    // Use avahi-publish on Linux if available (standard and integrates with system Avahi daemon)
    if (fs.existsSync('/usr/bin/avahi-publish')) {
      try {
        const avahiArgs = [
          '-s',
          `Regnant - ${config.hostName}`,
          '_regnant._tcp',
          String(port),
          `version=${config.protocolVersion}`,
          `hostId=${config.hostId}`,
          `fp=${cryptoManager.certFingerprint}`,
        ];
        this.avahiProcess = spawn('/usr/bin/avahi-publish', avahiArgs, {
          stdio: 'ignore',
        });
        this.avahiProcess.on('error', () => {});
        this.isAdvertising = true;
        return;
      } catch (e) {
        // Fall back to bonjour-service
      }
    }

    // Fallback using bonjour-service
    try {
      import('bonjour-service').then(({ Bonjour }) => {
        this.bonjour = new Bonjour();
        this.service = this.bonjour.publish({
          name: `Regnant - ${config.hostName}`,
          type: 'regnant',
          protocol: 'tcp',
          port,
          txt: {
            version: config.protocolVersion,
            hostId: config.hostId,
            name: config.hostName,
            fp: cryptoManager.certFingerprint,
          },
        });
        this.service.on('error', () => {});
        this.isAdvertising = true;
      }).catch(() => {});
    } catch (e) {
      // Non-fatal
    }
  }

  stop() {
    if (this.service) {
      try {
        this.service.stop();
      } catch (e) {}
    }
    if (this.bonjour) {
      try {
        this.bonjour.destroy();
      } catch (e) {}
    }
    if (this.avahiProcess) {
      try {
        this.avahiProcess.kill('SIGTERM');
      } catch (e) {}
      this.avahiProcess = null;
    }
    this.isAdvertising = false;
  }
}

export const serviceDiscovery = new ServiceDiscovery();
