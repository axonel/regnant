import { spawn } from 'node:child_process';
import { actionExecutor } from './executor.js';

export class WebManager {
  validateUrl(rawUrl) {
    if (!rawUrl || typeof rawUrl !== 'string') {
      throw new Error('URL must be a non-empty string');
    }

    let parsed;
    try {
      parsed = new URL(rawUrl.trim());
    } catch (e) {
      // If missing protocol, assume https://
      if (!rawUrl.includes('://')) {
        return this.validateUrl(`https://${rawUrl.trim()}`);
      }
      throw new Error(`Invalid URL format: ${rawUrl}`);
    }

    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      throw new Error(`Forbidden protocol: only http: and https: are permitted (received ${parsed.protocol})`);
    }

    return parsed.href;
  }

  openUrl(rawUrl) {
    const validUrl = this.validateUrl(rawUrl);
    const env = actionExecutor.resolveSessionEnvironment();

    // Use xdg-open or brave
    const browser = '/usr/bin/brave';
    const execPath = fs.existsSync(browser) ? browser : '/usr/bin/xdg-open';

    const child = spawn(execPath, [validUrl], {
      detached: true,
      stdio: 'ignore',
      env,
    });
    child.unref();

    return {
      success: true,
      message: `Opened URL: ${validUrl}`,
      url: validUrl,
      pid: child.pid,
    };
  }

  searchWeb(query) {
    if (!query || typeof query !== 'string' || !query.trim()) {
      throw new Error('Search query must be non-empty');
    }

    const cleanQuery = query.trim();
    const encoded = encodeURIComponent(cleanQuery);
    const searchUrl = `https://search.brave.com/search?q=${encoded}`;

    return this.openUrl(searchUrl);
  }
}

import fs from 'node:fs';
export const webManager = new WebManager();
