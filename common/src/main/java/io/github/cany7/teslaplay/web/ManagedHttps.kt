// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay.web

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** DuckDNS credentials and retry state never enter SharedPreferences or ordinary backups. */
internal object ManagedHttps {
    private const val JOB = 0x54504c
    private const val HOUR = 3_600_000L
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "teslaplay-acme") }
    private val active = AtomicBoolean(false)
    val busy get() = active.get()
    @Volatile var progress: String = ""
        private set
    class Config(val domain: String, val token: String, val address: String, val enabled: Boolean)
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "managed-https.json"))
    @Synchronized private fun read(context: Context): JSONObject = runCatching {
        file(context).openRead().bufferedReader().use { JSONObject(it.readText()) }
    }.getOrElse { JSONObject() }
    @Synchronized private fun write(context: Context, data: JSONObject) {
        val target = file(context)
        val output = target.startWrite()
        try { output.write(data.toString().toByteArray()); target.finishWrite(output) }
        catch (e: Exception) { target.failWrite(output); throw e }
    }
    @Synchronized fun config(context: Context): Config? = read(context).let {
        if (!it.has("domain")) null else Config(it.getString("domain"), it.getString("token"),
            it.getString("address"), it.optBoolean("enabled"))
    }
    data class Status(
        val mode: WebListenSettings.CertificateType,
        val task: String,
        val enabled: Boolean,
        val installed: LanTls.CertificateInfo?,
        val serving: LanTls.CertificateInfo?,
    )

    /** Task completion is deliberately separate from the installed and live identities. */
    fun status(context: Context): Status {
        migrateLegacyCertificate(context)
        return Status(WebListenSettings.certificateType(context), state(context),
            config(context)?.enabled == true, LanTls.installedCertificate(context),
            WebSession.tls?.info.takeIf { WebSession.httpsReady })
    }

    @Synchronized
    internal fun migrateLegacyCertificate(context: Context) {
        val data = read(context)
        if (!data.has("domain") || data.optInt("version") >= 2) return
        val domain = data.optString("domain")
        val matching = LanTls.customExpiry(context, domain) != null
        if (data.optBoolean("enabled") && matching &&
            WebListenSettings.certificateType(context) == WebListenSettings.CertificateType.PEM) {
            WebListenSettings.saveCertificateSelection(context, WebListenSettings.CertificateType.DUCKDNS, domain)
        } else if (data.optString("state") == "ready" && !matching) {
            // Do not silently reissue a historical success whose installed identity was removed.
            data.put("enabled", false)
        }
        val address = TeslaHttpCompatibility.config(context).accessAddress
        if (data.optString("address") != address) {
            data.put("address", address).put("dnsConfigured", false).put("nextAttempt", 0L)
        }
        write(context, data.put("version", 2))
    }

    fun validAddress(value: String) = DuckDnsClient.validIpv4(value)
    @Synchronized fun configure(context: Context, domain: String, token: String, address: String) {
        check(!busy) { "Certificate request in progress" }
        val checked = DuckDnsConfig.create(domain, token.ifBlank { config(context)?.token.orEmpty() })
        require(validAddress(address)) { "Enter the IPv4 address reachable by the Tesla browser" }
        val previous = config(context)
        val sameAddress = previous?.domain == checked.domain && previous.address == address
        write(context, JSONObject().put("version", 2).put("domain", checked.domain).put("token", checked.token)
            .put("address", address).put("enabled", true).put("nextAttempt", 0L)
            .put("dnsConfigured", sameAddress && read(context).optBoolean("dnsConfigured")))
        schedule(context, 1_000)
    }
    @Synchronized fun disable(context: Context) {
        check(!busy) { "Certificate request in progress" }
        val previous = read(context)
        try {
            write(context, JSONObject(previous.toString()).put("enabled", false))
            context.getSystemService(JobScheduler::class.java).cancel(JOB)
        } catch (failure: Exception) {
            try { write(context, previous) } catch (restore: Exception) { failure.addSuppressed(restore) }
            throw failure
        }
    }
    fun state(context: Context): String = if (busy) progress else read(context).optString("state", "idle")
    fun nextAttempt(context: Context) = read(context).optLong("nextAttempt", 0)
    fun expiresAt(context: Context): Long? = config(context)?.let { LanTls.customExpiry(context, it.domain) }
    fun schedule(context: Context, delay: Long? = null) {
        migrateLegacyCertificate(context)
        if (config(context)?.enabled != true) return
        val wait = delay ?: (nextAttempt(context) - System.currentTimeMillis()).coerceAtLeast(1_000)
        context.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(JOB, ComponentName(context, ManagedHttpsJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(wait)
                .setPersisted(true).setBackoffCriteria(HOUR, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
    /** One worker is shared by the settings button and OS jobs. A failed issue keeps the old identity. */
    fun request(context: Context, force: Boolean = false, finished: () -> Unit = {}): Boolean {
        val app = context.applicationContext
        val snapshot = synchronized(this) {
            var value = config(app)?.takeIf { it.enabled } ?: return false
            if (busy) return false
            val address = TeslaHttpCompatibility.config(app).accessAddress
            if (value.address != address) {
                write(app, read(app).put("address", address).put("dnsConfigured", false).put("nextAttempt", 0L))
                value = Config(value.domain, value.token, address, true)
            }
            if (!active.compareAndSet(false, true)) return false
            value
        }
        progress = "connecting"
        worker.execute {
            var success = false
            try {
                val dns = DuckDnsConfig.create(snapshot.domain, snapshot.token)
                DuckDnsClient(InternetHttp(app)).updateAddress(dns, snapshot.address,
                    clearPrevious = !read(app).optBoolean("dnsConfigured"))
                synchronized(this) { write(app, read(app).put("dnsConfigured", true)) }
                val expiry = LanTls.customExpiry(app, snapshot.domain)
                if (force || renewalDue(expiry) ||
                    WebListenSettings.certificateType(app) != WebListenSettings.CertificateType.DUCKDNS) {
                    AcmeClient(app).issue(dns) { progress = it.name }.use { issued ->
                        issued.prepare(snapshot.domain).use { prepared ->
                            progress = "INSTALLING"
                            check(WebSession.installCertificate(app, prepared, managed = true,
                                hostname = snapshot.domain) == null)
                        }
                    }
                }
                progress = "ready"
                success = true
            } catch (failure: AcmeFailure) {
                progress = failure.reason.name
            } catch (_: Exception) {
                // Never persist remote response bodies, URLs, token, account or private key details.
                progress = if (progress == "INSTALLING") "install_failed" else "failed"
            } finally {
                synchronized(this) {
                    try {
                        val data = read(app)
                        val failures = if (success) 0 else (data.optInt("failures") + 1).coerceAtMost(6)
                        val delay = if (success) 12 * HOUR else retryDelay(failures)
                        data.put("failures", failures).put("state", progress)
                            .put("nextAttempt", System.currentTimeMillis() + delay)
                        write(app, data)
                    } catch (_: Exception) { progress = "storage" }
                    finally { active.set(false) }
                }
                try { schedule(app) } finally { finished() }
            }
        }
        return true
    }
    internal fun renewalDue(expiresAt: Long?, now: Long = System.currentTimeMillis()) =
        expiresAt == null || expiresAt <= now + 30L * 24 * HOUR

    internal fun retryDelay(failures: Int) = (HOUR * (1L shl (failures - 1).coerceIn(0, 5))).coerceAtMost(24 * HOUR)
}

/** Persisted one-shot job permits expiry-based renewal and bounded exponential retry while App is closed. */
class ManagedHttpsJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean =
        ManagedHttps.request(this) { jobFinished(params, false) }
    // The HTTPS operation is bounded by its network/poll timeouts; its shared lock prevents a duplicate issue.
    override fun onStopJob(params: JobParameters) = true
}
