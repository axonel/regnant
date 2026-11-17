import { app, BrowserWindow, Tray, Menu, ipcMain } from 'electron';
import path from 'node:path';
import fs from 'node:fs';
import https from 'node:https';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '../../..');
const ICON_PATH = path.join(ROOT_DIR, 'assets', 'icon-128.png');
const TRAY_ICON_PATH = path.join(ROOT_DIR, 'assets', 'icon-48.png');
const HOST_ENTRY = path.join(ROOT_DIR, 'linux', 'host', 'src', 'index.js');

let mainWindow = null;
let tray = null;
let hostProcess = null;
let isQuitting = false;

app.name = 'Regnant';

// Host request helper
function queryHost(endpoint, method = 'GET', body = null) {
  return new Promise((resolve, reject) => {
    const postData = body ? JSON.stringify(body) : null;
    const req = https.request({
      hostname: '127.0.0.1',
      port: 8765,
      path: endpoint,
      method,
      rejectUnauthorized: false,
      headers: {
        'x-regnant-local': 'true',
        'Content-Type': 'application/json',
        ...(postData ? { 'Content-Length': Buffer.byteLength(postData) } : {}),
      },
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          resolve({ raw: data, status: res.statusCode });
        }
      });
    });

    req.on('error', (err) => {
      resolve({ error: err.message, offline: true });
    });

    if (postData) {
      req.write(postData);
    }
    req.end();
  });
}

function ensureHostRunning() {
  queryHost('/api/v1/health').then(res => {
    if (res.offline || res.error) {
      console.log('[Desktop] Starting Regnant Host background daemon...');
      hostProcess = spawn(process.execPath || '/usr/bin/node', [HOST_ENTRY], {
        detached: true,
        stdio: 'ignore',
        env: {
          ...process.env,
          WAYLAND_DISPLAY: process.env.WAYLAND_DISPLAY || 'wayland-1',
          XDG_CURRENT_DESKTOP: process.env.XDG_CURRENT_DESKTOP || 'Hyprland',
          XDG_SESSION_TYPE: process.env.XDG_SESSION_TYPE || 'wayland',
        },
      });
      hostProcess.unref();
    }
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 860,
    height: 740,
    minWidth: 700,
    minHeight: 500,
    backgroundColor: '#0C0F14',
    icon: ICON_PATH,
    frame: true,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));

  mainWindow.on('close', (e) => {
    if (!isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });
}

function createTray() {
  if (!fs.existsSync(TRAY_ICON_PATH)) return;

  try {
    tray = new Tray(TRAY_ICON_PATH);
    const contextMenu = Menu.buildFromTemplate([
      {
        label: 'Show Regnant',
        click: () => {
          if (mainWindow) {
            mainWindow.show();
            mainWindow.focus();
          }
        },
      },
      {
        label: 'Generate Pairing Code',
        click: async () => {
          await queryHost('/api/v1/pair/code/generate', 'POST');
          if (mainWindow) {
            mainWindow.show();
            mainWindow.focus();
          }
        },
      },
      { type: 'separator' },
      {
        label: 'Quit Regnant Desktop',
        click: () => {
          isQuitting = true;
          app.quit();
        },
      },
    ]);

    tray.setToolTip('Regnant — Personal Mobile Control Plane');
    tray.setContextMenu(contextMenu);
    tray.on('click', () => {
      if (mainWindow) {
        if (mainWindow.isVisible()) {
          mainWindow.hide();
        } else {
          mainWindow.show();
          mainWindow.focus();
        }
      }
    });
  } catch (err) {
    console.error('Tray initialization notice:', err.message);
  }
}

app.whenReady().then(() => {
  ensureHostRunning();
  createWindow();
  createTray();

  ipcMain.handle('get-status', async () => {
    return await queryHost('/api/v1/status');
  });

  ipcMain.handle('generate-pairing-code', async () => {
    return await queryHost('/api/v1/pair/code/generate', 'POST');
  });

  ipcMain.handle('revoke-device', async (event, deviceId) => {
    return await queryHost('/api/v1/devices/revoke', 'POST', { deviceId });
  });

  ipcMain.handle('restart-host', async () => {
    ensureHostRunning();
    return { ok: true };
  });

  ipcMain.handle('close-window', () => {
    if (mainWindow) mainWindow.hide();
  });

  ipcMain.handle('minimize-window', () => {
    if (mainWindow) mainWindow.minimize();
  });
});

app.on('activate', () => {
  if (mainWindow) {
    mainWindow.show();
  } else {
    createWindow();
  }
});

app.on('before-quit', () => {
  isQuitting = true;
});
