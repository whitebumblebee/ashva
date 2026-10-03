import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, stat } from 'node:fs/promises';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const MAX_DOWNLOAD = 2 * 1024 * 1024;
const MAX_PGN = 8 * 1024 * 1024;
export const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
export function snapshotArguments(args) {
  if (args.some(arg => arg !== '--record-lock' && !arg.startsWith('--snapshot=')) ||
      args.filter(arg => arg === '--record-lock').length > 1 || args.filter(arg => arg.startsWith('--snapshot=')).length > 1)
    throw new Error('Use [--snapshot=<reviewed-name>] [--record-lock]; unknown or duplicate arguments rejected');
  const snapshot = args.find(arg => arg.startsWith('--snapshot='))?.slice('--snapshot='.length);
  if (snapshot !== undefined && !/^[a-z0-9][a-z0-9-]{0,63}$/.test(snapshot)) throw new Error('Unsafe snapshot name');
  return { record: args.includes('--record-lock'), snapshot,
    configuration: snapshot ? `content/snapshots/${snapshot}.sources.json` : 'content/sources.json',
    lock: snapshot ? `content/snapshots/${snapshot}.lock.json` : 'content/snapshots.lock.json' };
}
export function validateConfiguration(config) {
  if (config.schemaVersion !== 1 || !Array.isArray(config.sources) || !config.sources.length ||
      new Set(config.sources.map(source => source.id)).size !== config.sources.length)
    throw new Error('Invalid source configuration schema or IDs');
  const dependencies = config.dependencies ?? [];
  if (!Array.isArray(dependencies) || dependencies.length > 32 ||
      new Set(dependencies.map(item => item.packId)).size !== dependencies.length ||
      dependencies.some(item => !/^[a-z0-9-]{1,200}$/.test(item.packId ?? '') || !/^[a-f0-9]{64}$/.test(item.manifestSha256 ?? '')))
    throw new Error('Invalid immutable taxonomy dependencies');
  const checksums = config.acquisitionSha256 ?? {};
  const inputs = new Set(config.sources.flatMap(source => (source.files ?? []).map(file => `${source.id}/${file.name}`)));
  if (!checksums || Array.isArray(checksums) || typeof checksums !== 'object' ||
      Object.entries(checksums).some(([path, hash]) => !inputs.has(path) || !/^[a-f0-9]{64}$/.test(hash)))
    throw new Error('Acquisition checksums must reference exact configured files');
  for (const source of config.sources) {
    const license = { OPENING_TAXONOMY: 'CC0-1.0', BROADCAST_GAMES: 'CC-BY-SA-4.0' }[source.kind];
    if (!license || source.license !== license || source.redistributionApproved !== true ||
        !/^\d{4}-\d{2}-\d{2}$/.test(source.rightsReviewedOn ?? '') ||
        ![source.url, source.licenseUrl, source.licenseEvidenceUrl].every(url => typeof url === 'string' && url.startsWith('https://')) ||
        ![source.attribution, source.revision, source.coverage, source.modifications].every(value => typeof value === 'string' && value.trim()))
      throw new Error('Collection rights/evidence must be reviewed before acquisition');
    if (!/^[a-z0-9-]+$/.test(source.id) || !/^[A-Za-z0-9_.-]+$/.test(source.revision) || source.revision.includes('..') ||
        !Array.isArray(source.files) || !source.files.length ||
        new Set(source.files.map(file => file.name)).size !== source.files.length ||
        !source.files.every(file => /^[A-Za-z0-9_.-]+$/.test(file.name) && !file.name.includes('..')))
      throw new Error('Invalid source ID or filenames');
    if (source.kind === 'OPENING_TAXONOMY' ?
        !source.files.some(file => file.format === 'OPENINGS_TSV') || !source.files.every(file => ['OPENINGS_TSV', 'LICENSE'].includes(file.format)) :
        source.files.length !== 1 || source.files[0].format !== 'PGN_ZSTD')
      throw new Error('Unsupported source file formats');
  }
}
export function retryDelay(value, now = Date.now()) {
  const seconds = value !== null && /^\d+(\.\d+)?$/.test(value.trim()) ? Number(value) : NaN;
  const delay = Number.isFinite(seconds) ? seconds * 1000 : Date.parse(value ?? '') - now;
  return Math.max(60_000, Number.isFinite(delay) ? delay : 0);
}

// No hidden retries: a 429 stops the run; the persisted cooldown survives a rerun.
export async function boundedDownload(url, fetcher = fetch) {
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || !['raw.githubusercontent.com', 'database.lichess.org'].includes(parsed.hostname))
    throw new Error('Download URL is outside the audited public source hosts');
  const response = await fetcher(url, { signal: AbortSignal.timeout(30_000), redirect: 'error',
    headers: { 'User-Agent': 'OpeningLab-LocalContentImporter/1' } });
  if (response.status === 429) {
    const error = new Error('Provider rate limited this request; wait at least a full minute before resuming');
    error.cooldownMillis = retryDelay(response.headers.get('retry-after'));
    throw error;
  }
  if (!response.ok) throw new Error(`Download failed with HTTP ${response.status}`);
  if (Number(response.headers.get('content-length')) > MAX_DOWNLOAD) throw new Error('Download exceeds 2 MiB limit');
  const reader = response.body.getReader();
  const chunks = []; let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.length;
    if (size > MAX_DOWNLOAD) { await reader.cancel(); throw new Error('Download exceeds 2 MiB limit'); }
    chunks.push(value);
  }
  return Buffer.concat(chunks);
}

async function atomicNewOrSame(path, bytes) {
  await mkdir(dirname(path), { recursive: true });
  try {
    const old = await readFile(path);
    if (sha256(old) !== sha256(bytes)) throw new Error(`Refusing to overwrite a different snapshot: ${path}`);
    return;
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const temporary = `${path}.${process.pid}.partial`;
  await writeFile(temporary, bytes, { flag: 'wx' });
  await rename(temporary, path);
}

async function main() {
  const { record, snapshot, configuration, lock: lockName } = snapshotArguments(process.argv.slice(2));
  const configurationPath = join(root, configuration);
  const config = JSON.parse(await readFile(configurationPath, 'utf8'));
  validateConfiguration(config);
  const lockPath = join(root, lockName);
  let lock;
  try { lock = JSON.parse(await readFile(lockPath, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (!lock && !record) throw new Error('No snapshot lock. First acquisition requires explicit --record-lock');
  if (lock && record) throw new Error('A snapshot lock already exists; do not silently replace it');
  if (lock && (lock.schemaVersion !== 1 || lock.files.length !== config.sources.reduce((sum, source) => sum + source.files.length, 0) ||
      new Set(lock.files.map(file => file.path)).size !== lock.files.length)) throw new Error('Invalid snapshot lock schema or file count');
  const cache = join(root, 'content/raw');
  const cooldownPath = join(root, '.content-download-cooldown.json');
  try {
    const cooldown = JSON.parse(await readFile(cooldownPath, 'utf8'));
    if (Date.now() < cooldown.until) throw new Error(`Provider cooldown active until ${new Date(cooldown.until).toISOString()}`);
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const result = { schemaVersion: 1, configSha256: sha256(await readFile(configurationPath)),
    retrievedAt: lock?.retrievedAt ?? new Date().toISOString(), files: [] };
  if (lock && lock.configSha256 !== result.configSha256) throw new Error('Source configuration changed; create a separately reviewed snapshot');
  for (const source of config.sources) for (const file of source.files) {
    if (!/^[a-z0-9-]+$/.test(source.id) || !/^[A-Za-z0-9_.-]+$/.test(file.name) || file.name.includes('..'))
      throw new Error('Invalid source ID or filename');
    const path = join(cache, source.id, file.name);
    const relative = `${source.id}/${file.name}`;
    const expected = lock?.files.find(item => item.path === relative);
    if (lock && !expected) throw new Error(`File not present in lock: ${relative}`);
    if (expected && expected.url !== file.url) throw new Error(`Source URL differs from lock: ${relative}`);
    let bytes;
    // Bootstrap must acquire from the provider, not certify an arbitrary pre-existing local file.
    if (lock) try {
      if ((await stat(path)).size > MAX_DOWNLOAD) throw new Error('Cached file is too large');
      bytes = await readFile(path);
    }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
    if (bytes === undefined) {
      try { bytes = await boundedDownload(file.url); }
      catch (failure) {
        if (failure.cooldownMillis) await writeFile(cooldownPath, JSON.stringify({ until: Date.now() + failure.cooldownMillis }));
        throw failure;
      }
    }
    const hash = sha256(bytes);
    if (config.acquisitionSha256?.[relative] && config.acquisitionSha256[relative] !== hash)
      throw new Error(`Provider checksum mismatch: ${relative}`);
    if (expected && (expected.sha256 !== hash || expected.bytes !== bytes.length)) throw new Error(`SHA-256/size mismatch: ${relative}`);
    await atomicNewOrSame(path, bytes);
    const item = { path: relative, url: file.url, bytes: bytes.length, sha256: hash };
    if (file.format === 'PGN_ZSTD') {
      const decoded = spawnSync('zstd', ['-dc', '--memory=64MB', path], { maxBuffer: MAX_PGN, timeout: 30_000 });
      if (decoded.error || decoded.status !== 0) throw new Error('Install zstd; decompression failed or exceeded limits');
      const plain = `${source.id}/broadcast.pgn`;
      const plainHash = sha256(decoded.stdout);
      if (expected?.decodedSha256 && expected.decodedSha256 !== plainHash) throw new Error('Decompressed checksum mismatch');
      await atomicNewOrSame(join(cache, plain), decoded.stdout);
      Object.assign(item, { decodedPath: plain, decodedBytes: decoded.stdout.length, decodedSha256: plainHash });
    }
    result.files.push(item);
    console.log(`Verified ${relative}: ${bytes.length} bytes`);
  }
  if (!lock) await atomicNewOrSame(lockPath, Buffer.from(JSON.stringify(result, null, 2) + '\n'));
  console.log(`Pinned source snapshot ready. Run ./gradlew :contentTools:run --args="import${snapshot ? ` ${snapshot}` : ''}"`);
}

if (import.meta.url === pathToFileURL(resolve(process.argv[1] ?? '')).href)
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
