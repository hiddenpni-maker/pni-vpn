package ir.pnifox.core.vpn

import ir.pnifox.core.model.DnsTunnelMode
import ir.pnifox.core.model.DnsTunnelSettings
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object RealDelaySocketProber {

    const val DEFAULT_PROBE_URL = "https://www.gstatic.com/generate_204"

    fun probeHttp204ViaSocks5(
        socksHost: String = "127.0.0.1",
        socksPort: Int,
        targetHost: String = "www.gstatic.com",
        targetPort: Int = 443,
        path: String = "/generate_204",
        timeoutMs: Int = 5500,
    ): Int? = runCatching {
        val rawSocket = Socket()
        rawSocket.soTimeout = timeoutMs
        rawSocket.connect(InetSocketAddress(socksHost, socksPort), 1500)

        val out = rawSocket.getOutputStream()
        val ins = rawSocket.getInputStream()

        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val greet = ByteArray(2)
        readExact(ins, greet)
        check(greet[0] == 0x05.toByte() && greet[1] == 0x00.toByte()) { "SOCKS5 greeting rejected" }

        val tStart = System.nanoTime()
        val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
        val req = ByteArrayOutputStream(7 + hostBytes.size)
        req.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()))
        req.write(hostBytes)
        req.write((targetPort ushr 8) and 0xFF)
        req.write(targetPort and 0xFF)
        out.write(req.toByteArray())
        out.flush()

        val repHead = ByteArray(4)
        readExact(ins, repHead)
        check(repHead[0] == 0x05.toByte() && repHead[1] == 0x00.toByte()) { "SOCKS5 CONNECT error ${repHead[1]}" }
        when (repHead[3].toInt() and 0xFF) {
            0x01 -> readExact(ins, ByteArray(4 + 2))
            0x04 -> readExact(ins, ByteArray(16 + 2))
            0x03 -> {
                val len = ins.read()
                check(len > 0)
                readExact(ins, ByteArray(len + 2))
            }
        }

        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(rawSocket, targetHost, targetPort, true) as SSLSocket
        ssl.use { tlsSock ->
            tlsSock.soTimeout = timeoutMs
            val params = tlsSock.sslParameters
            params.serverNames = listOf(SNIHostName(targetHost))
            tlsSock.sslParameters = params
            tlsSock.startHandshake()

            val httpReq = "GET $path HTTP/1.1\r\nHost: $targetHost\r\nUser-Agent: pnifox/2.0\r\nConnection: close\r\n\r\n"
            tlsSock.outputStream.write(httpReq.toByteArray(Charsets.US_ASCII))
            tlsSock.outputStream.flush()

            val line = tlsSock.inputStream.bufferedReader().readLine() ?: error("Empty HTTP response")
            val statusCode = line.split(' ').getOrNull(1)?.toIntOrNull() ?: error("Malformed HTTP line: $line")
            check(statusCode in 200..499) { "HTTP error $statusCode" }
        }
        ((System.nanoTime() - tStart) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrNull()

    fun measureDnsResolverLatencyMs(dns: DnsTunnelSettings, timeoutMs: Int = 3500): Int? = runCatching {
        val raw = dns.resolverAddress.trim()
        if (dns.mode == DnsTunnelMode.DOH || raw.startsWith("https://", ignoreCase = true)) {
            val url = if (raw.startsWith("http")) raw else "https://$raw/dns-query"
            val tStart = System.nanoTime()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            check(code < 500)
            return@runCatching ((System.nanoTime() - tStart) / 1_000_000L).toInt().coerceAtLeast(1)
        }
        val host = raw.removePrefix("quic://").removePrefix("tls://").substringBefore('/').substringBefore(':')
        val baos = ByteArrayOutputStream()
        baos.write(byteArrayOf(0x41, 0x42, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        "google.com".split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            baos.write(bytes.size)
            baos.write(bytes)
        }
        baos.write(0x00)
        baos.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        val query = baos.toByteArray()
        val tStart = System.nanoTime()
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            s.send(DatagramPacket(query, query.size, InetAddress.getByName(host), 53))
            s.receive(DatagramPacket(ByteArray(512), 512))
        }
        ((System.nanoTime() - tStart) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrNull()

    private fun readExact(ins: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = ins.read(buf, offset, buf.size - offset)
            if (n < 0) error("Unexpected EOF")
            offset += n
        }
    }
}
