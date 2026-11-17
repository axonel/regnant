import { execFile } from 'node:child_process';

export class DesktopPeekManager {
  constructor() {
    this.lastCaptureTime = 0;
    this.lastBuffer = null;
    this.minIntervalMs = 400; // Rate limit: maximum 2.5 frames per second
  }

  async captureJpeg(quality = 70) {
    const now = Date.now();
    if (this.lastBuffer && (now - this.lastCaptureTime < this.minIntervalMs)) {
      return {
        buffer: this.lastBuffer,
        cached: true,
        timestamp: this.lastCaptureTime
      };
    }

    const safeQuality = Math.max(20, Math.min(quality, 95));

    return new Promise((resolve, reject) => {
      execFile('grim', ['-t', 'jpeg', '-q', String(safeQuality), '-'], {
        encoding: 'buffer',
        maxBuffer: 10 * 1024 * 1024,
        timeout: 4000
      }, (err, stdout, stderr) => {
        if (err) {
          reject(new Error(`Desktop peek capture failed: ${stderr || err.message}`));
          return;
        }

        this.lastBuffer = stdout;
        this.lastCaptureTime = Date.now();

        resolve({
          buffer: stdout,
          cached: false,
          timestamp: this.lastCaptureTime
        });
      });
    });
  }
}

let _desktopPeekInstance = null;
export const desktopPeekManager = new Proxy({}, {
  get(target, prop) {
    if (!_desktopPeekInstance) {
      _desktopPeekInstance = new DesktopPeekManager();
    }
    const val = _desktopPeekInstance[prop];
    return typeof val === 'function' ? val.bind(_desktopPeekInstance) : val;
  }
});
