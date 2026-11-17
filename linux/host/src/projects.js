import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { dbManager } from './db.js';
import { actionExecutor } from './executor.js';

export class ProjectManager {
  constructor() {
    this.installedIdes = this.detectInstalledIdes();
  }

  detectInstalledIdes() {
    const home = os.homedir();
    const ides = {};

    const candidates = [
      { id: 'antigravity', name: 'Antigravity IDE', paths: [path.join(home, '.local/bin/antigravity'), '/usr/bin/antigravity'] },
      { id: 'code', name: 'Visual Studio Code', paths: ['/usr/bin/code', '/usr/local/bin/code'] },
      { id: 'cursor', name: 'Cursor', paths: ['/usr/bin/cursor', path.join(home, '.local/bin/cursor')] },
      { id: 'zed', name: 'Zed', paths: ['/usr/bin/zed', path.join(home, '.local/bin/zed')] },
      { id: 'terminal', name: 'Terminal (Foot)', paths: ['/usr/bin/foot', '/usr/bin/alacritty', '/usr/bin/kitty'] },
    ];

    for (const c of candidates) {
      for (const p of c.paths) {
        if (fs.existsSync(p)) {
          ides[c.id] = { id: c.id, name: c.name, exec: p };
          break;
        }
      }
    }

    return ides;
  }

  getSupportedIdes() {
    return Object.values(this.installedIdes);
  }

  listProjects() {
    const raw = dbManager.listProjects();
    return raw.map(p => ({
      ...p,
      exists: fs.existsSync(p.path),
    }));
  }

  registerProject(name, projPath, ide = 'auto') {
    const resolvedPath = path.resolve(projPath.replace(/^~(?=$|\/|\\)/, os.homedir()));
    if (!fs.existsSync(resolvedPath)) {
      throw new Error(`Project directory does not exist: ${resolvedPath}`);
    }
    const stat = fs.statSync(resolvedPath);
    if (!stat.isDirectory()) {
      throw new Error(`Project path must be a directory: ${resolvedPath}`);
    }

    const id = `proj-${path.basename(resolvedPath)}-${Date.now().toString(36)}`;
    const finalName = name || path.basename(resolvedPath);
    dbManager.registerProject(id, finalName, resolvedPath, ide);

    return { id, name: finalName, path: resolvedPath, ide };
  }

  deleteProject(id) {
    dbManager.deleteProject(id);
    return { success: true };
  }

  launchProject(id, requestedIde = null) {
    const project = dbManager.getProject(id);
    if (!project) {
      throw new Error(`Project not found with ID "${id}"`);
    }

    if (!fs.existsSync(project.path)) {
      throw new Error(`Project directory does not exist on disk: ${project.path}`);
    }

    const env = actionExecutor.resolveSessionEnvironment();
    let targetIde = requestedIde || project.ide || 'auto';

    if (targetIde === 'auto') {
      // Pick best available: Antigravity -> Code -> Terminal
      if (this.installedIdes['antigravity']) targetIde = 'antigravity';
      else if (this.installedIdes['code']) targetIde = 'code';
      else targetIde = 'terminal';
    }

    const ideMeta = this.installedIdes[targetIde];
    if (!ideMeta || !fs.existsSync(ideMeta.exec)) {
      throw new Error(`Selected IDE "${targetIde}" is not available on this system.`);
    }

    let spawnArgs = [];
    if (targetIde === 'terminal') {
      // For terminal, launch into the directory
      spawnArgs = ['-D', project.path];
    } else {
      spawnArgs = [project.path];
    }

    const child = spawn(ideMeta.exec, spawnArgs, {
      detached: true,
      stdio: 'ignore',
      env,
      cwd: project.path,
    });
    child.unref();

    return {
      success: true,
      message: `Launched ${project.name} in ${ideMeta.name}`,
      project: project.name,
      ide: ideMeta.name,
      pid: child.pid,
    };
  }
}

export const projectManager = new ProjectManager();
