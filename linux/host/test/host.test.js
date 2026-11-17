import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { commandParser } from '../src/parser.js';
import { actionExecutor } from '../src/executor.js';
import { CryptoManager } from '../src/crypto.js';
import { DatabaseManager } from '../src/db.js';
import { ACTIONS, TARGETS, TASK_STATUS } from '../../../shared/protocol.js';

test('CommandParser — valid commands and aliases', () => {
  const cases = [
    { input: 'open terminal', action: ACTIONS.LAUNCH_APP, target: TARGETS.TERMINAL },
    { input: 'Launch Terminal', action: ACTIONS.LAUNCH_APP, target: TARGETS.TERMINAL },
    { input: 'start terminal!', action: ACTIONS.LAUNCH_APP, target: TARGETS.TERMINAL },
    { input: 'terminal', action: ACTIONS.LAUNCH_APP, target: TARGETS.TERMINAL },
    { input: 'open brave', action: ACTIONS.LAUNCH_APP, target: TARGETS.BRAVE },
    { input: 'open browser', action: ACTIONS.LAUNCH_APP, target: TARGETS.BRAVE },
    { input: 'launch brave', action: ACTIONS.LAUNCH_APP, target: TARGETS.BRAVE },
    { input: 'open file manager', action: ACTIONS.LAUNCH_APP, target: TARGETS.FILE_MANAGER },
    { input: 'open files', action: ACTIONS.LAUNCH_APP, target: TARGETS.FILE_MANAGER },
    { input: 'files', action: ACTIONS.LAUNCH_APP, target: TARGETS.FILE_MANAGER },
    { input: 'open calculator', action: ACTIONS.LAUNCH_APP, target: TARGETS.CALCULATOR },
    { input: 'calculator', action: ACTIONS.LAUNCH_APP, target: TARGETS.CALCULATOR },
    { input: 'calc', action: ACTIONS.LAUNCH_APP, target: TARGETS.CALCULATOR },
    { input: 'open downloads', action: ACTIONS.OPEN_PATH, target: TARGETS.DOWNLOADS },
    { input: 'show downloads', action: ACTIONS.OPEN_PATH, target: TARGETS.DOWNLOADS },
    { input: 'open home', action: ACTIONS.OPEN_PATH, target: TARGETS.HOME },
    { input: 'open home folder', action: ACTIONS.OPEN_PATH, target: TARGETS.HOME },
  ];

  for (const { input, action, target } of cases) {
    const res = commandParser.parse(input);
    assert.equal(res.valid, true, `Failed on input: ${input}`);
    assert.equal(res.action, action);
    assert.equal(res.target, target);
  }
});

test('CommandParser — rejection of unsupported or malicious inputs', () => {
  const rejected = [
    'delete all my files',
    'rm -rf /',
    'sudo rm -rf ~',
    'cat /etc/passwd',
    'bash -c "whoami"',
    'drop table users',
    'play music on spotify',
    'what is the weather today',
    '',
    '   ',
    '???',
  ];

  for (const input of rejected) {
    const res = commandParser.parse(input);
    assert.equal(res.valid, false, `Should have rejected: "${input}"`);
    assert.ok(res.reason);
  }
});

test('ActionExecutor — detects real desktop applications', () => {
  const apps = actionExecutor.getSupportedApplications();
  assert.ok(apps.terminal.includes('foot'), 'Should detect foot terminal');
  assert.ok(apps.brave.includes('brave'), 'Should detect brave browser');
  assert.ok(apps.fileManager.includes('nautilus'), 'Should detect nautilus file manager');
  assert.ok(apps.calculator.includes('omacalc'), 'Should detect omacalc');
  assert.ok(apps.downloads.includes('Downloads'), 'Should point to Downloads folder');
  assert.ok(apps.home.length > 0, 'Should point to home folder');
});

test('CryptoManager — pairing code lifecycle, rate-limiting & single use', () => {
  const cryptoMgr = new CryptoManager();
  const session = cryptoMgr.generatePairingCode();

  assert.ok(session.code.startsWith('RG-'));
  assert.equal(session.code.length, 7);

  // Active session status
  const active = cryptoMgr.getActivePairingSession();
  assert.ok(active);
  assert.equal(active.code, session.code);

  // Incorrect code attempt 1
  let verify1 = cryptoMgr.verifyPairingCode('RG-WRONG');
  assert.equal(verify1.success, false);

  // Incorrect code attempt 2
  let verify2 = cryptoMgr.verifyPairingCode('RG-WRONG');
  assert.equal(verify2.success, false);

  // Valid attempt should succeed
  let verifyValid = cryptoMgr.verifyPairingCode(session.code);
  assert.equal(verifyValid.success, true);

  // Code is single-use: subsequent verification must fail
  let verifyAgain = cryptoMgr.verifyPairingCode(session.code);
  assert.equal(verifyAgain.success, false, 'Code should not be reusable');
});

test('CryptoManager — pairing code rate limit invalidation', () => {
  const cryptoMgr = new CryptoManager();
  const session = cryptoMgr.generatePairingCode();

  // 3 failed attempts
  cryptoMgr.verifyPairingCode('WRONG1');
  cryptoMgr.verifyPairingCode('WRONG2');
  const res3 = cryptoMgr.verifyPairingCode('WRONG3');
  assert.equal(res3.success, false);

  // Now even the correct code should fail because rate limit invalidated it
  const verifyAfterLock = cryptoMgr.verifyPairingCode(session.code);
  assert.equal(verifyAfterLock.success, false);
});

test('CryptoManager — bearer token generation, hashing and verification', () => {
  const cryptoMgr = new CryptoManager();
  const token = cryptoMgr.generateBearerToken();
  assert.equal(token.length, 64);

  const hash = cryptoMgr.hashToken(token);
  assert.equal(cryptoMgr.verifyBearerToken(token, hash), true);
  assert.equal(cryptoMgr.verifyBearerToken('tampered-token', hash), false);
});

test('DatabaseManager — device lifecycle, replay prevention & history', () => {
  const tmpDb = path.join(os.tmpdir(), `regnant-test-${Date.now()}.db`);
  const db = new DatabaseManager(tmpDb);

  try {
    const deviceId = 'test-android-phone-1';
    const deviceName = 'Test Vivo';
    const tokenHash = 'abcdef0123456789';

    // Register device
    db.registerDevice(deviceId, deviceName, tokenHash);
    const dev = db.getDevice(deviceId);
    assert.equal(dev.device_id, deviceId);
    assert.equal(dev.device_name, deviceName);

    // List devices
    const list = db.listDevices();
    assert.equal(list.length, 1);
    assert.equal(list[0].device_id, deviceId);

    // Replay attack prevention check
    const reqId = 'req-unique-uuid-1';
    assert.equal(db.hasRequest(reqId), false);

    db.recordCommand({
      requestId: reqId,
      deviceId,
      action: ACTIONS.LAUNCH_APP,
      target: TARGETS.TERMINAL,
      rawCommand: 'open terminal',
      status: TASK_STATUS.COMPLETED,
      message: 'Launched Foot',
      timestamp: Date.now(),
      executedAt: Date.now(),
    });

    assert.equal(db.hasRequest(reqId), true, 'Request ID should now be marked seen');

    // Retrieve history
    const history = db.getHistory(10);
    assert.equal(history.length, 1);
    assert.equal(history[0].request_id, reqId);
    assert.equal(history[0].status, TASK_STATUS.COMPLETED);

    // Revocation
    db.revokeDevice(deviceId);
    assert.equal(db.getDevice(deviceId), null);
    assert.equal(db.listDevices().length, 0);
  } finally {
    db.close();
    fs.unlinkSync(tmpDb);
  }
});
