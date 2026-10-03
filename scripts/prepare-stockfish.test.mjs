// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { ignoreRules, isIgnored } from './public-audit.mjs';
import { spawnSync } from 'node:child_process';
import { assertGitFreeBuild } from './prepare-stockfish.mjs';

test('engine source, network, ABI binaries and modern toolchain are pinned separately from app licensing', async () => {
  const lock = JSON.parse(await readFile(new URL('../engine/stockfish.lock.json', import.meta.url)));
  assert.equal(lock.name, 'Stockfish'); assert.equal(lock.version, '19'); assert.equal(lock.license, 'GPL-3.0-or-later');
  assert.equal(lock.api, 26); assert.equal(lock.pageSize, 16384); assert.equal(lock.ndkVersion, '30.0.16248370');
  assert.deepEqual(lock.abis, ['arm64-v8a', 'x86_64']);
  for (const hash of [lock.archiveSha256, lock.networkSha256, ...Object.values(lock.binaries)]) assert.match(hash, /^[a-f0-9]{64}$/);
  assert.equal(lock.networkName, `nn-${lock.networkSha256.slice(0,12)}.nnue`);
  assert.match(lock.archiveUrl, /^https:\/\/github.com\/official-stockfish\/Stockfish\/releases\/download\/sf_19\//);
  assert.match(lock.networkUrl, /^https:\/\/tests.stockfishchess.org\/api\/nn\//);
  const rules = ignoreRules(await readFile(new URL('../.gitignore', import.meta.url), 'utf8'));
  assert.equal(isIgnored('.engine-cache/prepared/jniLibs/arm64-v8a/libstockfish.so', rules), true);
  assert.equal(isIgnored('engine/stockfish.lock.json', rules), false);
});
test('build-only patch and guard remove Git expressions rather than unsafe command-line overrides', async () => {
  const script = await readFile(new URL('prepare-stockfish.mjs', import.meta.url), 'utf8');
  assert.ok(script.includes("await run('patch'"));
  assert.throws(() => assertGitFreeBuild('GIT_SHA := $(shell git rev-parse HEAD)'), /Git/);
  assert.throws(() => assertGitFreeBuild('GIT_DIFFINDEX := $(shell git update-index --refresh)'), /Git/);
  const patch = await readFile(new URL('../engine/disable-git.patch', import.meta.url), 'utf8');
  const replacements = patch.split('\n').filter(x => x.startsWith('+GIT_')).map(x => x.slice(1));
  assert.equal(replacements.length, 3);
  const makefile = [...replacements, 'all:', '\t@true', ''].join('\n');
  assert.doesNotThrow(() => assertGitFreeBuild(makefile));
  const result = spawnSync('make', ['-f', '-'], {
    encoding: 'utf8', input: makefile,
  });
  assert.equal(result.status, 0, result.stderr);
});
test('APK receives source and GPL notices without linking the engine or allowing checksum drift', async () => {
  const script = await readFile(new URL('prepare-stockfish.mjs', import.meta.url), 'utf8');
  for (const text of ['source.tar', 'COPYING.txt', 'AUTHORS', 'prepare-stockfish.mjs', '--strip-all', 'lock.binaries[abi]', 'No C++ source modifications']) assert.ok(script.includes(text));
  const adapter = await readFile(new URL('../androidApp/src/main/kotlin/com/openinglab/app/analysis/AndroidStockfish.kt', import.meta.url), 'utf8');
  assert.ok(adapter.includes('ProcessBuilder(binary.absolutePath)'));
  assert.equal(adapter.includes('System.loadLibrary'), false);
});
