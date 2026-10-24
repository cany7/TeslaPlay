// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay.web

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigInteger
import java.net.URI
import java.net.URLDecoder
import java.security.KeyPair
import java.security.KeyStore
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AcmeClientTest {
    private val config = DuckDnsConfig.create("TeslaCar", "01234567-89ab-cdef-0123-456789abcdef")
    private val provider = BouncyCastleProvider()
    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)
    private fun json(value: String) = JSONObject(decode(value).toString(Charsets.UTF_8))
    private fun pem(vararg certs: X509Certificate) = certs.flatMap {
        AcmeClient.pem("CERTIFICATE", it.encoded).asIterable()
    }.toByteArray()

    private inner class Ca {
        val keys = AcmeClient.newRsaKey()
        val root = certificate(keys, "Test Root", true)
        private fun certificate(server: KeyPair, name: String, root: Boolean, expired: Boolean = false): X509Certificate {
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(X500Name("CN=Test Root"), BigInteger.valueOf(if (root) 1 else 2),
                Date(now - 120000), Date(now + if (expired) -60000 else 86400000), X500Name("CN=$name"), server.public)
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(root))
            if (!root) builder.addExtension(Extension.subjectAlternativeName, false,
                GeneralNames(GeneralName(GeneralName.dNSName, name)))
            return JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(
                JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keys.private)))
        }
        fun chain(server: KeyPair, name: String = config.domain, expired: Boolean = false) =
            pem(certificate(server, name, false, expired), root)
        fun fromCsr(csr: PKCS10CertificationRequest): ByteArray {
            assertTrue(csr.isSignatureValid(JcaContentVerifierProviderBuilder().setProvider(provider).build(csr.subjectPublicKeyInfo)))
            val extensions = Extensions.getInstance(csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)
                .single().attrValues.getObjectAt(0))
            val names = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName)
            assertEquals(config.domain, names.names.single().name.toString())
            val now = System.currentTimeMillis()
            val builder = org.bouncycastle.cert.X509v3CertificateBuilder(X500Name("CN=Test Root"), BigInteger.TEN,
                Date(now - 60000), Date(now + 86400000), csr.subject, csr.subjectPublicKeyInfo)
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            builder.addExtension(Extension.subjectAlternativeName, false, names)
            val leaf = JcaX509CertificateConverter().setProvider(provider).getCertificate(builder.build(
                JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(keys.private)))
            return pem(leaf, root)
        }
        fun trust(): X509TrustManager {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("root", root) }
            return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
                .trustManagers.filterIsInstance<X509TrustManager>().single()
        }
    }

    /** Verify real JWS signatures, nonces, POST-as-GET bodies and the signed CSR across a fake CA. */
    private inner class CaHttp(val accountKeys: KeyPair, val ca: Ca) : AcmeHttp {
        val dnsQueries = mutableListOf<Map<String, String>>()
        val methods = mutableListOf<Pair<String, String>>()
        var accountAttempts = 0
        var validationStatus = "valid"
        var pollingRequests = 0
        var forceBadNonce = false
        private var nonce = "first-nonce"
        private var counter = 0
        private var challenged = false
        private var finalized = false
        private var chain = byteArrayOf()
        override fun request(method: String, url: String, body: ByteArray?): AcmeHttp.Response {
            val uri = URI(url)
            val path = uri.path
            methods.add(method to path)
            fun response(code: Int = 200, value: String = "{}", location: String? = null): AcmeHttp.Response {
                nonce = "nonce-${++counter}"
                val headers = mutableMapOf("Replay-Nonce" to nonce)
                if (location != null) headers["Location"] = "https://ca.test$location"
                return AcmeHttp.Response(code, headers, value.toByteArray())
            }
            if (uri.host == "www.duckdns.org") {
                val params = uri.rawQuery.split('&').associate {
                    val parts = it.split('=', limit = 2)
                    parts[0] to URLDecoder.decode(parts[1], "UTF-8")
                }
                dnsQueries.add(params)
                assertEquals(config.token, params["token"])
                return AcmeHttp.Response(200, emptyMap(), "OK\n".toByteArray())
            }
            if (path == "/directory") {
                assertEquals("GET", method)
                return AcmeHttp.Response(200, emptyMap(), """{"newNonce":"https://ca.test/nonce","newAccount":"https://ca.test/account","newOrder":"https://ca.test/order"}""".toByteArray())
            }
            if (path == "/nonce") { assertEquals("HEAD", method); return response() }
            assertEquals("POST", method)
            val envelope = JSONObject(body!!.toString(Charsets.UTF_8))
            val protected = envelope.getString("protected")
            val payload = envelope.getString("payload")
            val header = json(protected)
            assertEquals(nonce, header.getString("nonce"))
            assertEquals(url, header.getString("url"))
            assertEquals("RS256", header.getString("alg"))
            assertTrue(Signature.getInstance("SHA256withRSA").apply {
                initVerify(accountKeys.public); update("$protected.$payload".toByteArray(Charsets.US_ASCII))
            }.verify(decode(envelope.getString("signature"))))
            if (path == "/account") {
                assertTrue(header.has("jwk")); assertFalse(header.has("kid"))
                assertTrue(json(payload).getBoolean("termsOfServiceAgreed"))
                accountAttempts++
                if (accountAttempts == 1 || forceBadNonce) return response(400, """{"type":"urn:ietf:params:acme:error:badNonce"}""")
                return response(201, "{}", "/account/1")
            }
            assertFalse(header.has("jwk")); assertEquals("https://ca.test/account/1", header.getString("kid"))
            return when (path) {
                "/order" -> {
                    assertEquals(config.domain, json(payload).getJSONArray("identifiers").getJSONObject(0).getString("value"))
                    response(201, """{"authorizations":["https://ca.test/authz"],"finalize":"https://ca.test/finalize"}""", "/order/1")
                }
                "/authz" -> {
                    assertEquals("", payload)
                    if (challenged) pollingRequests++
                    response(value = """{"identifier":{"type":"dns","value":"${config.domain}"},"status":"${if (challenged) validationStatus else "pending"}","challenges":[{"type":"dns-01","token":"0123456789abcdef","url":"https://ca.test/challenge"}]}""")
                }
                "/challenge" -> {
                    assertEquals(0, json(payload).length()); challenged = true; response()
                }
                "/order/1" -> {
                    assertEquals("", payload)
                    response(value = if (finalized) """{"status":"valid","certificate":"https://ca.test/certificate"}""" else """{"status":"ready"}""")
                }
                "/finalize" -> {
                    chain = ca.fromCsr(PKCS10CertificationRequest(decode(json(payload).getString("csr"))))
                    finalized = true; response()
                }
                "/certificate" -> {
                    assertEquals("", payload)
                    AcmeHttp.Response(200, mapOf("Replay-Nonce" to "final"), chain)
                }
                else -> error("Unexpected fixture path")
            }
        }
    }

    @Test fun signsRetriesNonceValidatesDnsAndInstallsOnlyMatchingTrustedCertificate() {
        val keys = AcmeClient.newRsaKey()
        val ca = Ca()
        val http = CaHttp(keys, ca)
        val waits = mutableListOf<Long>()
        val statuses = mutableListOf<AcmeClient.Status>()
        val client = AcmeClient(http, { keys }, "https://ca.test/directory", { waits.add(it) }) { chain, key, domain ->
            AcmeCertificates.validate(chain, key, domain, ca.trust())
        }
        val issued = client.issue(config) { statuses.add(it) }
        issued.use {
            assertTrue(it.expiresAt > System.currentTimeMillis())
            it.prepare(config.domain).use { prepared -> assertTrue(prepared.endpoint.custom) }
            assertTrue(runCatching { it.prepare("other.duckdns.org") }.isFailure)
        }
        assertTrue(issued.privateKeyPem.all { it == 0.toByte() })
        assertEquals(2, http.accountAttempts)
        assertEquals(listOf(60000L), waits)
        assertEquals(AcmeClient.Status.CHECKING_CERTIFICATE, statuses.last())
        assertEquals(AcmeClient.dnsValue("0123456789abcdef", keys.public as RSAPublicKey), http.dnsQueries.first()["txt"])
        assertEquals("true", http.dnsQueries.last()["clear"])
        assertEquals("", http.dnsQueries.last()["txt"])
        assertTrue(http.methods.contains("HEAD" to "/nonce"))
    }

    @Test fun failedAuthorizationAndPollingTimeoutAlwaysClearTxtAndNeverRequestCertificate() {
        listOf("invalid", "pending").forEach { state ->
            val keys = AcmeClient.newRsaKey()
            val http = CaHttp(keys, Ca()).apply { validationStatus = state }
            val result = runCatching { AcmeClient(http, { keys }, "https://ca.test/directory", {}).issue(config) }
            assertEquals(if (state == "invalid") AcmeFailure.Reason.VALIDATION else AcmeFailure.Reason.TIMEOUT,
                (result.exceptionOrNull() as AcmeFailure).reason)
            assertEquals(if (state == "invalid") 1 else 36, http.pollingRequests)
            assertEquals("true", http.dnsQueries.last()["clear"])
            assertFalse(http.methods.any { it.second == "/certificate" })
        }
    }

    @Test fun repeatedBadNonceStopsAfterThreeSignedAttempts() {
        val keys = AcmeClient.newRsaKey()
        val http = CaHttp(keys, Ca()).apply { forceBadNonce = true }
        val result = runCatching { AcmeClient(http, { keys }, "https://ca.test/directory", {}).issue(config) }
        assertEquals(AcmeFailure.Reason.PROTOCOL, (result.exceptionOrNull() as AcmeFailure).reason)
        assertEquals(3, http.accountAttempts)
        assertTrue(http.dnsQueries.isEmpty())
    }

    @Test fun domainKeyExpiryAndTrustAreAllRequired() {
        val ca = Ca()
        val keys = AcmeClient.newRsaKey()
        val privatePem = AcmeClient.pem("PRIVATE KEY", keys.private.encoded)
        val chain = ca.chain(keys)
        assertTrue(AcmeCertificates.validate(chain, privatePem, config.domain, ca.trust()) > System.currentTimeMillis())
        assertTrue(runCatching { AcmeCertificates.validate(chain, privatePem, "other.duckdns.org", ca.trust()) }.isFailure)
        assertTrue(runCatching { AcmeCertificates.validate(chain,
            AcmeClient.pem("PRIVATE KEY", AcmeClient.newRsaKey().private.encoded), config.domain, ca.trust()) }.isFailure)
        assertTrue(runCatching { AcmeCertificates.validate(ca.chain(keys, expired = true), privatePem, config.domain, ca.trust()) }.isFailure)
        assertTrue(runCatching { AcmeCertificates.validate(chain, privatePem, config.domain, Ca().trust()) }.isFailure)
    }

    @Test fun accountKeyPersistsOnlyInPrivateNonBackupStorage() {
        val context = RuntimeEnvironment.getApplication()
        val first = AcmePrivateStore(context).accountKey()
        val second = AcmePrivateStore(context).accountKey()
        assertArrayEquals(first.private.encoded, second.private.encoded)
        assertTrue(java.io.File(context.noBackupFilesDir, "teslaplay-acme-account.pk8").exists())
        assertFalse(java.io.File(context.filesDir, "teslaplay-acme-account.pk8").exists())
    }

    @Test fun duckDnsRequiresExplicitAddressAndNeverExposesTokenFromTransportErrors() {
        assertEquals("teslacar.duckdns.org", config.domain)
        assertTrue(runCatching { DuckDnsConfig.create("car.example.org", config.token) }.isFailure)
        assertTrue(runCatching { DuckDnsConfig.create("car-", config.token) }.isFailure)
        assertFalse(config.toString().contains(config.token))
        assertTrue(DuckDnsClient.validIpv4("192.168.43.1"))
        listOf("", "example.org", "0.0.0.0", "127.0.0.1", "192.168.1.256", "192.168.1.01").forEach {
            assertFalse(DuckDnsClient.validIpv4(it))
        }
        val queries = mutableListOf<String>()
        val dns = DuckDnsClient(object : AcmeHttp {
            override fun request(method: String, url: String, body: ByteArray?): AcmeHttp.Response {
                queries.add(url)
                return AcmeHttp.Response(200, emptyMap(), "OK".toByteArray())
            }
        })
        dns.updateAddress(config, "192.168.43.1", clearPrevious = true)
        assertTrue(queries[0].contains("clear=true"))
        assertTrue(queries[1].contains("ip=192.168.43.1"))
        dns.updateAddress(config, "192.168.43.1")
        assertEquals(3, queries.size)
        assertFalse(queries[2].contains("clear="))
        val broken = DuckDnsClient(object : AcmeHttp {
            override fun request(method: String, url: String, body: ByteArray?): AcmeHttp.Response = throw java.io.IOException(url)
        })
        val error = runCatching { broken.setTxt(config, "proof") }.exceptionOrNull()!!
        assertFalse(error.toString().contains(config.token))
        assertNull(error.cause)
    }
}
