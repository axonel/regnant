import https from 'node:https';
import http from 'node:http';
import { WebSocketServer, WebSocket } from 'ws';
import { config } from './config.js';
import { cryptoManager } from './crypto.js';
import { dbManager } from './db.js';
import { commandParser } from './parser.js';
import { actionExecutor } from './executor.js';
import { notifier } from './notifier.js';
import { serviceDiscovery } from './discovery.js';
import {
  ACTIONS,
  TARGETS,
  TASK_STATUS,
  WS_EVENTS,
  PROTOCOL_VERSION
} from '../../../shared/protocol.js';

export class HostServer {
  constructor() {
    this.server = null;
    this.wss = null;
    this.activeClients = new Map(); // ws -> { deviceId, deviceName, isLocal, lastPing }
    this.pingInterval = null;
  }

  start(port = config.port) {
    const tlsCredentials = cryptoManager.getTlsCredentials();

    this.server = https.createServer({
      key: tlsCredentials.key,
      cert: tlsCredentials.cert,
    }, (req, res) => this.handleHttpRequest(req, res));

    this.wss = new WebSocketServer({ noServer: true });
    this.server.on('upgrade', (request, socket, head) => {
      this.handleUpgrade(request, socket, head);
    });

    this.server.listen(port, '0.0.0.0', () => {
      console.log(`[Regnant Host] Server listening securely on port ${port} (HTTPS/WSS)`);
      console.log(`[Regnant Host] Fingerprint: ${cryptoManager.certFingerprint}`);
      serviceDiscovery.start(port);
      notifier.notify('Regnant', `Host service active on port ${port}`);
    });

    this.setupHeartbeat();
  }

  stop() {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = null;
    }
    serviceDiscovery.stop();
    if (this.wss) {
      for (const client of this.wss.clients) {
        client.terminate();
      }
      this.wss.close();
    }
    if (this.server) {
      this.server.close();
    }
  }

  isLocalhost(req) {
    const ip = req.socket.remoteAddress;
    return ip === '127.0.0.1' || ip === '::1' || ip === '::ffff:127.0.0.1';
  }

  extractBearerToken(req) {
    const authHeader = req.headers['authorization'];
    if (authHeader && authHeader.startsWith('Bearer ')) {
      return authHeader.slice(7).trim();
    }
    return null;
  }

  authenticateRequest(req) {
    // If request originates strictly from localhost (e.g. desktop GUI), allow management access
    if (this.isLocalhost(req) && req.headers['x-regnant-local'] === 'true') {
      return { authenticated: true, deviceId: 'local-desktop', isLocal: true };
    }

    const token = this.extractBearerToken(req);
    if (!token) {
      return { authenticated: false, reason: 'Missing bearer token' };
    }

    const tokenHash = cryptoManager.hashToken(token);
    const device = dbManager.getDeviceByTokenHash(tokenHash);
    if (!device) {
      return { authenticated: false, reason: 'Invalid or revoked device token' };
    }

    dbManager.updateDeviceLastSeen(device.device_id);
    return {
      authenticated: true,
      deviceId: device.device_id,
      deviceName: device.device_name,
      isLocal: false
    };
  }

  sendJson(res, statusCode, data) {
    const json = JSON.stringify(data);
    res.writeHead(statusCode, {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(json),
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Regnant-Local',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    });
    res.end(json);
  }

  async parseBody(req) {
    return new Promise((resolve, reject) => {
      let body = '';
      req.on('data', chunk => {
        body += chunk;
        if (body.length > 1024 * 64) {
          reject(new Error('Request body too large'));
        }
      });
      req.on('end', () => {
        if (!body) return resolve({});
        try {
          resolve(JSON.parse(body));
        } catch (e) {
          reject(new Error('Invalid JSON'));
        }
      });
      req.on('error', reject);
    });
  }

  async handleHttpRequest(req, res) {
    if (req.method === 'OPTIONS') {
      res.writeHead(204, {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Regnant-Local',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      });
      res.end();
      return;
    }

    const parsedUrl = new URL(req.url, `https://${req.headers.host || 'localhost'}`);
    const pathname = parsedUrl.pathname;

    // Public Health Check
    if (req.method === 'GET' && pathname === '/api/v1/health') {
      return this.sendJson(res, 200, {
        status: 'ok',
        version: PROTOCOL_VERSION,
        hostId: config.hostId,
        hostName: config.hostName,
        certFingerprint: cryptoManager.certFingerprint,
        pairedDevicesCount: dbManager.listDevices().length,
      });
    }

    // Public Pairing Endpoint
    if (req.method === 'POST' && pathname === '/api/v1/pair') {
      try {
        const body = await this.parseBody(req);
        const { deviceId, deviceName, pairingCode } = body;

        if (!deviceId || !pairingCode) {
          return this.sendJson(res, 400, {
            status: 'FAILED',
            reason: 'Missing deviceId or pairingCode in payload',
          });
        }

        const verifyResult = cryptoManager.verifyPairingCode(pairingCode);
        if (!verifyResult.success) {
          return this.sendJson(res, 403, {
            status: 'FAILED',
            reason: verifyResult.reason,
          });
        }

        const token = cryptoManager.generateBearerToken();
        const tokenHash = cryptoManager.hashToken(token);
        dbManager.registerDevice(deviceId, deviceName || 'Android Phone', tokenHash);

        notifier.pairingCompleted(deviceName);

        // Broadcast to desktop UI
        this.broadcastStateUpdate();

        return this.sendJson(res, 200, {
          status: 'PAIRED',
          token,
          hostId: config.hostId,
          hostName: config.hostName,
          certFingerprint: cryptoManager.certFingerprint,
          version: PROTOCOL_VERSION,
        });
      } catch (err) {
        return this.sendJson(res, 400, { status: 'FAILED', reason: err.message });
      }
    }

    // Management Endpoint: Generate Pairing Code (localhost or authenticated)
    if (req.method === 'POST' && pathname === '/api/v1/pair/code/generate') {
      if (!this.isLocalhost(req)) {
        return this.sendJson(res, 403, { error: 'Only accessible locally' });
      }
      const session = cryptoManager.generatePairingCode();
      this.broadcastStateUpdate();
      return this.sendJson(res, 200, session);
    }

    // Management Endpoint: Host status (pairing code, devices, history, apps)
    if (req.method === 'GET' && pathname === '/api/v1/status') {
      if (!this.isLocalhost(req)) {
        const auth = this.authenticateRequest(req);
        if (!auth.authenticated) {
          return this.sendJson(res, 401, { error: 'Unauthorized' });
        }
      }

      return this.sendJson(res, 200, {
        hostId: config.hostId,
        hostName: config.hostName,
        version: PROTOCOL_VERSION,
        certFingerprint: cryptoManager.certFingerprint,
        pairingSession: cryptoManager.getActivePairingSession(),
        devices: dbManager.listDevices(),
        history: dbManager.getHistory(20),
        supportedApplications: actionExecutor.getSupportedApplications(),
        connectedClientsCount: this.activeClients.size,
      });
    }

    // Command Submission Endpoint
    if (req.method === 'POST' && pathname === '/api/v1/commands') {
      const auth = this.authenticateRequest(req);
      if (!auth.authenticated) {
        return this.sendJson(res, 401, { status: 'FAILED', reason: auth.reason });
      }

      try {
        const body = await this.parseBody(req);
        const result = await this.processCommand(auth.deviceId, body);
        return this.sendJson(res, result.success ? 200 : 400, result);
      } catch (err) {
        return this.sendJson(res, 500, { status: 'FAILED', reason: err.message });
      }
    }

    // Devices List Endpoint
    if (req.method === 'GET' && pathname === '/api/v1/devices') {
      const auth = this.authenticateRequest(req);
      if (!auth.authenticated) {
        return this.sendJson(res, 401, { error: 'Unauthorized' });
      }
      return this.sendJson(res, 200, { devices: dbManager.listDevices() });
    }

    // Device Revocation Endpoint
    if (req.method === 'POST' && pathname === '/api/v1/devices/revoke') {
      const auth = this.authenticateRequest(req);
      if (!auth.authenticated) {
        return this.sendJson(res, 401, { error: 'Unauthorized' });
      }

      try {
        const body = await this.parseBody(req);
        const { deviceId } = body;
        if (!deviceId) {
          return this.sendJson(res, 400, { error: 'Missing deviceId' });
        }

        dbManager.revokeDevice(deviceId);

        // Terminate any live WebSocket connections for this revoked device
        for (const [ws, client] of this.activeClients.entries()) {
          if (client.deviceId === deviceId) {
            ws.close(4001, 'Device revoked');
            this.activeClients.delete(ws);
          }
        }

        this.broadcastStateUpdate();
        return this.sendJson(res, 200, { success: true, revoked: deviceId });
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // History Endpoint
    if (req.method === 'GET' && pathname === '/api/v1/history') {
      const auth = this.authenticateRequest(req);
      if (!auth.authenticated) {
        return this.sendJson(res, 401, { error: 'Unauthorized' });
      }
      return this.sendJson(res, 200, { history: dbManager.getHistory(50) });
    }

    return this.sendJson(res, 404, { error: 'Not found' });
  }

  async processCommand(deviceId, payload) {
    const { requestId, rawCommand, action, target } = payload;
    const now = Date.now();

    if (!requestId) {
      return {
        success: false,
        status: TASK_STATUS.REJECTED,
        reason: 'Missing requestId in command payload',
      };
    }

    // Replay attack prevention check
    if (dbManager.hasRequest(requestId)) {
      return {
        success: false,
        requestId,
        status: TASK_STATUS.REJECTED,
        reason: 'Duplicate request ID: replayed requests are rejected.',
      };
    }

    let finalAction = action;
    let finalTarget = target;

    // If natural language phrase provided, parse deterministically
    if (rawCommand) {
      const parseResult = commandParser.parse(rawCommand);
      if (!parseResult.valid) {
        dbManager.recordCommand({
          requestId,
          deviceId,
          action: 'unknown',
          target: 'unknown',
          rawCommand,
          status: TASK_STATUS.REJECTED,
          message: parseResult.reason,
          timestamp: now,
          executedAt: now,
        });

        notifier.commandFailed(rawCommand, parseResult.reason);
        this.broadcastCommandStatus(requestId, TASK_STATUS.REJECTED, parseResult.reason);

        return {
          success: false,
          requestId,
          status: TASK_STATUS.REJECTED,
          reason: parseResult.reason,
        };
      }
      finalAction = parseResult.action;
      finalTarget = parseResult.target;
    }

    if (!finalAction || !finalTarget) {
      return {
        success: false,
        requestId,
        status: TASK_STATUS.REJECTED,
        reason: 'Action and target must be specified',
      };
    }

    // Record initial received status
    dbManager.recordCommand({
      requestId,
      deviceId,
      action: finalAction,
      target: finalTarget,
      rawCommand: rawCommand || `${finalAction} ${finalTarget}`,
      status: TASK_STATUS.RUNNING,
      message: 'Executing action...',
      timestamp: now,
    });

    this.broadcastCommandStatus(requestId, TASK_STATUS.RUNNING, 'Executing action...');

    // Execute through strict allowlist executor
    const execResult = await actionExecutor.execute(finalAction, finalTarget);
    const executedAt = Date.now();

    if (execResult.success) {
      dbManager.updateCommandStatus(requestId, TASK_STATUS.COMPLETED, execResult.message, executedAt);
      notifier.commandExecuted(rawCommand || `${finalAction} ${finalTarget}`);
      this.broadcastCommandStatus(requestId, TASK_STATUS.COMPLETED, execResult.message, executedAt);

      return {
        success: true,
        requestId,
        status: TASK_STATUS.COMPLETED,
        action: finalAction,
        target: finalTarget,
        message: execResult.message,
        executedAt,
      };
    } else {
      dbManager.updateCommandStatus(requestId, TASK_STATUS.FAILED, execResult.error, executedAt);
      notifier.commandFailed(rawCommand || `${finalAction} ${finalTarget}`, execResult.error);
      this.broadcastCommandStatus(requestId, TASK_STATUS.FAILED, execResult.error, executedAt);

      return {
        success: false,
        requestId,
        status: TASK_STATUS.FAILED,
        action: finalAction,
        target: finalTarget,
        error: execResult.error,
        executedAt,
      };
    }
  }

  handleUpgrade(request, socket, head) {
    const parsedUrl = new URL(request.url, `https://${request.headers.host || 'localhost'}`);
    if (parsedUrl.pathname !== '/ws') {
      socket.destroy();
      return;
    }

    const token = parsedUrl.searchParams.get('token') || this.extractBearerToken(request);
    const isLocal = this.isLocalhost(request) && parsedUrl.searchParams.get('local') === 'true';

    let deviceId = null;
    let deviceName = 'Android Device';

    if (isLocal) {
      deviceId = 'local-desktop';
      deviceName = 'Local Desktop';
    } else if (token) {
      const tokenHash = cryptoManager.hashToken(token);
      const device = dbManager.getDeviceByTokenHash(tokenHash);
      if (!device) {
        socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
        socket.destroy();
        return;
      }
      deviceId = device.device_id;
      deviceName = device.device_name;
      dbManager.updateDeviceLastSeen(deviceId);
    } else {
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
      return;
    }

    this.wss.handleUpgrade(request, socket, head, ws => {
      this.activeClients.set(ws, {
        deviceId,
        deviceName,
        isLocal,
        lastPing: Date.now(),
      });

      if (!isLocal) {
        notifier.phoneConnected(deviceName);
      }

      this.broadcastStateUpdate();
      this.setupClientWebSocket(ws);
    });
  }

  setupClientWebSocket(ws) {
    ws.on('message', async (data) => {
      const client = this.activeClients.get(ws);
      if (!client) return;

      try {
        const message = JSON.parse(data.toString());
        if (message.type === WS_EVENTS.PING) {
          client.lastPing = Date.now();
          ws.send(JSON.stringify({ type: WS_EVENTS.PONG, timestamp: Date.now() }));
          return;
        }

        if (message.type === WS_EVENTS.COMMAND) {
          const result = await this.processCommand(client.deviceId, message.data || {});
          ws.send(JSON.stringify({
            type: WS_EVENTS.COMMAND_STATUS,
            data: result,
          }));
          return;
        }
      } catch (err) {
        ws.send(JSON.stringify({
          type: 'ERROR',
          message: err.message,
        }));
      }
    });

    ws.on('close', () => {
      const client = this.activeClients.get(ws);
      if (client && !client.isLocal) {
        notifier.phoneDisconnected(client.deviceName);
      }
      this.activeClients.delete(ws);
      this.broadcastStateUpdate();
    });

    ws.on('error', () => {
      this.activeClients.delete(ws);
      this.broadcastStateUpdate();
    });
  }

  setupHeartbeat() {
    this.pingInterval = setInterval(() => {
      const now = Date.now();
      for (const [ws, client] of this.activeClients.entries()) {
        if (now - client.lastPing > 45000) {
          ws.terminate();
          this.activeClients.delete(ws);
        }
      }
    }, 15000);
  }

  broadcastCommandStatus(requestId, status, message, executedAt = Date.now()) {
    const payload = JSON.stringify({
      type: WS_EVENTS.COMMAND_STATUS,
      data: { requestId, status, message, executedAt },
    });
    for (const ws of this.activeClients.keys()) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(payload);
      }
    }
  }

  broadcastStateUpdate() {
    const payload = JSON.stringify({
      type: WS_EVENTS.STATE_UPDATE,
      data: {
        hostId: config.hostId,
        hostName: config.hostName,
        pairingSession: cryptoManager.getActivePairingSession(),
        devices: dbManager.listDevices(),
        connectedClientsCount: this.activeClients.size,
      },
    });
    for (const ws of this.activeClients.keys()) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(payload);
      }
    }
  }
}

export const hostServer = new HostServer();
