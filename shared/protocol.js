/**
 * Regnant — Shared Protocol Constants and Schemas
 * Version: 1.0.0
 */

export const PROTOCOL_VERSION = '1.0.0';
export const SERVICE_TYPE = '_regnant._tcp';
export const DEFAULT_PORT = 8765;
export const PAIRING_CODE_TTL_MS = 5 * 60 * 1000; // 5 minutes
export const MAX_PAIRING_ATTEMPTS = 3;
export const HEARTBEAT_INTERVAL_MS = 15000; // 15 seconds
export const HEARTBEAT_TIMEOUT_MS = 45000;  // 45 seconds

export const ACTIONS = {
  LAUNCH_APP: 'launch_app',
  OPEN_PATH: 'open_path',
};

export const TARGETS = {
  TERMINAL: 'terminal',
  BRAVE: 'brave',
  BROWSER: 'browser',
  FILE_MANAGER: 'file_manager',
  CALCULATOR: 'calculator',
  DOWNLOADS: 'downloads',
  HOME: 'home',
};

export const TASK_STATUS = {
  RECEIVED: 'RECEIVED',
  VALIDATING: 'VALIDATING',
  RUNNING: 'RUNNING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
  REJECTED: 'REJECTED',
};

export const WS_EVENTS = {
  PING: 'PING',
  PONG: 'PONG',
  AUTH: 'AUTH',
  AUTH_SUCCESS: 'AUTH_SUCCESS',
  AUTH_FAILED: 'AUTH_FAILED',
  COMMAND: 'COMMAND',
  COMMAND_STATUS: 'COMMAND_STATUS',
  STATE_UPDATE: 'STATE_UPDATE',
};
