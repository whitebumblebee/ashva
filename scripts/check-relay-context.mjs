// SPDX-License-Identifier: Apache-2.0
// Published-memory integrity only: no skill install, Git commands, writes or claim-liveness check.
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateLog(text, filename) {
  const header = text.match(/^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/)?.[1];
  if (!header) return ['missing-front-matter'];
  const fields = Object.fromEntries(header.split(/\r?\n/).map(line => {
    const match = line.match(/^([a-z]+):\s*(.*)$/);
    return match ? [match[1], match[2].replace(/^(["'])(.*)\1$/, '$2').trim()] : ['', ''];
  }));
  const issues = [];
  for (const key of ['seq', 'agent', 'date', 'task', 'status', 'summary', 'next']) {
    if (!fields[key] || /TODO:/i.test(fields[key])) issues.push(`missing-or-placeholder-${key}`);
  }
  if (!/^\d+$/.test(fields.seq ?? '') || Number(fields.seq) !== Number(filename.slice(0, 4))) issues.push('sequence-mismatch');
  if (!['done', 'partial', 'blocked'].includes(fields.status)) issues.push('invalid-log-status');
  if (!Number.isFinite(Date.parse(fields.date))) issues.push('invalid-log-date');
  return issues;
}

export async function checkRelayContext(root) {
  const base = path.join(root, '.relay');
  const [project, tasks, index, configText] = await Promise.all(['PROJECT.md', 'tasks.md', 'history.md', 'config.json'].map(file => readFile(path.join(base, file), 'utf8')));
  const config = JSON.parse(configText);
  const findings = [];
  if (!project.trim() || !Array.isArray(config.gates)) findings.push('missing-project-or-gates');
  const files = (await readdir(path.join(base, 'history'))).filter(file => /^\d{4}_.+\.md$/.test(file)).sort();
  const sequences = new Set();
  for (const file of files) {
    const seq = Number(file.slice(0, 4));
    if (sequences.has(seq)) findings.push('duplicate-history-sequence');
    sequences.add(seq);
    for (const rule of validateLog(await readFile(path.join(base, 'history', file), 'utf8'), file)) findings.push(`${file}: ${rule}`);
    if (!index.includes(`history/${file}`)) findings.push(`${file}: missing-index-link`);
  }
  if (Number(index.match(/Entries:\s*\*\*(\d+)\*\*/)?.[1]) !== files.length) findings.push('history-index-count-mismatch');
  const slugs = new Set();
  for (const match of tasks.matchAll(/^- \[([ ~!x])\] `([^`]+)`([^\n]*)$/gm)) {
    if (slugs.has(match[2])) findings.push('duplicate-task-slug');
    slugs.add(match[2]);
    if (match[1] === 'x' && !sequences.has(Number(match[3].match(/—\s*(\d+)\s*$/)?.[1]))) findings.push(`${match[2]}: missing-done-log`);
  }
  if (!slugs.size) findings.push('missing-tasks');
  return { history: files.length, tasks: slugs.size, findings };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  checkRelayContext(fileURLToPath(new URL('../', import.meta.url))).then(result => {
    for (const finding of result.findings) console.error(finding);
    console.log(`Relay published context: ${result.history} logs, ${result.tasks} tasks, ${result.findings.length} findings. No Git or local skill dependency; claim liveness requires local doctor.`);
    process.exitCode = result.findings.length ? 1 : 0;
  }).catch(() => { console.error('Relay context check could not read valid project files.'); process.exitCode = 1; });
}
