// SPDX-License-Identifier: Apache-2.0
// Resumable, parallel ranged downloader for deep-course build inputs (docs/DEEP_COURSE_PLAN.md, Phase 2/4).
// Inputs land in the ignored .course-cache/ directory; a manifest with byte ranges and SHA-256 hashes is written
// next to them so the build can record exactly which bytes it used. Only database.lichess.org is allowed.
//
// Usage: node scripts/fetch-course-data.mjs <club|broadcasts|puzzles> [--bytes=<prefix bytes>] [--months=<recent broadcast months>] [--parallel=<n>]
// HTTP 429 triggers a shared cooldown of at least one minute (longer Retry-After honoured) before any further request.
import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const cache = join(root, '.course-cache', 'raw');
const HOST = 'https://database.lichess.org/';
const CHUNK = 32 * 1024 * 1024;
export const CLUB_URL = `${HOST}standard/lichess_db_standard_rated_2026-09.pgn.zst`;
export const PUZZLE_URL = `${HOST}lichess_db_puzzle.csv.zst`;
export const BROADCAST_LIST = `${HOST}broadcast/list.txt`;

export function parseArgs(args) {
  const [target, ...rest] = args;
  if (!['club', 'broadcasts', 'puzzles'].includes(target)) throw new Error('Target must be club, broadcasts or puzzles');
  const options = { target, bytes: 1024 * 1024 * 1024, parallel: 2, months: 12 };
  for (const arg of rest) {
    const match = /^--(bytes|parallel|months)=(\d+)$/.exec(arg);
    if (!match) throw new Error(`Unknown argument ${arg}`);
    options[match[1]] = Number(match[2]);
  }
  if (options.parallel < 1 || options.parallel > 3) throw new Error('Parallel connections must be 1–3');
  if (options.bytes < 1024 * 1024) throw new Error('Prefix must be at least 1 MiB');
  return options;
}

export function chunkRanges(total, size = CHUNK) {
  const ranges = [];
  for (let start = 0; start < total; start += size) ranges.push([start, Math.min(start + size, total) - 1]);
  return ranges;
}

export function assertAllowed(url) {
  if (!url.startsWith(HOST) || url.includes('..')) throw new Error(`Refusing non-allowlisted URL ${url}`);
}

const run = (command, args) => new Promise((ok, fail) => {
  const child = spawn(command, args, { stdio: ['ignore', 'pipe', 'inherit'] });
  let out = '';
  child.stdout.on('data', data => { out += data; });
  child.on('close', code => code === 0 ? ok(out) : fail(new Error(`${command} exited ${code}`)));
});

async function size(path) { try { return (await stat(path)).size; } catch { return -1; } }

async function hashFile(path) {
  const hash = createHash('sha256');
  for await (const block of createReadStream(path)) hash.update(block);
  return hash.digest('hex');
}

// One cooldown shared by every worker: after a 429 nobody requests anything until it expires.
let cooldownUntil = 0;
const sleep = ms => new Promise(ok => setTimeout(ok, ms));
export function retryAfterMs(headers, now = Date.now()) {
  const value = /^retry-after:\s*(.+)$/im.exec(headers)?.[1]?.trim();
  const seconds = value === undefined ? NaN : /^\d+$/.test(value) ? Number(value) * 1000 : Date.parse(value) - now;
  return Math.max(60_000, Number.isFinite(seconds) ? seconds : 0);
}
async function polite(args) {
  for (;;) {
    const wait = cooldownUntil - Date.now();
    if (wait > 0) await sleep(wait);
    const headers = join(cache, `.headers-${process.pid}-${Math.random().toString(36).slice(2)}`);
    let code = 0;
    try { code = Number((await run('curl', ['-s', '-D', headers, '-w', '%{http_code}', ...args])).trim()); } catch { code = 0; }
    const text = await readFile(headers, 'utf8').catch(() => '');
    await import('node:fs/promises').then(fs => fs.rm(headers, { force: true }));
    if (code === 429) {
      cooldownUntil = Math.max(cooldownUntil, Date.now() + retryAfterMs(text));
      console.log(`HTTP 429: cooling down until ${new Date(cooldownUntil).toISOString()}`);
      continue;
    }
    return { code, headers: text };
  }
}

async function remoteLength(url) {
  for (let attempt = 1; attempt <= 5; attempt++) {
    const { code, headers } = await polite(['-I', '--max-time', '60', '-o', '/dev/null', url]);
    const match = [...headers.matchAll(/content-length:\s*(\d+)/gi)].pop();
    if (code === 200 && match) return Number(match[1]);
    await sleep(10_000 * attempt);
  }
  throw new Error(`No 200 response with a content length for ${url}`);
}

async function fetchRange(url, [start, end], path) {
  const expected = end - start + 1;
  if (await size(path) === expected) return;
  for (let attempt = 1; attempt <= 10; attempt++) {
    const partial = `${path}.part`;
    const { code } = await polite(['-r', `${start}-${end}`, '--speed-limit', '2000', '--speed-time', '60',
      '--connect-timeout', '30', '-o', partial, url]);
    if (code === 206 && await size(partial) === expected) { await rename(partial, path); return; }
    await sleep(Math.min(120, 10 * attempt) * 1000);
  }
  throw new Error(`Range ${start}-${end} of ${url} failed after retries`);
}

async function download(url, total, directory, parallel) {
  assertAllowed(url);
  await mkdir(directory, { recursive: true });
  const ranges = chunkRanges(total);
  let next = 0, done = 0;
  const worker = async () => {
    while (next < ranges.length) {
      const index = next++;
      await fetchRange(url, ranges[index], join(directory, `chunk-${String(index).padStart(5, '0')}`));
      done++;
      if (done % 4 === 0 || done === ranges.length) console.log(`${url.split('/').pop()}: ${done}/${ranges.length} chunks`);
    }
  };
  await Promise.all(Array.from({ length: Math.min(parallel, ranges.length) }, worker));
  const chunks = [];
  for (const [index, range] of ranges.entries()) {
    const file = join(directory, `chunk-${String(index).padStart(5, '0')}`);
    chunks.push({ file: file.slice(root.length + 1), start: range[0], end: range[1], sha256: await hashFile(file) });
  }
  return chunks;
}

async function text(url) {
  const out = join(cache, `.text-${process.pid}`);
  const { code } = await polite(['-L', '--max-time', '120', '-o', out, url]);
  const body = code === 200 ? await readFile(out, 'utf8') : '';
  await import('node:fs/promises').then(fs => fs.rm(out, { force: true }));
  return body;
}

async function published(url) {
  const sums = await text(`${url.slice(0, url.lastIndexOf('/') + 1)}sha256sums.txt`);
  const name = url.split('/').pop();
  return sums.split('\n').map(line => line.trim().split(/\s+/)).find(parts => parts[1]?.endsWith(name))?.[0] ?? null;
}

async function wholeFileHash(chunks) {
  const hash = createHash('sha256');
  for (const chunk of chunks) for await (const block of createReadStream(join(root, chunk.file))) hash.update(block);
  return hash.digest('hex');
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const manifestPath = join(cache, `${options.target}.manifest.json`);
  const entries = [];
  if (options.target === 'club') {
    const total = await remoteLength(CLUB_URL);
    const prefix = Math.min(options.bytes, total);
    const chunks = await download(CLUB_URL, prefix, join(cache, 'club-2026-09'), options.parallel);
    entries.push({ url: CLUB_URL, license: 'CC0-1.0', remoteBytes: total, prefixBytes: prefix, complete: prefix === total, chunks });
  } else if (options.target === 'puzzles') {
    // Only a prefix is needed to sample the evaluation set; a prefix cannot be checked against the whole-file hash.
    const total = await remoteLength(PUZZLE_URL);
    const prefix = Math.min(options.bytes, total);
    const chunks = await download(PUZZLE_URL, prefix, join(cache, 'puzzles'), options.parallel);
    entries.push({ url: PUZZLE_URL, license: 'CC0-1.0', remoteBytes: total, prefixBytes: prefix, complete: prefix === total, chunks });
  } else {
    const list = (await text(BROADCAST_LIST)).split('\n').map(line => line.trim()).filter(Boolean).slice(0, options.months);
    for (const url of list) {
      assertAllowed(url);
      const name = url.split('/').pop().replace('.pgn.zst', '');
      const total = await remoteLength(url);
      const chunks = await download(url, total, join(cache, 'broadcasts', name), options.parallel);
      entries.push({ url, license: 'CC-BY-SA-4.0', remoteBytes: total, sha256: await wholeFileHash(chunks), chunks });
    }
    const sums = await text(`${HOST}broadcast/sha256sums.txt`);
    for (const entry of entries) {
      const name = entry.url.split('/').pop();
      entry.publishedSha256 = sums.split('\n').map(line => line.trim().split(/\s+/)).find(parts => parts[1]?.endsWith(name))?.[0] ?? null;
    }
  }
  const mismatched = entries.filter(entry => entry.publishedSha256 && entry.sha256 && entry.publishedSha256 !== entry.sha256);
  await writeFile(manifestPath, `${JSON.stringify({ target: options.target, fetchedAt: new Date().toISOString(), entries }, null, 2)}\n`);
  console.log(`Wrote ${manifestPath.slice(root.length + 1)}: ${entries.length} file(s); checksum mismatches: ${mismatched.length}`);
  if (mismatched.length) process.exitCode = 1;
}

if (import.meta.url === `file://${process.argv[1]}`) main().catch(error => { console.error(error.message); process.exit(1); });
