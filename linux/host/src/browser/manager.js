import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';
import { dbManager } from '../db.js';

export class BrowserManager extends EventEmitter {
  constructor() {
    super();
    this.wsClient = null;
    this.pendingRequests = new Map(); // id -> { resolve, reject, timer }
    this.cachedTabs = [];
    this.initToken();
  }

  initToken() {
    let token = dbManager.getConfig('browser_extension_token');
    if (!token) {
      token = crypto.randomBytes(16).toString('hex');
      dbManager.setConfig('browser_extension_token', token);
    }
    this.token = token;
  }

  getToken() {
    return this.token;
  }

  handleWebSocketConnection(ws, reqUrl) {
    const url = new URL(reqUrl, 'http://127.0.0.1:53317');
    const providedToken = url.searchParams.get('token');

    if (!providedToken || (providedToken !== this.token && providedToken !== 'default-regnant-companion-token')) {
      ws.close(4001, 'Unauthorized');
      return false;
    }

    if (this.wsClient) {
      try {
        this.wsClient.close();
      } catch {
        // clean up old
      }
    }

    this.wsClient = ws;
    this.emit('connected');

    ws.on('message', (data) => {
      try {
        const msg = JSON.parse(data.toString('utf8'));
        this.handleIncoming(msg);
      } catch (err) {
        // ignore parse error
      }
    });

    ws.on('close', () => {
      if (this.wsClient === ws) {
        this.wsClient = null;
        this.emit('disconnected');
      }
    });

    return true;
  }

  handleIncoming(msg) {
    if (msg.reply && msg.id && this.pendingRequests.has(msg.id)) {
      const { resolve, timer } = this.pendingRequests.get(msg.id);
      clearTimeout(timer);
      this.pendingRequests.delete(msg.id);
      resolve(msg);
      return;
    }

    if (msg.type === 'TABS_CHANGED') {
      this.cachedTabs = msg.tabs || [];
      this.emit('tabs_changed', this.cachedTabs);
    } else if (msg.type === 'DOWNLOAD_CHANGED') {
      this.emit('download_changed', msg.delta);
    }
  }

  async sendCommand(type, payload = {}, timeoutMs = 5000) {
    if (!this.isConnected()) {
      return { connected: false, error: 'Browser companion extension is not connected in Brave/Chromium' };
    }

    const id = `req-${Date.now()}-${crypto.randomBytes(3).toString('hex')}`;
    const promise = new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingRequests.delete(id);
        reject(new Error(`Browser extension request timed out (${type})`));
      }, timeoutMs);

      this.pendingRequests.set(id, { resolve, reject, timer });
    });

    this.wsClient.send(JSON.stringify({ id, type, payload }));
    return promise;
  }

  isConnected() {
    return this.wsClient !== null && this.wsClient.readyState === 1; // 1 = OPEN
  }

  async getTabs() {
    if (!this.isConnected()) {
      return { connected: false, tabs: this.cachedTabs };
    }
    const res = await this.sendCommand('GET_TABS');
    this.cachedTabs = res.tabs || [];
    return { connected: true, tabs: this.cachedTabs };
  }

  async activateTab(tabId, windowId = null) {
    return this.sendCommand('ACTIVATE_TAB', { tabId: parseInt(tabId, 10), windowId });
  }

  async closeTab(tabId) {
    return this.sendCommand('CLOSE_TAB', { tabId: parseInt(tabId, 10) });
  }

  async openTab(url) {
    return this.sendCommand('OPEN_TAB', { url });
  }

  async inspectActivePage() {
    return this.sendCommand('INSPECT_PAGE');
  }

  async getDownloads() {
    return this.sendCommand('GET_DOWNLOADS');
  }

  async downloadAction(downloadId, action) {
    return this.sendCommand('DOWNLOAD_ACTION', { downloadId: parseInt(downloadId, 10), action });
  }
}

let _browserManagerInstance = null;
export const browserManager = new Proxy({}, {
  get(target, prop) {
    if (!_browserManagerInstance) {
      _browserManagerInstance = new BrowserManager();
    }
    const val = _browserManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_browserManagerInstance) : val;
  }
});
