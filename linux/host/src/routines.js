import { dbManager } from './db.js';
import { actionExecutor } from './executor.js';
import { projectManager } from './projects.js';
import { webManager } from './web.js';
import { powerManager } from './power.js';
import { ACTIONS, TARGETS } from '../../../shared/protocol.js';

export class RoutineManager {
  constructor() {
    this.seedDefaultRoutines();
  }

  seedDefaultRoutines() {
    const existing = dbManager.listRoutines();
    if (existing.length === 0) {
      dbManager.saveRoutine(
        'routine-start-work',
        'Start Work Session',
        'Launches Terminal, Brave browser, and opens current dev workspace',
        [
          { action: ACTIONS.LAUNCH_APP, target: TARGETS.TERMINAL, label: 'Launch Terminal' },
          { action: ACTIONS.LAUNCH_APP, target: TARGETS.BRAVE, label: 'Launch Brave Browser' },
          { action: ACTIONS.PROJECT_LAUNCH, target: 'proj-regnant', label: 'Open Regnant Workspace', optional: true },
        ]
      );

      dbManager.saveRoutine(
        'routine-quick-break',
        'Quick Break',
        'Copies current clipboard to memory and locks the workstation screen',
        [
          { action: ACTIONS.POWER_LOCK, target: 'session', label: 'Lock Workstation' },
        ]
      );
    }
  }

  listRoutines() {
    return dbManager.listRoutines();
  }

  saveRoutine(id, name, description, steps) {
    if (!id || !name || !Array.isArray(steps)) {
      throw new Error('Routine requires id, name, and an array of steps');
    }
    dbManager.saveRoutine(id, name, description, steps);
    return { id, name, description, steps };
  }

  deleteRoutine(id) {
    dbManager.deleteRoutine(id);
    return { success: true };
  }

  async executeRoutine(routineId) {
    const routines = dbManager.listRoutines();
    const routine = routines.find(r => r.id === routineId);
    if (!routine) {
      throw new Error(`Routine "${routineId}" not found`);
    }

    const stepResults = [];
    let allSuccessful = true;

    for (let i = 0; i < routine.steps.length; i++) {
      const step = routine.steps[i];
      try {
        let res = null;

        if (step.action === ACTIONS.LAUNCH_APP || step.action === ACTIONS.OPEN_PATH) {
          res = await actionExecutor.execute(step.action, step.target);
        } else if (step.action === ACTIONS.PROJECT_LAUNCH) {
          try {
            res = projectManager.launchProject(step.target);
          } catch (pe) {
            if (!step.optional) throw pe;
            res = { success: true, message: `Skipped optional project: ${pe.message}` };
          }
        } else if (step.action === ACTIONS.OPEN_URL) {
          res = webManager.openUrl(step.target);
        } else if (step.action === ACTIONS.POWER_LOCK) {
          res = await powerManager.lockSession();
        } else {
          res = { success: false, error: `Unknown routine action: ${step.action}` };
        }

        stepResults.push({
          step: i + 1,
          label: step.label || `${step.action} ${step.target}`,
          success: res.success,
          message: res.message || res.error,
        });

        if (!res.success && !step.optional) {
          allSuccessful = false;
          break;
        }
      } catch (err) {
        stepResults.push({
          step: i + 1,
          label: step.label || `${step.action} ${step.target}`,
          success: false,
          error: err.message,
        });

        if (!step.optional) {
          allSuccessful = false;
          break;
        }
      }
    }

    return {
      success: allSuccessful,
      routineId,
      routineName: routine.name,
      stepsCompleted: stepResults.length,
      totalSteps: routine.steps.length,
      stepResults,
    };
  }
}

export const routineManager = new RoutineManager();
