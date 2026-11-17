import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { DEFAULT_PORT, PROTOCOL_VERSION } from '../../../shared/protocol.js';

export class HostConfig {
  constructor() {
    this.configDir = process.env.REGNANT_CONFIG_DIR || path.join(os.homedir(), '.config', 'regnant');
    this.dataDir = process.env.REGNANT_DATA_DIR || path.join(os.homedir(), '.local', 'share', 'regnant');
    this.tlsDir = path.join(this.configDir, 'tls');

    this.ensureDirs();
    this.loadHostIdentity();
    this.port = parseInt(process.env.REGNANT_PORT || String(DEFAULT_PORT), 10);
    this.protocolVersion = PROTOCOL_VERSION;
  }

  ensureDirs() {
    fs.mkdirSync(this.configDir, { recursive: true, mode: 0o700 });
    fs.mkdirSync(this.dataDir, { recursive: true, mode: 0o700 });
    fs.mkdirSync(this.tlsDir, { recursive: true, mode: 0o700 });
  }

  loadHostIdentity() {
    const idPath = path.join(this.configDir, 'host-identity.json');
    if (fs.existsSync(idPath)) {
      try {
        const raw = JSON.parse(fs.readFileSync(idPath, 'utf8'));
        this.hostId = raw.hostId;
        this.hostName = raw.hostName || os.hostname() || 'Regnant Host';
        return;
      } catch (e) {
        // regenerate if corrupted
      }
    }

    this.hostId = crypto.randomUUID();
    this.hostName = `${os.hostname()} (Omarchy)`;
    fs.writeFileSync(idPath, JSON.stringify({
      hostId: this.hostId,
      hostName: this.hostName,
      created: Date.now(),
    }, null, 2), { mode: 0o600 });
  }
}

export const config = new HostConfig();
