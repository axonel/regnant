import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { config } from './config.js';
import { CAPABILITIES } from '../../../shared/protocol.js';

export class DatabaseManager {
  constructor(dbPath = null) {
    this.dbPath = dbPath || process.env.REGNANT_DB_PATH || path.join(config.dataDir, 'regnant.db');
    this.db = new DatabaseSync(this.dbPath);
    this.init();
  }

  init() {
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      PRAGMA busy_timeout = 5000;
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

      -- v1.2: AI Agent Sessions
      CREATE TABLE IF NOT EXISTS agent_sessions (
        id TEXT PRIMARY KEY,
        agent_type TEXT NOT NULL,
        workspace_path TEXT NOT NULL,
        prompt TEXT NOT NULL,
        status TEXT NOT NULL,
        pid INTEGER,
        created_at INTEGER NOT NULL,
        completed_at INTEGER
      );
      CREATE INDEX IF NOT EXISTS idx_agent_sessions_created ON agent_sessions(created_at DESC);

      -- v1.2: Automations
      CREATE TABLE IF NOT EXISTS automations (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        trigger_type TEXT NOT NULL,
        condition_json TEXT,
        action_type TEXT NOT NULL,
        action_payload TEXT,
        enabled INTEGER NOT NULL DEFAULT 1,
        created_at INTEGER NOT NULL
      );

      -- v1.2: Schedules
      CREATE TABLE IF NOT EXISTS schedules (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        schedule_type TEXT NOT NULL,
        schedule_expr TEXT NOT NULL,
        action_type TEXT NOT NULL,
        action_payload TEXT,
        enabled INTEGER NOT NULL DEFAULT 1,
        last_run_at INTEGER,
        next_run_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL
      );

      -- v1.2: Universal Search FTS5 Virtual Table
      CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts5(
        id,
        type,
        title,
        path,
        keywords
      );

      -- v1.2: Config Store
      CREATE TABLE IF NOT EXISTS config_store (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
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

  // --- AI Agent Sessions ---

  createAgentSession(id, agentType, workspacePath, prompt, pid = null) {
    const stmt = this.db.prepare(`
      INSERT INTO agent_sessions (id, agent_type, workspace_path, prompt, status, pid, created_at)
      VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)
    `);
    stmt.run(id, agentType, workspacePath, prompt, pid, Date.now());
  }

  updateAgentSession(id, status, completedAt = Date.now()) {
    const stmt = this.db.prepare(`
      UPDATE agent_sessions SET status = ?, completed_at = ? WHERE id = ?
    `);
    stmt.run(status, completedAt, id);
  }

  getAgentSession(id) {
    const stmt = this.db.prepare(`SELECT * FROM agent_sessions WHERE id = ?`);
    return stmt.get(id) || null;
  }

  listAgentSessions(limit = 30) {
    const stmt = this.db.prepare(`SELECT * FROM agent_sessions ORDER BY created_at DESC LIMIT ?`);
    return stmt.all(limit);
  }

  // --- Automations ---

  saveAutomation(id, name, triggerType, condition, actionType, actionPayload, enabled = 1) {
    const stmt = this.db.prepare(`
      INSERT OR REPLACE INTO automations (id, name, trigger_type, condition_json, action_type, action_payload, enabled, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);
    stmt.run(
      id,
      name,
      triggerType,
      typeof condition === 'object' ? JSON.stringify(condition) : (condition || '{}'),
      actionType,
      typeof actionPayload === 'object' ? JSON.stringify(actionPayload) : (actionPayload || '{}'),
      enabled ? 1 : 0,
      Date.now()
    );
  }

  listAutomations() {
    const stmt = this.db.prepare(`SELECT * FROM automations ORDER BY created_at DESC`);
    return stmt.all().map(a => ({
      ...a,
      condition: JSON.parse(a.condition_json || '{}'),
      actionPayload: JSON.parse(a.action_payload || '{}')
    }));
  }

  deleteAutomation(id) {
    const stmt = this.db.prepare(`DELETE FROM automations WHERE id = ?`);
    stmt.run(id);
  }

  // --- Schedules ---

  saveSchedule(id, name, scheduleType, scheduleExpr, actionType, actionPayload, nextRunAt, enabled = 1) {
    const stmt = this.db.prepare(`
      INSERT OR REPLACE INTO schedules (id, name, schedule_type, schedule_expr, action_type, action_payload, enabled, next_run_at, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    stmt.run(
      id,
      name,
      scheduleType,
      scheduleExpr,
      actionType,
      typeof actionPayload === 'object' ? JSON.stringify(actionPayload) : (actionPayload || '{}'),
      enabled ? 1 : 0,
      nextRunAt,
      Date.now()
    );
  }

  listSchedules() {
    const stmt = this.db.prepare(`SELECT * FROM schedules ORDER BY next_run_at ASC`);
    return stmt.all().map(s => ({
      ...s,
      actionPayload: JSON.parse(s.action_payload || '{}')
    }));
  }

  updateScheduleRun(id, lastRunAt, nextRunAt) {
    const stmt = this.db.prepare(`
      UPDATE schedules SET last_run_at = ?, next_run_at = ? WHERE id = ?
    `);
    stmt.run(lastRunAt, nextRunAt, id);
  }

  deleteSchedule(id) {
    const stmt = this.db.prepare(`DELETE FROM schedules WHERE id = ?`);
    stmt.run(id);
  }

  // --- Universal Search Index (FTS5) ---

  clearSearchIndex() {
    this.db.exec(`DELETE FROM search_index;`);
  }

  indexSearchItem(id, type, title, itemPath, keywords = '') {
    const stmt = this.db.prepare(`
      INSERT INTO search_index (id, type, title, path, keywords)
      VALUES (?, ?, ?, ?, ?)
    `);
    stmt.run(id, type, title, itemPath, keywords);
  }

  querySearch(query, limit = 25) {
    const sanitized = query.replace(/"/g, '""').trim();
    if (!sanitized) return [];
    try {
      const matchQuery = `"${sanitized}"*`;
      const stmt = this.db.prepare(`
        SELECT id, type, title, path, keywords, rank
        FROM search_index
        WHERE search_index MATCH ?
        ORDER BY rank
        LIMIT ?
      `);
      return stmt.all(matchQuery, limit);
    } catch {
      const stmt = this.db.prepare(`
        SELECT id, type, title, path, keywords
        FROM search_index
        WHERE title LIKE ? OR path LIKE ? OR keywords LIKE ?
        LIMIT ?
      `);
      const likeStr = `%${sanitized}%`;
      return stmt.all(likeStr, likeStr, likeStr, limit);
    }
  }

  // --- Config Store ---

  getConfig(key, defaultValue = null) {
    const stmt = this.db.prepare(`SELECT value FROM config_store WHERE key = ?`);
    const row = stmt.get(key);
    return row ? row.value : defaultValue;
  }

  setConfig(key, value) {
    const stmt = this.db.prepare(`
      INSERT INTO config_store (key, value) VALUES (?, ?)
      ON CONFLICT(key) DO UPDATE SET value = excluded.value
    `);
    stmt.run(key, String(value));
  }

  close() {
    this.db.close();
  }
}

let _dbManagerInstance = null;
export const dbManager = new Proxy({}, {
  get(target, prop) {
    if (!_dbManagerInstance) {
      _dbManagerInstance = new DatabaseManager();
    }
    const val = _dbManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_dbManagerInstance) : val;
  }
});
