package ir.pni.vpn.parser

import ir.pni.vpn.data.model.ProtocolType
import ir.pni.vpn.data.model.ProxyProfile

/**
 * Soldier-Client style Spoof Config Converter:
 * Rewrites VLESS / Trojan / VMess profiles to route through PNI's local SNI Spoof proxy
 * (`127.0.0.1:40443`), while keeping the original remote server target & SNI metadata
 * so SniSpoofService forwards fragmented/spoofed TLS ClientHello packets transparently.
 */
object SpoofConverter {

    data class ConversionResult(
        val profile: ProxyProfile,
        val originalTarget: String,
        val shareUri: String
    )

    fun convertToLocalSpoof(
        source: ProxyProfile,
        localPort: Int = 40443,
        fakeSni: String = ""
    ): ConversionResult? {
        if (source.protocol !in setOf(ProtocolType.VLESS, ProtocolType.TROJAN, ProtocolType.VMESS)) {
            return null
        }

        val target = "${source.server}:${source.port}"
        val preservedSni = source.sni.ifBlank { source.host.ifBlank { source.server } }
        val converted = source.copy(
            name = "${source.name} [SNI-Spoof]",
            server = "127.0.0.1",
            port = localPort,
            sni = preservedSni,
            host = source.host.ifBlank { preservedSni },
            unsafeTls = true,
            fingerprint = "unsafe",
            spoofEnabled = true,
            spoofTargetAddr = target,
            spoofFakeSni = fakeSni.ifBlank { preservedSni }
        )
        return ConversionResult(
            profile = converted,
            originalTarget = target,
            shareUri = ShareLinkParser.toShareLink(converted)
        )
    }
}
