package ir.pnifox.core.vpn

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure-JVM local SNI Spoof / TLS ClientHello fragmentation relay (default 127.0.0.1:40443),
 * ported cleanly from `core-go/xray-scanner/spoof/sni_spoof.go`.
 */
class SniSpoofServer {

    @Volatile
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var pool = Executors.newCachedThreadPool()

    val isRunning: Boolean
        get() = running.get()

    fun start(
        listenHost: String = "127.0.0.1",
        listenPort: Int = 40443,
        targetHost: String,
        targetPort: Int,
        fakeSni: String,
        method: String = "fragment+sni",
        fragLen: Int = 12,
        fragDelayMs: Long = 5L,
    ) {
        stop()
        pool = Executors.newCachedThreadPool()
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName(listenHost), listenPort))
        serverSocket = ss
        running.set(true)

        pool.execute {
            while (running.get() && !ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                pool.execute {
                    handleConnection(client, targetHost, targetPort, fakeSni, method, fragLen, fragDelayMs)
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { pool.shutdownNow() }
    }

    private fun handleConnection(
        client: Socket,
        targetHost: String,
        targetPort: Int,
        fakeSni: String,
        method: String,
        fragLen: Int,
        fragDelayMs: Long,
    ) {
        client.use { c ->
            val remote = Socket()
            runCatching {
                remote.connect(InetSocketAddress(targetHost, targetPort), 8000)
                remote.tcpNoDelay = true
                c.soTimeout = 8000
                val buf = ByteArray(16384)
                val n = c.getInputStream().read(buf)
                if (n <= 0) return
                c.soTimeout = 0

                var firstPacket = buf.copyOf(n)
                if ((method.contains("sni", ignoreCase = true)) && fakeSni.isNotBlank()) {
                    firstPacket = rewriteClientHelloSni(firstPacket, fakeSni.trim())
                }

                val rOut = remote.getOutputStream()
                if (method.contains("fragment", ignoreCase = true) && fragLen in 1 until firstPacket.size) {
                    writeFragmented(rOut, firstPacket, fragLen, fragDelayMs)
                } else {
                    rOut.write(firstPacket)
                    rOut.flush()
                }

                val t1 = Thread { pipe(c.getInputStream(), rOut) }.apply { isDaemon = true; start() }
                pipe(remote.getInputStream(), c.getOutputStream())
                runCatching { t1.join(500) }
            }.also {
                runCatching { remote.close() }
            }
        }
    }

    private fun pipe(ins: InputStream, outs: OutputStream) {
        val buf = ByteArray(16384)
        runCatching {
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                outs.write(buf, 0, n)
                outs.flush()
            }
        }
    }

    private fun writeFragmented(dst: OutputStream, data: ByteArray, fragLen: Int, delayMs: Long) {
        if (data.size > 5 && data[0] == 0x16.toByte() && data[1] == 0x03.toByte()) {
            val verMajor = data[1]
            val verMinor = data[2]
            var off = 5
            while (off < data.size) {
                val end = minOf(off + fragLen, data.size)
                val chunkLen = end - off
                dst.write(0x16)
                dst.write(verMajor.toInt() and 0xFF)
                dst.write(verMinor.toInt() and 0xFF)
                dst.write((chunkLen ushr 8) and 0xFF)
                dst.write(chunkLen and 0xFF)
                dst.write(data, off, chunkLen)
                dst.flush()
                if (delayMs > 0 && end < data.size) Thread.sleep(delayMs)
                off = end
            }
            return
        }
        var off = 0
        while (off < data.size) {
            val end = minOf(off + fragLen, data.size)
            dst.write(data, off, end - off)
            dst.flush()
            if (delayMs > 0 && end < data.size) Thread.sleep(delayMs)
            off = end
        }
    }

    companion object {
        fun rewriteClientHelloSni(pkt: ByteArray, newSni: String): ByteArray {
            if (pkt.size < 43 || pkt[0] != 0x16.toByte() || pkt[5] != 0x01.toByte()) return pkt
            var pos = 5 + 4 + 2 + 32
            if (pos >= pkt.size) return pkt
            val sidLen = pkt[pos].toInt() and 0xFF
            pos += 1 + sidLen
            if (pos + 2 > pkt.size) return pkt
            val csLen = u16(pkt, pos)
            pos += 2 + csLen
            if (pos + 1 > pkt.size) return pkt
            val compLen = pkt[pos].toInt() and 0xFF
            pos += 1 + compLen
            if (pos + 2 > pkt.size) return pkt
            val extTotalLen = u16(pkt, pos)
            val extStart = pos + 2
            val extEnd = extStart + extTotalLen
            if (extEnd > pkt.size) return pkt

            var cur = extStart
            while (cur + 4 <= extEnd) {
                val extType = u16(pkt, cur)
                val extLen = u16(pkt, cur + 2)
                if (cur + 4 + extLen > extEnd) break
                if (extType == 0x0000 && extLen >= 5) {
                    val nameType = pkt[cur + 6].toInt() and 0xFF
                    val nameLen = u16(pkt, cur + 7)
                    if (nameType == 0 && cur + 9 + nameLen <= extEnd) {
                        val oldStart = cur + 9
                        val oldEnd = oldStart + nameLen
                        val newBytes = newSni.toByteArray(Charsets.US_ASCII)
                        val delta = newBytes.size - nameLen
                        val baos = ByteArrayOutputStream(pkt.size + delta)
                        baos.write(pkt, 0, oldStart)
                        baos.write(newBytes)
                        baos.write(pkt, oldEnd, pkt.size - oldEnd)
                        val res = baos.toByteArray()
                        val recLen = u16(res, 3) + delta
                        val hsLen = ((res[6].toInt() and 0xFF) shl 16 or ((res[7].toInt() and 0xFF) shl 8) or (res[8].toInt() and 0xFF)) + delta
                        if (recLen <= 0 || hsLen <= 0) return pkt
                        putU16(res, 3, recLen)
                        res[6] = ((hsLen ushr 16) and 0xFF).toByte()
                        res[7] = ((hsLen ushr 8) and 0xFF).toByte()
                        res[8] = (hsLen and 0xFF).toByte()
                        putU16(res, pos, extTotalLen + delta)
                        putU16(res, cur + 2, extLen + delta)
                        putU16(res, cur + 4, u16(res, cur + 4) + delta)
                        putU16(res, cur + 7, newBytes.size)
                        return res
                    }
                }
                cur += 4 + extLen
            }
            return pkt
        }

        private fun u16(b: ByteArray, off: Int): Int =
            ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

        private fun putU16(b: ByteArray, off: Int, v: Int) {
            b[off] = ((v ushr 8) and 0xFF).toByte()
            b[off + 1] = (v and 0xFF).toByte()
        }
    }
}
