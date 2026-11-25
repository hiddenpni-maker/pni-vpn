package ir.pni.vpn.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.pni.vpn.data.model.AppSettings
import ir.pni.vpn.data.model.GuideSection
import ir.pni.vpn.data.model.ProtocolType
import ir.pni.vpn.data.model.ProxyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.pniDataStore by preferencesDataStore(name = "pni_store")

class PniRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val profilesKey = stringPreferencesKey("profiles_json")
    private val settingsKey = stringPreferencesKey("settings_json")

    val profilesFlow: Flow<List<ProxyProfile>> = context.pniDataStore.data.map { prefs ->
        val raw = prefs[profilesKey]
        if (raw.isNullOrBlank()) {
            defaultBuiltInProfiles()
        } else {
            runCatching { json.decodeFromString<List<ProxyProfile>>(raw) }
                .getOrElse { defaultBuiltInProfiles() }
        }
    }

    val settingsFlow: Flow<AppSettings> = context.pniDataStore.data.map { prefs ->
        val raw = prefs[settingsKey]
        if (raw.isNullOrBlank()) {
            AppSettings()
        } else {
            runCatching { json.decodeFromString<AppSettings>(raw) }
                .getOrElse { AppSettings() }
        }
    }

    suspend fun saveProfiles(profiles: List<ProxyProfile>) {
        context.pniDataStore.edit { prefs ->
            prefs[profilesKey] = json.encodeToString(profiles)
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.pniDataStore.edit { prefs ->
            val current = prefs[settingsKey]
                ?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
                ?: AppSettings()
            prefs[settingsKey] = json.encodeToString(transform(current))
        }
    }

    fun loadGuides(): List<GuideSection> = runCatching {
        val raw = context.assets.open("guides/guides_fa.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<GuideSection>>(raw)
    }.getOrDefault(emptyList())

    fun loadCdnCidrs(assetPath: String): List<String> = runCatching {
        context.assets.open(assetPath).bufferedReader().use { reader ->
            reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        }
    }.getOrDefault(emptyList())

    private fun defaultBuiltInProfiles(): List<ProxyProfile> = listOf(
        ProxyProfile(
            id = "builtin-aether-masque",
            name = "Aether MASQUE (بدون سرور - Cloudflare)",
            protocol = ProtocolType.AETHER_MASQUE,
            server = "162.159.198.1",
            port = 443,
            sni = "consumer-masque.cloudflareclient.com",
            aetherMode = "masque",
            group = "داخلی / اضطراری"
        ),
        ProxyProfile(
            id = "builtin-aether-warp",
            name = "Aether WARP (WireGuard + Noise)",
            protocol = ProtocolType.AETHER_WARP,
            server = "162.159.192.1",
            port = 2408,
            noiseEnabled = true,
            aetherMode = "warp",
            group = "داخلی / اضطراری"
        ),
        ProxyProfile(
            id = "builtin-psiphon",
            name = "Psiphon Exit (مسیر جایگزین غیر کلودفلر)",
            protocol = ProtocolType.PSIPHON,
            server = "psiphon",
            port = 1080,
            group = "داخلی / اضطراری"
        ),
        ProxyProfile(
            id = "builtin-tor-obfs4",
            name = "Tor + Lyrebird (obfs4 / WebTunnel)",
            protocol = ProtocolType.TOR,
            server = "127.0.0.1",
            port = 9050,
            torBridgeType = "obfs4",
            group = "داخلی / اضطراری"
        ),
        ProxyProfile(
            id = "sample-dnstt-am-tunnel",
            name = "نمونه تونل DNS (DNSTT / AM Tunnel)",
            protocol = ProtocolType.DNS_TUNNEL,
            server = "ns1.example.com",
            port = 53,
            dnsTransport = "udp",
            dnsResolver = "8.8.8.8:53",
            dnsDomain = "ns1.example.com",
            dnsPubkey = "",
            dnsInnerMode = "ssh",
            group = "DNS / اضطراری"
        ),
        ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = "نمونه VLESS + Fragment + PattN Unsafe",
            protocol = ProtocolType.VLESS,
            server = "104.16.132.229",
            port = 443,
            uuidOrPassword = "00000000-0000-0000-0000-000000000000",
            network = "ws",
            security = "tls",
            sni = "your-worker.workers.dev",
            host = "your-worker.workers.dev",
            path = "/ws",
            fingerprint = "unsafe",
            unsafeTls = true,
            fragmentEnabled = true,
            fragmentPackets = "tlshello",
            fragmentLength = "10-20",
            fragmentInterval = "10-20",
            group = "نمونه"
        )
    )
}
