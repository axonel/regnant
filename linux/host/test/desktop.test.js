import test from 'node:test';
import assert from 'node:assert/strict';
import { HyprlandManager } from '../src/hyprland/manager.js';
import { AudioMediaManager } from '../src/audio/manager.js';
import { DesktopPeekManager } from '../src/peek/manager.js';
import { BrowserManager } from '../src/browser/manager.js';

test('HyprlandManager — client and workspace queries', () => {
  const hypr = new HyprlandManager();
  const available = hypr.isAvailable();
  assert.equal(typeof available, 'boolean');

  if (available) {
    const clients = hypr.getClients();
    assert.ok(Array.isArray(clients));
    const workspaces = hypr.getWorkspaces();
    assert.ok(Array.isArray(workspaces));
  }
});

test('AudioMediaManager — get audio state via PipeWire wpctl', () => {
  const audio = new AudioMediaManager();
  const state = audio.getAudioState();
  assert.equal(typeof state.volume, 'number');
  assert.equal(typeof state.muted, 'boolean');
});

test('DesktopPeekManager — captureJpeg returns JPEG buffer', async () => {
  const peek = new DesktopPeekManager();
  try {
    const res = await peek.captureJpeg(60);
    assert.ok(Buffer.isBuffer(res.buffer));
    assert.ok(res.buffer.length > 100);
    // Check JPEG magic numbers: 0xFF, 0xD8
    assert.equal(res.buffer[0], 0xff);
    assert.equal(res.buffer[1], 0xd8);
  } catch (err) {
    // If running in headless env without display, check graceful error
    assert.ok(err.message.includes('grim') || err.message.includes('capture'));
  }
});

test('BrowserManager — token management and disconnected state safety', async () => {
  const browser = new BrowserManager();
  const token = browser.getToken();
  assert.ok(token);
  assert.equal(typeof token, 'string');
  assert.equal(browser.isConnected(), false);

  const tabsRes = await browser.getTabs();
  assert.equal(tabsRes.connected, false);
});

test('HyprlandManager — active workspace query and switch bounds validation', () => {
  const hypr = new HyprlandManager();
  if (hypr.isAvailable()) {
    const active = hypr.getActiveWorkspace();
    assert.ok(active);
    assert.equal(typeof active.id, 'number');
    assert.equal(typeof active.name, 'string');

    // Test rejection of out-of-range workspace numbers
    assert.throws(() => hypr.switchWorkspace(0), /Workspace ID must be an integer between 1 and 5/);
    assert.throws(() => hypr.switchWorkspace(6), /Workspace ID must be an integer between 1 and 5/);
    assert.throws(() => hypr.switchWorkspace('abc'), /Workspace ID must be an integer between 1 and 5/);
    assert.throws(() => hypr.switchWorkspace(-1), /Workspace ID must be an integer between 1 and 5/);

    // Switching to current workspace 1 is safe and valid
    const current = hypr.switchWorkspace(1);
    assert.ok(current);
    assert.equal(typeof current.id, 'number');
  }
});

test('PowerManager — lock status reporting', async () => {
  const { PowerManager } = await import('../src/power.js');
  const power = new PowerManager();
  const status = power.getLockStatus();
  assert.ok(status);
  assert.ok('locked' in status);
  assert.ok('state' in status);
  assert.ok(['LOCKED', 'UNLOCKED', 'UNKNOWN'].includes(status.state));
});

test('PowerManager — unlockSession validation and safety checks', async () => {
  const { PowerManager } = await import('../src/power.js');
  const power = new PowerManager();

  // Empty password validation
  const emptyRes = await power.unlockSession('');
  assert.equal(emptyRes.success, false);
  assert.equal(emptyRes.status, 'INVALID_PASSWORD');

  const nullRes = await power.unlockSession(null);
  assert.equal(nullRes.success, false);
  assert.equal(nullRes.status, 'INVALID_PASSWORD');

  // When workstation is currently unlocked, attempting unlock yields NOT_LOCKED
  const status = power.getLockStatus();
  if (status.locked === false) {
    const unlockedRes = await power.unlockSession('dummy-pass');
    assert.equal(unlockedRes.success, false);
    assert.equal(unlockedRes.status, 'NOT_LOCKED');
  }
});

