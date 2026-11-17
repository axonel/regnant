import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import { WebSocketServer, WebSocket } from 'ws';
import { config } from './config.js';
import { cryptoManager } from './crypto.js';
import { dbManager } from './db.js';
import { commandParser } from './parser.js';
import { actionExecutor } from './executor.js';
import { notifier } from './notifier.js';
import { serviceDiscovery } from './discovery.js';
import { projectManager } from './projects.js';
import { metricsCollector } from './metrics.js';
import { fileBrowser } from './filebrowser.js';
import { transferManager } from './transfer.js';
import { webManager } from './web.js';
import { clipboardManager } from './clipboard.js';
import { powerManager } from './power.js';
import { appManager } from './appmanager.js';
import { routineManager } from './routines.js';
import {
  ACTIONS,
  TARGETS,
  TASK_STATUS,
  WS_EVENTS,
  CAPABILITIES,
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

    // Listen on dual-stack '::' to accept IPv6 link-local and IPv4
    this.server.listen(port, '::', () => {
      console.log(`[Regnant Host] Server listening securely on port ${port} (HTTPS/WSS, IPv4/IPv6 dual-stack)`);
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
    // If request originates strictly from localhost (e.g. desktop companion), allow management access
    if (this.isLocalhost(req) && req.headers['x-regnant-local'] === 'true') {
      return { authenticated: true, deviceId: 'local-desktop', deviceName: 'Local Desktop', isLocal: true };
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
        if (body.length > 1024 * 1024 * 10) { // 10MB limit for general JSON/chunks
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

  async parseRawBody(req) {
    return new Promise((resolve, reject) => {
      const chunks = [];
      req.on('data', chunk => chunks.push(chunk));
      req.on('end', () => resolve(Buffer.concat(chunks)));
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

    // --- Public Endpoints ---

    // 1. Health Check
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

    // 2. Pairing Endpoint
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

    // --- Authenticated Management Endpoints ---

    // 3. Generate Pairing Code (localhost only)
    if (req.method === 'POST' && pathname === '/api/v1/pair/code/generate') {
      if (!this.isLocalhost(req)) {
        return this.sendJson(res, 403, { error: 'Only accessible locally from workstation desktop' });
      }
      const session = cryptoManager.generatePairingCode();
      this.broadcastStateUpdate();
      return this.sendJson(res, 200, session);
    }

    // 4. Host Status
    if (req.method === 'GET' && pathname === '/api/v1/status') {
      if (!this.isLocalhost(req)) {
        const auth = this.authenticateRequest(req);
        if (!auth.authenticated) return this.sendJson(res, 401, { error: 'Unauthorized' });
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
        supportedIdes: projectManager.getSupportedIdes(),
        connectedClientsCount: this.activeClients.size,
      });
    }

    // 5. Authenticate all subsequent endpoints
    const auth = this.authenticateRequest(req);
    if (!auth.authenticated) {
      return this.sendJson(res, 401, { error: auth.reason || 'Unauthorized' });
    }

    // --- Feature Endpoints ---

    // 6. Laptop Metrics Dashboard
    if (req.method === 'GET' && pathname === '/api/v1/metrics') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.VIEW_METRICS)) {
        return this.sendJson(res, 403, { error: 'Permission denied: viewing host metrics is restricted' });
      }
      return this.sendJson(res, 200, metricsCollector.getSnapshot());
    }

    // 7. Projects API
    if (req.method === 'GET' && pathname === '/api/v1/projects') {
      return this.sendJson(res, 200, {
        projects: projectManager.listProjects(),
        supportedIdes: projectManager.getSupportedIdes(),
      });
    }

    if (req.method === 'POST' && pathname === '/api/v1/projects/launch') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.PROJECTS)) {
        return this.sendJson(res, 403, { error: 'Permission denied: project launching is restricted' });
      }
      try {
        const body = await this.parseBody(req);
        const result = projectManager.launchProject(body.id, body.ide);
        notifier.notify('Regnant Project Launched', `${result.project} in ${result.ide}`);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/projects/register') {
      try {
        const body = await this.parseBody(req);
        const result = projectManager.registerProject(body.name, body.path, body.ide);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/projects/delete') {
      try {
        const body = await this.parseBody(req);
        const result = projectManager.deleteProject(body.id);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // 8. Remote File Browser API
    if (req.method === 'GET' && pathname === '/api/v1/files') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.BROWSE_FILES)) {
        return this.sendJson(res, 403, { error: 'Permission denied: file browsing is restricted' });
      }
      try {
        const dirPath = parsedUrl.searchParams.get('path');
        const listing = fileBrowser.listDirectory(dirPath);
        return this.sendJson(res, 200, listing);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    if (req.method === 'GET' && pathname === '/api/v1/files/read') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.BROWSE_FILES)) {
        return this.sendJson(res, 403, { error: 'Permission denied: file reading is restricted' });
      }
      try {
        const targetPath = parsedUrl.searchParams.get('path');
        const fileData = fileBrowser.readFileContent(targetPath);
        return this.sendJson(res, 200, fileData);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // 9. File Transfer API
    if (req.method === 'POST' && pathname === '/api/v1/transfer/init') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.TRANSFER_FILES)) {
        return this.sendJson(res, 403, { error: 'Permission denied: file transfer is restricted' });
      }
      try {
        const body = await this.parseBody(req);
        const result = transferManager.initUpload(body.fileName, body.fileSize, body.sha256);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/transfer/chunk') {
      try {
        const transferId = req.headers['x-transfer-id'];
        if (!transferId) return this.sendJson(res, 400, { error: 'Missing x-transfer-id header' });

        const rawChunk = await this.parseRawBody(req);
        const result = transferManager.appendChunk(transferId, rawChunk);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/transfer/finalize') {
      try {
        const body = await this.parseBody(req);
        const result = await transferManager.finalizeUpload(body.transferId);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    if (req.method === 'GET' && pathname === '/api/v1/transfer/download') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.TRANSFER_FILES)) {
        return this.sendJson(res, 403, { error: 'Permission denied: file transfer is restricted' });
      }
      try {
        const targetPath = parsedUrl.searchParams.get('path');
        const info = transferManager.getDownloadInfo(targetPath);

        res.writeHead(200, {
          'Content-Type': 'application/octet-stream',
          'Content-Length': info.fileSize,
          'Content-Disposition': `attachment; filename="${info.fileName}"`,
          'X-Checksum-SHA256': info.sha256,
        });

        const stream = fs.createReadStream(info.path);
        stream.pipe(res);
        return;
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // 10. Web & Search API
    if (req.method === 'POST' && pathname === '/api/v1/web/open') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.OPEN_WEB)) {
        return this.sendJson(res, 403, { error: 'Permission denied: web browser controls are restricted' });
      }
      try {
        const body = await this.parseBody(req);
        const result = webManager.openUrl(body.url);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/web/search') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.OPEN_WEB)) {
        return this.sendJson(res, 403, { error: 'Permission denied: web browser controls are restricted' });
      }
      try {
        const body = await this.parseBody(req);
        const result = webManager.searchWeb(body.query);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // 11. Clipboard API
    if (req.method === 'GET' && pathname === '/api/v1/clipboard') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.CLIPBOARD)) {
        return this.sendJson(res, 403, { error: 'Permission denied: clipboard access is restricted' });
      }
      try {
        const result = await clipboardManager.readClipboard();
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 500, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/clipboard') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.CLIPBOARD)) {
        return this.sendJson(res, 403, { error: 'Permission denied: clipboard access is restricted' });
      }
      try {
        const body = await this.parseBody(req);
        const result = await clipboardManager.writeClipboard(body.text || '');
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 500, { error: err.message });
      }
    }

    // 12. Running Application Manager API
    if (req.method === 'GET' && pathname === '/api/v1/apps') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.RUNNING_APPS)) {
        return this.sendJson(res, 403, { error: 'Permission denied: application management is restricted' });
      }
      try {
        const apps = await appManager.listRunningApps();
        return this.sendJson(res, 200, { apps });
      } catch (err) {
        return this.sendJson(res, 500, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/apps/close') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.RUNNING_APPS)) {
        return this.sendJson(res, 403, { error: 'Permission denied: application management is restricted' });
      }
      try {
        const body = await this.parseBody(req);
        const result = await appManager.closeWindow(body.address);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // 13. Power & Lock Controls API
    if (req.method === 'POST' && pathname === '/api/v1/power/lock') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.POWER_CONTROLS)) {
        return this.sendJson(res, 403, { error: 'Permission denied: power controls are restricted' });
      }
      try {
        const result = await powerManager.lockSession();
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 500, { error: err.message });
      }
    }

    if (req.method === 'POST' && pathname === '/api/v1/power/suspend') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.POWER_CONTROLS)) {
        return this.sendJson(res, 403, { error: 'Permission denied: power controls are restricted' });
      }
      try {
        const result = await powerManager.suspendSystem();
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 500, { error: err.message });
      }
    }

    // 14. Routines API
    if (req.method === 'GET' && pathname === '/api/v1/routines') {
      return this.sendJson(res, 200, { routines: routineManager.listRoutines() });
    }

    if (req.method === 'POST' && pathname === '/api/v1/routines/execute') {
      if (!dbManager.isPermissionAllowed(auth.deviceId, CAPABILITIES.ROUTINES)) {
        return this.sendJson(res, 403, { error: 'Permission denied: routine execution is restricted' });
      }
      try {
        const body = await this.parseBody(req);
        const result = await routineManager.executeRoutine(body.routineId);
        return this.sendJson(res, 200, result);
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // 15. Permissions Matrix API
    if (req.method === 'GET' && pathname === '/api/v1/permissions') {
      const targetDevice = parsedUrl.searchParams.get('deviceId') || auth.deviceId;
      return this.sendJson(res, 200, {
        deviceId: targetDevice,
        permissions: dbManager.getPermissions(targetDevice),
      });
    }

    if (req.method === 'POST' && pathname === '/api/v1/permissions') {
      // Modifying permissions requires local desktop access
      if (!auth.isLocal) {
        return this.sendJson(res, 403, { error: 'Modifying permissions is only allowed directly on workstation desktop' });
      }
      try {
        const body = await this.parseBody(req);
        dbManager.setPermission(body.deviceId, body.capability, body.allowed);
        return this.sendJson(res, 200, { success: true });
      } catch (err) {
        return this.sendJson(res, 400, { error: err.message });
      }
    }

    // 16. Command Submission Endpoint (Core execution)
    if (req.method === 'POST' && pathname === '/api/v1/commands') {
      try {
        const body = await this.parseBody(req);
        const result = await this.processCommand(auth.deviceId, body);
        return this.sendJson(res, result.success ? 200 : 400, result);
      } catch (err) {
        return this.sendJson(res, 500, { status: 'FAILED', reason: err.message });
      }
    }

    // 17. Device Management & History
    if (req.method === 'GET' && pathname === '/api/v1/devices') {
      return this.sendJson(res, 200, { devices: dbManager.listDevices() });
    }

    if (req.method === 'POST' && pathname === '/api/v1/devices/revoke') {
      try {
        const body = await this.parseBody(req);
        const { deviceId } = body;
        if (!deviceId) return this.sendJson(res, 400, { error: 'Missing deviceId' });

        dbManager.revokeDevice(deviceId);

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

    if (req.method === 'GET' && pathname === '/api/v1/history') {
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

    // Parse natural language if provided
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

    // Capability permission check
    let requiredCap = CAPABILITIES.LAUNCH_APPS;
    if (finalAction === ACTIONS.OPEN_PATH) requiredCap = CAPABILITIES.OPEN_PATHS;
    if (finalAction === ACTIONS.PROJECT_LAUNCH || finalAction === ACTIONS.PROJECT_LIST) requiredCap = CAPABILITIES.PROJECTS;
    if (finalAction === ACTIONS.GET_METRICS) requiredCap = CAPABILITIES.VIEW_METRICS;
    if (finalAction === ACTIONS.CLIPBOARD_READ || finalAction === ACTIONS.CLIPBOARD_WRITE) requiredCap = CAPABILITIES.CLIPBOARD;
    if (finalAction === ACTIONS.POWER_LOCK || finalAction === ACTIONS.POWER_SUSPEND) requiredCap = CAPABILITIES.POWER_CONTROLS;
    if (finalAction === ACTIONS.APPS_LIST || finalAction === ACTIONS.APPS_CLOSE) requiredCap = CAPABILITIES.RUNNING_APPS;
    if (finalAction === ACTIONS.ROUTINE_EXECUTE) requiredCap = CAPABILITIES.ROUTINES;
    if (finalAction === ACTIONS.WEB_SEARCH || finalAction === ACTIONS.OPEN_URL) requiredCap = CAPABILITIES.OPEN_WEB;

    if (!dbManager.isPermissionAllowed(deviceId, requiredCap)) {
      const reason = `Permission denied: device does not have permission for capability "${requiredCap}"`;
      dbManager.recordCommand({
        requestId,
        deviceId,
        action: finalAction,
        target: finalTarget,
        rawCommand: rawCommand || `${finalAction} ${finalTarget}`,
        status: TASK_STATUS.REJECTED,
        message: reason,
        timestamp: now,
        executedAt: now,
      });
      return { success: false, requestId, status: TASK_STATUS.REJECTED, reason };
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

    let execResult = null;

    try {
      if (finalAction === ACTIONS.LAUNCH_APP || finalAction === ACTIONS.OPEN_PATH) {
        execResult = await actionExecutor.execute(finalAction, finalTarget);
      } else if (finalAction === ACTIONS.PROJECT_LAUNCH) {
        execResult = projectManager.launchProject(finalTarget);
      } else if (finalAction === ACTIONS.PROJECT_LIST) {
        execResult = { success: true, message: `Projects: ${projectManager.listProjects().map(p => p.name).join(', ')}` };
      } else if (finalAction === ACTIONS.GET_METRICS) {
        const snap = metricsCollector.getSnapshot();
        execResult = { success: true, message: `CPU: ${snap.cpuPercent}% | RAM: ${snap.memory.percent}% | Bat: ${snap.battery.percent}%` };
      } else if (finalAction === ACTIONS.CLIPBOARD_READ) {
        const clip = await clipboardManager.readClipboard();
        execResult = { success: true, message: `Clipboard: "${clip.text.slice(0, 40)}..."` };
      } else if (finalAction === ACTIONS.POWER_LOCK) {
        execResult = await powerManager.lockSession();
      } else if (finalAction === ACTIONS.POWER_SUSPEND) {
        execResult = await powerManager.suspendSystem();
      } else if (finalAction === ACTIONS.APPS_LIST) {
        const apps = await appManager.listRunningApps();
        execResult = { success: true, message: `Open apps: ${apps.map(a => a.appClass).join(', ')}` };
      } else if (finalAction === ACTIONS.ROUTINE_EXECUTE) {
        execResult = await routineManager.executeRoutine(finalTarget);
      } else if (finalAction === ACTIONS.WEB_SEARCH) {
        execResult = webManager.searchWeb(finalTarget);
      } else if (finalAction === ACTIONS.OPEN_URL) {
        execResult = webManager.openUrl(finalTarget);
      } else {
        execResult = { success: false, error: `Unhandled action: ${finalAction}` };
      }
    } catch (err) {
      execResult = { success: false, error: err.message };
    }

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

        if (message.type === 'GET_METRICS') {
          ws.send(JSON.stringify({
            type: WS_EVENTS.METRICS_UPDATE,
            data: metricsCollector.getSnapshot(),
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
