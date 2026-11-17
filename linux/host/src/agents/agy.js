import { spawn, execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { AgentAdapter } from './base.js';

export class AgyAdapter extends AgentAdapter {
  constructor() {
    super('agy', 'Antigravity CLI');
    this.processes = new Map();
  }

  findBinary() {
    const candidates = [
      path.join(os.homedir(), '.local', 'bin', 'agy'),
      '/usr/local/bin/agy',
      '/usr/bin/agy'
    ];
    for (const c of candidates) {
      if (fs.existsSync(c)) {
        return c;
      }
    }
    try {
      const out = execSync('which agy', { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
      if (out && fs.existsSync(out)) return out;
    } catch {
      // not found
    }
    return null;
  }

  async detect() {
    const bin = this.findBinary();
    if (!bin) {
      return {
        id: this.id,
        name: this.name,
        available: false,
        version: null,
        path: null,
        guidance: 'agy CLI is not installed or not in ~/.local/bin.'
      };
    }

    let version = 'unknown';
    try {
      const out = execSync(`"${bin}" changelog | head -n 1`, {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 2000
      }).trim();
      if (out) version = out;
    } catch {
      version = 'installed';
    }

    return {
      id: this.id,
      name: this.name,
      available: true,
      version,
      path: bin,
      guidance: 'Operational and ready to execute development tasks.'
    };
  }

  async startSession({ sessionId, workspacePath, prompt, onOutput, onExit, onError }) {
    const bin = this.findBinary();
    if (!bin) {
      throw new Error('agy CLI binary not found on workstation');
    }

    const targetDir = fs.existsSync(workspacePath) ? workspacePath : os.homedir();
    // Use --output-format stream-json --print=<prompt>
    const args = [
      '--output-format', 'stream-json',
      `--print=${prompt}`
    ];

    const child = spawn(bin, args, {
      cwd: targetDir,
      env: { ...process.env, TERM: 'xterm-256color' },
      stdio: ['pipe', 'pipe', 'pipe']
    });

    this.processes.set(sessionId, child);

    let stdoutBuffer = '';
    child.stdout.on('data', (chunk) => {
      stdoutBuffer += chunk.toString('utf8');
      const lines = stdoutBuffer.split('\n');
      stdoutBuffer = lines.pop(); // keep last incomplete line

      for (const line of lines) {
        if (!line.trim()) continue;
        try {
          const parsed = JSON.parse(line);
          onOutput({ type: 'json', raw: line, data: parsed });
        } catch {
          onOutput({ type: 'text', raw: line, data: { content: line } });
        }
      }
    });

    let stderrBuffer = '';
    child.stderr.on('data', (chunk) => {
      stderrBuffer += chunk.toString('utf8');
      const lines = stderrBuffer.split('\n');
      stderrBuffer = lines.pop();
      for (const line of lines) {
        if (!line.trim()) continue;
        onOutput({ type: 'stderr', raw: line, data: { error: line } });
      }
    });

    child.on('error', (err) => {
      this.processes.delete(sessionId);
      if (onError) onError(err);
    });

    child.on('exit', (code, signal) => {
      this.processes.delete(sessionId);
      if (stdoutBuffer.trim()) {
        try {
          onOutput({ type: 'json', raw: stdoutBuffer, data: JSON.parse(stdoutBuffer) });
        } catch {
          onOutput({ type: 'text', raw: stdoutBuffer, data: { content: stdoutBuffer } });
        }
      }
      if (onExit) onExit(code, signal);
    });

    return { pid: child.pid };
  }

  sendInput(sessionId, input) {
    const child = this.processes.get(sessionId);
    if (!child || !child.stdin || !child.stdin.writable) {
      return false;
    }
    child.stdin.write(input + '\n');
    return true;
  }

  cancelSession(sessionId) {
    const child = this.processes.get(sessionId);
    if (!child) return false;
    child.kill('SIGTERM');
    setTimeout(() => {
      if (this.processes.has(sessionId)) {
        try {
          child.kill('SIGKILL');
        } catch {
          // already dead
        }
      }
    }, 2000);
    return true;
  }
}
