package ir.pnifox.core.vpn

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import com.therealaleph.mhrv.Native
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class MhrCaStatus(
    val exported: Boolean = false,
    val subjectCn: String? = null,
    val fingerprintHex: String? = null,
    val isTrustedInstalled: Boolean = false,
    val savedLocationLabel: String? = null,
)

object MhrCaInstaller {

    private const val INTERNAL_CA_FILE = "mhr-ca.crt"
    private const val DOWNLOAD_CA_FILE = "pnifox-mhr-ca.crt"

    fun caInternalFile(ctx: Context): File = File(File(ctx.filesDir, "mhr").apply { mkdirs() }, INTERNAL_CA_FILE)

    fun exportAndInspect(ctx: Context): MhrCaStatus {
        if (!Native.ensureLoaded()) return MhrCaStatus()
        val dataDir = File(ctx.filesDir, "mhr").apply { mkdirs() }
        Native.setDataDir(dataDir.absolutePath)
        val dest = caInternalFile(ctx)
        val ok = runCatching { Native.exportCa(dest.absolutePath) }.getOrDefault(false)
        if (!ok || !dest.exists() || dest.length() <= 0L) return MhrCaStatus()
        val der = readDer(dest) ?: return MhrCaStatus()
        val fp = sha256(der)
        val fpHex = fp.joinToString(":") { "%02X".format(it) }
        val cn = parseSubjectCn(der)
        val installed = isInstalledInAndroidCaStore(fp)
        return MhrCaStatus(
            exported = true,
            subjectCn = cn,
            fingerprintHex = fpHex,
            isTrustedInstalled = installed,
        )
    }

    fun exportAndSaveToDownloads(ctx: Context): Result<MhrCaStatus> = runCatching {
        val status = exportAndInspect(ctx)
        check(status.exported) { "Failed to export MHR MITM CA certificate (libmhrv_rs.so)" }
        val der = readDer(caInternalFile(ctx)) ?: error("Unable to read exported CA certificate")
        val pem = derToPem(der)
        val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(ctx, DOWNLOAD_CA_FILE, pem) ?: error("MediaStore write failed")
            "Downloads/$DOWNLOAD_CA_FILE"
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: error("Download directory unavailable")
            dir.mkdirs()
            val out = File(dir, DOWNLOAD_CA_FILE)
            out.writeBytes(pem)
            out.absolutePath
        }
        status.copy(savedLocationLabel = savedPath)
    }

    fun buildOpenSettingsForCaIntent(): Intent =
        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun saveViaMediaStore(ctx: Context, displayName: String, bytes: ByteArray): Boolean? {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/x-x509-ca-cert")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        runCatching {
            val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, sel, arrayOf(displayName))
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            true
        }.getOrNull()
    }

    private fun isInstalledInAndroidCaStore(targetSha256: ByteArray): Boolean = runCatching {
        val ks = KeyStore.getInstance("AndroidCAStore")
        ks.load(null)
        val aliases = ks.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            val cert = ks.getCertificate(alias) ?: continue
            val encoded = runCatching { cert.encoded }.getOrNull() ?: continue
            if (sha256(encoded).contentEquals(targetSha256)) return true
        }
        false
    }.getOrDefault(false)

    private fun parseSubjectCn(der: ByteArray): String? = runCatching {
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(der.inputStream()) as X509Certificate
        Regex("""CN=([^,]+)""").find(cert.subjectX500Principal.name)?.groupValues?.get(1)
    }.getOrNull()

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun derToPem(der: ByteArray): ByteArray {
        val b64 = Base64.encodeToString(der, Base64.NO_WRAP)
        val chunks = b64.chunked(64).joinToString("\n")
        return "-----BEGIN CERTIFICATE-----\n$chunks\n-----END CERTIFICATE-----\n"
            .toByteArray(Charsets.US_ASCII)
    }

    private fun readDer(file: File): ByteArray? {
        val raw = runCatching { file.readBytes() }.getOrNull() ?: return null
        val text = runCatching { raw.toString(Charsets.US_ASCII) }.getOrNull()
        if (text != null && text.contains("BEGIN CERTIFICATE")) {
            val body = text
                .substringAfter("-----BEGIN CERTIFICATE-----", "")
                .substringBefore("-----END CERTIFICATE-----", "")
                .replace(Regex("\\s+"), "")
            if (body.isNotEmpty()) {
                return runCatching { Base64.decode(body, Base64.DEFAULT) }.getOrNull() ?: raw
            }
        }
        return raw
    }
}
