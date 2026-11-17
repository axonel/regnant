import { ACTIONS, TARGETS } from '../../../shared/protocol.js';

export class CommandParser {
  /**
   * Deterministically parses a user natural-language phrase into a typed action.
   * Rejects any unrecognized or arbitrary shell patterns.
   *
   * @param {string} text - User command (voice transcript or typed input)
   * @returns {{ valid: boolean, action?: string, target?: string, payload?: any, reason?: string }}
   */
  parse(text) {
    if (!text || typeof text !== 'string') {
      return { valid: false, reason: 'Empty command received' };
    }

    // Normalize: lowercase, remove punctuation except spaces, collapse multiple spaces
    const clean = text
      .toLowerCase()
      .trim()
      .replace(/[.,/#!$%^&*;:{}=\-_`~()?]/g, '')
      .replace(/\s+/g, ' ');

    if (!clean) {
      return { valid: false, reason: 'Command contains no recognizable words' };
    }

    // Explicit blocklist check for malicious or shell-like attempts (matched as whole words)
    const tokens = clean.split(' ');
    const forbiddenKeywords = new Set([
      'rm', 'sudo', 'chmod', 'chown', 'curl', 'wget', 'bash', 'sh', 'zsh', 'exec',
      'eval', 'kill', 'pkill', 'reboot', 'shutdown', 'systemctl', 'cat', 'grep',
      'delete', 'destroy', 'truncate', 'format', 'mkfs', 'drop', 'dd'
    ]);
    for (const token of tokens) {
      if (forbiddenKeywords.has(token)) {
        return {
          valid: false,
          reason: 'Command rejected: arbitrary shell and destructive commands are not permitted.'
        };
      }
    }

    // Web search pattern: "search <query>", "google <query>", "look up <query>"
    if (clean.startsWith('search ') || clean.startsWith('google ') || clean.startsWith('look up ')) {
      const query = clean.replace(/^(search|google|look up)\s+/, '').trim();
      if (query) {
        return { valid: true, action: ACTIONS.WEB_SEARCH, target: query };
      }
    }

    // Projects launcher patterns
    if (
      clean === 'projects' ||
      clean === 'project' ||
      clean === 'open projects' ||
      clean === 'launch projects' ||
      clean === 'show projects' ||
      clean === 'workspaces' ||
      clean === 'open workspaces' ||
      clean === 'my projects'
    ) {
      return { valid: true, action: ACTIONS.PROJECT_LIST, target: TARGETS.PROJECTS };
    }

    // Terminal patterns
    if (
      clean === 'terminal' ||
      clean === 'foot' ||
      clean === 'open terminal' ||
      clean === 'launch terminal' ||
      clean === 'start terminal' ||
      clean === 'open my terminal' ||
      clean === 'run terminal' ||
      clean === 'open foot' ||
      clean === 'start foot'
    ) {
      return { valid: true, action: ACTIONS.LAUNCH_APP, target: TARGETS.TERMINAL };
    }

    // Brave / Browser patterns
    if (
      clean === 'brave' ||
      clean === 'open brave' ||
      clean === 'launch brave' ||
      clean === 'start brave' ||
      clean === 'open brave browser' ||
      clean === 'browser' ||
      clean === 'open browser' ||
      clean === 'launch browser' ||
      clean === 'open my browser' ||
      clean === 'start browser' ||
      clean === 'open internet' ||
      clean === 'web browser'
    ) {
      return { valid: true, action: ACTIONS.LAUNCH_APP, target: TARGETS.BRAVE };
    }

    // File manager / Files patterns
    if (
      clean === 'files' ||
      clean === 'file manager' ||
      clean === 'open files' ||
      clean === 'open file manager' ||
      clean === 'launch files' ||
      clean === 'launch file manager' ||
      clean === 'start file manager' ||
      clean === 'open my files' ||
      clean === 'nautilus' ||
      clean === 'open nautilus'
    ) {
      return { valid: true, action: ACTIONS.LAUNCH_APP, target: TARGETS.FILE_MANAGER };
    }

    // Calculator patterns
    if (
      clean === 'calculator' ||
      clean === 'calc' ||
      clean === 'omacalc' ||
      clean === 'open calculator' ||
      clean === 'launch calculator' ||
      clean === 'start calculator' ||
      clean === 'open calc' ||
      clean === 'open omacalc'
    ) {
      return { valid: true, action: ACTIONS.LAUNCH_APP, target: TARGETS.CALCULATOR };
    }

    // Downloads folder patterns
    if (
      clean === 'downloads' ||
      clean === 'downloads folder' ||
      clean === 'open downloads' ||
      clean === 'open downloads folder' ||
      clean === 'open my downloads' ||
      clean === 'show downloads' ||
      clean === 'show my downloads' ||
      clean === 'show downloads folder'
    ) {
      return { valid: true, action: ACTIONS.OPEN_PATH, target: TARGETS.DOWNLOADS };
    }

    // Home directory patterns
    if (
      clean === 'home' ||
      clean === 'home folder' ||
      clean === 'open home' ||
      clean === 'open home folder' ||
      clean === 'open my home' ||
      clean === 'show home' ||
      clean === 'show home folder' ||
      clean === 'open user folder'
    ) {
      return { valid: true, action: ACTIONS.OPEN_PATH, target: TARGETS.HOME };
    }

    // Status / Metrics patterns
    if (
      clean === 'status' ||
      clean === 'laptop status' ||
      clean === 'system status' ||
      clean === 'battery' ||
      clean === 'battery status' ||
      clean === 'cpu' ||
      clean === 'metrics' ||
      clean === 'stats'
    ) {
      return { valid: true, action: ACTIONS.GET_METRICS, target: 'host' };
    }

    // Clipboard patterns
    if (
      clean === 'clipboard' ||
      clean === 'read clipboard' ||
      clean === 'copy clipboard' ||
      clean === 'get clipboard' ||
      clean === 'laptop clipboard'
    ) {
      return { valid: true, action: ACTIONS.CLIPBOARD_READ, target: 'system' };
    }

    // Lock session patterns
    if (
      clean === 'lock' ||
      clean === 'lock screen' ||
      clean === 'lock laptop' ||
      clean === 'lock workstation' ||
      clean === 'lock session'
    ) {
      return { valid: true, action: ACTIONS.POWER_LOCK, target: 'session' };
    }

    // Suspend patterns
    if (
      clean === 'suspend' ||
      clean === 'sleep' ||
      clean === 'suspend laptop' ||
      clean === 'suspend workstation' ||
      clean === 'put laptop to sleep'
    ) {
      return { valid: true, action: ACTIONS.POWER_SUSPEND, target: 'system' };
    }

    // Running apps patterns
    if (
      clean === 'running apps' ||
      clean === 'open apps' ||
      clean === 'open windows' ||
      clean === 'windows' ||
      clean === 'tasks' ||
      clean === 'show running apps'
    ) {
      return { valid: true, action: ACTIONS.APPS_LIST, target: 'all' };
    }

    // Routines patterns
    if (
      clean === 'start work' ||
      clean === 'start work session' ||
      clean === 'work session' ||
      clean === 'begin work'
    ) {
      return { valid: true, action: ACTIONS.ROUTINE_EXECUTE, target: 'routine-start-work' };
    }

    return {
      valid: false,
      reason: `Unsupported command: "${text}". Supported: open terminal, open brave, open files, open projects, open downloads, laptop status, lock screen, running apps, start work.`
    };
  }
}

export const commandParser = new CommandParser();
