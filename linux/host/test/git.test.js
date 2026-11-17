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

test('GitHubManager — authenticated state check and safe remote error handling', () => {
  const gh = new GitHubManager();
  const auth = gh.checkAuth();
  assert.equal(auth.authenticated, true);
  assert.equal(auth.user, 'roonakyadav');
  assert.ok(auth.scopes.includes('repo'));

  // Since local regnant clone has no remote yet, listPullRequests should gracefully return error object
  const prs = gh.listPullRequests(PROJECT_ROOT, 5);
  assert.ok(prs.error || Array.isArray(prs));
});
