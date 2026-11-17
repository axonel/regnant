import fs from 'node:fs';
import path from 'node:path';
import { dbManager } from './db.js';

export class FileBrowser {
  getApprovedRoots() {
    return dbManager.listApprovedRoots();
  }

  isPathWithinApprovedRoots(targetPath) {
    const roots = this.getApprovedRoots();
    const resolvedTarget = path.resolve(targetPath);

    for (const root of roots) {
      const resolvedRoot = path.resolve(root.path);
      const relative = path.relative(resolvedRoot, resolvedTarget);
      if (!relative.startsWith('..') && !path.isAbsolute(relative)) {
        // Also verify realpath if it exists to defeat symlink escapes
        if (fs.existsSync(resolvedTarget)) {
          const realTarget = fs.realpathSync(resolvedTarget);
          const realRoot = fs.realpathSync(resolvedRoot);
          const realRelative = path.relative(realRoot, realTarget);
          if (realRelative.startsWith('..') || path.isAbsolute(realRelative)) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  listDirectory(dirPath) {
    if (!dirPath) {
      // Return root categories if no path specified
      return {
        currentPath: '',
        isRoot: true,
        items: this.getApprovedRoots().map(r => ({
          name: r.label,
          path: r.path,
          isDirectory: true,
          size: 0,
          modified: r.added_at,
          isRootCategory: true,
        })),
      };
    }

    const resolved = path.resolve(dirPath);
    if (!this.isPathWithinApprovedRoots(resolved)) {
      throw new Error(`Access denied: path is outside approved roots: ${dirPath}`);
    }

    if (!fs.existsSync(resolved)) {
      throw new Error(`Path does not exist: ${dirPath}`);
    }

    const stat = fs.statSync(resolved);
    if (!stat.isDirectory()) {
      throw new Error(`Path is not a directory: ${dirPath}`);
    }

    const entries = fs.readdirSync(resolved, { withFileTypes: true });
    const items = [];

    for (const entry of entries) {
      if (entry.name.startsWith('.')) continue; // skip hidden files

      const itemPath = path.join(resolved, entry.name);
      try {
        const itemStat = fs.statSync(itemPath);
        items.push({
          name: entry.name,
          path: itemPath,
          isDirectory: entry.isDirectory(),
          size: entry.isDirectory() ? 0 : itemStat.size,
          modified: Math.round(itemStat.mtimeMs),
        });
      } catch (e) {
        // skip unreadable items
      }
    }

    // Sort: directories first, then alphabetically
    items.sort((a, b) => {
      if (a.isDirectory && !b.isDirectory) return -1;
      if (!a.isDirectory && b.isDirectory) return 1;
      return a.name.localeCompare(b.name);
    });

    return {
      currentPath: resolved,
      isRoot: false,
      parentPath: this.isPathWithinApprovedRoots(path.dirname(resolved)) ? path.dirname(resolved) : null,
      items,
    };
  }

  readFileContent(filePath, maxBytes = 256 * 1024) {
    const resolved = path.resolve(filePath);
    if (!this.isPathWithinApprovedRoots(resolved)) {
      throw new Error(`Access denied: path is outside approved roots: ${filePath}`);
    }

    if (!fs.existsSync(resolved)) {
      throw new Error(`File does not exist: ${filePath}`);
    }

    const stat = fs.statSync(resolved);
    if (stat.isDirectory()) {
      throw new Error(`Cannot read directory as file: ${filePath}`);
    }

    if (stat.size > maxBytes) {
      throw new Error(`File size (${Math.round(stat.size / 1024)} KB) exceeds read limit (${Math.round(maxBytes / 1024)} KB)`);
    }

    const buffer = fs.readFileSync(resolved);
    // Simple text detection: check for null bytes
    const isText = !buffer.subarray(0, 1024).includes(0);

    return {
      path: resolved,
      fileName: path.basename(resolved),
      size: stat.size,
      modified: stat.mtimeMs,
      isText,
      content: isText ? buffer.toString('utf8') : null,
    };
  }
}

export const fileBrowser = new FileBrowser();
