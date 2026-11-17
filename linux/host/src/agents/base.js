/**
 * Regnant — Base Agent Adapter
 */

export class AgentAdapter {
  constructor(id, name) {
    this.id = id;
    this.name = name;
  }

  async detect() {
    throw new Error('detect() must be implemented');
  }

  async startSession({ sessionId, workspacePath, prompt, onOutput, onExit, onError }) {
    throw new Error('startSession() must be implemented');
  }

  sendInput(sessionId, input) {
    throw new Error('sendInput() must be implemented');
  }

  cancelSession(sessionId) {
    throw new Error('cancelSession() must be implemented');
  }
}
