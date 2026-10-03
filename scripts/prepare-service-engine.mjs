// SPDX-License-Identifier: Apache-2.0
// A local host executable for service tests, never linked into Ashva or downloaded by Gradle.
import { readFile, mkdir, mkdtemp, copyFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertGitFreeBuild, sha256 } from './prepare-stockfish.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
export async function prepareServiceEngine() {
  const upstream = path.join(root, '.engine-cache/prepared/assets/engine');
  const metadata = JSON.parse(await readFile(path.join(upstream, 'identity.json'), 'utf8'));
  if (await sha256(path.join(upstream, 'source.tar')) !== metadata.sourceSha256 ||
      await sha256(path.join(upstream, metadata.networkName)) !== metadata.networkSha256)
    throw new Error('Prepare the reviewed Android engine/source first; host source/network checks failed');
  const host = process.platform === 'darwin' ? (process.arch === 'arm64' ? 'apple-silicon' : 'x86-64') :
    process.platform === 'linux' ? (process.arch === 'arm64' ? 'armv8' : 'x86-64') : null;
  if (!host) throw new Error('Host engine preparation supports macOS/Linux only');
  const directory = path.join(root, '.engine-cache/service');
  await mkdir(directory, { recursive: true });
  const work = await mkdtemp(path.join(directory, 'build-'));
  async function run(command, args, cwd = work) {
    await new Promise((resolve, reject) => {
      const child = spawn(command, args, { cwd, stdio: 'inherit', env: { ...process.env,
        CFLAGS: '', CXXFLAGS: '', CPPFLAGS: '', LDFLAGS: '' } });
      child.on('error', reject); child.on('exit', code => code === 0 ? resolve() : reject(new Error(`Host build failed (${code})`)));
    });
  }
  await run('tar', ['-xf', path.join(upstream, 'source.tar')]);
  const source = path.join(work, 'src');
  assertGitFreeBuild(await readFile(path.join(source, 'Makefile'), 'utf8'));
  await copyFile(path.join(upstream, metadata.networkName), path.join(source, metadata.networkName));
  await run('make', ['-j2', 'build', `ARCH=${host}`, 'COMP=clang', 'EXTRACXXFLAGS=-DNNUE_EMBEDDING_OFF'], source);
  await copyFile(path.join(source, 'stockfish'), path.join(directory, 'stockfish'));
  await copyFile(path.join(upstream, metadata.networkName), path.join(directory, metadata.networkName));
  for (const name of ['source.tar', 'COPYING.txt', 'AUTHORS', 'disable-git.patch', 'prepare-stockfish.mjs', 'stockfish.lock.json'])
    await copyFile(path.join(upstream, name), path.join(directory, name));
  await copyFile(fileURLToPath(import.meta.url), path.join(directory, 'prepare-service-engine.mjs'));
  await writeFile(path.join(directory, 'identity.json'), JSON.stringify({ name: metadata.name, version: metadata.version,
    binarySha256: await sha256(path.join(directory, 'stockfish')), networks: metadata.networks,
    sourceSha256: metadata.sourceSha256, recipeSha256: await sha256(fileURLToPath(import.meta.url)),
    architecture: host, license: metadata.license,
    modifications: 'Reviewed unchanged C++ source; upstream Git checks disabled by preserved build-only patch. Host clang non-PGO separate UCI executable; NNUE embedding disabled.' }, null, 2) + '\n');
  return directory;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await prepareServiceEngine(); console.log('Local host UCI engine prepared with exact identity, source and notices.'); }
  catch (error) { console.error(`Service engine preparation failed: ${error.message}`); process.exitCode = 1; }
}
