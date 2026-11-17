import crypto from 'node:crypto';
import { EventEmitter } from 'node:events';
import { AgyAdapter } from './agy.js';
import { ClaudeAdapter } from './claude.js';
import { CodexAdapter } from './codex.js';
import { dbManager } from '../db.js';

export class AgentManager extends EventEmitter {
  constructor() {
    super();
    this.adapters = new Map();
    this.registerAdapter(new AgyAdapter());
    this.registerAdapter(new ClaudeAdapter());
    this.registerAdapter(new CodexAdapter());

    this.activeSessions = new Map(); // sessionId -> { adapter, pid, workspacePath, prompt, startTime }
  }

  registerAdapter(adapter) {
    this.adapters.set(adapter.id, adapter);
  }

  async listAgents() {
    const results = [];
    for (const [id, adapter] of this.adapters.entries()) {
      try {
        const info = await adapter.detect();
        results.push(info);
      } catch (err) {
        results.push({
          id,
          name: adapter.name,
          available: false,
          version: null,
          path: null,
          guidance: `Detection failed: ${err.message}`
        });
      }
    }
    return results;
  }

  async startSession({ agentId, workspacePath, prompt, broadcastFn = null }) {
    const adapter = this.adapters.get(agentId);
    if (!adapter) {
      throw new Error(`Unsupported agent type: ${agentId}`);
    }

    const detection = await adapter.detect();
    if (!detection.available) {
      throw new Error(`Agent '${adapter.name}' is not available: ${detection.guidance}`);
    }

    const sessionId = `agent-${Date.now()}-${crypto.randomBytes(3).toString('hex')}`;
    
    // Save to database
    dbManager.createAgentSession(sessionId, agentId, workspacePath, prompt);

    const onOutput = (event) => {
      this.emit('output', { sessionId, event });
      if (broadcastFn) {
        broadcastFn({
          type: 'AGENT_OUTPUT',
          sessionId,
          agentId,
          event
        });
      }
    };

    const onExit = (code, signal) => {
      const status = (code === 0) ? 'COMPLETED' : 'FAILED';
      this.activeSessions.delete(sessionId);
      dbManager.updateAgentSession(sessionId, status);

      this.emit('completed', { sessionId, agentId, status, code, signal });
      if (broadcastFn) {
        broadcastFn({
          type: 'AGENT_STATUS',
          sessionId,
          agentId,
          status,
          code,
          signal
        });
      }
    };

    const onError = (err) => {
      this.activeSessions.delete(sessionId);
      dbManager.updateAgentSession(sessionId, 'FAILED');

      this.emit('error', { sessionId, agentId, error: err.message });
      if (broadcastFn) {
        broadcastFn({
          type: 'AGENT_STATUS',
          sessionId,
          agentId,
          status: 'FAILED',
          error: err.message
        });
      }
    };

    const sessionInfo = await adapter.startSession({
      sessionId,
      workspacePath,
      prompt,
      onOutput,
      onExit,
      onError
    });

    this.activeSessions.set(sessionId, {
      adapter,
      pid: sessionInfo.pid,
      workspacePath,
      prompt,
      startTime: Date.now()
    });

    return {
      sessionId,
      agentId,
      pid: sessionInfo.pid,
      workspacePath,
      status: 'RUNNING'
    };
  }

  sendInput(sessionId, input) {
    const session = this.activeSessions.get(sessionId);
    if (!session) {
      throw new Error(`Active session not found: ${sessionId}`);
    }
    return session.adapter.sendInput(sessionId, input);
  }

  cancelSession(sessionId) {
    const session = this.activeSessions.get(sessionId);
    if (!session) {
      // Check if it's in DB as running and update it
      dbManager.updateAgentSession(sessionId, 'CANCELLED');
      return true;
    }
    const cancelled = session.adapter.cancelSession(sessionId);
    this.activeSessions.delete(sessionId);
    dbManager.updateAgentSession(sessionId, 'CANCELLED');
    this.emit('cancelled', { sessionId });
    return cancelled;
  }

  listSessions(limit = 30) {
    return dbManager.listAgentSessions(limit);
  }

  getActiveSessions() {
    return Array.from(this.activeSessions.entries()).map(([sessionId, s]) => ({
      sessionId,
      agentId: s.adapter.id,
      pid: s.pid,
      workspacePath: s.workspacePath,
      prompt: s.prompt,
      startTime: s.startTime
    }));
  }
}

let _agentManagerInstance = null;
export const agentManager = new Proxy({}, {
  get(target, prop) {
    if (!_agentManagerInstance) {
      _agentManagerInstance = new AgentManager();
    }
    const val = _agentManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_agentManagerInstance) : val;
  }
});
