package ir.pnifox.desktop.storage

import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsTunnelSettings
import ir.pnifox.core.model.GamingSettings
import ir.pnifox.core.model.MhrSettings
import ir.pnifox.core.model.ProxyProfile
import ir.pnifox.core.model.ScenarioGoalId
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
        if (!stateFile.exists()) return DesktopPersistedState(echDefaultOffMigrated = true)
        val loaded = runCatching {
            json.decodeFromString<DesktopPersistedState>(stateFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: return DesktopPersistedState(echDefaultOffMigrated = true)
        if (!loaded.echDefaultOffMigrated) {
            val migrated = loaded.copy(
                echDefaultOffMigrated = true,
                aetherSettings = loaded.aetherSettings.copy(
                    echEnabled = false,
                    smartEchBootstrap = false,
                    warpApiUseEch = false,
                    echBase64Override = "",
                ),
            )
            runCatching {
                stateFile.writeText(json.encodeToString(migrated), Charsets.UTF_8)
            }
            return migrated
        }
        return loaded
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

    fun updateGamingSettings(transform: (GamingSettings) -> GamingSettings) = update {
        it.copy(gamingSettings = transform(it.gamingSettings))
    }

    fun setSelectedScenario(scenarioId: ScenarioGoalId?) = update {
        it.copy(selectedScenarioId = scenarioId)
    }

    fun updateBanditState(nextJson: String) = update {
        it.copy(banditStateJson = nextJson)
    }

    fun setTunPromptDismissed(dismissed: Boolean) = update {
        it.copy(tunPromptDismissed = dismissed)
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

    private var pendingTrafficBytes = 0L

    fun recordTrafficDelta(rxDelta: Long, txDelta: Long, flushNow: Boolean = false) {
        if (rxDelta <= 0L && txDelta <= 0L && !flushNow) return
        synchronized(lock) {
            val cur = _stateFlow.value
            val updated = cur.copy(
                totalRxBytes = cur.totalRxBytes + rxDelta.coerceAtLeast(0L),
                totalTxBytes = cur.totalTxBytes + txDelta.coerceAtLeast(0L),
            )
            _stateFlow.value = updated
            pendingTrafficBytes += (rxDelta.coerceAtLeast(0L) + txDelta.coerceAtLeast(0L))
            if (flushNow || pendingTrafficBytes >= 65_536L) {
                pendingTrafficBytes = 0L
                runCatching {
                    dataDir.mkdirs()
                    stateFile.writeText(json.encodeToString(updated), Charsets.UTF_8)
                }
            }
        }
    }

    fun flushState() {
        synchronized(lock) {
            pendingTrafficBytes = 0L
            runCatching {
                dataDir.mkdirs()
                stateFile.writeText(json.encodeToString(_stateFlow.value), Charsets.UTF_8)
            }
        }
    }

    fun resetDefaults() = update { cur ->
        DesktopPersistedState(
            echDefaultOffMigrated = true,
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
