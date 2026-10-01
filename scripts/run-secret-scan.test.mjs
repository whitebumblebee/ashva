import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { archives, gitleaksVersion, verifyArchive } from './run-secret-scan.mjs';

test('scanner is pinned for local Apple Silicon and hosted Linux, with SHA-256 digests', () => {
  assert.equal(gitleaksVersion, '8.30.1');
  assert.deepEqual(Object.keys(archives).sort(), ['darwin-arm64', 'linux-x64']);
  for (const entry of Object.values(archives)) assert.match(entry.sha256, /^[a-f0-9]{64}$/);
});
test('scanner refuses an altered downloaded archive before extraction or execution', () => {
  const bytes = Buffer.from('non-executable archive verification fixture');
  const checksum = createHash('sha256').update(bytes).digest('hex');
  assert.doesNotThrow(() => verifyArchive(bytes, checksum));
  assert.throws(() => verifyArchive(Buffer.concat([bytes, Buffer.from('altered')]), checksum), /checksum mismatch/);
});
