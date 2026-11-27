package ir.pnifox.core.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Rootless, zero-external-server Local DPI Desync proxy (SOCKS5 on localhost).
 *
 * Implements practical anti-DPI techniques on-device with zero remote VPS needed:
 * 1. Anti-poisoning DNS resolver over DoH (`1.1.1.1` / `dns.google`) + clean UDP/53 rejecting `10.10.34.34-36`.
 * 2. Real TLS Record framing split (`0x16 0x03 0x01`) + multi-segment TCP write right across the SNI extension
 *    with `TCP_NODELAY = true` so stateful DPI boxes fail to match the blocked hostname while servers reassemble cleanly.
 * 3. HTTP Host header mixed-case + split (`hoSt:` + chunked flush) for plaintext HTTP/80 flows.
 * 4. Accurate live `rxBytes` / `txBytes` telemetry compatible with Android & Windows speed meters.
 */
class LocalDpiDesyncServer(
    private val listenPort: Int = 10820,
    private val bindHost: String = "127.0.0.1",
    private val fragmentChunkSize: Int = 3,
    private val fragmentDelayMs: Long = 2L,
    private val preferCleanDns: String = "1.1.1.1",
    private val protectTcpSocket: ((Socket) -> Unit)? = null,
    private val protectUdpSocket: ((DatagramSocket) -> Unit)? = null,
    private val onLog: (String) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private val rxBytes = AtomicLong(0L)
    private val txBytes = AtomicLong(0L)
    private var serverSocket: ServerSocket? = null
    private var scope: CoroutineScope? = null

    private data class CachedResolution(val address: InetAddress, val expiresAtMs: Long)
    private val dnsCache = ConcurrentHashMap<String, CachedResolution>()

    private val dohClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    val activePort: Int
        get() = serverSocket?.localPort ?: listenPort

    fun bytesSnapshot(): Pair<Long, Long> = rxBytes.get() to txBytes.get()

    fun start() {
        if (running.getAndSet(true)) return
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = jobScope
        val bindAddr = InetAddress.getByName(bindHost)
        val srv = ServerSocket(listenPort, 256, bindAddr)
        serverSocket = srv
        onLog("Local DPI Desync active on $bindHost:${srv.localPort} (TLS Record+SNI Split + DoH Anti-Poison)")

        jobScope.launch {
            while (running.get() && !srv.isClosed) {
                val client = runCatching { srv.accept() }.getOrNull() ?: break
                jobScope.launch { handleClient(client) }
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { scope?.cancel() }
        scope = null
        dnsCache.clear()
        onLog("Local DPI Desync stopped.")
    }

    private fun handleClient(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = 30_000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // SOCKS5 Greeting
            val ver = input.read()
            if (ver != 0x05) return
            val nMethods = input.read().coerceAtLeast(0)
            val methods = ByteArray(nMethods)
            if (!readFully(input, methods)) return
            // Reply: no authentication required (0x00)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // SOCKS5 Request header: VER(1) CMD(1) RSV(1) ATYP(1)
            val reqHdr = ByteArray(4)
            if (!readFully(input, reqHdr)) return
            val cmd = reqHdr[1].toInt() and 0xFF
            val atyp = reqHdr[3].toInt() and 0xFF

            val targetHost = when (atyp) {
                0x01 -> {
                    val ip = ByteArray(4)
                    if (!readFully(input, ip)) return
                    InetAddress.getByAddress(ip).hostAddress ?: return
                }
                0x03 -> {
                    val domLen = input.read()
                    if (domLen <= 0) return
                    val domBytes = ByteArray(domLen)
                    if (!readFully(input, domBytes)) return
                    String(domBytes, Charsets.UTF_8)
                }
                0x04 -> {
                    val ip6 = ByteArray(16)
                    if (!readFully(input, ip6)) return
                    InetAddress.getByAddress(ip6).hostAddress ?: return
                }
                else -> {
                    output.write(buildSocksReply(0x08))
                    return
                }
            }

            val portBytes = ByteArray(2)
            if (!readFully(input, portBytes)) return
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            if (cmd != 0x01) {
                // Only TCP CONNECT supported cleanly for DPI desync
                output.write(buildSocksReply(0x07))
                return
            }

            val resolvedAddr = resolveAntiPoison(targetHost)
            if (resolvedAddr == null || isPoisonedIp(resolvedAddr.hostAddress.orEmpty())) {
                output.write(buildSocksReply(0x04))
                return
            }

            val remote = Socket()
            protectTcpSocket?.invoke(remote)
            remote.tcpNoDelay = true
            remote.soTimeout = 30_000
            remote.connect(InetSocketAddress(resolvedAddr, targetPort), 6_000)
            upstream = remote

            output.write(buildSocksReply(0x00))
            output.flush()

            val remoteIn = remote.getInputStream()
            val remoteOut = remote.getOutputStream()

            // Downstream pump in background thread/coroutine while current coroutine handles initial desync + upstream
            val downThread = Thread {
                pumpStream(remoteIn, output, isRx = true)
                runCatching { client.shutdownOutput() }
            }.apply {
                isDaemon = true
                start()
            }

            pumpUpstreamWithDesync(input, remoteOut, targetPort)
            runCatching { remote.shutdownOutput() }
            runCatching { downThread.join(4_000) }
        } catch (_: Exception) {
            // Connection terminated normally or reset by peer
        } finally {
            runCatching { upstream?.close() }
            runCatching { client.close() }
        }
    }

    private fun pumpUpstreamWithDesync(
        clientIn: InputStream,
        remoteOut: OutputStream,
        targetPort: Int,
    ) {
        val buf = ByteArray(16_384)
        var firstPacketHandled = false
        while (running.get()) {
            val read = runCatching { clientIn.read(buf) }.getOrDefault(-1)
            if (read <= 0) break
            txBytes.addAndGet(read.toLong())
            if (!firstPacketHandled) {
                firstPacketHandled = true
                val first = buf.copyOf(read)
                when {
                    isTlsClientHello(first) -> writeDesyncedTlsHello(first, remoteOut)
                    targetPort == 80 && looksLikeHttpRequest(first) -> writeDesyncedHttp(first, remoteOut)
                    else -> {
                        remoteOut.write(first)
                        remoteOut.flush()
                    }
                }
            } else {
                remoteOut.write(buf, 0, read)
                remoteOut.flush()
            }
        }
    }

    private fun writeDesyncedTlsHello(packet: ByteArray, out: OutputStream) {
        val sniOffset = findSniStartOffset(packet).coerceIn(10, (packet.size - 1).coerceAtLeast(10))
        // Split at start of SNI (or fragmentChunkSize) inside valid multi-record TLS framing
        val splitPoint = sniOffset.coerceIn(7, packet.size - 2)
        val records = buildSplitTlsRecords(packet, splitPoint)
        val chunkSize = fragmentChunkSize.coerceIn(2, 16)
        for ((idx, record) in records.withIndex()) {
            if (idx == 0 && record.size > chunkSize) {
                var pos = 0
                while (pos < record.size) {
                    val end = (pos + chunkSize).coerceAtMost(record.size)
                    out.write(record, pos, end - pos)
                    out.flush()
                    pos = end
                    if (fragmentDelayMs > 0 && pos < record.size) {
                        Thread.sleep(fragmentDelayMs)
                    }
                }
            } else {
                out.write(record)
                out.flush()
                if (fragmentDelayMs > 0) Thread.sleep(fragmentDelayMs)
            }
        }
    }

    private fun writeDesyncedHttp(packet: ByteArray, out: OutputStream) {
        val rawStr = String(packet, Charsets.ISO_8859_1)
        val mutated = rawStr.replace("\r\nHost:", "\r\nhoSt:", ignoreCase = true)
            .toByteArray(Charsets.ISO_8859_1)
        val split = mutated.indexOfSlice("hoSt:".toByteArray(Charsets.ISO_8859_1))
        if (split in 4 until mutated.size - 4) {
            out.write(mutated, 0, split + 3)
            out.flush()
            if (fragmentDelayMs > 0) Thread.sleep(fragmentDelayMs)
            out.write(mutated, split + 3, mutated.size - (split + 3))
            out.flush()
        } else {
            out.write(mutated)
            out.flush()
        }
    }

    private fun pumpStream(src: InputStream, dst: OutputStream, isRx: Boolean) {
        val buf = ByteArray(16_384)
        while (running.get()) {
            val read = runCatching { src.read(buf) }.getOrDefault(-1)
            if (read <= 0) break
            if (isRx) rxBytes.addAndGet(read.toLong()) else txBytes.addAndGet(read.toLong())
            dst.write(buf, 0, read)
            dst.flush()
        }
    }

    private fun resolveAntiPoison(host: String): InetAddress? {
        // Direct IP literal needs no lookup
        if (host.all { it.isDigit() || it == '.' } || host.contains(':')) {
            return runCatching { InetAddress.getByName(host) }.getOrNull()
        }
        val key = host.lowercase()
        val cached = dnsCache[key]
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiresAtMs > now) return cached.address

        // 1. Clean DoH JSON lookup
        val dohResolved = resolveViaDoh(key)
        if (dohResolved != null && !isPoisonedIp(dohResolved.hostAddress.orEmpty())) {
            dnsCache[key] = CachedResolution(dohResolved, now + 60_000L)
            return dohResolved
        }

        // 2. Clean direct UDP/53 wire lookup (bypasses system resolver)
        val cleanIp = resolveViaUdp53(key, preferCleanDns)
        if (cleanIp != null && !isPoisonedIp(cleanIp.hostAddress.orEmpty())) {
            dnsCache[key] = CachedResolution(cleanIp, now + 60_000L)
            return cleanIp
        }

        // 3. Last resort system lookup ONLY if not poisoned
        val sys = runCatching { InetAddress.getByName(host) }.getOrNull()
        return if (sys != null && !isPoisonedIp(sys.hostAddress.orEmpty())) sys else null
    }

    private fun resolveViaDoh(domain: String): InetAddress? {
        val endpoints = listOf(
            "https://1.1.1.1/dns-query?name=$domain&type=A",
            "https://dns.google/resolve?name=$domain&type=A",
            "https://1.0.0.1/dns-query?name=$domain&type=A",
        )
        for (url in endpoints) {
            val parsedUrl = url.toHttpUrlOrNull() ?: continue
            val req = Request.Builder()
                .url(parsedUrl)
                .header("Accept", "application/dns-json")
                .build()
            val ipStr = runCatching {
                dohClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    val obj = dohJson.parseToJsonElement(body) as? JsonObject ?: return@use null
                    val answers = obj["Answer"] as? JsonArray ?: return@use null
                    for (elem in answers) {
                        val ans = elem as? JsonObject ?: continue
                        if (ans["type"]?.jsonPrimitive?.intOrNull == 1) {
                            val data = ans["data"]?.jsonPrimitive?.content?.trim().orEmpty()
                            if (data.isNotBlank() && !isPoisonedIp(data)) return@use data
                        }
                    }
                    null
                }
            }.getOrNull()
            if (ipStr != null) {
                return runCatching { InetAddress.getByName(ipStr) }.getOrNull()
            }
        }
        return null
    }

    private fun resolveViaUdp53(domain: String, dnsIp: String): InetAddress? = runCatching {
        val cleanDns = dnsIp.substringBefore(':').ifBlank { "1.1.1.1" }
        val query = buildDnsARecordQuery(domain)
        DatagramSocket().use { sock ->
            protectUdpSocket?.invoke(sock)
            sock.soTimeout = 1_500
            sock.send(DatagramPacket(query, query.size, InetAddress.getByName(cleanDns), 53))
            val recv = ByteArray(512)
            val pkt = DatagramPacket(recv, recv.size)
            sock.receive(pkt)
            val ip = extractFirstARecord(recv.copyOf(pkt.length)) ?: return@runCatching null
            if (isPoisonedIp(ip)) return@runCatching null
            InetAddress.getByName(ip)
        }
    }.getOrNull()

    private fun readFully(input: InputStream, buf: ByteArray): Boolean = runCatching {
        DataInputStream(input).readFully(buf)
        true
    }.getOrDefault(false)

    private fun buildSocksReply(rep: Int): ByteArray =
        byteArrayOf(0x05, rep.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0)

    companion object {
        private val dohJson = Json { ignoreUnknownKeys = true }
        private val POISONED_IPS = setOf("10.10.34.34", "10.10.34.35", "10.10.34.36")

        fun isPoisonedIp(ip: String): Boolean = ip.trim() in POISONED_IPS

        fun isTlsClientHello(packet: ByteArray): Boolean =
            packet.size > 9 &&
                packet[0] == 0x16.toByte() &&
                packet[1] == 0x03.toByte() &&
                packet[5] == 0x01.toByte()

        fun looksLikeHttpRequest(packet: ByteArray): Boolean {
            if (packet.size < 12) return false
            val prefix = String(packet, 0, 8.coerceAtMost(packet.size), Charsets.US_ASCII)
            return prefix.startsWith("GET ") || prefix.startsWith("POST ") ||
                prefix.startsWith("HEAD ") || prefix.startsWith("PUT ") || prefix.startsWith("OPTIONS ")
        }

        /**
         * Splits a single TLS ClientHello record (`[0x16, 0x03, verLo, lenHi, lenLo, body...]`)
         * into two valid TLS Plaintext Records around [payloadSplitIdx] inside the handshake payload,
         * preserving exact bytes for the target TLS server while frustrating first-record DPI SNI extraction.
         */
        fun buildSplitTlsRecords(clientHelloRecord: ByteArray, rawSplitOffset: Int): List<ByteArray> {
            if (!isTlsClientHello(clientHelloRecord) || clientHelloRecord.size <= 12) {
                return listOf(clientHelloRecord)
            }
            val declaredLen = ((clientHelloRecord[3].toInt() and 0xFF) shl 8) or (clientHelloRecord[4].toInt() and 0xFF)
            val totalPayload = (clientHelloRecord.size - 5).coerceAtMost(declaredLen)
            if (totalPayload < 12) return listOf(clientHelloRecord)

            val splitInPayload = (rawSplitOffset - 5).coerceIn(4, totalPayload - 4)
            val firstLen = splitInPayload
            val secondLen = totalPayload - splitInPayload
            val verLo = clientHelloRecord[2]

            val r1 = ByteArray(5 + firstLen)
            r1[0] = 0x16
            r1[1] = 0x03
            r1[2] = verLo
            r1[3] = ((firstLen ushr 8) and 0xFF).toByte()
            r1[4] = (firstLen and 0xFF).toByte()
            System.arraycopy(clientHelloRecord, 5, r1, 5, firstLen)

            val r2 = ByteArray(5 + secondLen)
            r2[0] = 0x16
            r2[1] = 0x03
            r2[2] = verLo
            r2[3] = ((secondLen ushr 8) and 0xFF).toByte()
            r2[4] = (secondLen and 0xFF).toByte()
            System.arraycopy(clientHelloRecord, 5 + firstLen, r2, 5, secondLen)

            val trailing = clientHelloRecord.size - (5 + totalPayload)
            return if (trailing > 0) {
                listOf(r1, r2, clientHelloRecord.copyOfRange(5 + totalPayload, clientHelloRecord.size))
            } else {
                listOf(r1, r2)
            }
        }

        /**
         * Locates the approximate start index of the SNI hostname extension inside a TLS ClientHello packet.
         */
        fun findSniStartOffset(pkt: ByteArray): Int {
            // Skip 5-byte TLS record header + 4-byte Handshake header + 2-byte version + 32-byte random = 43
            if (pkt.size < 50) return pkt.size / 2
            var pos = 43
            val sessionIdLen = pkt[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen
            if (pos + 2 >= pkt.size) return pkt.size / 2
            val cipherLen = ((pkt[pos].toInt() and 0xFF) shl 8) or (pkt[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherLen
            if (pos + 1 >= pkt.size) return pkt.size / 2
            val compLen = pkt[pos].toInt() and 0xFF
            pos += 1 + compLen + 2 // skip extensions length
            while (pos + 4 < pkt.size) {
                val extType = ((pkt[pos].toInt() and 0xFF) shl 8) or (pkt[pos + 1].toInt() and 0xFF)
                val extLen = ((pkt[pos + 2].toInt() and 0xFF) shl 8) or (pkt[pos + 3].toInt() and 0xFF)
                if (extType == 0x0000) {
                    return (pos + 9).coerceAtMost(pkt.size - 1)
                }
                pos += 4 + extLen
            }
            return (pkt.size / 2).coerceAtLeast(10)
        }

        internal fun buildDnsARecordQuery(domain: String, txId: Int = 0x4242, qType: Int = 1): ByteArray {
            val out = ByteArrayOutputStream()
            out.write((txId ushr 8) and 0xFF)
            out.write(txId and 0xFF)
            out.write(0x01); out.write(0x00) // Standard query, recursion desired
            out.write(0x00); out.write(0x01) // QDCOUNT = 1
            out.write(0x00); out.write(0x00) // ANCOUNT = 0
            out.write(0x00); out.write(0x00) // NSCOUNT = 0
            out.write(0x00); out.write(0x00) // ARCOUNT = 0
            domain.trim('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.write(bytes.size and 0xFF)
                out.write(bytes)
            }
            out.write(0x00)
            out.write((qType ushr 8) and 0xFF)
            out.write(qType and 0xFF) // QTYPE
            out.write(0x00); out.write(0x01) // QCLASS IN
            return out.toByteArray()
        }

        internal fun extractFirstARecord(pkt: ByteArray): String? {
            if (pkt.size < 16) return null
            val qd = ((pkt[4].toInt() and 0xFF) shl 8) or (pkt[5].toInt() and 0xFF)
            val an = ((pkt[6].toInt() and 0xFF) shl 8) or (pkt[7].toInt() and 0xFF)
            var pos = 12
            repeat(qd) {
                pos = skipDnsName(pkt, pos)
                if (pos < 0 || pos + 4 > pkt.size) return null
                pos += 4
            }
            repeat(an) {
                pos = skipDnsName(pkt, pos)
                if (pos < 0 || pos + 10 > pkt.size) return null
                val rType = ((pkt[pos].toInt() and 0xFF) shl 8) or (pkt[pos + 1].toInt() and 0xFF)
                val rLen = ((pkt[pos + 8].toInt() and 0xFF) shl 8) or (pkt[pos + 9].toInt() and 0xFF)
                pos += 10
                if (pos + rLen > pkt.size) return null
                if (rType == 1 && rLen == 4) {
                    return "${pkt[pos].toInt() and 0xFF}.${pkt[pos + 1].toInt() and 0xFF}.${pkt[pos + 2].toInt() and 0xFF}.${pkt[pos + 3].toInt() and 0xFF}"
                }
                pos += rLen
            }
            return null
        }

        internal fun skipDnsName(pkt: ByteArray, start: Int): Int {
            var pos = start
            var steps = 0
            while (pos < pkt.size && steps++ < 64) {
                val len = pkt[pos].toInt() and 0xFF
                if (len == 0) return pos + 1
                if ((len and 0xC0) == 0xC0) return pos + 2
                pos += 1 + len
            }
            return -1
        }
    }
}

private fun ByteArray.indexOfSlice(needle: ByteArray): Int {
    if (needle.isEmpty() || this.size < needle.size) return -1
    outer@ for (i in 0..(this.size - needle.size)) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}
