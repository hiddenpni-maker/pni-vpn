package ir.pnifox.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WarpKey(
    val id: String,
    val protocolTag: String,
    val deviceId: String,
    val accessToken: String,
    val privateKey: String,
    val publicKey: String,
    val peerPublicKey: String,
    val endpointV4: String,
    val endpointV6: String,
    val clientAddressV4: String,
    val clientAddressV6: String,
    val reservedCsv: String,
    val createdAtEpochMs: Long,
)
