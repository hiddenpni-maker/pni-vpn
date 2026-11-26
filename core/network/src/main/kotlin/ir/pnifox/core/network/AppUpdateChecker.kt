package ir.pnifox.core.network

import ir.pnifox.core.model.AppUpdateInfo
import ir.pnifox.core.model.UpdateCheckState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object VersionComparator {

    fun parseNumericParts(version: String): Triple<Int, Int, Int> {
        val cleaned = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        val parts = cleaned.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    fun isRemoteNewer(currentVersion: String, remoteTag: String): Boolean {
        val cur = parseNumericParts(currentVersion)
        val rem = parseNumericParts(remoteTag)
        if (rem.first != cur.first) return rem.first > cur.first
        if (rem.second != cur.second) return rem.second > cur.second
        if (rem.third != cur.third) return rem.third > cur.third
        val curPre = currentVersion.substringAfter('-', "")
        val remPre = remoteTag.substringAfter('-', "")
        // Stable release (no pre-release suffix) supersedes same numeric pre-release (e.g. 2.0.0 > 2.0.0-phase1)
        return curPre.isNotBlank() && remPre.isBlank()
    }
}

class AppUpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatestRelease(
        currentVersionName: String,
        ownerRepo: String = DEFAULT_REPO,
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$ownerRepo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "pnifox-android/$currentVersionName")
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext if (resp.code == 404) {
                        UpdateCheckState.UpToDate(currentVersionName)
                    } else {
                        UpdateCheckState.Error("HTTP ${resp.code}: ${resp.message}")
                    }
                }
                val body = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body) as? JsonObject
                    ?: return@withContext UpdateCheckState.Error("Invalid GitHub release JSON")

                val tagName = obj["tag_name"]?.jsonPrimitive?.content.orEmpty()
                if (tagName.isBlank()) {
                    return@withContext UpdateCheckState.UpToDate(currentVersionName)
                }

                if (!VersionComparator.isRemoteNewer(currentVersionName, tagName)) {
                    return@withContext UpdateCheckState.UpToDate(currentVersionName)
                }

                val assets = obj["assets"] as? JsonArray
                var apkUrl: String? = null
                var apkSize = 0L
                assets?.forEach { el ->
                    val asset = el as? JsonObject ?: return@forEach
                    val name = asset["name"]?.jsonPrimitive?.content.orEmpty()
                    if (apkUrl == null && name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset["browser_download_url"]?.jsonPrimitive?.content
                        apkSize = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
                    }
                }

                val info = AppUpdateInfo(
                    latestTag = tagName,
                    versionName = tagName.removePrefix("v").removePrefix("V"),
                    releaseName = obj["name"]?.jsonPrimitive?.content.orEmpty().ifBlank { tagName },
                    changelog = obj["body"]?.jsonPrimitive?.content.orEmpty(),
                    releaseUrl = obj["html_url"]?.jsonPrimitive?.content
                        .orEmpty()
                        .ifBlank { "https://github.com/$ownerRepo/releases/latest" },
                    apkDownloadUrl = apkUrl,
                    apkSizeBytes = apkSize,
                    publishedAt = obj["published_at"]?.jsonPrimitive?.content.orEmpty().take(10),
                )
                UpdateCheckState.UpdateAvailable(info = info, currentVersion = currentVersionName)
            }
        }.getOrElse { err ->
            UpdateCheckState.Error(err.message ?: err::class.java.simpleName)
        }
    }

    companion object {
        const val DEFAULT_REPO = "hiddenpni-maker/pni-vpn"
    }
}
