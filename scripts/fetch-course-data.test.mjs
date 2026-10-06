// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { assertAllowed, chunkRanges, parseArgs, retryAfterMs } from './fetch-course-data.mjs';

test('course data arguments are bounded and polite by default', () => {
  assert.deepEqual(parseArgs(['club']), { target: 'club', bytes: 1024 * 1024 * 1024, parallel: 2, months: 12 });
  assert.equal(parseArgs(['broadcasts', '--months=3']).months, 3);
  assert.throws(() => parseArgs(['club', '--parallel=9']), /1–3/);
  assert.throws(() => parseArgs(['everything']), /Target/);
  assert.throws(() => parseArgs(['club', '--unknown=1']), /Unknown/);
});

test('ranges cover a prefix exactly once', () => {
  const ranges = chunkRanges(70, 32);
  assert.deepEqual(ranges, [[0, 31], [32, 63], [64, 69]]);
  assert.equal(ranges.reduce((sum, [a, b]) => sum + b - a + 1, 0), 70);
});

test('HTTP 429 cools down for at least a minute and honours longer Retry-After', () => {
  assert.equal(retryAfterMs(''), 60_000);
  assert.equal(retryAfterMs('Retry-After: 5\r\n'), 60_000);
  assert.equal(retryAfterMs('retry-after: 180\r\n'), 180_000);
  const now = Date.parse('2026-10-05T00:00:00Z');
  assert.equal(retryAfterMs('Retry-After: Mon, 05 Oct 2026 00:05:00 GMT\r\n', now), 300_000);
});

test('only the Lichess database host is allowed', () => {
  assert.doesNotThrow(() => assertAllowed('https://database.lichess.org/standard/lichess_db_standard_rated_2026-09.pgn.zst'));
  assert.throws(() => assertAllowed('https://example.com/file.zst'), /non-allowlisted/);
  assert.throws(() => assertAllowed('https://database.lichess.org/../etc'), /non-allowlisted/);
});
