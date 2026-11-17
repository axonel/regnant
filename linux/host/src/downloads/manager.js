import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { browserManager } from '../browser/manager.js';

export class DownloadManager {
  constructor() {
    this.downloadsDir = path.join(os.homedir(), 'Downloads');
  }

  getRecentFiles(limit = 20) {
    if (!fs.existsSync(this.downloadsDir)) {
      return [];
    }

    try {
      const entries = fs.readdirSync(this.downloadsDir, { withFileTypes: true });
      const files = [];

      for (const entry of entries) {
        if (!entry.isFile()) continue;
        const fullPath = path.join(this.downloadsDir, entry.name);
        try {
          const stat = fs.statSync(fullPath);
          const isPartial = entry.name.endsWith('.crdownload') || entry.name.endsWith('.part') || entry.name.endsWith('.tmp');
          files.push({
            name: entry.name,
            path: fullPath,
            size: stat.size,
            mtime: stat.mtimeMs,
            isPartial
          });
        } catch {
          // ignore inaccessible file
        }
      }

      files.sort((a, b) => b.mtime - a.mtime);
      return files.slice(0, limit);
    } catch {
      return [];
    }
  }

  async getAllDownloads(limit = 20) {
    const fsFiles = this.getRecentFiles(limit);

    let browserDownloads = [];
    if (browserManager.isConnected()) {
      try {
        const res = await browserManager.getDownloads();
        if (res && Array.isArray(res.downloads)) {
          browserDownloads = res.downloads;
        }
      } catch {
        // browser query failed
      }
    }

    return {
      downloadsDir: this.downloadsDir,
      files: fsFiles,
      browserDownloads
    };
  }

  async performAction(downloadId, action) {
    if (!browserManager.isConnected()) {
      throw new Error('Browser companion extension is not connected');
    }
    return browserManager.downloadAction(downloadId, action);
  }
}

let _downloadManagerInstance = null;
export const downloadManager = new Proxy({}, {
  get(target, prop) {
    if (!_downloadManagerInstance) {
      _downloadManagerInstance = new DownloadManager();
    }
    const val = _downloadManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_downloadManagerInstance) : val;
  }
});
