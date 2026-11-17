import test from 'node:test';
import assert from 'node:assert/strict';
import { AgyAdapter } from '../src/agents/agy.js';
import { ClaudeAdapter } from '../src/agents/claude.js';
import { CodexAdapter } from '../src/agents/codex.js';
import { AgentManager } from '../src/agents/manager.js';
import { DatabaseManager } from '../src/db.js';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';

test('Agent Adapters — detection and guidance verification', async () => {
  const agy = new AgyAdapter();
  const agyInfo = await agy.detect();
  assert.equal(agyInfo.id, 'agy');
  assert.equal(typeof agyInfo.available, 'boolean');
  assert.ok(agyInfo.guidance);

  const claude = new ClaudeAdapter();
  const claudeInfo = await claude.detect();
  assert.equal(claudeInfo.id, 'claude');
  assert.equal(claudeInfo.available, false);
  assert.match(claudeInfo.guidance, /Claude Code is not installed/);

  const codex = new CodexAdapter();
  const codexInfo = await codex.detect();
  assert.equal(codexInfo.id, 'codex');
  assert.equal(codexInfo.available, false);
  assert.match(codexInfo.guidance, /Codex CLI is not installed/);
});

test('AgentManager — agent listing and session recording', async () => {
  const tmpDb = path.join(os.tmpdir(), `regnant-agent-test-${Date.now()}.db`);
  const testDb = new DatabaseManager(tmpDb);

  try {
    const mgr = new AgentManager();
    const agents = await mgr.listAgents();
    assert.equal(agents.length, 3);
    const ids = agents.map(a => a.id);
    assert.ok(ids.includes('agy'));
    assert.ok(ids.includes('claude'));
    assert.ok(ids.includes('codex'));

    // Test DB methods directly on testDb
    testDb.createAgentSession('test-sess-1', 'agy', '/home/user/project', 'Write tests', 12345);
    const sess = testDb.getAgentSession('test-sess-1');
    assert.equal(sess.id, 'test-sess-1');
    assert.equal(sess.agent_type, 'agy');
    assert.equal(sess.status, 'RUNNING');

    testDb.updateAgentSession('test-sess-1', 'COMPLETED');
    const updated = testDb.getAgentSession('test-sess-1');
    assert.equal(updated.status, 'COMPLETED');
    assert.ok(updated.completed_at > 0);

    const list = testDb.listAgentSessions();
    assert.equal(list.length, 1);
  } finally {
    testDb.close();
    if (fs.existsSync(tmpDb)) fs.unlinkSync(tmpDb);
  }
});
