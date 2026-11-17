import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { actionExecutor } from './executor.js';

const execFileAsync = promisify(execFile);

export class ClipboardManager {
  async readClipboard() {
    const env = actionExecutor.resolveSessionEnvironment();

    try {
      const { stdout } = await execFileAsync('/usr/bin/wl-paste', ['-t', 'text/plain', '--no-newline'], {
        env,
        timeout: 3000,
        maxBuffer: 1024 * 512, // 512KB max
      });

      return {
        success: true,
        text: stdout,
        length: stdout.length,
      };
    } catch (err) {
      if (err.code === 1 || err.stderr?.includes('No selection')) {
        return { success: true, text: '', length: 0 };
      }
      throw new Error(`Failed to read clipboard from workstation: ${err.message}`);
    }
  }

  async writeClipboard(text) {
    if (typeof text !== 'string') {
      throw new Error('Clipboard content must be text');
    }

    const env = actionExecutor.resolveSessionEnvironment();

    return new Promise((resolve, reject) => {
      const child = spawn('/usr/bin/wl-copy', [], {
        env,
        stdio: ['pipe', 'ignore', 'pipe'],
      });

      let errOutput = '';
      child.stderr.on('data', chunk => { errOutput += chunk; });

      child.on('close', code => {
        if (code === 0) {
          resolve({ success: true, message: 'Copied text to workstation clipboard', length: text.length });
        } else {
          reject(new Error(`wl-copy exited with code ${code}: ${errOutput}`));
        }
      });

      child.on('error', reject);

      child.stdin.write(text);
      child.stdin.end();
    });
  }
}

export const clipboardManager = new ClipboardManager();
