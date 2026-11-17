import { ACTIONS, TARGETS } from '../../../shared/protocol.js';

export class CommandParser {
  /**
   * Deterministically parses a user natural-language phrase into a typed action.
   * Rejects any unrecognized or arbitrary shell patterns.
   *
   * @param {string} text - User command (voice transcript or typed input)
   * @returns {{ valid: boolean, action?: string, target?: string, reason?: string }}
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

    return {
      valid: false,
      reason: `Unsupported command: "${text}". Supported actions: open terminal, open brave, open files, open calculator, open downloads, open home folder.`
    };
  }
}

export const commandParser = new CommandParser();
