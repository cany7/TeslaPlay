// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay.web

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal object AcmeCertificates {
    /** LanTls verifies key matching, chain ordering/signatures, server EKU and certificate validity. */
    fun validate(certificates: ByteArray, privateKey: ByteArray, domain: String): Long =
        validate(certificates, privateKey, domain, systemTrustManager())

    internal fun validate(
        certificates: ByteArray,
        privateKey: ByteArray,
        domain: String,
        trust: X509TrustManager,
    ): Long {
        try {
            LanTls.preparePem(certificates, privateKey).use { /* Only prepare: preserve installed identity. */ }
            val chain = CertificateFactory.getInstance("X.509").generateCertificates(certificates.inputStream())
                .map { it as X509Certificate }.toTypedArray()
            val leaf = chain.first()
            // Issue only a single exact DuckDNS name. A CN alone or wildcard is not sufficient.
            val names = leaf.subjectAlternativeNames.orEmpty()
                .filter { it.size >= 2 && it[0] == 2 }.mapNotNull { it[1] as? String }
            if (names.none { it.equals(domain, ignoreCase = true) }) throw AcmeFailure(AcmeFailure.Reason.CERTIFICATE)
            trust.checkServerTrusted(chain, leaf.publicKey.algorithm)
            return leaf.notAfter.time
        } catch (error: AcmeFailure) {
            throw error
        } catch (_: Exception) {
            throw AcmeFailure(AcmeFailure.Reason.CERTIFICATE)
        }
    }

    private fun systemTrustManager(): X509TrustManager = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
        .trustManagers.filterIsInstance<X509TrustManager>().first()
}
