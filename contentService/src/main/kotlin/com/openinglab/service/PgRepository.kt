// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Base64
import java.util.UUID
import java.util.Properties

internal data class ClaimedJob(val id: String, val kind: String, val request: String, val attempts: Int, val lease: String)

/** Separate service schema. All dynamic values use JDBC bindings; no learner SQLite DB is used. */
class PgRepository(private val jdbcUrl: String, private val user: String, private val password: String,
    private val schema: String = "ashva_content", private val now: () -> Long = System::currentTimeMillis) {
    private val connections = Semaphore(8)
    init {
        require(schema.matches(Regex("ashva_(content|test_[a-f0-9]{1,32})")))
        // Local validation only: deployment/TLS/auth/hosting is a separate owner decision.
        require(jdbcUrl.matches(Regex("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/[a-zA-Z0-9_-]{1,80}")))
    }
    private suspend fun <T> db(block: (Connection) -> T): T = connections.withPermit { withContext(Dispatchers.IO) {
        val properties = Properties().apply {
            setProperty("user", user); setProperty("password", password)
            setProperty("connectTimeout", "5"); setProperty("socketTimeout", "15"); setProperty("loginTimeout", "5")
            setProperty("cancelSignalTimeout", "2"); setProperty("ApplicationName", "AshvaLocalService")
        }
        DriverManager.getConnection(jdbcUrl, properties).use { c ->
            c.createStatement().use { it.execute("SET statement_timeout = '10s'"); it.execute("SET search_path TO $schema") }
            block(c)
        }
    } }
    private fun <T> transaction(c: Connection, block: () -> T): T {
        c.autoCommit = false
        try { val result = block(); c.commit(); return result } catch (e: Exception) { c.rollback(); throw e }
    }
    suspend fun initialize() = db { c -> transaction(c) {
        c.createStatement().use { s ->
            s.execute("SELECT pg_advisory_xact_lock(1095977036)")
            s.execute("CREATE SCHEMA IF NOT EXISTS $schema")
            s.execute("SET search_path TO $schema")
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (version integer PRIMARY KEY)")
            s.execute("INSERT INTO schema_version VALUES (1) ON CONFLICT DO NOTHING")
            s.executeQuery("SELECT version FROM schema_version").use { rows -> require(rows.next() && rows.getInt(1) == 1 && !rows.next()) }
            s.execute("CREATE TABLE IF NOT EXISTS packs (hash text PRIMARY KEY, pack_id text UNIQUE NOT NULL, source_id text NOT NULL, descriptor jsonb NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS active_packs (source_id text PRIMARY KEY, hash text NOT NULL REFERENCES packs(hash))")
            s.execute("CREATE TABLE IF NOT EXISTS records (pack_hash text REFERENCES packs(hash), record_id text, kind text NOT NULL CHECK(kind IN ('OPENING','GAME')), search_text text NOT NULL, payload jsonb NOT NULL, PRIMARY KEY(pack_hash,record_id))")
            s.execute("CREATE INDEX IF NOT EXISTS records_kind_key ON records(kind,pack_hash,record_id)")
            s.execute("CREATE INDEX IF NOT EXISTS records_search ON records USING gin(to_tsvector('simple',search_text))")
            s.execute("CREATE TABLE IF NOT EXISTS catalogs (version text PRIMARY KEY, payload jsonb NOT NULL)")
            s.execute("CREATE TABLE IF NOT EXISTS jobs (id text PRIMARY KEY, kind text NOT NULL, request jsonb NOT NULL, status text NOT NULL, attempts integer NOT NULL DEFAULT 0, available_at bigint NOT NULL, lease_until bigint, lease text, expires_at bigint, error_code text, result jsonb)")
            s.execute("CREATE INDEX IF NOT EXISTS jobs_ready ON jobs(status,available_at)")
        }
    } }
    internal suspend fun publish(pack: ValidatedPack, owner: ClaimedJob? = null) = db { c -> transaction(c) {
        c.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(1095977037)") }
        owner?.let { job ->
            c.prepareStatement("SELECT status,lease,lease_until FROM jobs WHERE id=? FOR UPDATE").use { s -> s.setString(1, job.id); s.executeQuery().use {
                if (!it.next() || it.getString(1) != "RUNNING" || it.getString(2) != job.lease || it.getLong(3) <= now()) throw ApiFailure("STALE_IMPORT_LEASE", 409)
            } }
        }
        val descriptor = pack.descriptor; val m = descriptor.manifest
        m.dependencies.forEach { dep ->
            c.prepareStatement("SELECT hash FROM packs WHERE pack_id=?").use { s ->
                s.setString(1, dep.packId); s.executeQuery().use { require(it.next() && it.getString(1) == dep.manifestSha256) { "DEPENDENCY_UNAVAILABLE" } }
            }
        }
        val existing = c.prepareStatement("SELECT hash FROM packs WHERE pack_id=?").use { s ->
            s.setString(1, m.packId); s.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
        require(existing == null || existing == descriptor.manifestSha256) { "IMMUTABLE_PACK_CONFLICT" }
        if (existing == null) {
            c.prepareStatement("INSERT INTO packs VALUES (?,?,?,?::jsonb)").use { s ->
                s.values(descriptor.manifestSha256, m.packId, m.source.id, codec.encodeToString(descriptor)); s.executeUpdate()
            }
            c.prepareStatement("INSERT INTO records VALUES (?,?,?,?,?::jsonb)").use { s ->
                var count = 0
                fun add(id: String, kind: String, text: String, payload: String) {
                    s.values(descriptor.manifestSha256, id, kind, text.lowercase(), payload); s.addBatch()
                    if (++count % 200 == 0) { s.executeBatch(); s.clearBatch() }
                }
                pack.openings.forEach { add(it.id, "OPENING", "${it.eco} ${it.family} ${it.name}", codec.encodeToString(it)) }
                pack.games.forEach { add(it.id, "GAME", "${it.white.name} ${it.black.name} ${it.tags["Event"]} ${it.tags["Date"]} ${it.result}", codec.encodeToString(it)) }
                s.executeBatch()
            }
        }
        c.prepareStatement("INSERT INTO active_packs VALUES (?,?) ON CONFLICT(source_id) DO UPDATE SET hash=excluded.hash").use { s ->
            s.values(m.source.id, descriptor.manifestSha256); s.executeUpdate()
        }
        val catalog = catalog(c)
        owner?.let { complete(c, it, codec.parseToJsonElement(codec.encodeToString(descriptor))) }
        catalog
    } }
    private fun catalog(c: Connection): Catalog {
        val descriptors = c.createStatement().use { s -> s.executeQuery("SELECT p.descriptor::text FROM active_packs a JOIN packs p ON a.hash=p.hash ORDER BY a.source_id").use { rows ->
            buildList { while (rows.next()) add(codec.decodeFromString<PackDescriptor>(rows.getString(1))) }
        } }
        val version = hash(descriptors.joinToString("\n") { "${it.manifest.source.id}:${it.manifestSha256}" })
        val result = Catalog(version, descriptors)
        c.prepareStatement("INSERT INTO catalogs VALUES (?,?::jsonb) ON CONFLICT DO NOTHING").use { s -> s.values(version, codec.encodeToString(result)); s.executeUpdate() }
        return result
    }
    suspend fun catalog(): Catalog = db { c -> transaction(c) { c.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ; catalog(c) } }
    suspend fun delta(from: String): CatalogDelta = db { c -> transaction(c) {
        require(hashPattern.matches(from)); c.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        val current = catalog(c)
        val old = c.prepareStatement("SELECT payload::text FROM catalogs WHERE version=?").use { s -> s.setString(1, from); s.executeQuery().use {
            if (!it.next()) throw ApiFailure("UNKNOWN_CATALOG_VERSION", 409)
            codec.decodeFromString<Catalog>(it.getString(1))
        } }
        val before = old.packs.associateBy { it.manifest.source.id }; val after = current.packs.associateBy { it.manifest.source.id }
        CatalogDelta(from, current.version, current.packs.filter { before[it.manifest.source.id]?.manifestSha256 != it.manifestSha256 }, (before.keys - after.keys).sorted())
    } }
    suspend fun pack(hash: String): PackDescriptor = db { c ->
        if (!hashPattern.matches(hash)) throw ApiFailure("PACK_NOT_FOUND", 404)
        c.prepareStatement("SELECT descriptor::text FROM packs WHERE hash=?").use { s -> s.setString(1, hash); s.executeQuery().use {
            if (!it.next()) throw ApiFailure("PACK_NOT_FOUND", 404)
            codec.decodeFromString<PackDescriptor>(it.getString(1))
        } }
    }
    suspend fun page(kind: String, query: String, limit: Int, cursor: String?): ContentPage = db { c -> transaction(c) {
        require(kind in listOf("OPENING", "GAME") && query.length <= 80 && query.none { it.isISOControl() } && limit in 1..100)
        c.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        val catalog = catalog(c)
        val parsed = cursor?.let {
            require(it.length <= 4096)
            codec.decodeFromString<PageCursor>(Base64.getUrlDecoder().decode(it).decodeToString(throwOnInvalidSequence = true)).also { value ->
                require(value.kind == kind && value.query == query && value.limit == limit && hashPattern.matches(value.lastPack) && value.lastRecord.length <= 200)
                if (value.version != catalog.version) throw ApiFailure("STALE_CURSOR_RESTART", 409)
            }
        }
        val pattern = "%${query.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        val items = c.prepareStatement("SELECT r.pack_hash,r.record_id,r.payload::text FROM records r JOIN active_packs a ON r.pack_hash=a.hash WHERE r.kind=? AND r.search_text LIKE ? ESCAPE '\\' AND (r.pack_hash,r.record_id)>(?,?) ORDER BY r.pack_hash,r.record_id LIMIT ?").use { s ->
            s.values(kind, pattern, parsed?.lastPack ?: "", parsed?.lastRecord ?: ""); s.setInt(5, limit + 1)
            s.executeQuery().use { rows -> buildList { while (rows.next()) add(ContentItem(rows.getString(1), rows.getString(2), codec.parseToJsonElement(rows.getString(3)))) } }
        }
        val shown = items.take(limit)
        val next = if (items.size > limit) shown.last().let { Base64.getUrlEncoder().withoutPadding().encodeToString(
            codec.encodeToString(PageCursor(catalog.version, kind, query, limit, it.packSha256, it.recordId)).encodeToByteArray()) } else null
        ContentPage(catalog.version, catalog.packs.filter { p -> shown.any { it.packSha256 == p.manifestSha256 } }, shown, next)
    } }
    suspend fun enqueue(id: String, kind: String, request: String): JobView = db { c -> transaction(c) {
        require(hashPattern.matches(id) && kind in listOf("IMPORT", "ANALYSIS") && request.encodeToByteArray().size <= 65_536)
        c.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(1095977038)") }
        val old = job(c, id)
        if (old != null) {
            require(old.kind == kind)
            c.prepareStatement("SELECT request=?::jsonb FROM jobs WHERE id=?").use { s -> s.values(request, id); s.executeQuery().use {
                require(it.next() && it.getBoolean(1)) { "JOB_IDENTITY_CONFLICT" }
            } }
            if (old.status == "EXPIRED") {
                c.prepareStatement("UPDATE jobs SET status='PENDING',attempts=0,available_at=?,expires_at=NULL,result=NULL,error_code=NULL WHERE id=?").use { s -> s.setLong(1, now()); s.setString(2, id); s.executeUpdate() }
                return@transaction requireNotNull(job(c, id))
            }
            return@transaction old.copy(cached = old.status == "SUCCEEDED")
        }
        val count = c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM jobs").use { it.next(); it.getLong(1) } }
        if (count >= 1000) throw ApiFailure("JOB_CAPACITY_REACHED", 429)
        c.prepareStatement("INSERT INTO jobs(id,kind,request,status,available_at) VALUES (?,?,?::jsonb,'PENDING',?)").use { s ->
            s.values(id, kind, request); s.setLong(4, now()); s.executeUpdate()
        }
        requireNotNull(job(c, id))
    } }
    private fun job(c: Connection, id: String): JobView? = c.prepareStatement("SELECT id,kind,status,attempts,available_at,expires_at,error_code,result::text FROM jobs WHERE id=?").use { s ->
        s.setString(1, id); s.executeQuery().use { rows -> if (rows.next()) rows.view(now()) else null }
    }
    suspend fun job(id: String): JobView = db { c -> if (!hashPattern.matches(id)) throw ApiFailure("JOB_NOT_FOUND", 404); job(c, id) ?: throw ApiFailure("JOB_NOT_FOUND", 404) }
    internal suspend fun claim(): ClaimedJob? = db { c -> transaction(c) {
        c.prepareStatement("UPDATE jobs SET status='FAILED',error_code='LEASE_RETRY_EXHAUSTED',lease=NULL,lease_until=NULL WHERE status='RUNNING' AND lease_until<=? AND attempts>=3").use { s -> s.setLong(1, now()); s.executeUpdate() }
        c.prepareStatement("SELECT id,kind,request::text,attempts FROM jobs WHERE attempts<3 AND ((status='PENDING' AND available_at<=?) OR (status='RUNNING' AND lease_until<=?)) ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT 1").use { s ->
            s.setLong(1, now()); s.setLong(2, now()); s.executeQuery().use { rows ->
                if (!rows.next()) return@transaction null
                val id = rows.getString(1); val kind = rows.getString(2); val request = rows.getString(3); val attempt = rows.getInt(4) + 1; val lease = UUID.randomUUID().toString()
                c.prepareStatement("UPDATE jobs SET status='RUNNING',attempts=?,lease_until=?,lease=?,error_code=NULL WHERE id=?").use { u ->
                    u.setInt(1, attempt); u.setLong(2, now() + 240_000); u.setString(3, lease); u.setString(4, id); u.executeUpdate()
                }
                ClaimedJob(id, kind, request, attempt, lease)
            }
        }
    } }
    internal suspend fun complete(job: ClaimedJob, result: JsonElement): Boolean = db { c -> complete(c, job, result) }
    private fun complete(c: Connection, job: ClaimedJob, result: JsonElement): Boolean =
        c.prepareStatement("UPDATE jobs SET status='SUCCEEDED',result=?::jsonb,expires_at=?,lease=NULL,lease_until=NULL WHERE id=? AND status='RUNNING' AND lease=? AND lease_until>?").use { s ->
            s.setString(1, result.toString()); if (job.kind == "ANALYSIS") s.setLong(2, now() + 7L * 24 * 3600 * 1000) else s.setNull(2, java.sql.Types.BIGINT)
            s.setString(3, job.id); s.setString(4, job.lease); s.setLong(5, now()); s.executeUpdate() == 1
        }
    internal suspend fun fail(job: ClaimedJob, retryable: Boolean, code: String) = db { c ->
        require(code.matches(Regex("[A-Z_]{1,80}")))
        c.prepareStatement("UPDATE jobs SET status=?,available_at=?,error_code=?,lease=NULL,lease_until=NULL WHERE id=? AND status='RUNNING' AND lease=? AND lease_until>?").use { s ->
            s.setString(1, if (retryable && job.attempts < 3) "PENDING" else "FAILED")
            s.setLong(2, now() + minOf(60_000L, 1000L shl job.attempts)); s.setString(3, code); s.setString(4, job.id); s.setString(5, job.lease); s.setLong(6, now()); s.executeUpdate()
        }
    }
    suspend fun cancel(id: String): JobView = db { c ->
        if (!hashPattern.matches(id)) throw ApiFailure("JOB_NOT_FOUND", 404)
        c.prepareStatement("UPDATE jobs SET status='CANCELLED',lease=NULL,lease_until=NULL WHERE id=? AND status IN ('PENDING','RUNNING')").use { s -> s.setString(1, id); s.executeUpdate() }
        job(c, id) ?: throw ApiFailure("JOB_NOT_FOUND", 404)
    }
    suspend fun metrics(): Metrics = db { c ->
        val counts = c.prepareStatement("SELECT CASE WHEN status='SUCCEEDED' AND expires_at<=? THEN 'EXPIRED' ELSE status END,count(*) FROM jobs GROUP BY 1").use { s ->
            s.setLong(1, now()); s.executeQuery().use { rows -> buildMap { while (rows.next()) put(rows.getString(1), rows.getLong(2)) } }
        }
        val attempts = c.createStatement().use { s -> s.executeQuery("SELECT coalesce(sum(attempts),0) FROM jobs").use { it.next(); it.getLong(1) } }
        Metrics(counts, attempts)
    }
    private fun PreparedStatement.values(vararg values: String) { values.forEachIndexed { i, value -> setString(i + 1, value) } }
    private fun ResultSet.view(now: Long): JobView {
        val expiry = getObject("expires_at")?.let { getLong("expires_at") }; val expired = expiry != null && expiry <= now && getString("status") == "SUCCEEDED"
        return JobView(getString("id"), getString("kind"), if (expired) "EXPIRED" else getString("status"), getInt("attempts"), getLong("available_at"), expiry,
            getString("error_code"), if (expired) null else getString("result")?.let(codec::parseToJsonElement))
    }
}
