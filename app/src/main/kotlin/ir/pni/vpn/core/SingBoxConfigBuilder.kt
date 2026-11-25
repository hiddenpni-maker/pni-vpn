package ir.pni.vpn.core

import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.ProtocolType
import ir.pni.vpn.data.model.ProxyProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds sing-box JSON configs for protocols handled by `libpnisingbox.so`
 * (Lantern sing-box-minimal stack: Hysteria2, TUIC v5, SSH Tunnel, Lantern/Outline Stealth).
 */
object SingBoxConfigBuilder {

    private val prettyJson = Json { prettyPrint = true }

    fun build(profile: ProxyProfile, settings: AppSettings): String {
        val outbound = when (profile.protocol) {
            ProtocolType.HYSTERIA2 -> buildJsonObject {
                put("type", "hysteria2")
                put("tag", "proxy")
                put("server", profile.server)
                put("server_port", profile.port)
                put("password", profile.uuidOrPassword)
                if (profile.obfsPassword.isNotBlank()) {
                    putJsonObject("obfs") {
                        put("type", "salamander")
                        put("password", profile.obfsPassword)
                    }
                }
                putJsonObject("tls") {
                    put("enabled", true)
                    put("server_name", profile.sni.ifBlank { profile.server })
                    put("insecure", profile.unsafeTls)
                    putJsonArray("alpn") { add(JsonPrimitive("h3")) }
                }
            }
            ProtocolType.TUIC -> {
                val uuid = profile.uuidOrPassword.substringBefore(":", profile.uuidOrPassword)
                val pass = profile.uuidOrPassword.substringAfter(":", "")
                buildJsonObject {
                    put("type", "tuic")
                    put("tag", "proxy")
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("uuid", uuid)
                    put("password", pass)
                    put("congestion_control", "bbr")
                    putJsonObject("tls") {
                        put("enabled", true)
                        put("server_name", profile.sni.ifBlank { profile.server })
                        put("insecure", profile.unsafeTls)
                        putJsonArray("alpn") { add(JsonPrimitive("h3")) }
                    }
                }
            }
            ProtocolType.SSH -> {
                val user = profile.uuidOrPassword.substringBefore(":", "root")
                val pass = profile.uuidOrPassword.substringAfter(":", "")
                buildJsonObject {
                    put("type", "ssh")
                    put("tag", "proxy")
                    put("server", profile.server)
                    put("server_port", profile.port)
                    put("user", user)
                    put("password", pass)
                }
            }
            else -> buildJsonObject {
                put("type", "direct")
                put("tag", "proxy")
            }
        }

        val root = buildJsonObject {
            putJsonObject("log") { put("level", "info") }
            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("type", "mixed")
                    put("tag", "mixed-in")
                    put("listen", "127.0.0.1")
                    put("listen_port", XrayConfigBuilder.LOCAL_SOCKS_PORT)
                })
            }
            putJsonArray("outbounds") {
                add(outbound)
                add(buildJsonObject {
                    put("type", "direct")
                    put("tag", "direct")
                })
            }
            putJsonObject("route") {
                put("final", "proxy")
                if (settings.bypassIranIps) {
                    putJsonArray("rules") {
                        add(buildJsonObject {
                            put("ip_is_private", true)
                            put("outbound", "direct")
                        })
                    }
                }
            }
        }
        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }
}
