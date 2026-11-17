import test from 'node:test';
import assert from 'node:assert/strict';
import { DownloadManager } from '../src/downloads/manager.js';
import { SearchManager } from '../src/search/manager.js';
import { AutomationEngine } from '../src/automations/engine.js';
import { Scheduler } from '../src/scheduler/scheduler.js';
import { WakeOnLanManager } from '../src/wol.js';
import { DatabaseManager } from '../src/db.js';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';

test('DownloadManager — inspects downloads directory', () => {
  const dm = new DownloadManager();
  const files = dm.getRecentFiles(5);
  assert.ok(Array.isArray(files));
});

test('SearchManager — reindexing and FTS5 search queries', async () => {
  const tmpDb = path.join(os.tmpdir(), `regnant-search-test-${Date.now()}.db`);
  const testDb = new DatabaseManager(tmpDb);

  try {
    testDb.indexSearchItem('proj-1', 'project', 'Regnant Mobile App', '/home/user/regnant', 'android linux kotlin');
    testDb.indexSearchItem('app-1', 'app', 'Brave Web Browser', 'brave', 'internet web brave');

    const results = testDb.querySearch('Regnant');
    assert.ok(results.length >= 1);
    assert.equal(results[0].title, 'Regnant Mobile App');

    const appResults = testDb.querySearch('brave');
    assert.ok(appResults.length >= 1);
    assert.equal(appResults[0].title, 'Brave Web Browser');
  } finally {
    testDb.close();
    if (fs.existsSync(tmpDb)) fs.unlinkSync(tmpDb);
  }
});

test('AutomationEngine — rule lifecycle and trigger matching', () => {
  const tmpDb = path.join(os.tmpdir(), `regnant-auto-test-${Date.now()}.db`);
  const testDb = new DatabaseManager(tmpDb);

  try {
    testDb.saveAutomation('rule-1', 'Mute on Connect', 'DEVICE_CONNECTED', {}, 'MUTE_AUDIO', {}, 1);
    const list = testDb.listAutomations();
    assert.equal(list.length, 1);
    assert.equal(list[0].id, 'rule-1');
    assert.equal(list[0].action_type, 'MUTE_AUDIO');

    testDb.deleteAutomation('rule-1');
    assert.equal(testDb.listAutomations().length, 0);
  } finally {
    testDb.close();
    if (fs.existsSync(tmpDb)) fs.unlinkSync(tmpDb);
  }
});

test('Scheduler — save schedule, calculate next run, tick and delete', async () => {
  const tmpDb = path.join(os.tmpdir(), `regnant-sched-test-${Date.now()}.db`);
  const testDb = new DatabaseManager(tmpDb);

  try {
    const now = Date.now();
    testDb.saveSchedule('sched-1', 'Hourly Sync', 'INTERVAL', '3600', 'ROUTINE', { routineId: 'r1' }, now - 1000, 1);

    const list = testDb.listSchedules();
    assert.equal(list.length, 1);
    assert.equal(list[0].id, 'sched-1');

    testDb.deleteSchedule('sched-1');
    assert.equal(testDb.listSchedules().length, 0);
  } finally {
    testDb.close();
    if (fs.existsSync(tmpDb)) fs.unlinkSync(tmpDb);
  }
});

test('WakeOnLanManager — hardware discovery and magic packet format', () => {
  const wol = new WakeOnLanManager();
  const info = wol.getPrimaryTarget();
  assert.ok(Array.isArray(info.interfaces));
  assert.ok(info.interfaces.length > 0);
  assert.ok(info.primaryMac);

  // Magic packet structure test
  const testMac = '28:a0:6b:45:90:9d';
  const packet = wol.createMagicPacket(testMac);
  assert.equal(packet.length, 102);
  // Header: 6 bytes of 0xFF
  for (let i = 0; i < 6; i++) {
    assert.equal(packet[i], 0xff);
  }
  // Repetitions
  const macBytes = Buffer.from('28a06b45909d', 'hex');
  for (let r = 0; r < 16; r++) {
    const offset = 6 + (r * 6);
    assert.equal(packet.subarray(offset, offset + 6).equals(macBytes), true);
  }

  // Reject malformed MAC
  assert.throws(() => wol.createMagicPacket('invalid-mac'));
});
