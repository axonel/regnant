/**
 * Regnant — Shared Protocol Constants and Schemas
 * Version: 1.2.0
 */

export const PROTOCOL_VERSION = '1.2.1';
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

  // v1.2: AI Agent Control Centre
  AGENT_LIST: 'agent_list',
  AGENT_START: 'agent_start',
  AGENT_CANCEL: 'agent_cancel',
  AGENT_SESSIONS: 'agent_sessions',

  // v1.2: Persistent Remote Terminal
  TERM_CREATE: 'term_create',
  TERM_LIST: 'term_list',
  TERM_CLOSE: 'term_close',

  // v1.2: Git & GitHub Cockpit
  GIT_STATUS: 'git_status',
  GIT_BRANCHES: 'git_branches',
  GIT_COMMITS: 'git_commits',
  GIT_DIFF: 'git_diff',
  GITHUB_PRS: 'github_prs',
  GITHUB_PR_VIEW: 'github_pr_view',
  GITHUB_RUNS: 'github_runs',
  GITHUB_RUN_LOG: 'github_run_log',

  // v1.2: Window & Workspace Controller (Hyprland)
  HYPR_WINDOWS: 'hypr_windows',
  HYPR_WORKSPACES: 'hypr_workspaces',
  HYPR_DISPATCH: 'hypr_dispatch',

  // v1.2: Browser Tab Controller
  BROWSER_TABS: 'browser_tabs',
  BROWSER_TAB_ACTIVATE: 'browser_tab_activate',
  BROWSER_TAB_CLOSE: 'browser_tab_close',
  BROWSER_TAB_OPEN: 'browser_tab_open',
  BROWSER_PAGE_INSPECT: 'browser_page_inspect',

  // v1.2: Audio & Media Controller
  AUDIO_GET: 'audio_get',
  AUDIO_SET_VOLUME: 'audio_set_volume',
  AUDIO_TOGGLE_MUTE: 'audio_toggle_mute',
  MEDIA_GET: 'media_get',
  MEDIA_CONTROL: 'media_control',

  // v1.2: Download Manager
  DOWNLOADS_LIST: 'downloads_list',
  DOWNLOADS_ACTION: 'downloads_action',

  // v1.2: Universal Search
  SEARCH_QUERY: 'search_query',
  SEARCH_REINDEX: 'search_reindex',

  // v1.2: Automations & Scheduler
  AUTOMATIONS_LIST: 'automations_list',
  AUTOMATIONS_SAVE: 'automations_save',
  AUTOMATIONS_DELETE: 'automations_delete',
  SCHEDULES_LIST: 'schedules_list',
  SCHEDULES_SAVE: 'schedules_save',
  SCHEDULES_DELETE: 'schedules_delete',

  // v1.2: Wake-on-LAN
  WOL_STATUS: 'wol_status',
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

  // v1.2 Capabilities
  AI_AGENTS: 'ai_agents',
  TERMINAL_ACCESS: 'terminal_access',
  GIT_GITHUB: 'git_github',
  WINDOW_MANAGEMENT: 'window_management',
  BROWSER_CONTROL: 'browser_control',
  DESKTOP_PEEK: 'desktop_peek',
  AUDIO_MEDIA: 'audio_media',
  DOWNLOAD_MANAGER: 'download_manager',
  SEARCH: 'search',
  AUTOMATIONS: 'automations',
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

  // v1.2 WebSocket Events
  AGENT_OUTPUT: 'AGENT_OUTPUT',
  AGENT_STATUS: 'AGENT_STATUS',
  AGENT_INPUT: 'AGENT_INPUT',
  TERM_DATA: 'TERM_DATA',
  TERM_RESIZE: 'TERM_RESIZE',
  TERM_EXIT: 'TERM_EXIT',
  MEDIA_UPDATE: 'MEDIA_UPDATE',
  AUTOMATION_TRIGGERED: 'AUTOMATION_TRIGGERED',

  // v1.2.1 Workspace & Lock Events
  WORKSPACE_STATE: 'WORKSPACE_STATE',
  LOCK_STATE: 'LOCK_STATE',
};
