package ir.pnifox.core.vpn

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory

data class EchBootstrapResult(
    val echConfigBase64: String,
    val sourceLabel: String,
    val resolvedDomain: String,
)

/**
 * 3-Stage Smart ECH Bootstrap Resolver:
 * When standard UDP Port-53 ECH lookups are blocked or return poisoned data,
 * queries `TYPE=65` (HTTPS/SVCB) and extracts `SvcParamKey = 5` (`ech` / `echconfig`)
 * across:
 *   1. DoH (HTTPS JSON `application/dns-json`)
 *   2. DoT (DNS-over-TLS on TCP/853 wire format)
 *   3. Clean UDP/53 wire query
 *
 * Also supports Cloudflare's Free-plan shared ECH property: an ECHConfig fetched from any
 * unblocked Cloudflare domain (`cloudflare-ech.com` / `crypto.cloudflare.com`) can be
 * supplied directly to Aether via `--ech <base64>`.
 */
object EchBootstrapResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private val fallbackDomains = listOf(
        "cloudflare-ech.com",
        "crypto.cloudflare.com",
        "speed.cloudflare.com",
    )

    fun resolveEchConfig(
        targetDomain: String = "cloudflare-ech.com",
        customDohOrDns: String = "",
        onLog: (String) -> Unit = {},
    ): EchBootstrapResult? {
        val candidates = (listOf(targetDomain.trim()) + fallbackDomains)
            .filter { it.isNotBlank() }
            .distinct()

        // Stage 1: DoH JSON lookup
        val dohBaseUrls = buildList {
            if (customDohOrDns.startsWith("https://", ignoreCase = true)) add(customDohOrDns.trim())
            add("https://1.1.1.1/dns-query")
            add("https://dns.google/resolve")
            add("https://1.0.0.1/dns-query")
        }.distinct()

        for (dom in candidates) {
            for (doh in dohBaseUrls) {
                val res = fetchViaDoh(doh, dom)
                if (res != null) {
                    onLog("ECH Bootstrap (DoH $doh): fetched ${res.length}ch config via $dom")
                    return EchBootstrapResult(res, "DoH ($doh)", dom)
                }
            }
        }

        // Stage 2: DoT TCP/853 wire query
        val dotHosts = listOf("1.1.1.1", "8.8.8.8", "1.0.0.1")
        for (dom in candidates) {
            for (dotHost in dotHosts) {
                val res = fetchViaDot(dotHost, dom)
                if (res != null) {
                    onLog("ECH Bootstrap (DoT $dotHost:853): fetched ${res.length}ch config via $dom")
                    return EchBootstrapResult(res, "DoT ($dotHost:853)", dom)
                }
            }
        }

        // Stage 3: Clean UDP/53 wire TYPE=65 query
        val udpResolvers = buildList {
            if (customDohOrDns.isNotBlank() && !customDohOrDns.startsWith("https://")) {
                add(customDohOrDns.substringBefore(':').trim())
            }
            add("1.1.1.1")
            add("9.9.9.9")
        }.distinct()

        for (dom in candidates) {
            for (dnsIp in udpResolvers) {
                val res = fetchViaUdp53(dnsIp, dom)
                if (res != null) {
                    onLog("ECH Bootstrap (UDP $dnsIp:53): fetched ${res.length}ch config via $dom")
                    return EchBootstrapResult(res, "UDP ($dnsIp:53)", dom)
                }
            }
        }

        return null
    }

    private fun fetchViaDoh(dohUrl: String, domain: String): String? = runCatching {
        val sep = if ('?' in dohUrl) "&" else "?"
        val url = "${dohUrl.trimEnd('/')}${sep}name=$domain&type=65"
        val req = Request.Builder().url(url).header("Accept", "application/dns-json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            extractEchBase64FromDohJson(resp.body?.string().orEmpty())
        }
    }.getOrNull()

    private fun fetchViaDot(dotIp: String, domain: String): String? = runCatching {
        val query = LocalDpiDesyncServer.buildDnsARecordQuery(domain, txId = 0x6501, qType = 65)
        val factory = SSLSocketFactory.getDefault()
        factory.createSocket().use { sock ->
            sock.connect(InetSocketAddress(InetAddress.getByName(dotIp), 853), 2_500)
            sock.soTimeout = 2_500
            val out = DataOutputStream(sock.getOutputStream())
            out.writeShort(query.size)
            out.write(query)
            out.flush()
            val inp = DataInputStream(sock.getInputStream())
            val respLen = inp.readUnsignedShort()
            if (respLen in 12..8192) {
                val wire = ByteArray(respLen)
                inp.readFully(wire)
                extractEchBase64FromDnsWirePacket(wire)
            } else {
                null
            }
        }
    }.getOrNull()

    private fun fetchViaUdp53(dnsIp: String, domain: String): String? = runCatching {
        val query = LocalDpiDesyncServer.buildDnsARecordQuery(domain, txId = 0x6502, qType = 65)
        DatagramSocket().use { sock ->
            sock.soTimeout = 1_800
            sock.send(DatagramPacket(query, query.size, InetAddress.getByName(dnsIp), 53))
            val buf = ByteArray(2048)
            val pkt = DatagramPacket(buf, buf.size)
            sock.receive(pkt)
            extractEchBase64FromDnsWirePacket(buf.copyOf(pkt.length))
        }
    }.getOrNull()

    private val dohJson = Json { ignoreUnknownKeys = true }

    /**
     * Parses `application/dns-json` TYPE=65 answer data (`ech="..."` or `\ #` hex wire blob).
     */
    fun extractEchBase64FromDohJson(jsonBody: String): String? {
        if (jsonBody.isBlank()) return null
        val obj = runCatching { dohJson.parseToJsonElement(jsonBody) as? JsonObject }.getOrNull() ?: return null
        val answers = obj["Answer"] as? JsonArray ?: return null
        for (elem in answers) {
            val ans = elem as? JsonObject ?: continue
            if (ans["type"]?.jsonPrimitive?.intOrNull != 65) continue
            val data = ans["data"]?.jsonPrimitive?.content?.trim().orEmpty()
            // Text format from Cloudflare/Google DoH: `1 . alpn="h2,h3" ipv4hint=... ech=<base64>`
            val match = Regex("""\bech=([A-Za-z0-9+/=_-]{16,})""").find(data)
            if (match != null) {
                return match.groupValues[1].trim('"', ' ')
            }
            // RFC 3597 unknown RR format from some resolvers: `\# <len> <hex...>`
            if (data.startsWith("\\#")) {
                val hexPart = data.substringAfter("\\#").trim().substringAfter(' ').replace(Regex("\\s+"), "")
                val rawBytes = hexToBytes(hexPart) ?: continue
                extractEchConfigFromHttpsRdata(rawBytes)?.let { return Base64.getEncoder().encodeToString(it) }
            }
        }
        return null
    }

    /**
     * Extracts Base64 ECHConfigList from raw DNS wire response containing TYPE=65 HTTPS/SVCB answer.
     */
    fun extractEchBase64FromDnsWirePacket(pkt: ByteArray): String? {
        if (pkt.size < 16) return null
        val qd = ((pkt[4].toInt() and 0xFF) shl 8) or (pkt[5].toInt() and 0xFF)
        val an = ((pkt[6].toInt() and 0xFF) shl 8) or (pkt[7].toInt() and 0xFF)
        var pos = 12
        repeat(qd) {
            pos = LocalDpiDesyncServer.skipDnsName(pkt, pos)
            if (pos < 0 || pos + 4 > pkt.size) return null
            pos += 4
        }
        repeat(an) {
            pos = LocalDpiDesyncServer.skipDnsName(pkt, pos)
            if (pos < 0 || pos + 10 > pkt.size) return null
            val rType = ((pkt[pos].toInt() and 0xFF) shl 8) or (pkt[pos + 1].toInt() and 0xFF)
            val rLen = ((pkt[pos + 8].toInt() and 0xFF) shl 8) or (pkt[pos + 9].toInt() and 0xFF)
            pos += 10
            if (pos + rLen > pkt.size) return null
            if (rType == 65) {
                val rdata = pkt.copyOfRange(pos, pos + rLen)
                extractEchConfigFromHttpsRdata(rdata)?.let { echBytes ->
                    return Base64.getEncoder().encodeToString(echBytes)
                }
            }
            pos += rLen
        }
        return null
    }

    /**
     * Parses RFC 9460 SVCB/HTTPS RDATA:
     *   SvcPriority (2 bytes) + TargetName (DNS labels) + SvcParams `(Key[2], ValLen[2], Value[ValLen])*`.
     * Returns raw bytes of `SvcParamKey == 5` (`ech`) when present.
     */
    fun extractEchConfigFromHttpsRdata(rdata: ByteArray): ByteArray? {
        if (rdata.size < 4) return null
        var pos = 2 // skip SvcPriority
        while (pos < rdata.size) {
            val labelLen = rdata[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break
            if ((labelLen and 0xC0) == 0xC0) {
                pos++
                break
            }
            pos += labelLen
        }
        while (pos + 4 <= rdata.size) {
            val key = ((rdata[pos].toInt() and 0xFF) shl 8) or (rdata[pos + 1].toInt() and 0xFF)
            val valLen = ((rdata[pos + 2].toInt() and 0xFF) shl 8) or (rdata[pos + 3].toInt() and 0xFF)
            pos += 4
            if (pos + valLen > rdata.size) break
            if (key == 5 && valLen >= 4) {
                return rdata.copyOfRange(pos, pos + valLen)
            }
            pos += valLen
        }
        return null
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0 || hex.isEmpty()) return null
        return runCatching {
            val out = ByteArrayOutputStream(hex.length / 2)
            for (i in hex.indices step 2) {
                out.write(hex.substring(i, i + 2).toInt(16))
            }
            out.toByteArray()
        }.getOrNull()
    }
}
