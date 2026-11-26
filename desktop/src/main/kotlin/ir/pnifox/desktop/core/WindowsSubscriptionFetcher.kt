package ir.pnifox.desktop.core

import ir.pnifox.core.model.LogLevel
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.network.ShareLinkParser
import ir.pnifox.core.network.SubscriptionClient
import ir.pnifox.core.vpn.LogRingBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

data class SubscriptionFetchOutcome(
    val profiles: List<ProxyProfile>,
    val transportUsed: String,
)

class WindowsSubscriptionFetcher(
    httpClient: OkHttpClient,
    private val logs: LogRingBuffer,
) {
    private val sharedClient = SubscriptionClient(httpClient)

    suspend fun fetch(
        url: String,
        groupId: String,
        activeHttpPort: Int? = null,
        activeSocksPort: Int? = null,
    ): Result<SubscriptionFetchOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = validateSubscriptionUrl(url)
            var lastErr: Throwable? = null

            // 1. If an active local pnifox tunnel is running, prefer fetching through it
            val proxyCandidates = buildList {
                if (activeHttpPort != null) {
                    add("Active HTTP Tunnel :$activeHttpPort" to Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", activeHttpPort)))
                }
                if (activeSocksPort != null) {
                    add("Active SOCKS5 Tunnel :$activeSocksPort" to Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", activeSocksPort)))
                }
            }
            for ((label, proxy) in proxyCandidates) {
                val res = sharedClient.fetchSubscriptionProfiles(cleanUrl, groupId, proxy = proxy)
                val list = res.getOrNull()
                if (!list.isNullOrEmpty()) {
                    logs.append("Sub", LogLevel.INFO, "Fetched ${list.size} server(s) via $label")
                    return@runCatching SubscriptionFetchOutcome(list, label)
                }
                res.exceptionOrNull()?.let { lastErr = it }
            }

            // 2. Direct OkHttp with DoH DNS fallback
            val directRes = runCatching { sharedClient.fetchRawBody(cleanUrl, proxy = null) }
            if (directRes.isSuccess) {
                val parsed = parseOrExplainHtml(directRes.getOrThrow(), groupId)
                if (parsed.isNotEmpty()) {
                    logs.append("Sub", LogLevel.INFO, "Fetched ${parsed.size} server(s) directly (OkHttp)")
                    return@runCatching SubscriptionFetchOutcome(parsed, "Direct (OkHttp)")
                }
            } else {
                lastErr = directRes.exceptionOrNull()
                logs.append("Sub", LogLevel.WARN, "Direct OkHttp sub fetch failed (${lastErr?.message}), retrying via Windows TLS (Schannel)...")
            }

            // 3. Windows native Schannel via built-in curl.exe (defeats Java JSSE TLS access_denied fingerprints while keeping strict cert check)
            val curlAttempts = listOf(
                "Windows TLS (curl)" to listOf("--doh-url", "https://1.1.1.1/dns-query"),
                "Windows TLS (curl system DNS)" to emptyList(),
            )
            for ((label, extraArgs) in curlAttempts) {
                val curlBodyRes = fetchViaWindowsCurl(
                    cleanUrl = cleanUrl,
                    proxyArg = if (activeHttpPort != null) "http://127.0.0.1:$activeHttpPort" else null,
                    extraArgs = extraArgs,
                )
                if (curlBodyRes.isSuccess) {
                    val body = curlBodyRes.getOrThrow()
                    val parsed = parseOrExplainHtml(body, groupId)
                    if (parsed.isNotEmpty()) {
                        logs.append("Sub", LogLevel.INFO, "Fetched ${parsed.size} server(s) via $label")
                        return@runCatching SubscriptionFetchOutcome(parsed, label)
                    }
                } else {
                    lastErr = curlBodyRes.exceptionOrNull() ?: lastErr
                }
            }

            throw lastErr ?: IllegalStateException("Subscription returned 0 valid servers.")
        }
    }

    private fun validateSubscriptionUrl(rawUrl: String): String {
        val s = rawUrl.trim()
        val uri = runCatching { URI(s) }.getOrNull() ?: error("Invalid subscription URL")
        val scheme = uri.scheme?.lowercase().orEmpty()
        require(scheme == "http" || scheme == "https") { "Subscription URL must start with http:// or https://" }
        require(!uri.host.isNullOrBlank()) { "Subscription URL must include a host" }
        return s
    }

    private fun parseOrExplainHtml(body: String, groupId: String): List<ProxyProfile> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) error("Subscription returned an empty response")
        val parsed = ShareLinkParser.parseBatch(trimmed, groupId = groupId)
        if (parsed.isEmpty() && (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true))) {
            error("Server returned an HTML page instead of configs (connect AETHER/MHR first and retry)")
        }
        if (parsed.isEmpty()) {
            error("No valid VLESS / VMess / Trojan / Hy2 / SS / Clash / JSON configs found in response")
        }
        return parsed
    }

    private fun fetchViaWindowsCurl(
        cleanUrl: String,
        proxyArg: String?,
        extraArgs: List<String>,
    ): Result<String> = runCatching {
        val sysRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
        val curlExe = File(sysRoot, "System32\\curl.exe")
        require(curlExe.exists()) { "Windows curl.exe not found" }

        val args = buildList {
            add(curlExe.absolutePath)
            add("-sSL")
            add("--fail")
            add("--max-time")
            add("18")
            add("-A")
            add("v2rayNG/1.9.34")
            add("-H")
            add("Accept: */*")
            if (!proxyArg.isNullOrBlank()) {
                add("--proxy")
                add(proxyArg)
            }
            addAll(extraArgs)
            add("--")
            add(cleanUrl)
        }
        val proc = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val finished = proc.waitFor(22, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            error("curl timed out")
        }
        if (proc.exitValue() != 0) {
            error(output.trim().ifBlank { "curl exited with code ${proc.exitValue()}" })
        }
        output
    }
}
