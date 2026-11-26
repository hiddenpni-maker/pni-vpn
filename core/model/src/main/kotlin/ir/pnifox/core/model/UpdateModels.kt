package ir.pnifox.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val latestTag: String,
    val versionName: String,
    val releaseName: String,
    val changelog: String,
    val releaseUrl: String,
    val apkDownloadUrl: String? = null,
    val apkSizeBytes: Long = 0L,
    val publishedAt: String = "",
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val currentVersion: String) : UpdateCheckState
    data class UpdateAvailable(
        val info: AppUpdateInfo,
        val currentVersion: String,
    ) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}
