package ir.pnifox.core.network

import ir.pnifox.core.model.ProxyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun fetchSubscriptionProfiles(url: String, groupId: String): Result<List<ProxyProfile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(url.trim())
                    .header("User-Agent", "pnifox/2.0 v2rayNG")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body?.string().orEmpty()
                    ShareLinkParser.parseBatch(body, groupId = groupId)
                }
            }
        }
}
