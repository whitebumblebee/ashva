<!-- SPDX-License-Identifier: Apache-2.0 -->
# Local content and analysis service

`contentService` is optional JVM infrastructure, **not an Android network dependency or a deployed backend**. Native Android0.14 remains entirely offline. The service uses Ktor3.6.0/CIO, pgJDBC42.7.13 and PostgreSQL18.6 with explicit loopback-only configuration. No accounts, paid providers, new data acquisition, cloud bucket or cross-device sync are configured.

## API

| Endpoint | Contract |
| --- | --- |
| `GET /health` | Process liveness, not DB readiness |
| `GET /v1/catalog` | Active descriptors/full manifests, rights/coverage/dependencies, snapshot hash, ETag/304 |
| `GET /v1/catalog/delta?from=…` | Changed descriptors/inactive source IDs since a retained snapshot; unknown versions409 |
| `GET /v1/openings` or `/v1/games` | Literal case-insensitive metadata `q`(max80), `limit`1–100, keyset `cursor`; exact pack on each record and full source descriptors |
| `GET /v1/packs/{manifestSha256}/{file}` | Retained manifest/four allowlisted payloads, rechecked size/hash, SHA/ETag/immutable caching; missing/corrupt objects fail closed |
| `POST /v1/jobs/import` | Authenticated operator queues reviewed `packId`; never an arbitrary client path, URL or PGN |
| `POST /v1/jobs/analysis` | Authenticated explicit initial FEN/full UCI history/original move/budget; max512 history plies/shared legal and budget limits |
| `GET /v1/jobs/{id}`, `POST /v1/jobs/{id}/cancel` | Authenticated persistent status/result; queued work202, explicit errors/expiration/cancellation |
| `GET /v1/metrics` | Authenticated status/attempt counts and limits; no FEN/PGN/token/raw exceptions |

Cursors bind query/kind/page size/catalog snapshot/last key. Catalog changes cause `STALE_CURSOR_RESTART`409 instead of silently mixing versions. Prepared SQL uses literal wildcard escaping. PostgreSQL stores retained metadata/JSON and atomic active source pointers; content-addressed filesystem objects are a replaceable **local** adapter, not S3. Deltas describe changed packs/files, not binary patches: clients can reuse unchanged file hashes. Previous versions remain retained/downloadable; no learner DB, bookmark or policy is opened/rebound.

Imports reuse mobile's full legal/SAN/FEN/identity/rights/count/dependency validator in an isolated temporary Room DB. Only the three reviewed source packs are allowlisted by trusted hash. Staged bytes precede a single lease-guarded PostgreSQL commit of metadata/activation/import success. Failure/cancellation/stale leases preserve prior active content. Interrupted staging may leave ignored orphan objects, not API-visible unvalidated data. A manifest's own license claim is not new acquisition authority.

Analysis uses one worker and fresh **separate standard-UCI** processes, no JNI/linking. Keys include initial FEN/clocks, every ordered move, original move, all budget fields, exact binary/NNUE identities and protocol revision. Injected output is rechecked for root, identity, budget, terminal status, coherent candidates, legal UCI and canonical SAN. Durable results expire after seven days: expired responses hide old output; an explicit identical request requeues. Expiry is **not deletion**: requests/results remain in this separate local DB until operator disposal. No encryption/account recovery is claimed.

Queue limits:1000 retained jobs, one worker per process,3 attempts, PostgreSQL `SKIP LOCKED` leases/guarded stale completion, cancellation, bounded transient IO/DB/timeout retry backoff. Distinct excess jobs return429, not silent pruning. Analysis outer ceiling20seconds/import180seconds/lease240seconds, plus bounded JDBC timeouts. Shutdown cancels/reaps processes and leaves interrupted work retryable. Multiple server processes would multiply workers; distributed quotas, archival/capacity tuning and backups need operator policy before deployment.

`ProviderBackoff` is a tested **adapter contract**, not an enabled downloader. It serializes calls, spaces them, honors numeric/date Retry-After, waits at least60seconds on429, limits retries to3 and defers rather than retrying early above24hours. No provider URL/credential/collection is enabled; additional acquisition requires rights/terms/scope review.

## Reproduce checks

Use an already-running Docker runtime, reviewed [engine preparation](ENGINE_ANALYSIS.md), and host clang/make. Host preparation builds a local executable from the reviewed corresponding source/Git-disabling patch, retaining exact identity/source/network/recipe/notices. It is not Android ELF or part of the Apache service JAR.

```bash
node scripts/prepare-service-engine.mjs
node scripts/test-content-service.mjs
```

The test harness creates one uniquely named/labeled PostgreSQL container using a pinned image digest, tmpfs and a random **127.0.0.1-only** port. Passwordless trust is only for this disposable synthetic/public-data test database: never use it for production/private persistent data. It runs unit and real PostgreSQL/HTTP/host-UCI checks, builds the distribution, launches a real CIO process and tests background import/search/analysis. Finally it stops/removes only its own labeled container/process. It never starts Docker, stops unrelated containers, deletes volumes or touches Android/owner databases. Ignored object/build diagnostics remain.

`./gradlew :contentService:test` is portable. `:contentService:integrationTest` requires `ASHVA_SERVICE_TEST_JDBC` specifically targeting disposable `ashva_test` and `ASHVA_SERVICE_TEST_ENGINE_DIR`; missing configuration **fails**, never skips. Hosted CI configuration is not evidence of a hosted successful run.

## Manual local run

Select a separate local PostgreSQL DB, not the learner/another project's database. Privately set `ASHVA_SERVICE_JDBC` (`jdbc:postgresql://127.0.0.1:<port>/<database>`), `ASHVA_SERVICE_DB_USER`, optional `ASHVA_SERVICE_DB_PASSWORD`, and a randomly generated32–128-character `ASHVA_SERVICE_TOKEN`. The service creates its own `ashva_content` schema. `ASHVA_SERVICE_PORT` defaults8080; host is always loopback. Set `ASHVA_SERVICE_ENGINE_DIR` to the reviewed prepared host directory to enable analysis; omission returns503, never fake output.

```bash
export ASHVA_SERVICE_TOKEN="$(openssl rand -hex 32)"
# Set the other private local configuration without printing or committing credentials.
./gradlew :contentService:run
```

In another terminal with the privately retained token, import taxonomy before game packs:

```bash
curl -fsS http://127.0.0.1:8080/v1/jobs/import \
  -H "Authorization: Bearer $ASHVA_SERVICE_TOKEN" -H 'Content-Type: application/json' \
  --data '{"packId":"lichess-openings-c67912be581f-import-v1"}'
curl -fsS 'http://127.0.0.1:8080/v1/openings?q=Ruy%20Lopez&limit=20'
```

Poll the authenticated returned job URL before searching. Errors expose codes, not raw content/secrets. Mutations/job reads/metrics require authorization; browser Origin requests are rejected, CORS is absent, bodies are JSON-only/64KiB/5-second-read bounded. Mutation admission has5-request burst/one-per-second refill. This is **local single-operator authentication**, not production accounts. TLS, hosted storage, external binding, internet security/load review, distribution compliance, provider/budget selection and Android opt-in downloads remain separate decisions. No Android internet permission/upload was added.
