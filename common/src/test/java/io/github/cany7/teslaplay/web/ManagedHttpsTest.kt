// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay.web

import android.app.job.JobScheduler
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigInteger
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ManagedHttpsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val token = "01234567-89ab-cdef-0123-456789abcdef"
    private val domain = "testcar.duckdns.org"
    private val hour = 3_600_000L
    private fun stateFile() = File(context.noBackupFilesDir, "managed-https.json")
    private fun state() = JSONObject(stateFile().readText())

    private fun identity(expiresAt: Long): LanTls.Prepared {
        val keys = AcmeClient.newRsaKey()
        val name = X500Name("CN=$domain")
        val builder = JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date(System.currentTimeMillis() - hour),
            Date(expiresAt), name, keys.public)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.dNSName, domain)))
        val provider = BouncyCastleProvider()
        val certificate = JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(
            JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keys.private)))
        return LanTls.preparePem(AcmeClient.pem("CERTIFICATE", certificate.encoded), AcmeClient.pem("PRIVATE KEY", keys.private.encoded))
    }

    @Test fun configAndTokenLiveOnlyInNoBackupAndScheduleCanBeDisabled() {
        ManagedHttps.configure(context, "TestCar", token, "192.168.43.1")
        val saved = ManagedHttps.config(context)!!
        assertEquals(domain, saved.domain)
        assertEquals(token, saved.token)
        assertTrue(saved.enabled)
        assertFalse(saved.toString().contains(token))
        assertEquals(token, state().getString("token"))
        assertFalse(File(context.filesDir, "managed-https.json").exists())
        val jobs = context.getSystemService(JobScheduler::class.java)
        assertTrue(jobs.allPendingJobs.any { it.service.className == ManagedHttpsJobService::class.java.name && it.isPersisted })
        ManagedHttps.disable(context)
        assertFalse(ManagedHttps.config(context)!!.enabled)
        assertTrue(jobs.allPendingJobs.none { it.service.className == ManagedHttpsJobService::class.java.name })
        assertFalse(ManagedHttps.request(context))
    }

    @Test fun invalidConfigKeepsSavedCredentialsAndBlankTokenRetainsExistingToken() {
        ManagedHttps.configure(context, domain, token, "192.168.43.1")
        val original = stateFile().readText()
        assertTrue(runCatching { ManagedHttps.configure(context, "wrong.example.com", token, "192.168.43.1") }.isFailure)
        assertTrue(runCatching { ManagedHttps.configure(context, domain, "bad", "192.168.43.1") }.isFailure)
        assertTrue(runCatching { ManagedHttps.configure(context, domain, token, "") }.isFailure)
        assertEquals(original, stateFile().readText())
        stateFile().writeText(state().put("dnsConfigured", true).toString())
        ManagedHttps.configure(context, domain, "", "192.168.43.1")
        assertEquals(token, ManagedHttps.config(context)!!.token)
        assertTrue(state().getBoolean("dnsConfigured"))
        ManagedHttps.configure(context, domain, "", "192.168.44.1")
        assertFalse(state().getBoolean("dnsConfigured"))
    }

    @Test fun renewalUsesInstalledCertificateExpiryAndExactDomain() {
        val expiry = (System.currentTimeMillis() + 40 * 24 * hour) / 1000 * 1000
        assertNull(LanTls.customExpiry(context, domain))
        identity(expiry).use { LanTls.saveCustom(context, it) }
        assertEquals(expiry, LanTls.customExpiry(context, domain))
        assertNull(LanTls.customExpiry(context, "another.duckdns.org"))
        ManagedHttps.configure(context, domain, token, "192.168.43.1")
        assertEquals(expiry, ManagedHttps.expiresAt(context))
        assertFalse(ManagedHttps.renewalDue(expiry, expiry - 30 * 24 * hour - 1))
        assertTrue(ManagedHttps.renewalDue(expiry, expiry - 30 * 24 * hour))
        assertTrue(ManagedHttps.renewalDue(expiry, expiry + 1))
        assertTrue(ManagedHttps.renewalDue(null, expiry))
    }

    @Test fun retriesGrowExponentiallyButRemainBounded() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 24L, 24L).map { it * hour },
            (1..7).map(ManagedHttps::retryDelay))
    }

    @Test fun offlineRequestIsExclusiveAndKeepsPreviousCertificateForRetry() {
        val expiry = System.currentTimeMillis() + 40 * 24 * hour
        identity(expiry).use { prepared ->
            LanTls.saveCustom(context, prepared)
            val fingerprint = LanTls.create(context, emptyList()).fingerprint
            ManagedHttps.configure(context, domain, token, "192.168.43.1")
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            for (network in connectivity.allNetworks) {
                val capabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
                Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Shadows.shadowOf(connectivity).setNetworkCapabilities(network, capabilities)
            }
            val done = CountDownLatch(1)
            val before = System.currentTimeMillis()
            // Holding the scheduling lock prevents completion racing the second request in this test.
            synchronized(ManagedHttps) {
                assertTrue(ManagedHttps.request(context, force = true) { done.countDown() })
                assertFalse(ManagedHttps.request(context, force = true))
                assertNotNull(WebSession.installCertificate(context, prepared))
                assertNotNull(WebSession.restoreDefaultCertificate(context))
                assertNotNull(WebSession.setHostname(context, "another.duckdns.org"))
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertFalse(ManagedHttps.busy)
            assertEquals("OFFLINE", ManagedHttps.state(context))
            assertEquals(TeslaHttpCompatibility.config(context).accessAddress, ManagedHttps.config(context)!!.address)
            assertEquals(fingerprint, LanTls.create(context, emptyList()).fingerprint)
            assertTrue(ManagedHttps.nextAttempt(context) >= before + hour)
            assertTrue(ManagedHttps.config(context)!!.enabled)
        }
    }

    @Test fun manualCertificateDisablesAutomaticReplacement() {
        ManagedHttps.configure(context, domain, token, "192.168.43.1")
        identity(System.currentTimeMillis() + 24 * hour).use { prepared ->
            assertNull(WebSession.installCertificate(context, prepared))
        }
        assertFalse(ManagedHttps.config(context)!!.enabled)
        assertNotNull(LanTls.customExpiry(context, domain))
    }
    @Test fun changingManualHostnameDisablesAutomaticDomainManagement() {
        ManagedHttps.configure(context, domain, token, "192.168.43.1")
        assertNull(WebSession.setHostname(context, domain))
        assertTrue(ManagedHttps.config(context)!!.enabled)
        assertNull(WebSession.setHostname(context, "different.duckdns.org"))
        assertEquals("different.duckdns.org", WebListenSettings.hostname(context))
        assertFalse(ManagedHttps.config(context)!!.enabled)
        assertFalse(ManagedHttps.request(context))
    }

    @Test fun historicalSuccessWithoutInstalledIdentityIsNotReportedAsInstalledOrReissued() {
        stateFile().parentFile.mkdirs()
        stateFile().writeText(JSONObject().put("domain", domain).put("token", token)
            .put("address", "6.6.6.6").put("enabled", false).put("state", "ready").toString())
        WebListenSettings.saveCertificateType(context, WebListenSettings.CertificateType.DEFAULT)
        val status = ManagedHttps.status(context)
        assertEquals("ready", status.task)
        assertFalse(status.enabled)
        assertNull(status.installed)
        assertEquals(WebListenSettings.CertificateType.DEFAULT, status.mode)
        ManagedHttps.schedule(context)
        assertFalse(ManagedHttps.request(context))
        assertTrue(context.getSystemService(JobScheduler::class.java).allPendingJobs.isEmpty())
    }

    @Test fun oldEnabledPemMigratesOnlyWithMatchingInstalledIdentity() {
        identity(System.currentTimeMillis() + 40 * 24 * hour).use { LanTls.saveCustom(context, it) }
        WebListenSettings.saveCertificateType(context, WebListenSettings.CertificateType.PEM)
        stateFile().writeText(JSONObject().put("domain", domain).put("token", token)
            .put("address", "6.6.6.6").put("enabled", true).put("state", "ready")
            .put("nextAttempt", System.currentTimeMillis() + 12 * hour).toString())
        val status = ManagedHttps.status(context)
        assertEquals(WebListenSettings.CertificateType.DUCKDNS, status.mode)
        assertTrue(status.enabled)
        assertNotNull(status.installed)
        assertEquals(domain, WebListenSettings.hostname(context))
        assertFalse(ManagedHttps.busy)
        assertEquals("100.96.0.1", ManagedHttps.config(context)!!.address)
        assertEquals(0L, ManagedHttps.nextAttempt(context))
        assertFalse(state().getBoolean("dnsConfigured"))
        assertFalse(ManagedHttps.renewalDue(ManagedHttps.expiresAt(context)))
        val jobs = context.getSystemService(JobScheduler::class.java)
        assertTrue(jobs.allPendingJobs.isEmpty())
        ManagedHttps.schedule(context)
        assertEquals(1_000L, jobs.allPendingJobs.single().minLatencyMillis)
    }

    @Test fun legacySuccessWithRemovedCertificateStopsBeforeAnyRenewal() {
        stateFile().parentFile.mkdirs()
        stateFile().writeText(JSONObject().put("domain", domain).put("token", token)
            .put("address", "6.6.6.6").put("enabled", true).put("state", "ready").toString())
        WebListenSettings.saveCertificateType(context, WebListenSettings.CertificateType.PEM)
        ManagedHttps.schedule(context)
        assertFalse(ManagedHttps.config(context)!!.enabled)
        assertNull(ManagedHttps.status(context).installed)
        assertFalse(ManagedHttps.request(context))
    }

    @Test fun stoppedRenewalKeepsDuckDnsIdentityAndExplicitRestoreClearsIt() {
        ManagedHttps.configure(context, domain, token, "100.96.0.1")
        identity(System.currentTimeMillis() + 40 * 24 * hour).use {
            assertNull(WebSession.installCertificate(context, it, managed = true, hostname = domain))
        }
        val installed = ManagedHttps.status(context).installed!!
        assertEquals(WebListenSettings.CertificateType.DUCKDNS, WebListenSettings.certificateType(context))
        ManagedHttps.disable(context)
        val stopped = ManagedHttps.status(context)
        assertFalse(stopped.enabled)
        assertEquals(WebListenSettings.CertificateType.DUCKDNS, stopped.mode)
        assertEquals(installed.fingerprint, stopped.installed!!.fingerprint)
        assertEquals(installed.fingerprint, LanTls.create(context, emptyList()).info!!.fingerprint)
        assertNull(WebSession.restoreDefaultCertificate(context))
        val restored = ManagedHttps.status(context)
        assertEquals(WebListenSettings.CertificateType.DEFAULT, restored.mode)
        assertNull(restored.installed)
        assertFalse(restored.enabled)
    }

    @Test fun metadataCommitFailureRestoresPreviousPrivateIdentity() {
        val expiry = System.currentTimeMillis() + 40 * 24 * hour
        identity(expiry).use { LanTls.saveCustom(context, it) }
        val before = LanTls.installedCertificate(context)!!.fingerprint
        identity(expiry).use {
            assertTrue(runCatching {
                LanTls.replaceCustom(context, it) { error("Simulated metadata write failure") }
            }.isFailure)
        }
        assertEquals(before, LanTls.installedCertificate(context)!!.fingerprint)
        assertTrue(runCatching {
            LanTls.replaceCustom(context, null) { error("Simulated metadata write failure") }
        }.isFailure)
        assertEquals(before, LanTls.installedCertificate(context)!!.fingerprint)
    }

}
