import test from 'node:test';
import assert from 'node:assert';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';
import { projectManager } from '../src/projects.js';
import { metricsCollector } from '../src/metrics.js';
import { fileBrowser } from '../src/filebrowser.js';
import { webManager } from '../src/web.js';
import { routineManager } from '../src/routines.js';
import { dbManager } from '../src/db.js';
import { CAPABILITIES, ACTIONS } from '../../../shared/protocol.js';

test('ProjectManager — IDE detection and project listing', () => {
  const ides = projectManager.getSupportedIdes();
  assert.ok(Array.isArray(ides), 'IDES should be an array');
  assert.ok(ides.length > 0, 'Should detect at least foot or code');

  const projects = projectManager.listProjects();
  assert.ok(Array.isArray(projects), 'Projects should be an array');
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

test('FileBrowser — Directory traversal protection and approved roots', () => {
  const roots = fileBrowser.getApprovedRoots();
  assert.ok(roots.length > 0, 'Approved roots should be pre-seeded');

  // Verify list directory of root
  const rootListing = fileBrowser.listDirectory(null);
  assert.ok(rootListing.isRoot, 'Should identify as root listing');
  assert.ok(rootListing.items.length > 0, 'Should list root categories');

  // Traversal attack attempt: /etc/shadow or /etc/passwd
  assert.throws(() => {
    fileBrowser.listDirectory('/etc');
  }, /Access denied/, 'Must reject path outside approved roots');

  assert.throws(() => {
    fileBrowser.readFileContent('/etc/passwd');
  }, /Access denied/, 'Must reject reading file outside approved roots');

  // Relative traversal attempt: ~/Projects/../../etc/passwd
  const malicious = path.join(os.homedir(), 'Projects', '..', '..', 'etc', 'passwd');
  assert.throws(() => {
    fileBrowser.readFileContent(malicious);
  }, /Access denied/, 'Must defeat relative traversal attempt');
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

  // Default permissions
  assert.strictEqual(dbManager.isPermissionAllowed(testDevId, CAPABILITIES.VIEW_METRICS), true);

  // Revoke capability
  dbManager.setPermission(testDevId, CAPABILITIES.VIEW_METRICS, false);
  assert.strictEqual(dbManager.isPermissionAllowed(testDevId, CAPABILITIES.VIEW_METRICS), false);

  // Re-grant
  dbManager.setPermission(testDevId, CAPABILITIES.VIEW_METRICS, true);
  assert.strictEqual(dbManager.isPermissionAllowed(testDevId, CAPABILITIES.VIEW_METRICS), true);
});
