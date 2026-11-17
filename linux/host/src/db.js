import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { config } from './config.js';

export class DatabaseManager {
  constructor(dbPath = null) {
    this.dbPath = dbPath || path.join(config.dataDir, 'regnant.db');
    this.db = new DatabaseSync(this.dbPath);
    this.init();
  }

  init() {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS devices (
        device_id TEXT PRIMARY KEY,
        device_name TEXT,
        token_hash TEXT NOT NULL,
        paired_at INTEGER NOT NULL,
        last_seen INTEGER NOT NULL
      );

      CREATE TABLE IF NOT EXISTS history (
        request_id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        action TEXT NOT NULL,
        target TEXT NOT NULL,
        raw_command TEXT,
        status TEXT NOT NULL,
        message TEXT,
        timestamp INTEGER NOT NULL,
        executed_at INTEGER
      );

      CREATE INDEX IF NOT EXISTS idx_history_timestamp ON history(timestamp DESC);
    `);
  }

  registerDevice(deviceId, deviceName, tokenHash) {
    const now = Date.now();
    const stmt = this.db.prepare(`
      INSERT INTO devices (device_id, device_name, token_hash, paired_at, last_seen)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(device_id) DO UPDATE SET
        device_name = excluded.device_name,
        token_hash = excluded.token_hash,
        last_seen = excluded.last_seen
    `);
    stmt.run(deviceId, deviceName, tokenHash, now, now);
  }

  getDevice(deviceId) {
    const stmt = this.db.prepare(`SELECT * FROM devices WHERE device_id = ?`);
    return stmt.get(deviceId) || null;
  }

  getDeviceByTokenHash(tokenHash) {
    const stmt = this.db.prepare(`SELECT * FROM devices WHERE token_hash = ?`);
    return stmt.get(tokenHash) || null;
  }

  listDevices() {
    const stmt = this.db.prepare(`SELECT device_id, device_name, paired_at, last_seen FROM devices ORDER BY last_seen DESC`);
    return stmt.all();
  }

  revokeDevice(deviceId) {
    const stmt = this.db.prepare(`DELETE FROM devices WHERE device_id = ?`);
    stmt.run(deviceId);
  }

  updateDeviceLastSeen(deviceId) {
    const stmt = this.db.prepare(`UPDATE devices SET last_seen = ? WHERE device_id = ?`);
    stmt.run(Date.now(), deviceId);
  }

  hasRequest(requestId) {
    const stmt = this.db.prepare(`SELECT 1 FROM history WHERE request_id = ?`);
    return !!stmt.get(requestId);
  }

  recordCommand(record) {
    const stmt = this.db.prepare(`
      INSERT INTO history (request_id, device_id, action, target, raw_command, status, message, timestamp, executed_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    stmt.run(
      record.requestId,
      record.deviceId,
      record.action,
      record.target,
      record.rawCommand || '',
      record.status,
      record.message || '',
      record.timestamp || Date.now(),
      record.executedAt || null
    );
  }

  updateCommandStatus(requestId, status, message = '', executedAt = Date.now()) {
    const stmt = this.db.prepare(`
      UPDATE history SET status = ?, message = ?, executed_at = ? WHERE request_id = ?
    `);
    stmt.run(status, message, executedAt, requestId);
  }

  getHistory(limit = 50) {
    const stmt = this.db.prepare(`
      SELECT request_id, device_id, action, target, raw_command, status, message, timestamp, executed_at
      FROM history ORDER BY timestamp DESC LIMIT ?
    `);
    return stmt.all(limit);
  }

  close() {
    this.db.close();
  }
}

export const dbManager = new DatabaseManager();
