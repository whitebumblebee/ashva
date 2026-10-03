// SPDX-License-Identifier: Apache-2.0
// Downloads are explicit; Gradle never downloads an executable or silently changes its source.
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir, copyFile, mkdtemp, rm } from 'node:fs/promises';
import { createReadStream } from 'node:fs';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const cache = path.join(root, '.engine-cache');
const lock = JSON.parse(await readFile(path.join(root, 'engine/stockfish.lock.json'), 'utf8'));
const patchFile = path.join(root, 'engine/disable-git.patch');
const recipeSha256 = createHash('sha256').update(await readFile(fileURLToPath(import.meta.url))).update(await readFile(patchFile)).digest('hex');
export function assertGitFreeBuild(makefile) {
  if (/\$\(shell[^\n]*\bgit\b/.test(makefile)) throw new Error('Upstream build still contains automatic Git commands');
}
export async function sha256(file) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}
async function run(command, args, options = {}) {
  await new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', ...options });
    child.on('error', reject);
    child.on('exit', code => code === 0 ? resolve() : reject(new Error(`${command} failed (${code})`)));
  });
}
async function download(url, destination, expected) {
  let existing = false;
  try { existing = await sha256(destination) === expected; } catch { /* first download */ }
  if (!existing) await run('curl', ['-fLsS', '--retry', '2', '--max-time', '180', url, '-o', destination]);
  if (await sha256(destination) !== expected) throw new Error('Upstream checksum mismatch; refusing extraction or execution');
}

async function verify() {
  const prepared = path.join(cache, 'prepared');
  const metadata = JSON.parse(await readFile(path.join(prepared, 'assets/engine/identity.json'), 'utf8'));
  if (metadata.archiveSha256 !== lock.archiveSha256 || metadata.recipeSha256 !== recipeSha256 ||
      metadata.ndkVersion !== lock.ndkVersion || metadata.networks[lock.networkName] !== lock.networkSha256)
    throw new Error('Prepared engine does not match the pinned source/build recipe');
  for (const abi of lock.abis) {
    if (metadata.binaries[abi] !== lock.binaries[abi] ||
        await sha256(path.join(prepared, 'jniLibs', abi, 'libstockfish.so')) !== lock.binaries[abi])
      throw new Error(`Prepared engine checksum mismatch (${abi})`);
  }
  if (await sha256(path.join(prepared, 'assets/engine/source.tar')) !== metadata.sourceSha256)
    throw new Error('Corresponding-source archive checksum mismatch');
  if (await sha256(path.join(prepared, 'assets/engine', lock.networkName)) !== lock.networkSha256)
    throw new Error('Packaged NNUE checksum mismatch');
  console.log('Stockfish 19 prepared source, networks, recipe and both ABI binaries verified.');
}

async function prepare() {
  await mkdir(cache, { recursive: true });
  try { await verify(); return; } catch { /* rebuild only from verified upstream bytes */ }
  const ndk = process.env.ASHVA_NDK_DIR;
  if (!ndk) throw new Error(`Set ASHVA_NDK_DIR to NDK ${lock.ndkVersion}; see docs/ENGINE_ANALYSIS.md`);
  const properties = await readFile(path.join(ndk, 'source.properties'), 'utf8');
  if (!properties.includes(`Pkg.Revision = ${lock.ndkVersion}`)) throw new Error('Wrong NDK revision');
  const host = process.platform === 'darwin' ? 'darwin-x86_64' : process.platform === 'linux' ? 'linux-x86_64' : null;
  if (!host) throw new Error('Engine preparation currently requires macOS or Linux');
  const tools = path.join(ndk, 'toolchains/llvm/prebuilt', host, 'bin');
  const archive = path.join(cache, 'stockfish-android-arm64-universal.tar.gz');
  const network = path.join(cache, lock.networkName);
  await download(lock.archiveUrl, archive, lock.archiveSha256);
  await download(lock.networkUrl, network, lock.networkSha256);
  const work = await mkdtemp(path.join(cache, 'build-'));
  await run('tar', ['-xzf', archive, '-C', work]); // Exact reviewed, checksummed official archive, not arbitrary input.
  const source = path.join(work, 'stockfish');
  await run('patch', ['--batch', '-p0', '-i', patchFile], { cwd: source });
  assertGitFreeBuild(await readFile(path.join(source, 'src/Makefile'), 'utf8'));
  const prepared = path.join(cache, 'prepared');
  await mkdir(path.join(prepared, 'assets/engine'), { recursive: true });
  // Preserve exact corresponding source/scripts/license, including the disclosed build-only Git patch.
  // AAPT transparently removes .gz extensions; keep the exact hashed tar bytes in the APK instead.
  await rm(path.join(prepared, 'assets/engine/source.tar.gz'), { force: true }); // Only our obsolete generated artifact.
  await run('tar', ['-cf', path.join(prepared, 'assets/engine/source.tar'), '-C', source,
    'src', 'scripts', 'Copying.txt', 'AUTHORS', 'README.md']);
  await copyFile(path.join(source, 'Copying.txt'), path.join(prepared, 'assets/engine/COPYING.txt'));
  await copyFile(path.join(source, 'AUTHORS'), path.join(prepared, 'assets/engine/AUTHORS'));
  await copyFile(path.join(ndk, 'NOTICE'), path.join(prepared, 'assets/engine/NDK-NOTICE.txt'));
  await copyFile(path.join(ndk, 'toolchains/llvm/prebuilt', host, 'NOTICE'), path.join(prepared, 'assets/engine/LLVM-NOTICE.txt'));
  await copyFile(network, path.join(prepared, 'assets/engine', lock.networkName));
  const binaries = {};
  for (const abi of lock.abis) {
    const build = path.join(work, abi);
    await mkdir(build);
    await run('tar', ['-xf', path.join(prepared, 'assets/engine/source.tar'), '-C', build]);
    await copyFile(network, path.join(build, 'src', lock.networkName));
    const target = abi === 'arm64-v8a' ? 'aarch64-linux-android' : 'x86_64-linux-android';
    const arch = abi === 'arm64-v8a' ? 'armv8' : 'x86-64';
    await run('make', ['-j2', 'build', `ARCH=${arch}`, 'COMP=ndk', 'TARGET_KERNEL=Linux', 'KERNEL=Linux', 'OS=Android',
      `COMPCXX=${path.join(tools, `${target}${lock.api}-clang++`)}`,
      'EXTRACXXFLAGS=-DNNUE_EMBEDDING_OFF',
      'EXTRALDFLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'],
      { cwd: path.join(build, 'src'), env: { ...process.env, CFLAGS: '', CXXFLAGS: '', CPPFLAGS: '', LDFLAGS: '',
          PATH: `${tools}${path.delimiter}${process.env.PATH}` } });
    const directory = path.join(prepared, 'jniLibs', abi);
    await mkdir(directory, { recursive: true });
    await copyFile(path.join(build, 'src/stockfish'), path.join(directory, 'libstockfish.so'));
    await run(path.join(tools, 'llvm-strip'), ['--strip-all', path.join(directory, 'libstockfish.so')]);
    binaries[abi] = await sha256(path.join(directory, 'libstockfish.so'));
    await run(path.join(tools, 'llvm-readelf'), ['-l', path.join(directory, 'libstockfish.so')]);
  }
  for (const abi of lock.abis) if (binaries[abi] !== lock.binaries[abi])
    throw new Error(`Rebuilt executable differs from the reviewed binary pin (${abi}); do not bypass this gate`);
  await writeFile(path.join(prepared, 'assets/engine/identity.json'), JSON.stringify({
    ...lock, recipeSha256, binaries, networks: { [lock.networkName]: lock.networkSha256 },
    sourceSha256: await sha256(path.join(prepared, 'assets/engine/source.tar')),
    modifications: 'No C++ source modifications. Build-only Makefile patch disables automatic Git checks. Separate non-PGO API26 executables; NNUE embedding disabled, exact network supplied as data; 16KB linker alignment. Standard UCI only.',
  }, null, 2) + '\n');
  // Corresponding network and exact build recipe are provided as pinned public URLs/source files.
  await copyFile(path.join(root, 'engine/stockfish.lock.json'), path.join(prepared, 'assets/engine/stockfish.lock.json'));
  await copyFile(fileURLToPath(import.meta.url), path.join(prepared, 'assets/engine/prepare-stockfish.mjs'));
  await copyFile(patchFile, path.join(prepared, 'assets/engine/disable-git.patch'));
  await verify();
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await (process.argv.includes('--verify') ? verify() : prepare()); }
  catch (error) { console.error(`Engine preparation failed: ${error.message}`); process.exitCode = 1; }
}
