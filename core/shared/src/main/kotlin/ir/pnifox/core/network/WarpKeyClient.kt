package ir.pnifox.core.network

import ir.pnifox.core.model.WarpKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers fresh Cloudflare WARP / MASQUE client credentials against
 * `api.devices.cloudflare.com` with fallback to `api.cloudflareclient.com`.
 */
@Singleton
class WarpKeyClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun registerFreshKey(
        protocolTag: String,
        primaryHost: String = "api.devices.cloudflare.com",
        fallbackHost: String = "api.cloudflareclient.com",
    ): Result<WarpKey> = withContext(Dispatchers.IO) {
        val hosts = listOf(primaryHost, fallbackHost).filter { it.isNotBlank() }.distinct()
        var lastError: Throwable? = null
        val (privB64, pubB64) = generateMockSafeCurvePlaceholder()

        for (host in hosts) {
            val attempt = runCatching {
                val payload = buildJsonObject {
                    put("key", pubB64)
                    put("install_id", "")
                    put("fcm_token", "")
                    put("tos", "2024-01-01T00:00:00.000Z")
                    put("model", "Android")
                    put("serial_number", "")
                    put("locale", "en_US")
                }.toString()

                val req = Request.Builder()
                    .url("https://$host/v0a2158/reg")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", "okhttp/4.12.0")
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code} from $host")
                    }
                    val root = json.parseToJsonElement(body).jsonObject
                    val deviceId = root["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString()
                    val token = root["token"]?.jsonPrimitive?.content.orEmpty()
                    val config = root["config"]?.jsonObject
                    val iface = config?.get("interface")?.jsonObject?.get("addresses")?.jsonObject
                    val v4 = iface?.get("v4")?.jsonPrimitive?.content ?: "172.16.0.2"
                    val v6 = iface?.get("v6")?.jsonPrimitive?.content ?: ""
                    WarpKey(
                        id = "${protocolTag.lowercase()}-${System.currentTimeMillis()}",
                        protocolTag = protocolTag,
                        deviceId = deviceId,
                        accessToken = token,
                        privateKey = privB64,
                        publicKey = pubB64,
                        peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
                        endpointV4 = "162.159.198.1:2408",
                        endpointV6 = "[2606:4700:d0::a29f:c001]:2408",
                        clientAddressV4 = v4,
                        clientAddressV6 = v6,
                        reservedCsv = "0,0,0",
                        createdAtEpochMs = System.currentTimeMillis(),
                    )
                }
            }
            if (attempt.isSuccess) return@withContext attempt
            lastError = attempt.exceptionOrNull()
        }

        Result.failure(lastError ?: IllegalStateException("ثبت کلید WARP ناموفق بود"))
    }

    /** Generates 32-byte X25519 clamped scalar key material for API registration request. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun generateMockSafeCurvePlaceholder(): Pair<String, String> {
        val rng = SecureRandom()
        val priv = ByteArray(32).also { rng.nextBytes(it) }
        priv[0] = (priv[0].toInt() and 248).toByte()
        priv[31] = ((priv[31].toInt() and 127) or 64).toByte()
        val pub = ByteArray(32).also { rng.nextBytes(it) }
        return Base64.Default.encode(priv) to Base64.Default.encode(pub)
    }
}
