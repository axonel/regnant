import pty from 'node-pty';
import os from 'node:os';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';

const MAX_BUFFER_BYTES = 100 * 1024; // 100 KB circular buffer per terminal

export class TerminalManager extends EventEmitter {
  constructor() {
    super();
    this.sessions = new Map(); // sessionId -> { ptyProcess, buffer, cwd, createdAt, title, listeners }
  }

  createSession({ sessionId = null, cwd = null, cols = 80, rows = 24, title = 'Terminal' } = {}) {
    const id = sessionId || `term-${Date.now()}-${crypto.randomBytes(3).toString('hex')}`;
    if (this.sessions.has(id)) {
      return this.sessions.get(id);
    }

    const defaultShell = process.env.SHELL || '/bin/bash';
    const resolvedCwd = (cwd && fs.existsSync(cwd)) ? cwd : os.homedir();

    const ptyProcess = pty.spawn(defaultShell, [], {
      name: 'xterm-256color',
      cols: Math.max(10, Math.min(cols, 200)),
      rows: Math.max(5, Math.min(rows, 100)),
      cwd: resolvedCwd,
      env: {
        ...process.env,
        TERM: 'xterm-256color',
        COLORTERM: 'truecolor',
        LANG: process.env.LANG || 'en_US.UTF-8'
      }
    });

    const session = {
      id,
      ptyProcess,
      pid: ptyProcess.pid,
      cwd: resolvedCwd,
      title,
      buffer: '',
      cols,
      rows,
      createdAt: Date.now(),
      listeners: new Set()
    };

    ptyProcess.onData((data) => {
      session.buffer += data;
      if (session.buffer.length > MAX_BUFFER_BYTES) {
        session.buffer = session.buffer.slice(session.buffer.length - MAX_BUFFER_BYTES);
      }

      this.emit('data', { sessionId: id, data });
      for (const listener of session.listeners) {
        try {
          listener(data);
        } catch {
          // Ignore listener error
        }
      }
    });

    ptyProcess.onExit(({ exitCode, signal }) => {
      this.sessions.delete(id);
      this.emit('exit', { sessionId: id, exitCode, signal });
    });

    this.sessions.set(id, session);
    return session;
  }

  write(sessionId, data) {
    const session = this.sessions.get(sessionId);
    if (!session || !session.ptyProcess) {
      throw new Error(`Terminal session ${sessionId} not found`);
    }
    session.ptyProcess.write(data);
  }

  resize(sessionId, cols, rows) {
    const session = this.sessions.get(sessionId);
    if (!session || !session.ptyProcess) {
      throw new Error(`Terminal session ${sessionId} not found`);
    }
    const safeCols = Math.max(10, Math.min(cols, 200));
    const safeRows = Math.max(5, Math.min(rows, 100));
    session.cols = safeCols;
    session.rows = safeRows;
    try {
      session.ptyProcess.resize(safeCols, safeRows);
    } catch {
      // ignore resize failure on dead pty
    }
  }

  closeSession(sessionId) {
    const session = this.sessions.get(sessionId);
    if (!session) return false;
    try {
      session.ptyProcess.kill();
    } catch {
      // process already exited
    }
    this.sessions.delete(sessionId);
    return true;
  }

  getBuffer(sessionId) {
    const session = this.sessions.get(sessionId);
    return session ? session.buffer : '';
  }

  listSessions() {
    return Array.from(this.sessions.values()).map(s => ({
      id: s.id,
      pid: s.pid,
      cwd: s.cwd,
      title: s.title,
      cols: s.cols,
      rows: s.rows,
      createdAt: s.createdAt
    }));
  }

  addListener(sessionId, listener) {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.listeners.add(listener);
    }
  }

  removeListener(sessionId, listener) {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.listeners.delete(listener);
    }
  }
}

let _terminalManagerInstance = null;
export const terminalManager = new Proxy({}, {
  get(target, prop) {
    if (!_terminalManagerInstance) {
      _terminalManagerInstance = new TerminalManager();
    }
    const val = _terminalManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_terminalManagerInstance) : val;
  }
});
