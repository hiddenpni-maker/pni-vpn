package ir.pni.vpn.core

import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.ProtocolType
import ir.pni.vpn.data.model.ProxyProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds full Xray-core JSON configs combining:
 * - Soldier-Client / v2rayNG SOCKS5 (`10808`) & HTTP (`10809`) inbounds + stats
 * - PattN extensions: custom `cipherSuites`, `unsafe` fingerprint, unencrypted public VLESS/Trojan
 * - BPB-VPN features: TLS Fragment, UDP Noise, Chain Proxies (`dialerProxy`)
 * - Iran smart routing (Iran IPs/domains direct, ads block, DoH DNS)
 */
object XrayConfigBuilder {

    const val LOCAL_SOCKS_PORT = 10808
    const val LOCAL_HTTP_PORT = 10809

    private val prettyJson = Json { prettyPrint = true }

    fun build(
        profile: ProxyProfile,
        settings: AppSettings,
        chainNextProfile: ProxyProfile? = null
    ): String {
        if (profile.protocol == ProtocolType.CUSTOM_JSON && profile.rawCustomJson.isNotBlank()) {
            return profile.rawCustomJson
        }

        val hasFragment = profile.fragmentEnabled && !profile.spoofEnabled
        val hasNoise = profile.noiseEnabled && profile.protocol == ProtocolType.WIREGUARD
        val hasChain = chainNextProfile != null

        val outbounds = buildJsonArray {
            add(
                buildOutbound(
                    tag = "proxy",
                    profile = profile,
                    dialerProxy = when {
                        hasChain -> "chain-hop"
                        hasFragment -> "fragment"
                        else -> null
                    }
                )
            )
            if (chainNextProfile != null) {
                add(
                    buildOutbound(
                        tag = "chain-hop",
                        profile = chainNextProfile,
                        dialerProxy = if (hasFragment) "fragment" else null
                    )
                )
            }
            if (hasFragment || hasNoise) {
                add(buildJsonObject {
                    put("tag", "fragment")
                    put("protocol", "freedom")
                    putJsonObject("settings") {
                        if (hasFragment) {
                            putJsonObject("fragment") {
                                put("packets", profile.fragmentPackets.ifBlank { "tlshello" })
                                put("length", profile.fragmentLength.ifBlank { "10-20" })
                                put("interval", profile.fragmentInterval.ifBlank { "10-20" })
                            }
                        }
                        if (hasNoise) {
                            putJsonArray("noises") {
                                add(buildJsonObject {
                                    put("type", profile.noiseType.ifBlank { "rand" })
                                    put("packet", profile.noisePacket.ifBlank { "50-100" })
                                    put("delay", profile.noiseDelay.ifBlank { "10-15" })
                                })
                            }
                        }
                    }
                })
            }
            add(buildJsonObject {
                put("tag", "direct")
                put("protocol", "freedom")
                putJsonObject("settings") { put("domainStrategy", "UseIP") }
            })
            add(buildJsonObject {
                put("tag", "block")
                put("protocol", "blackhole")
                putJsonObject("settings") {
                    putJsonObject("response") { put("type", "http") }
                }
            })
        }

        val root = buildJsonObject {
            putJsonObject("log") { put("loglevel", "warning") }
            putJsonObject("stats") {}
            putJsonObject("policy") {
                putJsonObject("levels") {
                    putJsonObject("0") {
                        put("statsUserUplink", true)
                        put("statsUserDownlink", true)
                    }
                }
                putJsonObject("system") {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    add(JsonPrimitive(settings.dnsMode.ifBlank { "https://1.1.1.1/dns-query" }))
                    add(JsonPrimitive(settings.fallbackDns.ifBlank { "9.9.9.9" }))
                    if (settings.bypassIranDomains) {
                        add(buildJsonObject {
                            put("address", "223.5.5.5")
                            putJsonArray("domains") { add(JsonPrimitive("geosite:ir")) }
                        })
                    }
                }
            }
            putJsonArray("inbounds") {
                add(buildJsonObject {
                    put("tag", "socks-in")
                    put("port", LOCAL_SOCKS_PORT)
                    put("listen", "127.0.0.1")
                    put("protocol", "socks")
                    putJsonObject("settings") {
                        put("auth", "noauth")
                        put("udp", true)
                    }
                    putJsonObject("sniffing") {
                        put("enabled", true)
                        putJsonArray("destOverride") {
                            add(JsonPrimitive("http"))
                            add(JsonPrimitive("tls"))
                            add(JsonPrimitive("quic"))
                        }
                    }
                })
                add(buildJsonObject {
                    put("tag", "http-in")
                    put("port", LOCAL_HTTP_PORT)
                    put("listen", "127.0.0.1")
                    put("protocol", "http")
                })
            }
            put("outbounds", outbounds)
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    if (settings.blockAds) {
                        add(buildJsonObject {
                            put("type", "field")
                            putJsonArray("domain") { add(JsonPrimitive("geosite:category-ads-all")) }
                            put("outboundTag", "block")
                        })
                    }
                    if (settings.bypassIranDomains) {
                        add(buildJsonObject {
                            put("type", "field")
                            putJsonArray("domain") {
                                add(JsonPrimitive("geosite:ir"))
                                add(JsonPrimitive("regexp:.*\\.ir$"))
                            }
                            put("outboundTag", "direct")
                        })
                    }
                    if (settings.bypassIranIps) {
                        add(buildJsonObject {
                            put("type", "field")
                            putJsonArray("ip") {
                                add(JsonPrimitive("geoip:private"))
                                add(JsonPrimitive("geoip:ir"))
                            }
                            put("outboundTag", "direct")
                        })
                    }
                }
            }
        }

        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    private fun buildOutbound(tag: String, profile: ProxyProfile, dialerProxy: String?): JsonObject {
        val settingsObj = when (profile.protocol) {
            ProtocolType.VLESS -> buildJsonObject {
                putJsonArray("vnext") {
                    add(buildJsonObject {
                        put("address", profile.server)
                        put("port", profile.port)
                        putJsonArray("users") {
                            add(buildJsonObject {
                                put("id", profile.uuidOrPassword)
                                put("encryption", "none")
                                if (profile.flow.isNotBlank()) put("flow", profile.flow)
                            })
                        }
                    })
                }
            }
            ProtocolType.VMESS -> buildJsonObject {
                putJsonArray("vnext") {
                    add(buildJsonObject {
                        put("address", profile.server)
                        put("port", profile.port)
                        putJsonArray("users") {
                            add(buildJsonObject {
                                put("id", profile.uuidOrPassword)
                                put("alterId", 0)
                                put("security", "auto")
                            })
                        }
                    })
                }
            }
            ProtocolType.TROJAN -> buildJsonObject {
                putJsonArray("servers") {
                    add(buildJsonObject {
                        put("address", profile.server)
                        put("port", profile.port)
                        put("password", profile.uuidOrPassword)
                    })
                }
            }
            ProtocolType.SHADOWSOCKS -> {
                val method = profile.uuidOrPassword.substringBefore(":", "chacha20-ietf-poly1305")
                val pass = profile.uuidOrPassword.substringAfter(":", "")
                buildJsonObject {
                    putJsonArray("servers") {
                        add(buildJsonObject {
                            put("address", profile.server)
                            put("port", profile.port)
                            put("method", method)
                            put("password", pass)
                        })
                    }
                }
            }
            ProtocolType.WIREGUARD -> buildJsonObject {
                put("secretKey", profile.uuidOrPassword)
                putJsonArray("address") { add(JsonPrimitive("172.16.0.2/32")) }
                putJsonArray("peers") {
                    add(buildJsonObject {
                        put("publicKey", profile.publicKey)
                        put("endpoint", "${profile.server}:${profile.port}")
                    })
                }
                put("mtu", 1280)
            }
            else -> buildJsonObject {}
        }

        val xrayProtocol = when (profile.protocol) {
            ProtocolType.VLESS -> "vless"
            ProtocolType.VMESS -> "vmess"
            ProtocolType.TROJAN -> "trojan"
            ProtocolType.SHADOWSOCKS -> "shadowsocks"
            ProtocolType.WIREGUARD -> "wireguard"
            else -> "vless"
        }

        return buildJsonObject {
            put("tag", tag)
            put("protocol", xrayProtocol)
            put("settings", settingsObj)
            if (profile.protocol != ProtocolType.WIREGUARD) {
                put("streamSettings", buildStreamSettings(profile, dialerProxy))
            } else if (dialerProxy != null) {
                putJsonObject("streamSettings") {
                    putJsonObject("sockopt") { put("dialerProxy", dialerProxy) }
                }
            }
        }
    }

    private fun buildStreamSettings(profile: ProxyProfile, dialerProxy: String?): JsonObject {
        val net = profile.network.ifBlank { "tcp" }
        val sec = profile.security.ifBlank { "none" }
        val sni = profile.sni.ifBlank { profile.host.ifBlank { profile.server } }

        return buildJsonObject {
            put("network", net)
            put("security", sec)
            when (sec) {
                "tls" -> putJsonObject("tlsSettings") {
                    put("serverName", sni)
                    put("allowInsecure", profile.unsafeTls || profile.fingerprint.equals("unsafe", ignoreCase = true))
                    if (profile.fingerprint.isNotBlank()) {
                        put("fingerprint", profile.fingerprint)
                    }
                    if (profile.alpn.isNotBlank()) {
                        put("alpn", JsonArray(profile.alpn.split(",").map { JsonPrimitive(it.trim()) }))
                    }
                    if (profile.cipherSuites.isNotBlank()) {
                        put("cipherSuites", profile.cipherSuites)
                    }
                }
                "reality" -> putJsonObject("realitySettings") {
                    put("serverName", sni)
                    put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
                    put("publicKey", profile.publicKey)
                    put("shortId", profile.shortId)
                    if (profile.spiderX.isNotBlank()) put("spiderX", profile.spiderX)
                }
            }

            when (net) {
                "ws" -> putJsonObject("wsSettings") {
                    put("path", profile.path.ifBlank { "/" })
                    val hostHdr = profile.host.ifBlank { sni }
                    if (hostHdr.isNotBlank()) {
                        putJsonObject("headers") { put("Host", hostHdr) }
                    }
                }
                "xhttp", "splithttp" -> putJsonObject("xhttpSettings") {
                    put("path", profile.path.ifBlank { "/" })
                    val hostHdr = profile.host.ifBlank { sni }
                    if (hostHdr.isNotBlank()) put("host", hostHdr)
                }
                "grpc" -> putJsonObject("grpcSettings") {
                    put("serviceName", profile.path.trimStart('/'))
                }
                "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                    put("path", profile.path.ifBlank { "/" })
                    val hostHdr = profile.host.ifBlank { sni }
                    if (hostHdr.isNotBlank()) put("host", hostHdr)
                }
            }

            if (dialerProxy != null) {
                putJsonObject("sockopt") {
                    put("dialerProxy", dialerProxy)
                }
            }
        }
    }
}
