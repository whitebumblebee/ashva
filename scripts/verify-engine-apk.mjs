// SPDX-License-Identifier: Apache-2.0
// Verify the actual distributed bytes, not only Gradle's input cache. No Git operations.
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const lock = JSON.parse(await readFile(new URL('../engine/stockfish.lock.json', import.meta.url), 'utf8'));
async function entry(apk, name, asText = false) {
  return new Promise((resolve, reject) => {
    const child = spawn('unzip', ['-p', apk, name], { stdio: ['ignore', 'pipe', 'ignore'] });
    const hash = createHash('sha256'); const chunks = []; let size = 0;
    child.stdout.on('data', bytes => {
      size += bytes.length;
      if (size > 110 * 1024 * 1024 || (asText && size > 2 * 1024 * 1024)) { child.kill(); return; }
      hash.update(bytes); if (asText) chunks.push(bytes);
    });
    child.on('error', reject);
    child.on('close', code => code === 0 && size > 0 && size <= 110 * 1024 * 1024
      ? resolve(asText ? Buffer.concat(chunks).toString('utf8') : hash.digest('hex'))
      : reject(new Error(`Missing, oversized or invalid APK entry: ${name}`)));
  });
}
export async function verifyEngineApk(apk) {
  const identity = JSON.parse(await entry(apk, 'assets/engine/identity.json', true));
  const packagedLock = JSON.parse(await entry(apk, 'assets/engine/stockfish.lock.json', true));
  if (JSON.stringify(packagedLock) !== JSON.stringify(lock)) throw new Error('APK engine lock differs from the reviewed source lock');
  for (const abi of lock.abis) {
    if (identity.binaries[abi] !== lock.binaries[abi] || await entry(apk, `lib/${abi}/libstockfish.so`) !== lock.binaries[abi])
      throw new Error(`APK engine binary checksum mismatch: ${abi}`);
  }
  if (await entry(apk, `assets/engine/${lock.networkName}`) !== lock.networkSha256 ||
      identity.networks[lock.networkName] !== lock.networkSha256) throw new Error('APK NNUE checksum mismatch');
  if (await entry(apk, 'assets/engine/source.tar') !== identity.sourceSha256) throw new Error('APK corresponding-source checksum mismatch');
  const recipe = await entry(apk, 'assets/engine/prepare-stockfish.mjs', true);
  const patch = await entry(apk, 'assets/engine/disable-git.patch', true);
  if (createHash('sha256').update(recipe).update(patch).digest('hex') !== identity.recipeSha256)
    throw new Error('APK build recipe checksum mismatch');
  for (const name of ['COPYING.txt', 'AUTHORS', 'NDK-NOTICE.txt', 'LLVM-NOTICE.txt']) await entry(apk, `assets/engine/${name}`);
  console.log(`${path.basename(apk)}: exact engine/source/NNUE/recipe hashes and required notices verified.`);
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (!process.argv[2]) throw new Error('Usage: node scripts/verify-engine-apk.mjs <APK>');
  await verifyEngineApk(path.resolve(process.argv[2]));
}
