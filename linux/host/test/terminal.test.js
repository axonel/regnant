import test from 'node:test';
import assert from 'node:assert/strict';
import { TerminalManager } from '../src/terminal/manager.js';

test('TerminalManager — lifecycle, writing, buffer retention and resize', async () => {
  const mgr = new TerminalManager();
  const session = mgr.createSession({
    cols: 80,
    rows: 24,
    title: 'Test Bash'
  });

  assert.ok(session.id);
  assert.ok(session.pid > 0);
  assert.equal(session.cols, 80);
  assert.equal(session.rows, 24);

  // Check listing
  const list = mgr.listSessions();
  assert.equal(list.length, 1);
  assert.equal(list[0].id, session.id);

  // Resize
  mgr.resize(session.id, 100, 30);
  assert.equal(session.cols, 100);
  assert.equal(session.rows, 30);

  // Write a command and wait for output
  let received = '';
  await new Promise((resolve) => {
    const listener = (data) => {
      received += data;
      if (received.includes('TEST_OK_PTY')) {
        mgr.removeListener(session.id, listener);
        resolve();
      }
    };
    mgr.addListener(session.id, listener);
    mgr.write(session.id, 'echo TEST_OK_PTY\n');
  });

  assert.ok(received.includes('TEST_OK_PTY'));
  assert.ok(mgr.getBuffer(session.id).includes('TEST_OK_PTY'));

  // Close session
  const closed = mgr.closeSession(session.id);
  assert.equal(closed, true);
  assert.equal(mgr.listSessions().length, 0);
});
