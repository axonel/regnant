import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

export class GitHubManager {
  constructor({ runCommand = execFileSync } = {}) {
    this.runCommand = runCommand;
  }

  checkAuth() {
    try {
      const out = this.runCommand('gh', ['auth', 'status'], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 4000
      });
      const loggedIn = out.includes('Logged in to github.com');
      const accountMatch = out.match(/account\s+([a-zA-Z0-9_-]+)/);
      const scopesMatch = out.match(/Token scopes:\s+(.+)/);

      return {
        authenticated: loggedIn,
        user: accountMatch ? accountMatch[1] : null,
        scopes: scopesMatch ? scopesMatch[1].replace(/['\s]/g, '').split(',') : []
      };
    } catch (err) {
      const errOut = (err.stderr || '') + (err.stdout || '');
      const loggedIn = errOut.includes('Logged in to github.com');
      const accountMatch = errOut.match(/account\s+([a-zA-Z0-9_-]+)/);
      const scopesMatch = errOut.match(/Token scopes:\s+(.+)/);

      return {
        authenticated: loggedIn,
        user: accountMatch ? accountMatch[1] : null,
        scopes: scopesMatch ? scopesMatch[1].replace(/['\s]/g, '').split(',') : []
      };
    }
  }

  listPullRequests(repoPath, limit = 15) {
    if (!fs.existsSync(repoPath)) {
      throw new Error(`Directory not found: ${repoPath}`);
    }
    const safeLimit = Math.max(1, Math.min(limit, 50));
    try {
      const out = this.runCommand('gh', [
        'pr', 'list',
        '--limit', String(safeLimit),
        '--json', 'number,title,author,state,isDraft,headRefName,baseRefName,updatedAt,url'
      ], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 10000
      });
      return JSON.parse(out);
    } catch (err) {
      const msg = (err.stderr || err.message || '').toString();
      if (msg.includes('no git remotes')) {
        return { error: 'No GitHub remotes found for this repository' };
      }
      if (err.code === 'ENOENT') {
        return { error: 'GitHub CLI (gh) is not installed on this host' };
      }
      throw new Error(`Failed to list pull requests: ${msg.trim()}`);
    }
  }

  getPullRequest(repoPath, prNumber) {
    if (!fs.existsSync(repoPath)) {
      throw new Error(`Directory not found: ${repoPath}`);
    }
    const num = parseInt(prNumber, 10);
    if (isNaN(num)) throw new Error('Invalid PR number');

    try {
      const out = this.runCommand('gh', [
        'pr', 'view', String(num),
        '--json', 'number,title,body,author,state,headRefName,baseRefName,createdAt,url,reviews,comments'
      ], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 10000
      });
      return JSON.parse(out);
    } catch (err) {
      const reason = err.code === 'ENOENT' ? 'GitHub CLI (gh) is not installed on this host' : (err.stderr || err.message);
      throw new Error(`Failed to view PR #${num}: ${reason.trim()}`);
    }
  }

  listWorkflowRuns(repoPath, limit = 15) {
    if (!fs.existsSync(repoPath)) {
      throw new Error(`Directory not found: ${repoPath}`);
    }
    const safeLimit = Math.max(1, Math.min(limit, 50));
    try {
      const out = this.runCommand('gh', [
        'run', 'list',
        '--limit', String(safeLimit),
        '--json', 'databaseId,name,headBranch,headSha,status,conclusion,event,createdAt,url'
      ], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        timeout: 10000
      });
      return JSON.parse(out);
    } catch (err) {
      const msg = (err.stderr || err.message || '').toString();
      if (msg.includes('no git remotes')) {
        return { error: 'No GitHub remotes found for this repository' };
      }
      if (err.code === 'ENOENT') {
        return { error: 'GitHub CLI (gh) is not installed on this host' };
      }
      throw new Error(`Failed to list workflow runs: ${msg.trim()}`);
    }
  }

  getWorkflowRunLog(repoPath, runId) {
    if (!fs.existsSync(repoPath)) {
      throw new Error(`Directory not found: ${repoPath}`);
    }
    const safeId = String(runId).replace(/[^0-9]/g, '');
    if (!safeId) throw new Error('Invalid workflow run ID');

    try {
      const out = this.runCommand('gh', [
        'run', 'view', safeId,
        '--log'
      ], {
        cwd: repoPath,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        maxBuffer: 2 * 1024 * 1024,
        timeout: 15000
      });
      return out.length > 100000 ? (out.slice(-100000) + '\n[Log truncated: displaying last 100 KB]') : out;
    } catch (err) {
      const reason = err.code === 'ENOENT' ? 'GitHub CLI (gh) is not installed on this host' : (err.stderr || err.message);
      throw new Error(`Failed to get run log: ${reason.trim()}`);
    }
  }
}

let _githubManagerInstance = null;
export const githubManager = new Proxy({}, {
  get(target, prop) {
    if (!_githubManagerInstance) {
      _githubManagerInstance = new GitHubManager();
    }
    const val = _githubManagerInstance[prop];
    return typeof val === 'function' ? val.bind(_githubManagerInstance) : val;
  }
});
