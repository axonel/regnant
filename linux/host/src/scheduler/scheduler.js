import crypto from 'node:crypto';
import { dbManager } from '../db.js';
import { routineManager } from '../routines.js';
import { audioMediaManager } from '../audio/manager.js';
import { actionExecutor } from '../executor.js';

export class Scheduler {
  constructor() {
    this.timer = null;
    this.intervalMs = 30000; // 30 second heartbeat
  }

  start() {
    if (this.timer) return;
    this.timer = setInterval(() => this.tick(), this.intervalMs);
  }

  stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  saveSchedule({ id = null, name, scheduleType = 'INTERVAL', schedule_type, scheduleExpr, schedule_expr, actionType, action_type, actionPayload = {}, action_payload, enabled = 1 }) {
    const schedId = id || `sched-${Date.now()}-${crypto.randomBytes(3).toString('hex')}`;
    const sType = schedule_type || scheduleType || 'INTERVAL';
    const sExpr = schedule_expr !== undefined ? schedule_expr : scheduleExpr;
    const aType = action_type || actionType;
    const aPayload = actionPayload || (typeof action_payload === 'string' ? JSON.parse(action_payload || '{}') : action_payload) || {};
    const now = Date.now();
    let nextRunAt = now;

    if (sType === 'INTERVAL') {
      const seconds = Math.max(10, parseInt(sExpr, 10) || 60);
      nextRunAt = now + (seconds * 1000);
    } else if (sType === 'ONCE') {
      nextRunAt = parseInt(sExpr, 10) || now;
    }

    dbManager.saveSchedule(schedId, name, sType, String(sExpr), aType, aPayload, nextRunAt, enabled);
    return { id: schedId, name, schedule_type: sType, schedule_expr: String(sExpr), action_type: aType, action_payload: JSON.stringify(aPayload), next_run_at: nextRunAt, enabled };
  }

  listSchedules() {
    return dbManager.listSchedules();
  }

  deleteSchedule(id) {
    dbManager.deleteSchedule(id);
    return true;
  }

  async tick(broadcastFn = null) {
    const now = Date.now();
    const all = dbManager.listSchedules();
    const due = all.filter(s => s.enabled && s.next_run_at <= now);

    for (const item of due) {
      await this.runSchedule(item, broadcastFn);
    }
  }

  async runSchedule(item, broadcastFn) {
    const now = Date.now();
    try {
      const { action_type, actionPayload } = item;
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
        default:
          break;
      }

      // Calculate next run
      let nextRun = now;
      let stillEnabled = item.enabled;

      if (item.schedule_type === 'INTERVAL') {
        const seconds = Math.max(10, parseInt(item.schedule_expr, 10) || 60);
        nextRun = now + (seconds * 1000);
      } else if (item.schedule_type === 'ONCE') {
        stillEnabled = 0; // disable after single run
        nextRun = item.next_run_at;
      }

      dbManager.updateScheduleRun(item.id, now, nextRun);
      if (item.schedule_type === 'ONCE') {
        dbManager.saveSchedule(
          item.id,
          item.name,
          item.schedule_type,
          item.schedule_expr,
          item.action_type,
          item.actionPayload,
          nextRun,
          0
        );
      }
    } catch {
      // error in scheduled task
    }
  }
}

let _schedulerInstance = null;
export const scheduler = new Proxy({}, {
  get(target, prop) {
    if (!_schedulerInstance) {
      _schedulerInstance = new Scheduler();
    }
    const val = _schedulerInstance[prop];
    return typeof val === 'function' ? val.bind(_schedulerInstance) : val;
  }
});
