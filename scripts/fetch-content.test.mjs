import test from 'node:test';
import assert from 'node:assert/strict';
import { boundedDownload, retryDelay, sha256, snapshotArguments, validateConfiguration } from './fetch-content.mjs';
import { readFile } from 'node:fs/promises';

const url = 'https://database.lichess.org/broadcast/test.pgn.zst';
test('additional snapshots cannot alter default paths or escape their namespace', () => {
  assert.deepEqual(snapshotArguments([]), { record: false, snapshot: undefined,
    configuration: 'content/sources.json', lock: 'content/snapshots.lock.json' });
  assert.equal(snapshotArguments(['--snapshot=broadcast-2020-01', '--record-lock']).lock, 'content/snapshots/broadcast-2020-01.lock.json');
  for (const args of [['--snapshot=../escape'], ['--snapshot='], ['--snapshot=/tmp'], ['--record-lock','--record-lock'],
      ['--snapshot=a','--snapshot=b'], ['--unknown']]) assert.throws(() => snapshotArguments(args));
});
test('dependency and acquisition pins are bounded, unique and refer to configured inputs', async () => {
  const config = JSON.parse(await readFile(new URL('../content/sources.json', import.meta.url), 'utf8'));
  const dependency = { packId: 'taxonomy-v1', manifestSha256: 'a'.repeat(64) };
  const acquisitionSha256 = { 'lichess-openings/a.tsv': 'b'.repeat(64) };
  assert.doesNotThrow(() => validateConfiguration({ ...config, dependencies: [dependency], acquisitionSha256 }));
  for (const change of [{ dependencies: [dependency,dependency] }, { dependencies: [{ ...dependency, packId: '../escape' }] },
      { dependencies: [{ ...dependency, manifestSha256: 'bad' }] }, { acquisitionSha256: { 'unknown/file': 'a'.repeat(64) } },
      { acquisitionSha256: { 'lichess-openings/a.tsv': 'bad' } }, { acquisitionSha256: [] }])
    assert.throws(() => validateConfiguration({ ...config, ...change }));
  const extra = JSON.parse(await readFile(new URL('../content/snapshots/broadcast-2020-01.sources.json', import.meta.url), 'utf8'));
  assert.doesNotThrow(() => validateConfiguration(extra));
});
test('checked-in source configuration is explicitly cleared before acquisition', async () => {
  const config = JSON.parse(await readFile(new URL('../content/sources.json', import.meta.url), 'utf8'));
  assert.doesNotThrow(() => validateConfiguration(config));
  for (const change of [{ redistributionApproved: false }, { license: 'AGPL-3.0' }, { licenseEvidenceUrl: '' }, { revision: '../../escape' },
      { files: [{ name: '../escape', format: 'OPENINGS_TSV' }] }]) {
    const altered = { ...config, sources: [{ ...config.sources[0], ...change }] };
    assert.throws(() => validateConfiguration(altered));
  }
});
test('unknown source/format/schema and duplicate IDs are rejected', async () => {
  const config = JSON.parse(await readFile(new URL('../content/sources.json', import.meta.url), 'utf8'));
  assert.throws(() => validateConfiguration({ ...config, schemaVersion: 99 }));
  assert.throws(() => validateConfiguration({ ...config, sources: [config.sources[0], config.sources[0]] }));
  assert.throws(() => validateConfiguration({ ...config, sources: [{ ...config.sources[0], kind: 'UNKNOWN' }] }));
  assert.throws(() => validateConfiguration({ ...config, sources: [{ ...config.sources[0], files: [{ name: 'test', format: 'UNKNOWN' }] }] }));
});
test('only audited HTTPS hosts are fetched', async () => {
  await assert.rejects(boundedDownload('https://example.org/private', () => { throw Error('must not run'); }), /outside/);
  await assert.rejects(boundedDownload('http://database.lichess.org/test'), /outside/);
});
test('download is bounded even without content-length', async () => {
  await assert.rejects(boundedDownload(url, async () => new Response(new Uint8Array(2 * 1024 * 1024 + 1))), /exceeds/);
});
test('HTTP errors are not saved as source data', async () => {
  await assert.rejects(boundedDownload(url, async () => new Response('failure', { status: 503 })), /503/);
});
test('429 does not retry and respects full-minute cooldown', async () => {
  let calls = 0;
  await assert.rejects(boundedDownload(url, async () => { calls++; return new Response('', { status: 429, headers: { 'retry-after': '5' } }); }),
    error => error.cooldownMillis >= 60_000);
  assert.equal(calls, 1);
});
test('429 longer Retry-After is honored', async () => {
  await assert.rejects(boundedDownload(url, async () => new Response('', { status: 429, headers: { 'retry-after': '120' } })),
    error => error.cooldownMillis === 120_000);
});
test('HTTP-date Retry-After and malformed headers are safe', () => {
  const now = Date.parse('2026-10-01T11:00:00Z');
  assert.equal(retryDelay('Thu, 01 Oct 2026 11:03:00 GMT', now), 180_000);
  for (const value of [null, '', 'invalid', '-1', 'Thu, 01 Oct 2026 10:00:00 GMT'])
    assert.equal(retryDelay(value, now), 60_000);
});
test('received bytes are preserved with SHA-256', async () => {
  const bytes = await boundedDownload(url, async () => new Response('abc'));
  assert.equal(bytes.toString(), 'abc');
  assert.equal(sha256(bytes), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
});
