package ir.pnifox.desktop.storage

import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.SubscriptionGroup
import ir.pnifox.core.model.WarpKey
import ir.pnifox.desktop.model.DesktopPersistedState
import ir.pnifox.desktop.model.WindowsTunnelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class WindowsStore(
    val dataDir: File = defaultDataDir(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val lock = Any()
    private val stateFile = File(dataDir, "pnifox-state.json")

    private val _stateFlow = MutableStateFlow(loadInitial())
    val stateFlow: StateFlow<DesktopPersistedState> = _stateFlow.asStateFlow()

    val aetherDir: File get() = File(dataDir, "aether").apply { mkdirs() }
    val mhrDir: File get() = File(dataDir, "mhr").apply { mkdirs() }
    val geoDir: File get() = File(dataDir, "geo").apply { mkdirs() }
    val runtimeDir: File get() = File(dataDir, "runtime").apply { mkdirs() }

    private fun loadInitial(): DesktopPersistedState {
        dataDir.mkdirs()
        if (!stateFile.exists()) return DesktopPersistedState()
        return runCatching {
            json.decodeFromString<DesktopPersistedState>(stateFile.readText(Charsets.UTF_8))
        }.getOrDefault(DesktopPersistedState())
    }

    fun update(transform: (DesktopPersistedState) -> DesktopPersistedState): DesktopPersistedState {
        synchronized(lock) {
            val updated = transform(_stateFlow.value)
            _stateFlow.value = updated
            runCatching {
                dataDir.mkdirs()
                stateFile.writeText(json.encodeToString(updated), Charsets.UTF_8)
            }
            return updated
        }
    }

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) = update {
        it.copy(appSettings = transform(it.appSettings))
    }

    fun updateAetherSettings(transform: (AetherSettings) -> AetherSettings) = update {
        it.copy(aetherSettings = transform(it.aetherSettings))
    }

    fun updateDnsSettings(transform: (DnsTunnelSettings) -> DnsTunnelSettings) = update {
        it.copy(dnsSettings = transform(it.dnsSettings))
    }

    fun updateMhrSettings(transform: (MhrSettings) -> MhrSettings) = update {
        it.copy(mhrSettings = transform(it.mhrSettings))
    }

    fun setWindowsTunnelMode(mode: WindowsTunnelMode) = update {
        it.copy(windowsTunnelMode = mode)
    }

    fun saveProfiles(newProfiles: List<ProxyProfile>) = update { cur ->
        val map = cur.profiles.associateBy { it.id }.toMutableMap()
        newProfiles.forEach { map[it.id] = it }
        val sorted = map.values.sortedWith(
            compareByDescending<ProxyProfile> { it.isPinned }.thenByDescending { it.updatedAtEpochMs },
        )
        cur.copy(profiles = sorted)
    }

    fun deleteProfile(id: String) = update { cur ->
        val nextApp = if (cur.appSettings.activeProfileId == id) {
            cur.appSettings.copy(activeProfileId = null)
        } else {
            cur.appSettings
        }
        cur.copy(profiles = cur.profiles.filterNot { it.id == id }, appSettings = nextApp)
    }

    fun saveSubscription(sub: SubscriptionGroup) = update { cur ->
        val next = cur.subscriptions.filterNot { it.id == sub.id } + sub
        cur.copy(subscriptions = next)
    }

    fun deleteGroupProfiles(groupId: String) = update { cur ->
        cur.copy(profiles = cur.profiles.filterNot { it.groupId == groupId })
    }

    fun deleteSubscription(id: String) = update { cur ->
        cur.copy(
            subscriptions = cur.subscriptions.filterNot { it.id == id },
            profiles = cur.profiles.filterNot { it.groupId == id },
        )
    }

    fun saveWarpKey(key: WarpKey) = update { cur ->
        val next = cur.warpKeys.filterNot { it.protocolTag == key.protocolTag && it.id == key.id } + key
        cur.copy(warpKeys = next)
    }

    fun recordTrafficDelta(rxDelta: Long, txDelta: Long) {
        if (rxDelta <= 0L && txDelta <= 0L) return
        update { cur ->
            cur.copy(
                totalRxBytes = cur.totalRxBytes + rxDelta.coerceAtLeast(0L),
                totalTxBytes = cur.totalTxBytes + txDelta.coerceAtLeast(0L),
            )
        }
    }

    fun resetDefaults() = update { cur ->
        DesktopPersistedState(
            profiles = cur.profiles,
            subscriptions = cur.subscriptions,
        )
    }

    companion object {
        fun defaultDataDir(): File {
            val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("user.home")
            return File(appData, "pnifox")
        }
    }
}
