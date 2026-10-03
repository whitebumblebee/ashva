// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { validateLog, checkRelayContext } from './check-relay-context.mjs';
import { fileURLToPath } from 'node:url';

const valid = '---\nseq: 1\nagent: fixture\ndate: "2026-10-01T00:00:00Z"\ntask: fixture\nstatus: partial\nsummary: "Validated a fixture."\nnext: "Run the next test."\nsupersedes: []\n---\n';
test('published history requires real metadata and a next action', () => {
  assert.deepEqual(validateLog(valid, '0001_fixture.md'), []);
  assert.ok(validateLog(valid.replace('Run the next test.', 'TODO: next'), '0001_fixture.md').includes('missing-or-placeholder-next'));
  assert.ok(validateLog(valid, '0002_fixture.md').includes('sequence-mismatch'));
  assert.deepEqual(validateLog('no metadata', '0001_fixture.md'), ['missing-front-matter']);
});
test('published project memory validates without loading the ignored Relay install', async () => {
  const result = await checkRelayContext(fileURLToPath(new URL('../', import.meta.url)));
  assert.deepEqual(result.findings, []);
  assert.ok(result.history > 0 && result.tasks > 0);
});
