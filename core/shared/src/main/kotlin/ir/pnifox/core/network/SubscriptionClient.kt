package ir.pnifox.core.network

import ir.pnifox.core.model.ProxyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val dnsJson = Json { ignoreUnknownKeys = true }

    suspend fun fetchSubscriptionProfiles(
        url: String,
        groupId: String,
        proxy: Proxy? = null,
    ): Result<List<ProxyProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = validateHttpUrl(url)
            val body = fetchRawBody(cleanUrl, proxy)
            ShareLinkParser.parseBatch(body, groupId = groupId)
        }
    }

    fun fetchRawBody(cleanUrl: String, proxy: Proxy? = null): String {
        val baseClient = okHttpClient.newBuilder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .apply {
                if (proxy != null) {
                    proxy(proxy)
                } else {
                    dns(resilientDns())
                }
            }
            .build()

        var lastError: Throwable? = null
        val userAgents = listOf("v2rayNG/1.9.34", "ClashMetaForAndroid/2.10", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        for (ua in userAgents) {
            var retryableCode = false
            try {
                val req = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", ua)
                    .header("Accept", "text/plain, application/json, application/yaml, */*")
                    .build()
                val bodyOrNull = baseClient.newCall(req).execute().use { resp ->
                    if (resp.code == 403 || resp.code == 406) {
                        lastError = IllegalStateException("HTTP ${resp.code}")
                        retryableCode = true
                        null
                    } else if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}")
                    } else {
                        resp.body?.string().orEmpty()
                    }
                }
                if (!retryableCode && bodyOrNull != null) return bodyOrNull
            } catch (t: Throwable) {
                lastError = t
                if (t is javax.net.ssl.SSLException) break
            }
        }
        throw lastError ?: IllegalStateException("Failed to fetch subscription")
    }

    private fun validateHttpUrl(rawUrl: String): String {
        val s = rawUrl.trim()
        val uri = runCatching { URI(s) }.getOrNull() ?: error("Invalid subscription URL")
        val scheme = uri.scheme?.lowercase().orEmpty()
        require(scheme == "http" || scheme == "https") { "Subscription URL must use http:// or https://" }
        require(!uri.host.isNullOrBlank()) { "Subscription URL has no hostname" }
        return s
    }

    private fun resilientDns(): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val sys = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
            val validSys = sys.filterNot { it.isLoopbackAddress || it.isAnyLocalAddress }
            if (validSys.isNotEmpty()) return validSys

            // Fallback DoH JSON lookup via Cloudflare & Google public resolvers
            for (dohUrl in listOf(
                "https://1.1.1.1/dns-query?name=$hostname&type=A",
                "https://8.8.8.8/resolve?name=$hostname&type=A",
            )) {
                val resolved = runCatching {
                    val dohClient = OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(5, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder()
                        .url(dohUrl)
                        .header("Accept", "application/dns-json")
                        .build()
                    dohClient.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return@use emptyList()
                        val obj = dnsJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject
                            ?: return@use emptyList()
                        val answers = obj["Answer"] as? JsonArray ?: return@use emptyList()
                        answers.mapNotNull { el ->
                            val ans = el as? JsonObject ?: return@mapNotNull null
                            val type = ans["type"]?.jsonPrimitive?.content?.toIntOrNull()
                            val data = ans["data"]?.jsonPrimitive?.content
                            if (type == 1 && !data.isNullOrBlank()) InetAddress.getByName(data) else null
                        }
                    }
                }.getOrDefault(emptyList())
                if (resolved.isNotEmpty()) return resolved
            }
            return sys
        }
    }
}
