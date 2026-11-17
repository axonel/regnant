import { execSync } from 'node:child_process';
import { AgentAdapter } from './base.js';

export class CodexAdapter extends AgentAdapter {
  constructor() {
    super('codex', 'OpenAI Codex CLI');
    this.processes = new Map();
  }

  async detect() {
    try {
      const out = execSync('codex --version', {
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
          path: execSync('which codex', { encoding: 'utf8' }).trim(),
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
      guidance: 'Codex CLI is not installed on this workstation. Run "mise use -g codex" to configure it.'
    };
  }

  async startSession() {
    throw new Error('Codex CLI is currently not installed on this workstation.');
  }

  sendInput() {
    return false;
  }

  cancelSession() {
    return false;
  }
}
