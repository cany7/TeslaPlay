// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay.web

import android.content.Context
import android.util.AtomicFile
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

/**
 * DNS-01 flow adapted from DashPlay own_domain.py (562e5b102d3ed63f593acc45c35d1f455b60f771).
 * The Android RFC 8555 transport replaces its acme.sh subprocess; no inbound port is required.
 * https://www.rfc-editor.org/rfc/rfc8555.html
 */
internal class AcmeClient(
    private val http: AcmeHttp,
    private val accountKey: () -> KeyPair,
    private val directoryUrl: String = DIRECTORY,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val validate: (ByteArray, ByteArray, String) -> Long = AcmeCertificates::validate,
) {
    constructor(context: Context) : this(InternetHttp(context), { AcmePrivateStore(context).accountKey() })

    enum class Status { ACCOUNT, DNS_CHALLENGE, DNS_PROPAGATION, VALIDATING, ISSUING, CHECKING_CERTIFICATE }

    /** Contains private key bytes; caller must close after preparing/installing through LanTls. */
    class IssuedCertificate(
        val certificatePem: ByteArray,
        val privateKeyPem: ByteArray,
        val expiresAt: Long,
        private val domain: String,
    ) : AutoCloseable {
        fun prepare(domain: String): LanTls.Prepared {
            require(domain == this.domain) { "证书域名已改变，请重新申请" }
            return LanTls.preparePem(certificatePem, privateKeyPem)
        }
        override fun close() { privateKeyPem.fill(0) }
    }

    private var nonce: String? = null
    private lateinit var keys: KeyPair
    private lateinit var directory: JSONObject
    private var accountUrl: String? = null

    fun issue(config: DuckDnsConfig, onStatus: (Status) -> Unit = {}): IssuedCertificate {
        if (!issuing.tryLock()) throw AcmeFailure(AcmeFailure.Reason.BUSY)
        try {
            return obtain(config, onStatus)
        } catch (error: AcmeFailure) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AcmeFailure(AcmeFailure.Reason.CANCELLED)
        } catch (_: Exception) {
            // Parser, provider and transport exception text may contain remote bodies or credentials.
            throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
        } finally {
            issuing.unlock()
        }
    }

    private fun obtain(config: DuckDnsConfig, onStatus: (Status) -> Unit): IssuedCertificate {
        nonce = null
        accountUrl = null
        keys = accountKey()
        directory = get(directoryUrl).json()
        onStatus(Status.ACCOUNT)
        val account = post(directory.getString("newAccount"), JSONObject().put("termsOfServiceAgreed", true))
        accountUrl = account.header("Location") ?: throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
        val order = post(directory.getString("newOrder"), JSONObject().put("identifiers", JSONArray().put(
            JSONObject().put("type", "dns").put("value", config.domain))))
        val orderUrl = order.header("Location") ?: throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
        val orderJson = order.json()
        val authorizations = orderJson.getJSONArray("authorizations")
        val dns = DuckDnsClient(http)
        for (index in 0 until authorizations.length()) {
            val authorizationUrl = authorizations.getString(index)
            val authorization = post(authorizationUrl, null).json()
            val identifier = authorization.getJSONObject("identifier")
            if (identifier.optString("type") != "dns" || identifier.optString("value") != config.domain ||
                authorization.optBoolean("wildcard", false)) throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
            if (authorization.optString("status") == "valid") continue
            val challenges = authorization.getJSONArray("challenges")
            val challenge = (0 until challenges.length()).map { challenges.getJSONObject(it) }
                .firstOrNull { it.optString("type") == "dns-01" }
                ?: throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
            val token = challenge.getString("token")
            if (!Regex("[A-Za-z0-9_-]{16,}").matches(token)) throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
            onStatus(Status.DNS_CHALLENGE)
            try {
                dns.setTxt(config, dnsValue(token, keys.public as RSAPublicKey))
                onStatus(Status.DNS_PROPAGATION)
                sleep(60_000) // DuckDNS propagation interval used by DashPlay, no external DNS proxy.
                post(challenge.getString("url"), JSONObject())
                onStatus(Status.VALIDATING)
                poll(authorizationUrl, setOf("valid"), setOf("pending", "processing"))
            } finally {
                // TXT deletion must never hide the original failure or remove A/AAAA records.
                runCatching { dns.clearTxt(config) }
            }
        }
        val ready = poll(orderUrl, setOf("ready", "valid"), setOf("pending", "processing"))
        // A newly created order cannot already be valid without the key for its CSR.
        if (ready.optString("status") != "ready") throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
        onStatus(Status.ISSUING)
        val serverKeys = newRsaKey()
        post(orderJson.getString("finalize"), JSONObject().put("csr", base64(csr(config.domain, serverKeys))))
        val issued = poll(orderUrl, setOf("valid"), setOf("ready", "pending", "processing"))
        val certificates = post(issued.getString("certificate"), null).body
        val privatePem = pem("PRIVATE KEY", serverKeys.private.encoded)
        try {
            onStatus(Status.CHECKING_CERTIFICATE)
            val expiry = validate(certificates, privatePem, config.domain)
            return IssuedCertificate(certificates, privatePem, expiry, config.domain)
        } catch (error: Exception) {
            privatePem.fill(0)
            if (error is AcmeFailure) throw error
            throw AcmeFailure(AcmeFailure.Reason.CERTIFICATE)
        }
    }

    private fun get(url: String): AcmeHttp.Response {
        val response = http.request("GET", url, null)
        nonce = response.header("Replay-Nonce") ?: nonce
        if (response.code !in 200..299) throw AcmeFailure(AcmeFailure.Reason.SERVER)
        return response
    }

    private fun post(url: String, payload: JSONObject?): AcmeHttp.Response {
        repeat(3) {
            val currentNonce = nonce ?: http.request("HEAD", directory.getString("newNonce"), null)
                .also { if (it.code !in 200..299) throw AcmeFailure(AcmeFailure.Reason.SERVER) }
                .header("Replay-Nonce") ?: throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
            nonce = null // A nonce is single-use, including when a request fails.
            val header = JSONObject().put("alg", "RS256").put("nonce", currentNonce).put("url", url)
            if (accountUrl == null) header.put("jwk", JSONObject(jwk(keys.public as RSAPublicKey)))
            else header.put("kid", accountUrl)
            val protected = base64(header.toString().toByteArray())
            // POST-as-GET uses an empty payload, distinct from the {} challenge acknowledgement.
            val encodedPayload = base64(payload?.toString()?.toByteArray() ?: byteArrayOf())
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(keys.private)
                update("$protected.$encodedPayload".toByteArray(Charsets.US_ASCII))
            }.sign()
            val body = JSONObject().put("protected", protected).put("payload", encodedPayload)
                .put("signature", base64(signature)).toString().toByteArray()
            val response = http.request("POST", url, body)
            nonce = response.header("Replay-Nonce")
            if (response.code in 200..299) return response
            val type = runCatching { response.json().optString("type") }.getOrDefault("")
            if (type != "urn:ietf:params:acme:error:badNonce") {
                throw AcmeFailure(if (response.code == 429) AcmeFailure.Reason.RATE_LIMIT else AcmeFailure.Reason.SERVER)
            }
        }
        throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
    }

    private fun poll(url: String, finished: Set<String>, pending: Set<String>): JSONObject {
        // Respect Retry-After while keeping each resource poll bounded to three minutes.
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3)
        repeat(36) {
            if (System.nanoTime() >= deadline) throw AcmeFailure(AcmeFailure.Reason.TIMEOUT)
            val response = post(url, null)
            val value = response.json()
            when (value.optString("status")) {
                in finished -> return value
                !in pending -> throw AcmeFailure(AcmeFailure.Reason.VALIDATION)
            }
            val retry = response.header("Retry-After")
            val seconds = retry?.toLongOrNull() ?: runCatching {
                java.time.ZonedDateTime.parse(retry, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toEpochSecond() - System.currentTimeMillis() / 1000
            }.getOrDefault(5)
            val delay = seconds.coerceAtLeast(1)
            if (delay > 180 || java.util.concurrent.TimeUnit.SECONDS.toNanos(delay) > deadline - System.nanoTime())
                throw AcmeFailure(AcmeFailure.Reason.TIMEOUT)
            sleep(delay * 1000)
        }
        throw AcmeFailure(AcmeFailure.Reason.TIMEOUT)
    }

    companion object {
        const val DIRECTORY = "https://acme-v02.api.letsencrypt.org/directory"
        private val issuing = ReentrantLock()
        internal fun newRsaKey(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        internal fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        private fun unsigned(value: java.math.BigInteger): String = base64(value.toByteArray().let {
            if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        })
        // RFC 7638: canonical lexicographic member order, no whitespace.
        internal fun jwk(key: RSAPublicKey): String = "{\"e\":\"${unsigned(key.publicExponent)}\",\"kty\":\"RSA\",\"n\":\"${unsigned(key.modulus)}\"}"
        internal fun dnsValue(token: String, key: RSAPublicKey): String {
            val hash = MessageDigest.getInstance("SHA-256")
            val thumbprint = base64(hash.digest(jwk(key).toByteArray(Charsets.US_ASCII)))
            return base64(hash.digest("$token.$thumbprint".toByteArray(Charsets.US_ASCII)))
        }
        internal fun csr(domain: String, keys: KeyPair): ByteArray {
            val extensions = ExtensionsGenerator().apply {
                addExtension(Extension.subjectAlternativeName, false,
                    GeneralNames(GeneralName(GeneralName.dNSName, domain)))
            }
            return JcaPKCS10CertificationRequestBuilder(X500Name("CN=$domain"), keys.public)
                .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions.generate())
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider()).build(keys.private)).encoded
        }
        internal fun pem(type: String, bytes: ByteArray): ByteArray =
            ("-----BEGIN $type-----\n" + Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(bytes) +
                "\n-----END $type-----\n").toByteArray(Charsets.US_ASCII)
    }
}

/** Account key alone identifies and recovers the ACME account via newAccount (RFC 8555 §7.3). */
internal class AcmePrivateStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "teslaplay-acme-account.pk8"))
    fun accountKey(): KeyPair = synchronized(lock) {
        if (file.baseFile.exists()) {
            val encoded = file.openRead().use { it.readBytes() }
            try {
                val factory = KeyFactory.getInstance("RSA")
                val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(encoded)) as RSAPrivateCrtKey
                KeyPair(factory.generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)), privateKey)
            } finally { encoded.fill(0) }
        } else {
            AcmeClient.newRsaKey().also { keys ->
                val output = file.startWrite()
                val encoded = keys.private.encoded
                try {
                    output.write(encoded)
                    file.finishWrite(output)
                    file.baseFile.setReadable(false, false)
                    file.baseFile.setReadable(true, true)
                    file.baseFile.setWritable(false, false)
                    file.baseFile.setWritable(true, true)
                } catch (error: Exception) {
                    file.failWrite(output)
                    throw error
                } finally { encoded.fill(0) }
            }
        }
    }
    private companion object { val lock = Any() }
}
