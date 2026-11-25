package ir.pni.vpn.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * BPB-VPN compatible Cloudflare Workers REST Deployer:
 * Deploys `assets/worker/worker.js` to the user's Cloudflare account with an `ENV` binding
 * (`base64({v:1, id, pips, fb})`) and outputs a ready-to-import VLESS+WS+TLS link.
 */
object CloudflareDeployer {

    private const val CF_API = "https://api.cloudflare.com/client/v4"
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class CfAccount(val id: String, val name: String)

    data class DeployResult(
        val workerUrl: String,
        val shareLink: String,
        val uuid: String
    )

    fun tokenCreationUrl(): String {
        val permissions = """[{"key":"workers_scripts","type":"edit"},{"key":"page","type":"edit"},{"key":"dns","type":"edit"},{"key":"user_details","type":"read"}]"""
        val encoded = java.net.URLEncoder.encode(permissions, "UTF-8")
        return "https://dash.cloudflare.com/profile/api-tokens?permissionGroupKeys=$encoded&accountId=*&zoneId=all&name=PNI-VPN"
    }

    fun sanitizeToken(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_-]"), "")

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeEnv(uuid: String, proxyIps: List<String>, fallbackUrl: String): String {
        val payload = buildJsonObject {
            put("v", 1)
            put("id", uuid)
            putJsonArray("pips") {
                proxyIps.filter { it.isNotBlank() }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.trim())) }
            }
            put("fb", fallbackUrl.trim())
        }.toString()
        return Base64.Default.encode(payload.toByteArray(Charsets.UTF_8))
    }

    suspend fun listAccounts(rawToken: String): Result<List<CfAccount>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = sanitizeToken(rawToken)
            require(token.isNotBlank()) { "توکن Cloudflare معتبر نیست" }
            val req = Request.Builder()
                .url("$CF_API/accounts?page=1&per_page=20")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                if (root["success"]?.jsonPrimitive?.content != "true") {
                    throw IllegalStateException("خطای API کلودفلر (${resp.code}): $body")
                }
                root["result"]?.jsonArray?.map { el ->
                    val obj = el.jsonObject
                    CfAccount(
                        id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                        name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    )
                }.orEmpty()
            }
        }
    }

    suspend fun deployWorker(
        context: Context,
        rawToken: String,
        accountId: String,
        workerName: String,
        uuidInput: String,
        proxyIps: List<String>,
        fallbackUrl: String,
        cleanDomainOrIp: String = ""
    ): Result<DeployResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = sanitizeToken(rawToken)
            require(token.isNotBlank()) { "توکن کلودفلر خالی است" }
            require(accountId.isNotBlank()) { "شناسه اکانت کلودفلر مشخص نیست" }

            val uuid = uuidInput.ifBlank { UUID.randomUUID().toString() }
            val scriptName = workerName.lowercase().replace(Regex("[^a-z0-9-]"), "-").ifBlank { "pni-edge" }
            val workerCode = context.assets.open("worker/worker.js").bufferedReader().use { it.readText() }
            val envValue = encodeEnv(uuid, proxyIps, fallbackUrl)

            val metadata = buildJsonObject {
                put("main_module", "worker.js")
                put("compatibility_date", "2024-11-01")
                putJsonArray("bindings") {
                    add(buildJsonObject {
                        put("type", "plain_text")
                        put("name", "ENV")
                        put("text", envValue)
                    })
                }
            }.toString()

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadata.toRequestBody("application/json".toMediaType())
                )
                .addFormDataPart(
                    "worker.js",
                    "worker.js",
                    workerCode.toRequestBody("application/javascript+module".toMediaType())
                )
                .build()

            val uploadReq = Request.Builder()
                .url("$CF_API/accounts/$accountId/workers/scripts/$scriptName")
                .header("Authorization", "Bearer $token")
                .put(multipart)
                .build()

            client.newCall(uploadReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                if (root["success"]?.jsonPrimitive?.content != "true") {
                    throw IllegalStateException("خطا در آپلود Worker (${resp.code}): $body")
                }
            }

            // Enable workers.dev subdomain route
            val enableBody = """{"enabled":true}""".toRequestBody("application/json".toMediaType())
            val subEnableReq = Request.Builder()
                .url("$CF_API/accounts/$accountId/workers/scripts/$scriptName/subdomain")
                .header("Authorization", "Bearer $token")
                .post(enableBody)
                .build()
            runCatching { client.newCall(subEnableReq).execute().close() }

            // Fetch workers.dev account subdomain
            var subdomainHost = "$scriptName.workers.dev"
            val subReq = Request.Builder()
                .url("$CF_API/accounts/$accountId/workers/subdomain")
                .header("Authorization", "Bearer $token")
                .build()
            runCatching {
                client.newCall(subReq).execute().use { resp ->
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val sub = root["result"]?.jsonObject?.get("subdomain")?.jsonPrimitive?.content
                    if (!sub.isNullOrBlank()) {
                        subdomainHost = "$scriptName.$sub.workers.dev"
                    }
                }
            }

            val connectAddr = cleanDomainOrIp.ifBlank { subdomainHost }
            val shareLink =
                "vless://$uuid@$connectAddr:443?encryption=none&security=tls&type=ws&host=$subdomainHost&sni=$subdomainHost&path=%2Fws&fp=chrome#PNI-CF-$scriptName"

            DeployResult(
                workerUrl = "https://$subdomainHost",
                shareLink = shareLink,
                uuid = uuid
            )
        }
    }
}
