import { execSync } from 'node:child_process';
import { AgentAdapter } from './base.js';

export class ClaudeAdapter extends AgentAdapter {
  constructor() {
    super('claude', 'Claude Code');
    this.processes = new Map();
  }

  async detect() {
    try {
      const out = execSync('claude --version', {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 2000
      }).trim();
      if (out && !out.includes('mise ERROR') && !out.includes('not found')) {
        return {
          id: this.id,
          name: this.name,
          available: true,
          version: out,
          path: execSync('which claude', { encoding: 'utf8' }).trim(),
          guidance: 'Operational and ready to execute development tasks.'
        };
      }
    } catch {
      // not available or invalid shim
    }

    return {
      id: this.id,
      name: this.name,
      available: false,
      version: null,
      path: null,
      guidance: 'Claude Code is not installed on this workstation. Run "mise use -g claude" or "npm install -g @anthropic-ai/claude-code" to install it.'
    };
  }

  async startSession() {
    throw new Error('Claude Code is currently not installed on this workstation.');
  }

  sendInput() {
    return false;
  }

  cancelSession() {
    return false;
  }
}
