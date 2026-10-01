// Pinned official release, verified before execution. No installation or Git operations.
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, writeFile, chmod, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const gitleaksVersion = '8.30.1';
export const archives = {
  'darwin-arm64': { file: 'darwin_arm64', sha256: 'b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5' },
  'linux-x64': { file: 'linux_x64', sha256: '551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb' },
};
export function verifyArchive(bytes, expected) {
  if (createHash('sha256').update(bytes).digest('hex') !== expected) throw new Error('Scanner archive checksum mismatch');
}

async function main() {
  const entry = archives[`${process.platform}-${process.arch}`];
  if (!entry) throw new Error('Automatic scanner supports Apple Silicon macOS and Linux x64. Use official Gitleaks 8.30.1 manually on other systems.');
  const root = fileURLToPath(new URL('../', import.meta.url));
  const temp = await mkdtemp(path.join(tmpdir(), 'ashva-secret-scan-'));
  try {
    const name = `gitleaks_${gitleaksVersion}_${entry.file}.tar.gz`;
    const url = `https://github.com/gitleaks/gitleaks/releases/download/v${gitleaksVersion}/${name}`;
    const response = await fetch(url, { signal: AbortSignal.timeout(60_000) });
    if (!response.ok) throw new Error(`Official scanner download failed (${response.status})`);
    const declaredLength = Number(response.headers.get('content-length'));
    if (declaredLength > 16 * 1024 * 1024) throw new Error('Scanner archive exceeds download limit');
    const chunks = [];
    let size = 0;
    for await (const chunk of response.body) {
      size += chunk.length;
      if (size > 16 * 1024 * 1024) throw new Error('Scanner archive exceeds download limit');
      chunks.push(chunk);
    }
    const bytes = Buffer.concat(chunks);
    verifyArchive(bytes, entry.sha256);
    const archivePath = path.join(temp, 'scanner.tar.gz');
    await writeFile(archivePath, bytes, { mode: 0o600 });
    const extract = spawnSync('tar', ['-xzf', archivePath, '-C', temp, 'gitleaks'], { encoding: 'utf8' });
    if (extract.status !== 0) throw new Error('Scanner extraction failed');
    const binary = path.join(temp, 'gitleaks');
    await chmod(binary, 0o700);
    const report = path.join(temp, 'findings.json');
    const result = spawnSync(binary, ['dir', root, '--config', path.join(root, '.gitleaks.toml'),
      '--redact=100', '--no-banner', '--no-color', '--report-format', 'json', '--report-path', report,
      '--max-decode-depth', '2', '--max-archive-depth', '2', '--timeout', '120'],
    { cwd: root, encoding: 'utf8', timeout: 150_000, maxBuffer: 2 * 1024 * 1024 });
    // Never relay matches, secret values, full report content or scanner diagnostics.
    const findings = JSON.parse(await readFile(report, 'utf8'));
    for (const finding of findings.slice(0, 30)) {
      console.error(`${path.relative(root, path.resolve(root, finding.File))}:${finding.StartLine} · ${finding.RuleID} (value redacted)`);
    }
    if (findings.length > 30) console.error(`Additional redacted findings: ${findings.length - 30}`);
    if (result.status !== 0 || result.error || findings.length) throw new Error(`Secret scan failed (${findings.length} findings); review locally, never paste matched values.`);
    console.log(`Gitleaks ${gitleaksVersion}: 0 findings. Directory scan includes source, raw/derived data and Relay history; excludes generated/cache/Git/report directories. No Git history or image-content audit.`);
  } finally {
    // Only this mkdtemp-created directory is removed; the project is never a target.
    await rm(temp, { recursive: true, force: true });
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(() => {
    console.error('Secret scan could not pass. Check network, supported platform and local redacted findings; no matched values are printed.');
    process.exitCode = 1;
  });
}
