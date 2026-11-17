/**
 * Regnant — Shared Protocol Constants and Schemas
 * Version: 1.1.0
 */

export const PROTOCOL_VERSION = '1.1.0';
export const SERVICE_TYPE = '_regnant._tcp';
export const DEFAULT_PORT = 53317; // Firewall-friendly port pre-authorized in UFW rules
export const PAIRING_CODE_TTL_MS = 5 * 60 * 1000; // 5 minutes
export const MAX_PAIRING_ATTEMPTS = 3;
export const HEARTBEAT_INTERVAL_MS = 15000; // 15 seconds
export const HEARTBEAT_TIMEOUT_MS = 45000;  // 45 seconds

export const ACTIONS = {
  LAUNCH_APP: 'launch_app',
  OPEN_PATH: 'open_path',
  PROJECT_LAUNCH: 'project_launch',
  PROJECT_LIST: 'project_list',
  PROJECT_REGISTER: 'project_register',
  GET_METRICS: 'get_metrics',
  FILES_LIST: 'files_list',
  FILES_READ: 'files_read',
  TRANSFER_INIT: 'transfer_init',
  TRANSFER_CHUNK: 'transfer_chunk',
  TRANSFER_COMPLETE: 'transfer_complete',
  TRANSFER_DOWNLOAD: 'transfer_download',
  OPEN_URL: 'open_url',
  WEB_SEARCH: 'web_search',
  CLIPBOARD_READ: 'clipboard_read',
  CLIPBOARD_WRITE: 'clipboard_write',
  APPS_LIST: 'apps_list',
  APPS_CLOSE: 'apps_close',
  POWER_LOCK: 'power_lock',
  POWER_SUSPEND: 'power_suspend',
  POWER_REBOOT: 'power_reboot',
  POWER_SHUTDOWN: 'power_shutdown',
  ROUTINE_LIST: 'routine_list',
  ROUTINE_EXECUTE: 'routine_execute',
  ROUTINE_SAVE: 'routine_save',
  PERMISSIONS_GET: 'permissions_get',
  PERMISSIONS_UPDATE: 'permissions_update',
  APPROVAL_REQUEST: 'approval_request',
  APPROVAL_DECIDE: 'approval_decide',
};

export const TARGETS = {
  TERMINAL: 'terminal',
  BRAVE: 'brave',
  BROWSER: 'browser',
  FILE_MANAGER: 'file_manager',
  CALCULATOR: 'calculator',
  PROJECTS: 'projects',
  DOWNLOADS: 'downloads',
  HOME: 'home',
};

export const CAPABILITIES = {
  LAUNCH_APPS: 'launch_apps',
  OPEN_PATHS: 'open_paths',
  PROJECTS: 'projects',
  VIEW_METRICS: 'view_metrics',
  BROWSE_FILES: 'browse_files',
  TRANSFER_FILES: 'transfer_files',
  OPEN_WEB: 'open_web',
  CLIPBOARD: 'clipboard',
  POWER_CONTROLS: 'power_controls',
  RUNNING_APPS: 'running_apps',
  ROUTINES: 'routines',
};

export const TASK_STATUS = {
  RECEIVED: 'RECEIVED',
  VALIDATING: 'VALIDATING',
  RUNNING: 'RUNNING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
  REJECTED: 'REJECTED',
  PENDING_APPROVAL: 'PENDING_APPROVAL',
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
  METRICS_UPDATE: 'METRICS_UPDATE',
  APPROVAL_REQUIRED: 'APPROVAL_REQUIRED',
  TRANSFER_PROGRESS: 'TRANSFER_PROGRESS',
};
