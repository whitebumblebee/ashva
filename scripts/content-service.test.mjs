// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { postgresImage } from './test-content-service.mjs';

test('service tests use a pinned, ephemeral, loopback-only PostgreSQL lifecycle without owner databases', async () => {
  assert.match(postgresImage, /^postgres:18\.6-alpine@sha256:[a-f0-9]{64}$/);
  const script = await readFile(new URL('./test-content-service.mjs', import.meta.url), 'utf8');
  for (const marker of ['ashva.test-only', '127.0.0.1::5432', 'ashva_test', '--tmpfs', '--rm', 'finally']) assert.ok(script.includes(marker));
  assert.equal(/docker.*(?:system.*prune|volume.*rm|compose.*down)/.test(script), false);
  assert.equal(script.includes('open -a Docker'), false);
});
test('service host engine preserves reviewed source, Git-free build and separate UCI identity', async () => {
  const script = await readFile(new URL('./prepare-service-engine.mjs', import.meta.url), 'utf8');
  for (const marker of ['assertGitFreeBuild', 'sourceSha256', 'networkSha256', 'COPYING.txt', 'AUTHORS', 'binarySha256', 'recipeSha256', 'NNUE_EMBEDDING_OFF']) assert.ok(script.includes(marker));
  const adapter = await readFile(new URL('../contentService/src/main/kotlin/com/openinglab/service/HostEngine.kt', import.meta.url), 'utf8');
  assert.ok(adapter.includes('ProcessBuilder')); assert.equal(adapter.includes('System.loadLibrary'), false);
});
