import test from 'node:test';
import assert from 'node:assert/strict';
import { createCapabilityRefresh } from '../../../../packages/cloud-runtime/src/runtime/capability-refresh.ts';

test('missing config is probed once even when another provider has capabilities', async () => {
  const calls = [];
  let changes = 0;
  const refresh = createCapabilityRefresh(
    async (w, a) => calls.push([w, a.id]),
    () => 'workspace',
    () => changes++,
  );
  const options = {
    agents: [
      { id: 'grok', machineId: 'mac', cliType: 'builtin', agentType: 'grok' },
    ],
    capabilities: [
      {
        agentConfigId: 'other',
        machineId: 'mac',
        cliType: 'builtin',
        agentType: 'grok',
      },
    ],
  };
  refresh('workspace', options);
  refresh('workspace', options);
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(calls, [['workspace', 'grok']]);
  assert.equal(changes, 1);
  refresh('workspace', options);
  assert.equal(calls.length, 1);
});
test('existing exact or legacy capabilities do not start another agent', () => {
  const refresh = createCapabilityRefresh(
    () => assert.fail('unexpected request'),
    () => 'w',
    () => {},
  );
  refresh('w', {
    agents: [
      { id: 'a', machineId: 'm', cliType: 'builtin', agentType: 'grok' },
    ],
    capabilities: [{ machineId: 'm', cliType: 'builtin', agentType: 'grok' }],
  });
});
test('validated RPC capability is available immediately before catalog sync', async () => {
  const agent = {
    id: 'g',
    machineId: 'mac',
    cliType: 'builtin',
    agentType: 'grok',
  };
  const refresh = createCapabilityRefresh(
    async () => ({
      result: {
        success: true,
        machineId: 'mac',
        configId: 'g',
        cliType: 'builtin',
        agentType: 'grok',
        capability: { models: [{ modelId: 'grok-4.7', name: 'Grok 4.7' }] },
      },
    }),
    () => 'w',
    () => {},
  );
  refresh('w', { agents: [agent], capabilities: [] });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(refresh.rows('w')[0].value.models[0].modelId, 'grok-4.7');
  assert.deepEqual(refresh.rows('other'), []);
});
test('queued probes are fenced when the workspace changes', async () => {
  let workspace = 'w';
  const calls = [];
  const release = [];
  const refresh = createCapabilityRefresh(
    (w, a) => {
      calls.push(a.id);
      return new Promise((resolve) => release.push(resolve));
    },
    () => workspace,
    () => assert.fail('stale notification'),
  );
  refresh('w', {
    agents: ['a', 'b', 'c'].map((id) => ({
      id,
      machineId: 'm',
      cliType: 'builtin',
      agentType: 'grok',
    })),
    capabilities: [],
  });
  assert.deepEqual(calls, ['a']);
  workspace = 'other';
  release[0]();
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(calls, ['a']);
});
