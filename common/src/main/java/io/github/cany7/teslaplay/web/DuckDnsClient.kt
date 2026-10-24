// SPDX-License-Identifier: GPL-3.0-only
package io.github.cany7.teslaplay.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Deliberately has no generated toString/copy: the token must never appear in diagnostics. */
internal class DuckDnsConfig private constructor(val domain: String, val token: String) {
    val subdomain: String get() = domain.removeSuffix(".duckdns.org")
    companion object {
        fun create(domain: String, token: String): DuckDnsConfig {
            val label = domain.trim().lowercase(Locale.ROOT).removeSuffix(".duckdns.org")
            require(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?").matches(label)) {
                "请输入 DuckDNS 子域名，例如 mycar.duckdns.org"
            }
            val secret = token.trim()
            require(Regex("[A-Za-z0-9-]{16,64}").matches(secret)) { "DuckDNS token 格式不正确" }
            return DuckDnsConfig("$label.duckdns.org", secret)
        }
    }
}

/** Fixed categories only: neither remote error bodies nor token-bearing request URLs escape. */
internal class AcmeFailure(val reason: Reason) : Exception(reason.name) {
    enum class Reason { OFFLINE, NETWORK, SERVER, RATE_LIMIT, PROTOCOL, DNS_UPDATE, VALIDATION, TIMEOUT, CERTIFICATE, BUSY, CANCELLED }
}

internal interface AcmeHttp {
    class Response(val code: Int, val headers: Map<String, String>, val body: ByteArray) {
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        fun json(): JSONObject = JSONObject(body.toString(Charsets.UTF_8))
    }
    fun request(method: String, url: String, body: ByteArray?): Response
}

/** Use the phone's validated Internet Network, independent of any local casting interface. */
internal class InternetHttp(context: Context) : AcmeHttp {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    override fun request(method: String, url: String, body: ByteArray?): AcmeHttp.Response {
        if (Thread.currentThread().isInterrupted) throw AcmeFailure(AcmeFailure.Reason.CANCELLED)
        val network = (listOfNotNull(connectivity.activeNetwork) + connectivity.allNetworks.toList()).distinct()
            .firstOrNull { candidate ->
                connectivity.getNetworkCapabilities(candidate)?.let {
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                } == true
            } ?: throw AcmeFailure(AcmeFailure.Reason.OFFLINE)
        var connection: HttpURLConnection? = null
        try {
            val endpoint = URL(url)
            if (endpoint.protocol != "https" || endpoint.userInfo != null) throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
            connection = network.openConnection(endpoint) as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json, application/pem-certificate-chain")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/jose+json")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val headers = connection.headerFields.entries.filter { it.key != null && !it.value.isNullOrEmpty() }
                .associate { it.key!! to it.value.first() }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > LanTls.MAX_IMPORT_BYTES) throw AcmeFailure(AcmeFailure.Reason.PROTOCOL)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } ?: byteArrayOf()
            return AcmeHttp.Response(code, headers, bytes)
        } catch (error: AcmeFailure) {
            throw error
        } catch (_: Exception) {
            throw AcmeFailure(AcmeFailure.Reason.NETWORK)
        } finally {
            connection?.disconnect()
        }
    }
}

/** DuckDNS API: https://www.duckdns.org/spec.jsp; flow adapted from DashPlay own_domain.py. */
internal class DuckDnsClient(private val http: AcmeHttp) {
    fun updateAddress(config: DuckDnsConfig, address: String, clearPrevious: Boolean = false) {
        // Explicit reachable LAN IPv4 only; an empty ip would silently publish the phone's public IP.
        require(validIpv4(address)) { "请输入手机可供 Tesla 访问的 IPv4 地址" }
        // Clear stale AAAA only on first setup/address changes; routine refresh preserves working DNS.
        if (clearPrevious) update(config, mapOf("clear" to "true"))
        update(config, mapOf("ip" to address))
    }
    fun setTxt(config: DuckDnsConfig, value: String) = update(config, mapOf("txt" to value))
    fun clearTxt(config: DuckDnsConfig) = update(config, mapOf("txt" to "", "clear" to "true"))

    private fun update(config: DuckDnsConfig, fields: Map<String, String>) {
        val params = linkedMapOf("domains" to config.subdomain, "token" to config.token).apply { putAll(fields) }
        val query = params.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        try {
            val response = http.request("GET", "https://www.duckdns.org/update?$query", null)
            if (response.code !in 200..299 || response.body.toString(Charsets.US_ASCII).trim() != "OK") {
                throw AcmeFailure(AcmeFailure.Reason.DNS_UPDATE)
            }
        } catch (error: AcmeFailure) {
            throw error
        } catch (_: Exception) {
            throw AcmeFailure(AcmeFailure.Reason.DNS_UPDATE)
        }
    }

    companion object {
        fun validIpv4(value: String): Boolean {
            val parts = value.split('.')
            return parts.size == 4 && parts.all {
                it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) &&
                    (it.length == 1 || it[0] != '0') && (it.toIntOrNull() ?: -1) in 0..255
            } && parts[0].toInt() in 1..223 && parts[0] != "127" && value != "169.254.0.0"
        }
    }
}
