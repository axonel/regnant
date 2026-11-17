import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

export class GitManager {
  isRepo(targetPath) {
    if (!targetPath || !fs.existsSync(targetPath)) return false;
    try {
      const res = execFileSync('git', ['rev-parse', '--is-inside-work-tree'], {
        cwd: targetPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'ignore'],
        timeout: 3000
      }).trim();
      return res === 'true';
    } catch {
      return false;
    }
  }

  getStatus(repoPath) {
    if (!this.isRepo(repoPath)) {
      throw new Error(`Directory is not a valid git repository: ${repoPath}`);
    }

    const out = execFileSync('git', ['status', '--short', '-b'], {
      cwd: repoPath,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 5000
    });

    const lines = out.split('\n').filter(l => l.trim().length > 0);
    const branchLine = lines[0] || '';
    let currentBranch = 'HEAD (detached)';
    let tracking = null;
    let ahead = 0;
    let behind = 0;

    if (branchLine.startsWith('## ')) {
      const header = branchLine.slice(3);
      const parts = header.split('...');
      currentBranch = parts[0];
      if (parts[1]) {
        const trackParts = parts[1].split(' ');
        tracking = trackParts[0];
        const matchAhead = parts[1].match(/ahead (\d+)/);
        const matchBehind = parts[1].match(/behind (\d+)/);
        if (matchAhead) ahead = parseInt(matchAhead[1], 10);
        if (matchBehind) behind = parseInt(matchBehind[1], 10);
      }
    }

    const staged = [];
    const unstaged = [];
    const untracked = [];

    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      const indexStatus = line[0];
      const workTreeStatus = line[1];
      const filePath = line.slice(3).trim();

      if (indexStatus === '?' && workTreeStatus === '?') {
        untracked.push(filePath);
      } else {
        if (indexStatus !== ' ' && indexStatus !== '?') {
          staged.push({ file: filePath, status: indexStatus });
        }
        if (workTreeStatus !== ' ' && workTreeStatus !== '?') {
          unstaged.push({ file: filePath, status: workTreeStatus });
        }
      }
    }

    return {
      repoPath,
      branch: currentBranch,
      tracking,
      ahead,
      behind,
      isClean: staged.length === 0 && unstaged.length === 0 && untracked.length === 0,
      staged,
      unstaged,
      untracked
    };
  }

  getBranches(repoPath) {
    if (!this.isRepo(repoPath)) {
      throw new Error(`Directory is not a valid git repository: ${repoPath}`);
    }

    const out = execFileSync('git', ['branch', '-a', '--format=%(refname:short)|%(upstream:short)|%(HEAD)'], {
      cwd: repoPath,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 5000
    });

    const branches = [];
    for (const line of out.split('\n')) {
      if (!line.trim()) continue;
      const [name, upstream, headMarker] = line.split('|');
      branches.push({
        name,
        upstream: upstream || null,
        current: headMarker === '*'
      });
    }

    return branches;
  }

  getCommits(repoPath, limit = 20) {
    if (!this.isRepo(repoPath)) {
      throw new Error(`Directory is not a valid git repository: ${repoPath}`);
    }

    const safeLimit = Math.max(1, Math.min(limit, 100));
    const out = execFileSync('git', ['log', `-n`, String(safeLimit), '--pretty=format:%H|%h|%an|%ae|%at|%s'], {
      cwd: repoPath,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      timeout: 5000
    });

    const commits = [];
    for (const line of out.split('\n')) {
      if (!line.trim()) continue;
      const [hash, shortHash, author, email, timestampStr, subject] = line.split('|');
      commits.push({
        hash,
        shortHash,
        author,
        email,
        timestamp: parseInt(timestampStr, 10) * 1000,
        subject
      });
    }

    return commits;
  }

  getDiff(repoPath, commitSha = null) {
    if (!this.isRepo(repoPath)) {
      throw new Error(`Directory is not a valid git repository: ${repoPath}`);
    }

    let diff = '';
    let stat = '';

    if (commitSha) {
      // Validate commitSha format (hex only)
      if (!/^[a-f0-9]{4,40}$/i.test(commitSha)) {
        throw new Error('Invalid commit SHA format');
      }
      diff = execFileSync('git', ['show', '--format=', commitSha], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        maxBuffer: 2 * 1024 * 1024,
        timeout: 8000
      });
      stat = execFileSync('git', ['show', '--stat', '--format=', commitSha], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 5000
      });
    } else {
      // Unstaged + staged diff
      const workingDiff = execFileSync('git', ['diff'], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        maxBuffer: 2 * 1024 * 1024,
        timeout: 8000
      });
      const stagedDiff = execFileSync('git', ['diff', '--staged'], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        maxBuffer: 2 * 1024 * 1024,
        timeout: 8000
      });
      diff = workingDiff + (stagedDiff ? ('\n# Staged Changes:\n' + stagedDiff) : '');
      stat = execFileSync('git', ['diff', '--stat', 'HEAD'], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 5000
      });
    }

    return {
      repoPath,
      commitSha,
      stat: stat.trim(),
      diff: diff.length > 500000 ? (diff.slice(0, 500000) + '\n\n[Diff truncated: exceeds 500 KB]') : diff
    };
  }
}

let _gitManagerInstance = null;
export const gitManager = new Proxy({}, {
  get(target, prop) {
    if (!_gitManagerInstance) {
      _gitManagerInstance = new GitManager();
    }
    const val = _gitManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_gitManagerInstance) : val;
  }
});
