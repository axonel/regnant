import { EventEmitter } from 'node:events';
import crypto from 'node:crypto';
import { dbManager } from '../db.js';
import { routineManager } from '../routines.js';
import { audioMediaManager } from '../audio/manager.js';
import { actionExecutor } from '../executor.js';

export class AutomationEngine extends EventEmitter {
  constructor() {
    super();
    this.lastFired = new Map(); // ruleId -> timestamp
    this.cooldownMs = 5000;
  }

  saveRule({ id = null, name, triggerType, trigger_type, condition = {}, actionType, action_type, actionPayload = {}, action_payload, enabled = 1 }) {
    const tType = triggerType || trigger_type;
    const aType = actionType || action_type;
    const aPayload = actionPayload || (typeof action_payload === 'string' ? JSON.parse(action_payload || '{}') : action_payload) || {};
    const ruleId = id || `auto-${Date.now()}-${crypto.randomBytes(3).toString('hex')}`;
    dbManager.saveAutomation(ruleId, name, tType, condition, aType, aPayload, enabled);
    return { id: ruleId, name, trigger_type: tType, condition, action_type: aType, action_payload: JSON.stringify(aPayload), enabled };
  }

  listRules() {
    return dbManager.listAutomations();
  }

  deleteRule(id) {
    dbManager.deleteAutomation(id);
    return true;
  }

  async triggerEvent(eventType, eventData = {}, broadcastFn = null) {
    const rules = dbManager.listAutomations();
    const matchingRules = rules.filter(r => r.enabled && r.trigger_type === eventType);

    for (const rule of matchingRules) {
      const now = Date.now();
      const last = this.lastFired.get(rule.id) || 0;
      if (now - last < this.cooldownMs) {
        continue; // Cooldown deduplication
      }

      this.lastFired.set(rule.id, now);
      await this.executeAction(rule, eventData, broadcastFn);
    }
  }

  async executeAction(rule, eventData, broadcastFn) {
    try {
      const { action_type, actionPayload } = rule;

      switch (action_type) {
        case 'ROUTINE': {
          if (actionPayload.routineId) {
            await routineManager.executeRoutine(actionPayload.routineId);
          }
          break;
        }

        case 'MUTE_AUDIO': {
          audioMediaManager.toggleMute();
          break;
        }

        case 'LOCK_SCREEN': {
          actionExecutor.executeCommand('loginctl', ['lock-session']);
          break;
        }

        case 'LAUNCH_PROJECT': {
          if (actionPayload.projectId) {
            const proj = dbManager.getProject(actionPayload.projectId);
            if (proj) {
              actionExecutor.executeCommand(proj.ide === 'auto' ? 'code' : proj.ide, [proj.path]);
            }
          }
          break;
        }

        default:
          break;
      }

      this.emit('action_executed', { ruleId: rule.id, ruleName: rule.name, eventData });
      if (broadcastFn) {
        broadcastFn({
          type: 'AUTOMATION_TRIGGERED',
          ruleId: rule.id,
          ruleName: rule.name,
          actionType: rule.action_type,
          timestamp: Date.now()
        });
      }
    } catch (err) {
      this.emit('action_error', { ruleId: rule.id, error: err.message });
    }
  }
}

let _automationEngineInstance = null;
export const automationEngine = new Proxy({}, {
  get(target, prop) {
    if (!_automationEngineInstance) {
      _automationEngineInstance = new AutomationEngine();
    }
    const val = _automationEngineInstance[prop];
    return typeof val === 'function' ? val.bind(_automationEngineInstance) : val;
  }
});
