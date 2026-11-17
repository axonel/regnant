import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { config } from './config.js';
import { CAPABILITIES } from '../../../shared/protocol.js';

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

      -- Projects Table
      CREATE TABLE IF NOT EXISTS projects (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        path TEXT NOT NULL UNIQUE,
        ide TEXT NOT NULL DEFAULT 'auto',
        created_at INTEGER NOT NULL
      );

      -- Approved File Roots
      CREATE TABLE IF NOT EXISTS approved_roots (
        path TEXT PRIMARY KEY,
        label TEXT NOT NULL,
        read_only INTEGER NOT NULL DEFAULT 1,
        added_at INTEGER NOT NULL
      );

      -- Transfers Table
      CREATE TABLE IF NOT EXISTS transfers (
        id TEXT PRIMARY KEY,
        direction TEXT NOT NULL, -- 'upload' or 'download'
        file_name TEXT NOT NULL,
        file_size INTEGER NOT NULL,
        sha256 TEXT NOT NULL,
        destination TEXT NOT NULL,
        status TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        completed_at INTEGER
      );

      -- Saved Routines
      CREATE TABLE IF NOT EXISTS routines (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        description TEXT,
        steps_json TEXT NOT NULL,
        created_at INTEGER NOT NULL
      );

      -- Granular Permissions Matrix per Device
      CREATE TABLE IF NOT EXISTS permissions (
        device_id TEXT NOT NULL,
        capability TEXT NOT NULL,
        allowed INTEGER NOT NULL DEFAULT 1,
        PRIMARY KEY(device_id, capability)
      );

      -- Approval Center for Sensitive Actions
      CREATE TABLE IF NOT EXISTS approvals (
        id TEXT PRIMARY KEY,
        device_id TEXT NOT NULL,
        action TEXT NOT NULL,
        details TEXT,
        status TEXT NOT NULL, -- 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'
        created_at INTEGER NOT NULL,
        expires_at INTEGER NOT NULL
      );
    `);

    this.seedDefaultRootsAndProjects();
  }

  seedDefaultRootsAndProjects() {
    const home = os.homedir();
    const projectsDir = path.join(home, 'Projects');
    const downloadsDir = path.join(home, 'Downloads');

    // Ensure approved roots
    const rootStmt = this.db.prepare(`
      INSERT OR IGNORE INTO approved_roots (path, label, read_only, added_at)
      VALUES (?, ?, ?, ?)
    `);

    if (fs.existsSync(projectsDir)) {
      rootStmt.run(projectsDir, 'Projects', 1, Date.now());
    }
    if (fs.existsSync(downloadsDir)) {
      rootStmt.run(downloadsDir, 'Downloads', 1, Date.now());
    }

    // Auto-discover existing project directories in ~/Projects if none registered
    const countStmt = this.db.prepare(`SELECT COUNT(*) as count FROM projects`);
    const countResult = countStmt.get();
    if (countResult && countResult.count === 0 && fs.existsSync(projectsDir)) {
      try {
        const entries = fs.readdirSync(projectsDir, { withFileTypes: true });
        const insertProj = this.db.prepare(`
          INSERT OR IGNORE INTO projects (id, name, path, ide, created_at)
          VALUES (?, ?, ?, ?, ?)
        `);
        for (const entry of entries) {
          if (entry.isDirectory() && !entry.name.startsWith('.')) {
            const fullPath = path.join(projectsDir, entry.name);
            insertProj.run(`proj-${entry.name}`, entry.name, fullPath, 'auto', Date.now());
          }
        }
      } catch (e) {
        // ignore seed errors
      }
    }
  }

  // --- Devices & Auth ---

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

    // Initialize all capabilities as allowed by default for paired device
    const permStmt = this.db.prepare(`
      INSERT OR IGNORE INTO permissions (device_id, capability, allowed)
      VALUES (?, ?, 1)
    `);
    for (const cap of Object.values(CAPABILITIES)) {
      permStmt.run(deviceId, cap);
    }
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
    const pStmt = this.db.prepare(`DELETE FROM permissions WHERE device_id = ?`);
    pStmt.run(deviceId);
  }

  updateDeviceLastSeen(deviceId) {
    const stmt = this.db.prepare(`UPDATE devices SET last_seen = ? WHERE device_id = ?`);
    stmt.run(Date.now(), deviceId);
  }

  // --- Permissions Matrix ---

  getPermissions(deviceId) {
    const stmt = this.db.prepare(`SELECT capability, allowed FROM permissions WHERE device_id = ?`);
    const rows = stmt.all(deviceId);
    const result = {};
    for (const row of rows) {
      result[row.capability] = row.allowed === 1;
    }
    return result;
  }

  isPermissionAllowed(deviceId, capability) {
    if (deviceId === 'local-desktop') return true;
    const stmt = this.db.prepare(`SELECT allowed FROM permissions WHERE device_id = ? AND capability = ?`);
    const row = stmt.get(deviceId, capability);
    if (!row) return true; // Default allow if not restricted
    return row.allowed === 1;
  }

  setPermission(deviceId, capability, allowed) {
    const stmt = this.db.prepare(`
      INSERT INTO permissions (device_id, capability, allowed)
      VALUES (?, ?, ?)
      ON CONFLICT(device_id, capability) DO UPDATE SET allowed = excluded.allowed
    `);
    stmt.run(deviceId, capability, allowed ? 1 : 0);
  }

  // --- History & Audit ---

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

  // --- Projects ---

  listProjects() {
    const stmt = this.db.prepare(`SELECT id, name, path, ide, created_at FROM projects ORDER BY name ASC`);
    return stmt.all();
  }

  getProject(id) {
    const stmt = this.db.prepare(`SELECT id, name, path, ide, created_at FROM projects WHERE id = ?`);
    return stmt.get(id) || null;
  }

  registerProject(id, name, projPath, ide = 'auto') {
    const stmt = this.db.prepare(`
      INSERT INTO projects (id, name, path, ide, created_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(path) DO UPDATE SET name = excluded.name, ide = excluded.ide
    `);
    stmt.run(id, name, projPath, ide, Date.now());
  }

  deleteProject(id) {
    const stmt = this.db.prepare(`DELETE FROM projects WHERE id = ?`);
    stmt.run(id);
  }

  // --- Approved Roots ---

  listApprovedRoots() {
    const stmt = this.db.prepare(`SELECT path, label, read_only, added_at FROM approved_roots ORDER BY label ASC`);
    return stmt.all();
  }

  addApprovedRoot(rootPath, label, readOnly = 1) {
    const stmt = this.db.prepare(`
      INSERT OR REPLACE INTO approved_roots (path, label, read_only, added_at)
      VALUES (?, ?, ?, ?)
    `);
    stmt.run(rootPath, label, readOnly ? 1 : 0, Date.now());
  }

  removeApprovedRoot(rootPath) {
    const stmt = this.db.prepare(`DELETE FROM approved_roots WHERE path = ?`);
    stmt.run(rootPath);
  }

  // --- Transfers ---

  recordTransfer(transfer) {
    const stmt = this.db.prepare(`
      INSERT INTO transfers (id, direction, file_name, file_size, sha256, destination, status, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);
    stmt.run(
      transfer.id,
      transfer.direction,
      transfer.fileName,
      transfer.fileSize,
      transfer.sha256,
      transfer.destination,
      transfer.status,
      transfer.createdAt || Date.now()
    );
  }

  updateTransferStatus(id, status, completedAt = Date.now()) {
    const stmt = this.db.prepare(`UPDATE transfers SET status = ?, completed_at = ? WHERE id = ?`);
    stmt.run(status, completedAt, id);
  }

  getTransfer(id) {
    const stmt = this.db.prepare(`SELECT * FROM transfers WHERE id = ?`);
    return stmt.get(id) || null;
  }

  listTransfers(limit = 20) {
    const stmt = this.db.prepare(`SELECT * FROM transfers ORDER BY created_at DESC LIMIT ?`);
    return stmt.all(limit);
  }

  // --- Routines ---

  listRoutines() {
    const stmt = this.db.prepare(`SELECT id, name, description, steps_json, created_at FROM routines ORDER BY name ASC`);
    return stmt.all().map(r => ({
      ...r,
      steps: JSON.parse(r.steps_json || '[]')
    }));
  }

  saveRoutine(id, name, description, steps) {
    const stmt = this.db.prepare(`
      INSERT OR REPLACE INTO routines (id, name, description, steps_json, created_at)
      VALUES (?, ?, ?, ?, ?)
    `);
    stmt.run(id, name, description || '', JSON.stringify(steps), Date.now());
  }

  deleteRoutine(id) {
    const stmt = this.db.prepare(`DELETE FROM routines WHERE id = ?`);
    stmt.run(id);
  }

  // --- Approvals ---

  createApproval(id, deviceId, action, details, ttlSeconds = 60) {
    const now = Date.now();
    const expiresAt = now + (ttlSeconds * 1000);
    const stmt = this.db.prepare(`
      INSERT INTO approvals (id, device_id, action, details, status, created_at, expires_at)
      VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
    `);
    stmt.run(id, deviceId, action, JSON.stringify(details), now, expiresAt);
    return { id, deviceId, action, details, status: 'PENDING', createdAt: now, expiresAt };
  }

  getApproval(id) {
    const stmt = this.db.prepare(`SELECT * FROM approvals WHERE id = ?`);
    const row = stmt.get(id);
    if (!row) return null;
    return {
      ...row,
      details: JSON.parse(row.details || '{}')
    };
  }

  decideApproval(id, status) {
    const stmt = this.db.prepare(`UPDATE approvals SET status = ? WHERE id = ? AND status = 'PENDING'`);
    stmt.run(status, id);
  }

  close() {
    this.db.close();
  }
}

export const dbManager = new DatabaseManager();
