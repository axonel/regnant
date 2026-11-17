import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import { WebSocket } from 'ws';
import { HostServer } from '../src/server.js';
import { cryptoManager } from '../src/crypto.js';
import { dbManager } from '../src/db.js';
import { TASK_STATUS, WS_EVENTS } from '../../../shared/protocol.js';

// Helper for https requests ignoring self-signed test cert
function requestJson(url, options = {}, body = null) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const req = https.request({
      hostname: parsed.hostname,
      port: parsed.port,
      path: parsed.pathname + parsed.search,
      method: options.method || 'GET',
      headers: options.headers || {},
      rejectUnauthorized: false, // Self-signed test cert
    }, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, raw: data });
        }
      });
    });
    req.on('error', reject);
    if (body) {
      req.write(typeof body === 'string' ? body : JSON.stringify(body));
    }
    req.end();
  });
}

test('Integration — Server startup, pairing, authenticated commands, replay & revocation', async (t) => {
  const TEST_PORT = 8799;
  const server = new HostServer();
  server.start(TEST_PORT);

  // Give server time to bind
  await new Promise(r => setTimeout(r, 200));

  try {
    // 1. Health endpoint
    const health = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/health`);
    assert.equal(health.status, 200);
    assert.equal(health.data.status, 'ok');
    assert.ok(health.data.hostId);

    // 2. Generate a pairing code
    const pairing = cryptoManager.generatePairingCode();
    assert.ok(pairing.code);

    // 3. Pair device with valid code
    const testDeviceId = `test-phone-${Date.now()}`;
    const pairRes = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    }, {
      deviceId: testDeviceId,
      deviceName: 'Pixel Test Phone',
      pairingCode: pairing.code,
    });

    assert.equal(pairRes.status, 200);
    assert.equal(pairRes.data.status, 'PAIRED');
    assert.ok(pairRes.data.token);
    const token = pairRes.data.token;

    // 4. Submit command without auth -> Must fail 401
    const unauthRes = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/commands`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    }, {
      requestId: 'req-unauth-test',
      rawCommand: 'open terminal',
    });
    assert.equal(unauthRes.status, 401);

    // 5. Submit command with auth -> Must succeed
    const reqId1 = `req-${Date.now()}-1`;
    const cmdRes = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/commands`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }, {
      requestId: reqId1,
      rawCommand: 'open terminal',
    });

    assert.equal(cmdRes.status, 200);
    assert.equal(cmdRes.data.status, TASK_STATUS.COMPLETED);
    assert.ok(cmdRes.data.message.includes('foot'));

    // 6. Submit replayed command with same requestId -> Must be rejected
    const replayRes = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/commands`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }, {
      requestId: reqId1, // Same ID
      rawCommand: 'open terminal',
    });
    assert.equal(replayRes.status, 400);
    assert.equal(replayRes.data.status, TASK_STATUS.REJECTED);
    assert.ok(replayRes.data.reason.includes('Duplicate request ID'));

    // 7. Submit unsupported command -> Must be rejected
    const badCmdRes = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/commands`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }, {
      requestId: `req-bad-${Date.now()}`,
      rawCommand: 'delete all my files',
    });
    assert.equal(badCmdRes.status, 400);
    assert.equal(badCmdRes.data.status, TASK_STATUS.REJECTED);

    // 8. WebSocket connection with Bearer token
    const ws = new WebSocket(`wss://127.0.0.1:${TEST_PORT}/ws?token=${token}`, {
      rejectUnauthorized: false,
    });

    await new Promise((resolve, reject) => {
      ws.on('open', resolve);
      ws.on('error', reject);
    });

    // Send Ping over WS and wait for PONG
    ws.send(JSON.stringify({ type: WS_EVENTS.PING }));
    const wsMsg = await new Promise((resolve) => {
      ws.on('message', data => {
        const msg = JSON.parse(data.toString());
        if (msg.type === WS_EVENTS.PONG) {
          resolve(msg);
        }
      });
    });
    assert.equal(wsMsg.type, WS_EVENTS.PONG);
    ws.close();

    // 9. Revoke device
    const revokeRes = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/devices/revoke`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }, {
      deviceId: testDeviceId,
    });
    assert.equal(revokeRes.status, 200);

    // 10. Post-revocation request must fail 401
    const postRevokeRes = await requestJson(`https://127.0.0.1:${TEST_PORT}/api/v1/commands`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    }, {
      requestId: `req-post-revoke-${Date.now()}`,
      rawCommand: 'open terminal',
    });
    assert.equal(postRevokeRes.status, 401);

  } finally {
    server.stop();
  }
});
