package ir.pni.vpn.core

import android.content.Context
import ir.pni.core.pnixray.Pnixray
import ir.pni.core.pnixray.ScanCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Kotlin bridge to the gomobile-compiled `pni-core.aar` (`ir.pni.core.pnixray.Pnixray`).
 */
object CoreBridge {

    fun initAssets(context: Context) {
        val assetDir = File(context.filesDir, "xray_assets").apply { mkdirs() }
        copyAssetIfMissing(context, "dns/iran_dns.json", File(assetDir, "iran_dns.json"))
        // Copy optional geoip.dat / geosite.dat if bundled in assets/geo
        runCatching {
            val geoAssets = context.assets.list("geo") ?: emptyArray()
            for (name in geoAssets) {
                copyAssetIfMissing(context, "geo/$name", File(assetDir, name))
            }
        }
        runCatching {
            Pnixray.initEnv(assetDir.absolutePath)
        }
    }

    private fun copyAssetIfMissing(context: Context, assetPath: String, dest: File) {
        if (dest.exists() && dest.length() > 0) return
        runCatching {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        }
    }

    fun getXrayVersion(): String = runCatching { Pnixray.getXrayVersion() }.getOrElse { "Xray (pending core build)" }

    fun checkXrayConfig(configJson: String): String? {
        val err = runCatching { Pnixray.checkConfig(configJson) }.getOrElse { return it.message }
        return err.ifBlank { null }
    }

    fun startXray(configJson: String): Result<Unit> = runCatching {
        Pnixray.startXray(configJson)
    }

    fun stopXray(): Result<Unit> = runCatching {
        Pnixray.stopXray()
    }

    fun isXrayRunning(): Boolean = runCatching { Pnixray.isXrayRunning() }.getOrDefault(false)

    fun queryStats(tag: String = "proxy", direction: String = "downlink"): Long =
        runCatching { Pnixray.queryStats(tag, direction) }.getOrDefault(0L)

    suspend fun measureDelay(probeUrl: String = "https://www.gstatic.com/generate_204", timeoutMs: Int = 5000): Long =
        withContext(Dispatchers.IO) {
            runCatching { Pnixray.measureDelay(probeUrl, timeoutMs) }.getOrDefault(-1L)
        }

    fun startSniSpoof(
        listenAddr: String,
        targetAddr: String,
        fakeSni: String,
        method: String,
        fragLen: Int,
        fragDelayMs: Int
    ): Result<Unit> = runCatching {
        Pnixray.startSniSpoof(listenAddr, targetAddr, fakeSni, method, fragLen, fragDelayMs)
    }

    fun stopSniSpoof() {
        runCatching { Pnixray.stopSniSpoof() }
    }

    fun isSniSpoofRunning(): Boolean = runCatching { Pnixray.isSniSpoofRunning() }.getOrDefault(false)

    suspend fun lookupDomainIps(domain: String, dohUrl: String = "https://dns.google/resolve"): String =
        withContext(Dispatchers.IO) {
            runCatching { Pnixray.lookupDomainIPs(domain, dohUrl) }
                .getOrElse { """{"domain":"$domain","error":"${it.message}"}""" }
        }

    fun startScan(configJson: String, callback: ScanCallback): Result<Unit> = runCatching {
        Pnixray.startScan(configJson, callback)
    }

    fun stopScan() {
        runCatching { Pnixray.stopScan() }
    }

    fun isScanning(): Boolean = runCatching { Pnixray.isScanning() }.getOrDefault(false)

    fun exportScanResults(resultsJson: String, format: String, baseShareLink: String): String =
        runCatching { Pnixray.exportScanResults(resultsJson, format, baseShareLink) }
            .getOrElse { "error: ${it.message}" }
}
