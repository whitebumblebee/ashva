import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { ignoreRules, isIgnored, privacyFindings } from './public-audit.mjs';

test('ignore rules cover generated outputs, nested databases and negation without hiding content', () => {
  const rules = ignoreRules('.kotlin/\n**/build/\n*.db\n.env.*\n!.env.example\n');
  for (const file of ['.kotlin/errors/failure.log', 'shared/build/generated/code.kt', 'build/output', 'nested/learner.db', '.env.production']) assert.equal(isIgnored(file, rules), true);
  for (const file of ['.env.example', 'shared/schemas/2.json', 'content/raw/game.pgn', 'content/packs/games.jsonl']) assert.equal(isIgnored(file, rules), false);
});
test('root anchors and literal dots are respected', () => {
  const rules = ignoreRules('/captures/\nlocal.properties\n*.jks\n');
  assert.equal(isIgnored('captures/photo.png', rules), true);
  assert.equal(isIgnored('docs/captures/photo.png', rules), false);
  assert.equal(isIgnored('nested/local.properties', rules), true);
  assert.equal(isIgnored('localXproperties', rules), false);
  assert.equal(isIgnored('nested/release.jks', rules), true);
});
test('unsupported ignore syntax fails closed', () => {
  assert.throws(() => ignoreRules('[ab].txt'), /Unsupported/);
});
test('private paths and credential-bearing URLs are reported without returning values', () => {
  const home = '/' + 'Users' + '/example-owner/project/';
  const temporary = '/' + 'private/tmp' + '/audit-fixture/';
  const win = 'C:' + '\\Users\\' + 'example-owner\\project';
  const url = 'https:' + '//fixture-user:fixture-password@example.invalid';
  const findings = privacyFindings([home, temporary, win, url].join('\n'));
  assert.deepEqual(findings.map(x => x.line), [1, 2, 3, 4]);
  assert.equal(findings.length, 4);
  assert.equal(JSON.stringify(findings).includes('example-owner'), false);
  assert.equal(JSON.stringify(findings).includes('fixture-password'), false);
});
test('portable placeholders, public source URLs and chess positions are safe', () => {
  assert.deepEqual(privacyFindings('$ANDROID_HOME/platform-tools/adb\n<project-root>/androidApp\nhttps://database.lichess.org/#broadcasts\nrnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -'), []);
});

test('Ashva declares Apache-2.0 with unchanged terms and separate upstream license scopes', async () => {
  const license = await readFile(new URL('../LICENSE', import.meta.url), 'utf8');
  const retainedTerms = await readFile(new URL('../LICENSES/Apache-2.0.txt', import.meta.url), 'utf8');
  const notice = await readFile(new URL('../NOTICE', import.meta.url), 'utf8');
  assert.equal(license, retainedTerms);
  for (const declaration of ['Copyright 2026 Shishir Jha', 'SPDX-License-Identifier: Apache-2.0',
    'CC0-1.0', 'CC-BY-SA-4.0', 'Vendored Relay skill and CLI: MIT']) assert.ok(notice.includes(declaration));
  assert.deepEqual(privacyFindings(notice), []);
});
