import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { dbManager } from '../db.js';

export class SearchManager {
  constructor() {
    this.desktopDirs = [
      '/usr/share/applications',
      '/usr/local/share/applications',
      path.join(os.homedir(), '.local', 'share', 'applications')
    ];
  }

  async reindex() {
    dbManager.clearSearchIndex();

    // 1. Index Registered Projects
    const projects = dbManager.listProjects();
    for (const proj of projects) {
      dbManager.indexSearchItem(
        proj.id,
        'project',
        proj.name,
        proj.path,
        `project code repository ${proj.ide}`
      );
    }

    // 2. Index Installed Desktop Applications
    for (const dir of this.desktopDirs) {
      if (!fs.existsSync(dir)) continue;
      try {
        const files = fs.readdirSync(dir);
        for (const file of files) {
          if (!file.endsWith('.desktop')) continue;
          const fullPath = path.join(dir, file);
          try {
            const content = fs.readFileSync(fullPath, 'utf8');
            const nameMatch = content.match(/^Name=(.+)$/m);
            const execMatch = content.match(/^Exec=(.+)$/m);
            const commentMatch = content.match(/^Comment=(.+)$/m);
            const noDisplay = content.match(/^NoDisplay=true/m);

            if (noDisplay) continue;
            if (nameMatch) {
              const appName = nameMatch[1].trim();
              const exec = execMatch ? execMatch[1].trim() : '';
              const comment = commentMatch ? commentMatch[1].trim() : '';
              dbManager.indexSearchItem(
                `app-${file}`,
                'app',
                appName,
                exec || fullPath,
                `${appName} ${file} ${comment}`
              );
            }
          } catch {
            // ignore unreadable .desktop file
          }
        }
      } catch {
        // ignore directory read error
      }
    }

    // 3. Index Top-Level Files in Approved Roots
    const roots = dbManager.listApprovedRoots();
    for (const root of roots) {
      if (!fs.existsSync(root.path)) continue;
      try {
        const entries = fs.readdirSync(root.path, { withFileTypes: true });
        for (const entry of entries) {
          if (entry.name.startsWith('.') || entry.name === 'node_modules' || entry.name === 'target') continue;
          const fullPath = path.join(root.path, entry.name);
          dbManager.indexSearchItem(
            `file-${entry.name}`,
            entry.isDirectory() ? 'folder' : 'file',
            entry.name,
            fullPath,
            `${entry.name} in ${root.label}`
          );
        }
      } catch {
        // ignore root read error
      }
    }

    return { indexed: true, timestamp: Date.now() };
  }

  search(query, limit = 20) {
    if (!query || typeof query !== 'string') return [];
    return dbManager.querySearch(query, limit);
  }
}

let _searchManagerInstance = null;
export const searchManager = new Proxy({}, {
  get(target, prop) {
    if (!_searchManagerInstance) {
      _searchManagerInstance = new SearchManager();
    }
    const val = _searchManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_searchManagerInstance) : val;
  }
});
