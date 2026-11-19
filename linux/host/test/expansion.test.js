import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';
import { ProjectManager } from '../src/projects.js';
import { metricsCollector } from '../src/metrics.js';
import { FileBrowser } from '../src/filebrowser.js';
import { webManager } from '../src/web.js';
import { routineManager } from '../src/routines.js';
import { DatabaseManager, dbManager } from '../src/db.js';
import { CAPABILITIES } from '../../../shared/protocol.js';

test('ProjectManager — detects IDEs from an isolated fixture and lists projects', () => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'regnant-projects-'));
  const idePath = path.join(home, '.local/bin/antigravity');

  try {
    fs.mkdirSync(path.dirname(idePath), { recursive: true });
    fs.writeFileSync(idePath, '#!/bin/sh\nexit 0\n', { mode: 0o755 });

    const manager = new ProjectManager({ home });
    const ides = manager.getSupportedIdes();

    assert.ok(Array.isArray(ides), 'IDEs should be an array');
    assert.ok(ides.some(ide => ide.id === 'antigravity' && ide.exec === idePath),
      'Should detect an IDE installed in the supplied home directory');

    const projects = manager.listProjects();
    assert.ok(Array.isArray(projects), 'Projects should be an array');
  } finally {
    fs.rmSync(home, { recursive: true, force: true });
  }
});

test('MetricsCollector — System snapshot structure and validity', () => {
  const snap = metricsCollector.getSnapshot();
  assert.ok(typeof snap.cpuPercent === 'number', 'CPU percent should be number');
  assert.ok(snap.cpuPercent >= 0 && snap.cpuPercent <= 100, 'CPU percent within bounds');

  assert.ok(snap.memory.totalMb > 0, 'Total memory should be positive');
  assert.ok(snap.memory.percent >= 0 && snap.memory.percent <= 100, 'Memory percent within bounds');

  assert.ok(typeof snap.battery.percent === 'number', 'Battery percent should be number');
  assert.ok(snap.uptimeSeconds > 0, 'Uptime should be positive');
  assert.ok(snap.disk.totalGb > 0, 'Disk total should be positive');
});

test('FileBrowser — approved roots and traversal protection use an isolated database', () => {
  const sandbox = fs.mkdtempSync(path.join(os.tmpdir(), 'regnant-filebrowser-'));
  const root = path.join(sandbox, 'Projects');
  const database = new DatabaseManager(path.join(sandbox, 'test.db'));

  try {
    fs.mkdirSync(root, { recursive: true });
    database.addApprovedRoot(root, 'Projects');
    const browser = new FileBrowser(database);

    const roots = browser.getApprovedRoots();
    assert.ok(roots.some(entry => entry.path === root),
      'Test fixture root should be present in the approved-root list');

    const rootListing = browser.listDirectory(null);
    assert.ok(rootListing.isRoot, 'Should identify as root listing');
    assert.ok(rootListing.items.some(entry => entry.path === root),
      'Root listing should include the test fixture category');

    assert.throws(() => browser.listDirectory('/etc'), /Access denied/,
      'Must reject paths outside approved roots');

    assert.throws(() => browser.readFileContent('/etc/passwd'), /Access denied/,
      'Must reject reading files outside approved roots');

    const traversal = path.resolve(root, '../../../etc/passwd');
    assert.throws(() => browser.readFileContent(traversal), /Access denied/,
      'Must defeat relative traversal attempts');
  } finally {
    database.close();
    fs.rmSync(sandbox, { recursive: true, force: true });
  }
});

test('WebManager — URL validation rejects non-HTTP protocols', () => {
  assert.strictEqual(webManager.validateUrl('https://example.com'), 'https://example.com/');
  assert.strictEqual(webManager.validateUrl('github.com'), 'https://github.com/');

  assert.throws(() => {
    webManager.validateUrl('file:///etc/passwd');
  }, /Forbidden protocol/, 'Must reject file: protocol');

  assert.throws(() => {
    webManager.validateUrl('javascript:alert(1)');
  }, /Forbidden protocol/, 'Must reject javascript: protocol');
});

test('RoutineManager — Pre-seeded routines and step structure', () => {
  const routines = routineManager.listRoutines();
  assert.ok(routines.length >= 1, 'Should have pre-seeded starter routine');
  const startWork = routines.find(r => r.id === 'routine-start-work');
  assert.ok(startWork, 'routine-start-work should exist');
  assert.ok(startWork.steps.length >= 2, 'Should have multiple steps');
});

test('DatabaseManager — Granular permissions matrix', () => {
  const testDevId = 'test-dev-perm-1';
  dbManager.registerDevice(testDevId, 'Test Device', 'test-token-hash');

  assert.strictEqual(dbManager.isPermissionAllowed(testDevId, CAPABILITIES.VIEW_METRICS), true);
  dbManager.setPermission(testDevId, CAPABILITIES.VIEW_METRICS, false);
  assert.strictEqual(dbManager.isPermissionAllowed(testDevId, CAPABILITIES.VIEW_METRICS), false);
  dbManager.setPermission(testDevId, CAPABILITIES.VIEW_METRICS, true);
  assert.strictEqual(dbManager.isPermissionAllowed(testDevId, CAPABILITIES.VIEW_METRICS), true);
});
