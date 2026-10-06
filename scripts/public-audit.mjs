// Read-only source-exposure checks. This deliberately invokes no Git commands.
import { readdir, readFile, lstat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function ignoreRules(text) {
  return text.split(/\r?\n/).filter(line => line && !line.startsWith('#')).map(line => {
    const negate = line.startsWith('!');
    let pattern = negate ? line.slice(1) : line;
    const directory = pattern.endsWith('/');
    if (directory) pattern = pattern.slice(0, -1);
    const anchored = pattern.startsWith('/');
    if (anchored) pattern = pattern.slice(1);
    const slash = pattern.includes('/');
    let regex = '';
    for (let i = 0; i < pattern.length; i++) {
      if (pattern.slice(i, i + 3) === '**/') { regex += '(?:.*/)?'; i += 2; }
      else if (pattern.slice(i, i + 2) === '**') { regex += '.*'; i++; }
      else if (pattern[i] === '*') regex += '[^/]*';
      else if (pattern[i] === '?') regex += '[^/]';
      else if ('[]\\'.includes(pattern[i])) throw new Error('Unsupported ignore syntax: extend and test the audit parser first');
      else regex += pattern[i].replace(/[.+^$(){}|]/g, '\\$&');
    }
    return { negate, regex: new RegExp(`${anchored || slash ? '^' : '(?:^|/)'}${regex}${directory ? '(?:/.*)?$' : '$'}`) };
  });
}
export function isIgnored(file, rules) {
  let ignored = false;
  for (const rule of rules) if (rule.regex.test(file)) ignored = !rule.negate;
  return ignored;
}

export function localLinkPublicationRule(root, document, target, rules) {
  let relative;
  try { relative = path.relative(root, path.resolve(root, path.dirname(document), decodeURIComponent(target))).split(path.sep).join('/'); }
  catch { return 'invalid-local-document-link'; }
  if (relative === '..' || relative.startsWith('../') || path.isAbsolute(relative)) return 'local-document-link-outside-project';
  if (relative === '.git' || relative.startsWith('.git/') || isIgnored(relative, rules)) return 'local-document-link-not-publishable';
  return null;
}

export function privacyFindings(text) {
  const tests = [
    ['personal-home-path', /\/(?:Users|home)\/[A-Za-z0-9_.-]+\//],
    ['machine-temporary-path', /\/(?:private\/)?(?:var\/folders|tmp)\/[A-Za-z0-9_.-]+/],
    ['windows-home-path', /[A-Z]:\\Users\\[A-Za-z0-9_. -]+\\/i],
    ['private-key-header', /-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----/],
    ['url-credentials', /https?:\/\/[^\s/"<>]+:[^\s/"<>]+@/i],
  ];
  const found = [];
  text.split(/\r?\n/).forEach((line, i) => {
    for (const [rule, regex] of tests) if (regex.test(line)) found.push({ rule, line: i + 1 });
  });
  return found; // Never include the matched value.
}

const mandatory = ['README.md', 'LICENSE', 'NOTICE', 'CONTRIBUTING.md', 'SECURITY.md', 'THIRD_PARTY_NOTICES.md',
  'docs/PRIVACY.md', 'docs/PUBLIC_READINESS.md', '.github/workflows/ci.yml', '.gitleaks.toml'];
const wrapperSha = '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7';
const textExtensions = new Set(['.kt', '.kts', '.md', '.mjs', '.json', '.jsonl', '.toml', '.xml', '.properties', '.txt', '.tsv', '.pgn', '.yml', '.yaml', '.mdc', '.svg', '.bat', '.patch', '.py']);

export async function audit(root) {
  const rules = ignoreRules(await readFile(path.join(root, '.gitignore'), 'utf8'));
  const files = [];
  const findings = [];
  async function walk(directory, relative = '') {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const file = relative ? `${relative}/${entry.name}` : entry.name;
      if (file === '.git' || file.startsWith('.git/') || isIgnored(file, rules)) continue;
      const absolute = path.join(directory, entry.name);
      if (entry.isSymbolicLink()) { findings.push({ file, rule: 'symlink-requires-explicit-review' }); continue; }
      if (entry.isDirectory()) { await walk(absolute, file); continue; }
      files.push(file);
      const stat = await lstat(absolute);
      if (stat.size > 10 * 1024 * 1024) findings.push({ file, rule: 'large-file-requires-review' });
      if (['LICENSE', 'NOTICE'].includes(path.basename(file)) || file === '.gitignore' || file === 'gradlew' || textExtensions.has(path.extname(file))) {
        const text = await readFile(absolute, 'utf8');
        for (const finding of privacyFindings(text)) findings.push({ file, ...finding });
        if (file.endsWith('.md')) {
          for (const match of text.matchAll(/\]\(([^\s)#]+)(?:#[^)]*)?\)/g)) {
            const target = match[1];
            if (/^[a-z]+:|^\/|^</i.test(target)) continue;
            const publicationRule = localLinkPublicationRule(root, file, target, rules);
            if (publicationRule) { findings.push({ file, rule: publicationRule }); continue; }
            try { await lstat(path.resolve(path.dirname(absolute), decodeURIComponent(target))); }
            catch { findings.push({ file, rule: 'broken-local-document-link' }); }
          }
        }
      } else if (file.endsWith('.png')) {
        const bytes = await readFile(absolute);
        // Screencaps should not carry EXIF/text/location metadata.
        if (bytes.subarray(1, 4).toString() !== 'PNG') findings.push({ file, rule: 'invalid-png' });
        for (let offset = 8; offset + 12 <= bytes.length;) {
          const length = bytes.readUInt32BE(offset), type = bytes.subarray(offset + 4, offset + 8).toString();
          if (['tEXt', 'zTXt', 'iTXt', 'eXIf'].includes(type)) findings.push({ file, rule: 'image-metadata-requires-review' });
          offset += length + 12;
        }
      } else if (!file.endsWith('.pgn.zst') && file !== 'gradle/wrapper/gradle-wrapper.jar') {
        findings.push({ file, rule: 'unreviewed-binary' });
      }
    }
  }
  await walk(root);
  for (const file of mandatory) if (!files.includes(file)) findings.push({ file, rule: 'required-public-file-missing' });
  for (const file of ['.kotlin/errors/local.log', '.env.production', 'release.jks', 'signing.properties', 'local.properties',
    'androidApp/build/outputs/apk/debug/app.apk', 'learner.db', 'gitleaks.log']) {
    if (!isIgnored(file, rules)) findings.push({ file: '.gitignore', rule: 'missing-private-file-ignore' });
  }
  for (const file of ['shared/schemas/com.openinglab.shared.storage.LearningDatabase/2.json', 'gradle/wrapper/gradle-wrapper.jar',
    'content/raw/lichess-broadcast-2020-04/broadcast.pgn', 'content/packs/lichess-openings-c67912be581f-import-v1/openings.jsonl', '.env.example']) {
    if (isIgnored(file, rules)) findings.push({ file: '.gitignore', rule: 'required-source-hidden' });
  }
  if (files.includes('LICENSE')) {
    const license = await readFile(path.join(root, 'LICENSE'), 'utf8');
    if (/TODO|\[year\]|\[fullname\]/i.test(license)) findings.push({ file: 'LICENSE', rule: 'unfilled-license' });
  }
  const properties = await readFile(path.join(root, 'gradle/wrapper/gradle-wrapper.properties'), 'utf8');
  if (!/^distributionSha256Sum=[a-f0-9]{64}$/m.test(properties)) findings.push({ file: 'gradle/wrapper/gradle-wrapper.properties', rule: 'missing-gradle-distribution-checksum' });
  const jar = await readFile(path.join(root, 'gradle/wrapper/gradle-wrapper.jar'));
  if (createHash('sha256').update(jar).digest('hex') !== wrapperSha) findings.push({ file: 'gradle/wrapper/gradle-wrapper.jar', rule: 'wrapper-provenance-pin-changed' });
  for (const file of files.filter(x => x.startsWith('.github/workflows/'))) {
    const workflow = await readFile(path.join(root, file), 'utf8');
    if (/pull_request_target|secrets\.|write-all|self-hosted/.test(workflow)) findings.push({ file, rule: 'unsafe-public-workflow-setting' });
    for (const match of workflow.matchAll(/uses:\s*([^\s#]+)/g)) if (!/^[\w.-]+\/[\w./-]+@[a-f0-9]{40}$/.test(match[1])) findings.push({ file, rule: 'action-not-immutable-pinned' });
    if (!/permissions:\s*\n\s+contents: read/.test(workflow) || !/persist-credentials: false/.test(workflow)) findings.push({ file, rule: 'workflow-needs-read-only-token' });
  }
  return { files: files.sort(), findings };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = fileURLToPath(new URL('../', import.meta.url));
  audit(root).then(({ files, findings }) => {
    for (const finding of findings.slice(0, 40)) console.error(`${finding.file}${finding.line ? ':' + finding.line : ''} · ${finding.rule} (matched value omitted)`);
    console.log(`Public surface: ${files.length} candidate files, ${findings.length} findings. Read-only; no Git operations. Does not audit image pixels, prior Git history or staged-file overrides.`);
    process.exitCode = findings.length ? 1 : 0;
  }).catch(() => { console.error('Public audit could not complete; check required files and supported ignore syntax.'); process.exitCode = 1; });
}
