import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { DesktopNotifier } from '../src/notifier.js';

test('DesktopNotifier — missing notify-send is a non-fatal asynchronous spawn error', async () => {
  const child = new EventEmitter();
  child.unref = () => {};

  const notifier = new DesktopNotifier({
    spawnProcess: () => child,
  });

  assert.doesNotThrow(() => notifier.notify('Regnant test', 'Notification fallback'));
  child.emit('error', Object.assign(new Error('spawn /usr/bin/notify-send ENOENT'), {
    code: 'ENOENT',
  }));

  await new Promise(resolve => setImmediate(resolve));
});
