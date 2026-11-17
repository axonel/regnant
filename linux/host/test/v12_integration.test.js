import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import path from 'node:path';
import os from 'node:os';
import fs from 'node:fs';
import { HostServer } from '../src/server.js';
import { cryptoManager } from '../src/crypto.js';
import { dbManager } from '../src/db.js';
import { ACTIONS, TASK_STATUS } from '../../../shared/protocol.js';

const TEST_PORT = 8899;

function httpsRequest(options, postData = null) {
  return new Promise((resolve, reject) => {
    const req = https.request({
      ...options,
      rejectUnauthorized: false,
    }, (res) => {
      let body = '';
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => {
        try {
          const json = JSON.parse(body);
          resolve({ status: res.statusCode, data: json, headers: res.headers });
        } catch {
          resolve({ status: res.statusCode, raw: body, headers: res.headers });
        }
      });
    });

    req.on('error', reject);
    if (postData) {
      req.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
    }
    req.end();
  });
}

test('v1.2 Integration — Full REST Endpoint Suite & Capabilities', async () => {
  // Setup test device in dbManager
  const deviceId = `v12-test-${Date.now()}`;
  const token = cryptoManager.generateBearerToken();
  const tokenHash = cryptoManager.hashToken(token);
  dbManager.registerDevice(deviceId, 'Vivo Test Phone', tokenHash);

  const server = new HostServer();
  await server.start(TEST_PORT);
  await new Promise(r => setTimeout(r, 150));

  const authHeaders = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  };

  try {
    // 1. Agents API
    const agentsRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/agents',
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(agentsRes.status, 200);
    assert.ok(Array.isArray(agentsRes.data.agents));
    assert.equal(agentsRes.data.agents.length, 3);

    // 2. Terminal List
    const termRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/terminal/list',
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(termRes.status, 200);
    assert.ok(Array.isArray(termRes.data.sessions));

    // 3. Git Status
    const gitRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: `/api/v1/git/status?path=${encodeURIComponent(process.cwd())}`,
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(gitRes.status, 200);
    assert.ok(gitRes.data.branch);

    // 4. GitHub Auth
    const ghRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/github/auth',
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(ghRes.status, 200);
    assert.equal(typeof ghRes.data.authenticated, 'boolean');

    // 5. Audio State
    const audioRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/audio',
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(audioRes.status, 200);
    assert.equal(typeof audioRes.data.volume, 'number');

    // 6. Downloads List
    const dlRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/downloads',
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(dlRes.status, 200);
    assert.ok(Array.isArray(dlRes.data.files));

    // 7. Universal Search
    const searchRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/search?q=test',
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(searchRes.status, 200);
    assert.ok(Array.isArray(searchRes.data.results));

    // 8. Wake-on-LAN Status
    const wolRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/wol/status',
      method: 'GET',
      headers: authHeaders
    });
    assert.equal(wolRes.status, 200);
    assert.ok(Array.isArray(wolRes.data.interfaces));

    // 9. Command execution with audio_get
    const cmdRes = await httpsRequest({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: '/api/v1/commands',
      method: 'POST',
      headers: authHeaders
    }, {
      requestId: `req-v12-${Date.now()}`,
      action: ACTIONS.AUDIO_GET,
      target: 'sink'
    });
    assert.equal(cmdRes.status, 200);
    assert.equal(cmdRes.data.status, TASK_STATUS.COMPLETED);
    assert.ok(cmdRes.data.message.includes('Volume'));

  } finally {
    server.stop();
    dbManager.revokeDevice(deviceId);
  }
});
