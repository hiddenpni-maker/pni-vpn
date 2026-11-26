package ir.pnifox.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ProxyProtocol
import ir.pnifox.core.model.ScanProvider
import ir.pnifox.core.model.ScanResultItem
import ir.pnifox.core.model.ScanSpeedTier
import ir.pnifox.core.model.SubscriptionGroup
import ir.pnifox.core.model.WarpKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val protocolName: String,
    val address: String,
    val port: Int,
    val secret: String,
    val sni: String,
    val transport: String,
    val pathOrServiceName: String,
    val tlsFingerprint: String,
    val realityPublicKey: String,
    val realityShortId: String,
    val fragmentEnabled: Boolean,
    val spoofLocalhost40443: Boolean,
    val rawUri: String,
    val lastPingMs: Int?,
    val isPinned: Boolean,
    val updatedAtEpochMs: Long,
) {
    fun toModel(): ProxyProfile = ProxyProfile(
        id = id,
        groupId = groupId,
        name = name,
        protocol = runCatching { ProxyProtocol.valueOf(protocolName) }.getOrDefault(ProxyProtocol.VLESS),
        address = address,
        port = port,
        secret = secret,
        sni = sni,
        transport = transport,
        pathOrServiceName = pathOrServiceName,
        tlsFingerprint = tlsFingerprint,
        realityPublicKey = realityPublicKey,
        realityShortId = realityShortId,
        fragmentEnabled = fragmentEnabled,
        spoofLocalhost40443 = spoofLocalhost40443,
        rawUri = rawUri,
        lastPingMs = lastPingMs,
        isPinned = isPinned,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        fun fromModel(p: ProxyProfile): ProfileEntity = ProfileEntity(
            id = p.id,
            groupId = p.groupId,
            name = p.name,
            protocolName = p.protocol.name,
            address = p.address,
            port = p.port,
            secret = p.secret,
            sni = p.sni,
            transport = p.transport,
            pathOrServiceName = p.pathOrServiceName,
            tlsFingerprint = p.tlsFingerprint,
            realityPublicKey = p.realityPublicKey,
            realityShortId = p.realityShortId,
            fragmentEnabled = p.fragmentEnabled,
            spoofLocalhost40443 = p.spoofLocalhost40443,
            rawUri = p.rawUri,
            lastPingMs = p.lastPingMs,
            isPinned = p.isPinned,
            updatedAtEpochMs = p.updatedAtEpochMs,
        )
    }
}

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val autoUpdateHours: Int,
    val lastUpdatedEpochMs: Long,
    val profileCount: Int,
) {
    fun toModel(): SubscriptionGroup = SubscriptionGroup(
        id = id,
        name = name,
        url = url,
        autoUpdateHours = autoUpdateHours,
        lastUpdatedEpochMs = lastUpdatedEpochMs,
        profileCount = profileCount,
    )

    companion object {
        fun fromModel(s: SubscriptionGroup): SubscriptionEntity = SubscriptionEntity(
            id = s.id,
            name = s.name,
            url = s.url,
            autoUpdateHours = s.autoUpdateHours,
            lastUpdatedEpochMs = s.lastUpdatedEpochMs,
            profileCount = s.profileCount,
        )
    }
}

@Entity(tableName = "scan_results")
data class ScanResultEntity(
    @PrimaryKey val id: String,
    val ip: String,
    val providerName: String,
    val port: Int,
    val latencyMs: Int,
    val minLatencyMs: Int,
    val jitterMs: Int,
    val packetLossPct: Int,
    val tlsHandshakeMs: Int?,
    val downloadSpeedKbps: Int,
    val colo: String,
    val stabilityScore: Int,
    val tierName: String,
    val scannedAtEpochMs: Long,
) {
    fun toModel(): ScanResultItem = ScanResultItem(
        id = id,
        ip = ip,
        provider = runCatching { ScanProvider.valueOf(providerName) }.getOrDefault(ScanProvider.CLOUDFLARE),
        port = port,
        latencyMs = latencyMs,
        minLatencyMs = minLatencyMs,
        jitterMs = jitterMs,
        packetLossPct = packetLossPct,
        tlsHandshakeMs = tlsHandshakeMs,
        downloadSpeedKbps = downloadSpeedKbps,
        colo = colo,
        stabilityScore = stabilityScore,
        tier = runCatching { ScanSpeedTier.valueOf(tierName) }.getOrDefault(ScanSpeedTier.B),
        scannedAtEpochMs = scannedAtEpochMs,
    )

    companion object {
        fun fromModel(item: ScanResultItem): ScanResultEntity = ScanResultEntity(
            id = item.id,
            ip = item.ip,
            providerName = item.provider.name,
            port = item.port,
            latencyMs = item.latencyMs,
            minLatencyMs = item.minLatencyMs,
            jitterMs = item.jitterMs,
            packetLossPct = item.packetLossPct,
            tlsHandshakeMs = item.tlsHandshakeMs,
            downloadSpeedKbps = item.downloadSpeedKbps,
            colo = item.colo,
            stabilityScore = item.stabilityScore,
            tierName = item.tier.name,
            scannedAtEpochMs = item.scannedAtEpochMs,
        )
    }
}

@Entity(tableName = "traffic_buckets")
data class TrafficBucketEntity(
    @PrimaryKey val dayHourKey: String, // YYYY-MM-DD-HH
    val epochDay: Long,
    val hourOfDay: Int,
    val rxBytes: Long,
    val txBytes: Long,
)

@Entity(tableName = "warp_keys")
data class WarpKeyEntity(
    @PrimaryKey val id: String,
    val protocolTag: String, // MASQUE | WIREGUARD
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
) {
    fun toModel(): WarpKey = WarpKey(
        id = id,
        protocolTag = protocolTag,
        deviceId = deviceId,
        accessToken = accessToken,
        privateKey = privateKey,
        publicKey = publicKey,
        peerPublicKey = peerPublicKey,
        endpointV4 = endpointV4,
        endpointV6 = endpointV6,
        clientAddressV4 = clientAddressV4,
        clientAddressV6 = clientAddressV6,
        reservedCsv = reservedCsv,
        createdAtEpochMs = createdAtEpochMs,
    )

    companion object {
        fun fromModel(key: WarpKey): WarpKeyEntity = WarpKeyEntity(
            id = key.id,
            protocolTag = key.protocolTag,
            deviceId = key.deviceId,
            accessToken = key.accessToken,
            privateKey = key.privateKey,
            publicKey = key.publicKey,
            peerPublicKey = key.peerPublicKey,
            endpointV4 = key.endpointV4,
            endpointV6 = key.endpointV6,
            clientAddressV4 = key.clientAddressV4,
            clientAddressV6 = key.clientAddressV6,
            reservedCsv = key.reservedCsv,
            createdAtEpochMs = key.createdAtEpochMs,
        )
    }
}
