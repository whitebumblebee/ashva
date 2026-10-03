// SPDX-License-Identifier: Apache-2.0
// Opt-in, test-only Docker lifecycle; never starts the daemon or touches existing containers/databases.
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import net from 'node:net';

export const postgresImage = 'postgres:18.6-alpine@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873';
const root = fileURLToPath(new URL('../', import.meta.url));
async function run(command, args, { capture = false, env = process.env } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: root, env, stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'inherit' });
    let output = '';
    if (capture) child.stdout.on('data', bytes => { output += bytes; });
    if (capture) child.stderr.resume();
    // Captured diagnostics/config values are not printed; only sanitized lifecycle failure below.
    child.on('error', reject); child.on('exit', code => code === 0 ? resolve(output.trim()) : reject(new Error(`${command} test step failed (${code})`)));
  });
}
export async function testContentService() {
  await run('docker', ['info', '--format', '{{.ServerVersion}}'], { capture: true });
  const name = `ashva-service-test-${randomUUID()}`;
  let created = false;
  try {
    await run('docker', ['run', '--detach', '--rm', '--name', name, '--label', 'ashva.test-only=true',
      '--tmpfs', '/var/lib/postgresql', '--publish', '127.0.0.1::5432',
      '--env', 'POSTGRES_HOST_AUTH_METHOD=trust', '--env', 'POSTGRES_DB=ashva_test', postgresImage], { capture: true });
    created = true;
    let ready = false;
    for (let attempt = 0; attempt < 30; attempt++) {
      try { await run('docker', ['exec', name, 'pg_isready', '-U', 'postgres', '-d', 'ashva_test'], { capture: true }); ready = true; break; }
      catch { await new Promise(resolve => setTimeout(resolve, 1000)); }
    }
    if (!ready) throw new Error('Test-only PostgreSQL did not become ready');
    const port = await run('docker', ['port', name, '5432/tcp'], { capture: true });
    const match = port.match(/^127\.0\.0\.1:(\d+)$/);
    if (!match) throw new Error('Test-only database must have one loopback mapping');
    const engine = path.join(root, '.engine-cache/service');
    await run('./gradlew', [':contentService:test', ':contentService:integrationTest', ':contentService:installDist'], {
      env: { ...process.env, ASHVA_SERVICE_TEST_JDBC: `jdbc:postgresql://127.0.0.1:${match[1]}/ashva_test`,
        ASHVA_SERVICE_TEST_ENGINE_DIR: engine, ASHVA_SERVICE_TEST_PASSWORD: '' },
    });
    await smokeService(`jdbc:postgresql://127.0.0.1:${match[1]}/ashva_test`, engine);
    console.log('Local service checks passed; only the test-created database is removed next.');
  } finally {
    if (created) {
      const label = await run('docker', ['inspect', '--format', '{{index .Config.Labels "ashva.test-only"}}', name], { capture: true });
      if (label !== 'true') throw new Error('Refusing to stop a container without the test-only label');
      await run('docker', ['stop', name], { capture: true });
      console.log('Disposable service test database removed. Docker and unrelated containers remain unchanged.');
    }
  }
}
async function smokeService(jdbc, engine) {
  const reservation = net.createServer();
  await new Promise((resolve, reject) => { reservation.once('error', reject); reservation.listen(0, '127.0.0.1', resolve); });
  const port = reservation.address().port;
  await new Promise(resolve => reservation.close(resolve));
  const token = randomUUID();
  const child = spawn(path.join(root, 'contentService/build/install/contentService/bin/contentService'), [], {
    cwd: root, env: { ...process.env, ASHVA_SERVICE_JDBC: jdbc, ASHVA_SERVICE_DB_USER: 'postgres',
      ASHVA_SERVICE_DB_PASSWORD: '', ASHVA_SERVICE_TOKEN: token, ASHVA_SERVICE_PORT: String(port), ASHVA_SERVICE_ENGINE_DIR: engine },
    stdio: ['ignore', 'ignore', 'ignore'],
  });
  const exited = new Promise(resolve => { child.once('exit', resolve); child.once('error', resolve); });
  const base = `http://127.0.0.1:${port}`;
  const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
  async function completed(id) {
    for (let attempt = 0; attempt < 120; attempt++) {
      const response = await fetch(`${base}/v1/jobs/${id}`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error('Local process job lookup failed');
      const job = await response.json();
      if (job.status === 'SUCCEEDED') return job;
      if (['FAILED', 'CANCELLED'].includes(job.status)) throw new Error('Local process job did not succeed');
      await pause(250);
    }
    throw new Error('Local process job exceeded smoke-test wait');
  }
  try {
    let ready = false;
    for (let attempt = 0; attempt < 80; attempt++) {
      if (child.exitCode !== null) throw new Error('Local service process exited before readiness');
      try { if ((await fetch(`${base}/health`)).ok) { ready = true; break; } } catch { /* starting */ }
      await pause(250);
    }
    if (!ready) throw new Error('Local service process did not become ready');
    const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
    const imported = await fetch(`${base}/v1/jobs/import`, { method: 'POST', headers,
      body: JSON.stringify({ packId: 'lichess-openings-c67912be581f-import-v1' }) });
    if (imported.status !== 202) throw new Error('Local process import was not queued');
    await completed((await imported.json()).id);
    const page = await (await fetch(`${base}/v1/openings?q=Ruy%20Lopez&limit=100`)).json();
    if (page.items?.length !== 100 || page.sources?.[0]?.manifest?.source?.license !== 'CC0-1.0') throw new Error('Local process source search failed');
    const analyzed = await fetch(`${base}/v1/jobs/analysis`, { method: 'POST', headers,
      body: JSON.stringify({ initialFen: 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
        originalMove: 'e2e4', budget: { depth: 8, nodes: 10000, moveTimeMillis: 1000, multiPv: 2, hashMiB: 16 } }) });
    if (analyzed.status !== 202) throw new Error('Local process analysis was not queued');
    const result = (await completed((await analyzed.json()).id)).result;
    if (result?.original?.lines?.[0]?.uci?.[0] !== 'e2e4') throw new Error('Local process checked analysis failed');
    console.log('Actual loopback CIO service process: source import/search and real UCI analysis passed.');
  } finally {
    if (child.exitCode === null) child.kill('SIGTERM');
    const closed = await Promise.race([exited.then(() => true), pause(5000).then(() => false)]);
    if (!closed) { child.kill('SIGKILL'); await exited; }
  }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await testContentService(); }
  catch (error) { console.error(`Local service checks failed: ${error.message}`); process.exitCode = 1; }
}
