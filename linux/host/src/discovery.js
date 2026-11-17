import { Bonjour } from 'bonjour-service';
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

    const txtRecord = {
      version: config.protocolVersion,
      hostId: config.hostId,
      name: config.hostName,
      fp: cryptoManager.certFingerprint,
    };

    try {
      this.bonjour = new Bonjour();
      this.service = this.bonjour.publish({
        name: `Regnant - ${config.hostName}`,
        type: 'regnant',
        protocol: 'tcp',
        port,
        txt: txtRecord,
      });
      this.service.on('error', (err) => {
        // Avahi may already own the service name on the network
      });
      this.isAdvertising = true;
    } catch (e) {
      // Bonjour publish error non-fatal
    }

    // Also register with avahi-publish if available for system-level mDNS integration
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
      this.avahiProcess.on('error', () => {
        // Avahi-publish error is non-fatal
      });
    } catch (e) {
      // ignore
    }
  }

  stop() {
    if (this.service) {
      try {
        this.service.stop();
      } catch (e) {
        // ignore
      }
    }
    if (this.bonjour) {
      try {
        this.bonjour.destroy();
      } catch (e) {
        // ignore
      }
    }
    if (this.avahiProcess) {
      try {
        this.avahiProcess.kill('SIGTERM');
      } catch (e) {
        // ignore
      }
    }
    this.isAdvertising = false;
  }
}

export const serviceDiscovery = new ServiceDiscovery();
