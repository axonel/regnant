import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { config } from './config.js';
import { PAIRING_CODE_TTL_MS, MAX_PAIRING_ATTEMPTS } from '../../../shared/protocol.js';

export class CryptoManager {
  constructor() {
    this.tlsKeyPath = path.join(config.tlsDir, 'key.pem');
    this.tlsCertPath = path.join(config.tlsDir, 'cert.pem');
    this.currentPairingSession = null;
    this.ensureTlsCertificates();
  }

  ensureTlsCertificates() {
    if (!fs.existsSync(this.tlsKeyPath) || !fs.existsSync(this.tlsCertPath)) {
      // Gather local IP addresses for Subject Alternative Names (SAN)
      const sans = new Set(['DNS:localhost', 'IP:127.0.0.1']);
      const interfaces = os.networkInterfaces();
      for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
          if (iface.family === 'IPv4') {
            sans.add(`IP:${iface.address}`);
          }
        }
      }

      const sanString = Array.from(sans).join(',');
      execFileSync('/usr/bin/openssl', [
        'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
        '-keyout', this.tlsKeyPath,
        '-out', this.tlsCertPath,
        '-days', '3650',
        '-subj', '/CN=Regnant Host',
        '-addext', `subjectAltName=${sanString}`
      ], { stdio: 'pipe' });

      fs.chmodSync(this.tlsKeyPath, 0o600);
      fs.chmodSync(this.tlsCertPath, 0o644);
    }

    this.certFingerprint = this.computeCertFingerprint();
  }

  getTlsCredentials() {
    return {
      key: fs.readFileSync(this.tlsKeyPath),
      cert: fs.readFileSync(this.tlsCertPath),
      fingerprint: this.certFingerprint,
    };
  }

  computeCertFingerprint() {
    const certDer = fs.readFileSync(this.tlsCertPath);
    // Parse using openssl to extract clean SHA256 fingerprint
    const out = execFileSync('/usr/bin/openssl', [
      'x509', '-in', this.tlsCertPath, '-noout', '-fingerprint', '-sha256'
    ], { encoding: 'utf8' });
    const match = out.match(/Fingerprint=([0-9A-Fa-f:]+)/);
    if (match) {
      return match[1].trim().toUpperCase();
    }
    // Fallback sha256 of the file content
    const hash = crypto.createHash('sha256').update(certDer).digest('hex').toUpperCase();
    return hash.match(/.{2}/g).join(':');
  }

  /**
   * Generates a single-use 6-character uppercase pairing code (e.g. RG-7K9P)
   */
  generatePairingCode() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // Unambiguous chars (omitting 0, O, 1, I)
    const randomBytes = crypto.randomBytes(4);
    let code = 'RG-';
    for (let i = 0; i < 4; i++) {
      code += chars[randomBytes[i] % chars.length];
    }

    const now = Date.now();
    this.currentPairingSession = {
      code,
      createdAt: now,
      expiresAt: now + PAIRING_CODE_TTL_MS,
      attempts: 0,
      used: false,
    };

    return {
      code: this.currentPairingSession.code,
      expiresAt: this.currentPairingSession.expiresAt,
      ttlSeconds: Math.floor(PAIRING_CODE_TTL_MS / 1000),
    };
  }

  getActivePairingSession() {
    if (!this.currentPairingSession) return null;
    const now = Date.now();
    if (now >= this.currentPairingSession.expiresAt || this.currentPairingSession.used || this.currentPairingSession.attempts >= MAX_PAIRING_ATTEMPTS) {
      this.currentPairingSession = null;
      return null;
    }
    return {
      code: this.currentPairingSession.code,
      expiresAt: this.currentPairingSession.expiresAt,
      remainingSeconds: Math.max(0, Math.floor((this.currentPairingSession.expiresAt - now) / 1000)),
      attemptsLeft: MAX_PAIRING_ATTEMPTS - this.currentPairingSession.attempts,
    };
  }

  /**
   * Verify an entered pairing code with constant-time equality check
   */
  verifyPairingCode(inputCode) {
    if (!this.currentPairingSession) {
      return { success: false, reason: 'No active pairing code. Generate one on the laptop.' };
    }

    const now = Date.now();
    if (now > this.currentPairingSession.expiresAt) {
      this.currentPairingSession = null;
      return { success: false, reason: 'Pairing code expired. Please generate a new code.' };
    }

    if (this.currentPairingSession.used) {
      this.currentPairingSession = null;
      return { success: false, reason: 'Pairing code was already used.' };
    }

    if (this.currentPairingSession.attempts >= MAX_PAIRING_ATTEMPTS) {
      this.currentPairingSession = null;
      return { success: false, reason: 'Maximum pairing attempts exceeded. Please generate a new code.' };
    }

    this.currentPairingSession.attempts += 1;

    // Normalize comparison (ignore spaces/hyphens and case)
    const normalizedInput = (inputCode || '').replace(/[\s-]/g, '').toUpperCase();
    const normalizedExpected = this.currentPairingSession.code.replace(/[\s-]/g, '').toUpperCase();

    const inputHash = crypto.createHash('sha256').update(normalizedInput).digest();
    const expectedHash = crypto.createHash('sha256').update(normalizedExpected).digest();

    const matches = crypto.timingSafeEqual(inputHash, expectedHash);

    if (matches) {
      this.currentPairingSession.used = true;
      this.currentPairingSession = null; // Single use!
      return { success: true };
    }

    if (this.currentPairingSession.attempts >= MAX_PAIRING_ATTEMPTS) {
      this.currentPairingSession = null;
      return { success: false, reason: 'Invalid code. Rate limit reached; code invalidated.' };
    }

    return { success: false, reason: 'Invalid pairing code.' };
  }

  generateBearerToken() {
    return crypto.randomBytes(32).toString('hex');
  }

  hashToken(token) {
    return crypto.createHash('sha256').update(token).digest('hex');
  }

  verifyBearerToken(inputToken, storedHash) {
    if (!inputToken || !storedHash) return false;
    const computedHash = this.hashToken(inputToken);
    const bufA = Buffer.from(computedHash, 'hex');
    const bufB = Buffer.from(storedHash, 'hex');
    if (bufA.length !== bufB.length) return false;
    return crypto.timingSafeEqual(bufA, bufB);
  }
}

export const cryptoManager = new CryptoManager();
