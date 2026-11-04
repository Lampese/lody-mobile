import assert from 'node:assert/strict';
import test from 'node:test';
import { Flock } from '@loro-dev/flock-wasm/base64';
import { openTestSession } from '../helpers.mjs';

// A new session's first turn may run as an Agent Role (web chat landing): only
// the id crosses, and the runtime reads the fresh row itself.
const role = (patch = {}) => ({
  v: 1,
  id: 'reviewer',
  ownerUserId: 'u1',
  visibility: 'private',
  name: 'Reviewer',
  promptPrefix: 'Review carefully.',
  machineId: 'm1',
  agentConfigId: 'a1',
  runConfig: {
    modelId: 'gpt',
    configOptionValues: { api_key: 'fixture-secret', plan_mode: true },
    memory: { providerId: 'p', memoryId: 'mem' },
  },
  revision: 3,
  createdAt: 1,
  updatedAt: 1,
  ...patch,
});
const settingsWith = (value) => () => {
  const flock = new Flock(`settings-${crypto.randomUUID()}`);
  flock.set(['agentRole', 'reviewer'], value);
  flock.commit();
  return flock.exportFile();
};
const outcome = (result) => ({ state: result.state, reason: result.reason });
const turn = {
  sessionId: 's1',
  machineId: 'm1',
  userId: 'u1',
  cliType: 'builtin',
  agentType: 'codex',
  text: 'Fix the bug',
  agentRoleId: 'reviewer',
  agentConfigId: 'a1',
  modelId: 'gpt',
};

test('a first turn runs as the Role only while it is bound to the session and its pins still hold', async () => {
  const calls = [];
  const f = await openTestSession({
    settings: settingsWith(role()),
    onRpc(request) {
      calls.push(request);
      return { result: { accepted: true } };
    },
  });
  try {
    const before = f.appends.length;
    // Another Agent Config on the same machine is not this Role's binding.
    assert.deepEqual(outcome(await f.runtime.sendTurn({ ...turn, agentConfigId: 'a2' })), {
      state: 'not_sent',
      reason: 'role_unavailable',
    });
    // Moving a pinned value (the model) means the Role is no longer what will run.
    assert.deepEqual(outcome(await f.runtime.sendTurn({ ...turn, modelId: 'other' })), {
      state: 'not_sent',
      reason: 'role_changed',
    });
    assert.equal(f.appends.length, before, 'refusals write nothing');

    // An independent Plan toggle is a per-turn override and keeps the Role.
    const sent = await f.runtime.sendTurn({ ...turn, configOptionValues: { plan_mode: false } });
    assert.equal(sent.state, 'accepted');
    const config = calls[0].params.inputConfig;
    assert.equal(config.prompt, 'Review carefully.\n\nFix the bug');
    assert.deepEqual(config.inputBlocks, [{ type: 'text', text: 'Fix the bug' }]);
    assert.equal(config.agentRoleId, 'reviewer');
    assert.equal(config.agentRoleRevision, 3);
    assert.deepEqual(config.memory, { providerId: 'p', memoryId: 'mem' });
    assert.deepEqual(config.agentRoleSnapshot, { id: 'reviewer', revision: 3, name: 'Reviewer', emoji: '🪼' });
    assert.equal(JSON.stringify(config).includes('fixture-secret'), false);

    // A Role belongs to the first turn only.
    assert.deepEqual(outcome(await f.runtime.sendTurn({ ...turn, queue: true })), {
      state: 'not_sent',
      reason: 'role_unavailable',
    });
  } finally {
    f.close();
  }
});

test('a Role another user keeps private is never applied', async () => {
  const f = await openTestSession({ settings: settingsWith(role({ ownerUserId: 'someone-else' })) });
  try {
    assert.deepEqual(outcome(await f.runtime.sendTurn(turn)), { state: 'not_sent', reason: 'role_unavailable' });
  } finally {
    f.close();
  }
});
