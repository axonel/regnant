# REGNANT — Advanced Project Launcher Specification

## 1. Overview

The **Regnant Project Launcher** enables seamless discovery, inspection, and launching of development workspaces on a Linux workstation directly from an authenticated Android smartphone.

It replaces the previous calculator shortcut on the mobile control plane with a developer-first workspace navigation hub.

---

## 2. Architecture & Design Principles

```
+-------------------------------------------------------------+
|                     REGNANT ANDROID                         |
|  - ProjectsScreen (Compose LazyColumn)                      |
|  - Workspace status badges (Exists / Missing)               |
|  - IDE selection buttons (VS Code, Antigravity, Terminal)   |
|  - Register Workspace modal dialog                          |
+-------------------------------------------------------------+
                                │
               HTTPS POST /api/v1/projects/launch
                                │
+-------------------------------------------------------------+
|                      REGNANT HOST                           |
|  - Permission Check: CAPABILITIES.PROJECTS                   |
|  - ProjectManager: validates workspace existence & security  |
|  - Session Environment Resolver: Wayland & XDG variables    |
|  - Strict Non-Shell Process Spawner (child_process.spawn)   |
|  - Native Omarchy Desktop Notification (notify-send)        |
+-------------------------------------------------------------+
                                │
             IPC Launch into Graphical Session
                                │
+-------------------------------------------------------------+
|                     LINUX WORKSTATION                       |
|  - VS Code: /usr/bin/code <workspace_path>                  |
|  - Antigravity: ~/.local/bin/antigravity <workspace_path>   |
|  - Foot Terminal: /usr/bin/foot -D <workspace_path>         |
+-------------------------------------------------------------+
```

---

## 3. Workspaces Discovery & Seeding

On startup, `ProjectManager` inspects approved directories (such as `~/Projects`) for valid Git repositories, package configurations (`package.json`, `Cargo.toml`, `go.mod`, `build.gradle`), and project directories:

```javascript
export class ProjectManager {
  constructor() {
    this.installedIdes = this.detectInstalledIdes();
    this.seedDiscoveredProjects();
  }
}
```

Discovered projects are persisted in SQLite within the `projects` table:

| Column | Type | Description |
|---|---|---|
| `id` | `TEXT PRIMARY KEY` | Unique workspace identifier (e.g. `proj-regnant`) |
| `name` | `TEXT NOT NULL` | Display name of the project |
| `path` | `TEXT NOT NULL` | Absolute filesystem path |
| `ide` | `TEXT DEFAULT 'auto'` | Preferred editor (`auto`, `code`, `antigravity`, `terminal`) |
| `added_at` | `INTEGER NOT NULL` | Epoch timestamp when registered |

---

## 4. IDE Detection Matrix

The host dynamically probes the Linux workstation for installed editors and terminals without assuming hardcoded paths:

1. **Antigravity AI Agentic IDE**: `~/.local/bin/antigravity`
2. **Visual Studio Code**: `/usr/bin/code`
3. **Foot Terminal (Default Omarchy Wayland terminal)**: `/usr/bin/foot`

When `ide: 'auto'` is selected, the resolution strategy prefers:
`Antigravity` $\rightarrow$ `VS Code` $\rightarrow$ `Foot Terminal`.

---

## 5. Session Environment Propagation

When a process is spawned from a background service or daemon, it frequently lacks the necessary graphical and desktop environment variables to attach to the user's active Wayland compositor.

Regnant resolves this by capturing the user's live session environment:

```javascript
resolveSessionEnvironment() {
  const env = { ...process.env };
  env.WAYLAND_DISPLAY = env.WAYLAND_DISPLAY || 'wayland-1';
  env.XDG_CURRENT_DESKTOP = env.XDG_CURRENT_DESKTOP || 'Hyprland';
  env.XDG_SESSION_TYPE = env.XDG_SESSION_TYPE || 'wayland';
  env.XDG_RUNTIME_DIR = env.XDG_RUNTIME_DIR || `/run/user/${process.getuid()}`;
  return env;
}
```

When launching a terminal into a workspace, Foot is invoked with the `-D` working directory flag:
```bash
/usr/bin/foot -D /home/roonakyadav/Projects/MAD
```
This spawns the shell with its current working directory already set to the targeted workspace, detaching cleanly (`detached: true`, `stdio: 'ignore'`, `child.unref()`).

---

## 6. Security Guarantees

1. **Strict Non-Shell Invocation**: All processes are spawned with `shell: false`. Arguments are passed as an explicit array of strings to prevent shell injection, command chaining (`&&`, `;`), or redirection.
2. **Granular Capability Check**: Every project launch request requires `CAPABILITIES.PROJECTS` to be allowed for the requesting device token in `db.isPermissionAllowed()`.
3. **Desktop Feedback**: Launching a workspace triggers an immediate desktop notification via `notify-send`, ensuring the workstation user has complete visibility over remote actions.
