import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import os from 'node:os';
import { GitManager } from '../src/git/git.js';
import { GitHubManager } from '../src/git/github.js';

const PROJECT_ROOT = path.resolve('../..');

test('GitManager — repository validation, status, branches, commits, diff', () => {
  const git = new GitManager();
  assert.equal(git.isRepo(PROJECT_ROOT), true);
  assert.equal(git.isRepo(os.tmpdir()), false);

  const status = git.getStatus(PROJECT_ROOT);
  assert.equal(typeof status.branch, 'string');
  assert.equal(typeof status.isClean, 'boolean');
  assert.ok(Array.isArray(status.staged));
  assert.ok(Array.isArray(status.unstaged));
  assert.ok(Array.isArray(status.untracked));

  const branches = git.getBranches(PROJECT_ROOT);
  assert.ok(branches.length > 0);
  const current = branches.find(b => b.current);
  assert.ok(current);

  const commits = git.getCommits(PROJECT_ROOT, 5);
  assert.ok(commits.length > 0);
  assert.ok(commits[0].hash);
  assert.ok(commits[0].author);
  assert.ok(commits[0].subject);

  const diff = git.getDiff(PROJECT_ROOT);
  assert.equal(typeof diff.diff, 'string');
});

test('GitHubManager — parses authenticated state and handles missing remotes', () => {
  const gh = new GitHubManager({
    runCommand(_command, args) {
      if (args[0] === 'auth') {
        return "Logged in to github.com account roonakyadav (keyring)\nToken scopes: 'repo, workflow'";
      }
      const error = new Error('no git remotes');
      error.stderr = 'fatal: no git remotes found';
      throw error;
    },
  });

  const auth = gh.checkAuth();
  assert.equal(auth.authenticated, true);
  assert.equal(auth.user, 'roonakyadav');
  assert.deepEqual(auth.scopes, ['repo', 'workflow']);

  const prs = gh.listPullRequests(PROJECT_ROOT, 5);
  assert.equal(prs.error, 'No GitHub remotes found for this repository');
});

test('GitHubManager — reports unauthenticated and missing CLI states without crashing', () => {
  const gh = new GitHubManager({
    runCommand() {
      const error = new Error('spawn gh ENOENT');
      error.code = 'ENOENT';
      throw error;
    },
  });

  assert.deepEqual(gh.checkAuth(), {
    authenticated: false,
    user: null,
    scopes: [],
  });
  assert.equal(
    gh.listPullRequests(PROJECT_ROOT, 5).error,
    'GitHub CLI (gh) is not installed on this host',
  );
  assert.equal(
    gh.listWorkflowRuns(PROJECT_ROOT, 5).error,
    'GitHub CLI (gh) is not installed on this host',
  );
});
