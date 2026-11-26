package ir.pnifox.core.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import ir.pnifox.core.model.AetherSettings
import ir.pnifox.core.model.AppSettings
import ir.pnifox.core.model.DnsTunnelSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private companion object {
        val KEY_APP_SETTINGS = stringPreferencesKey("app_settings_json")
        val KEY_AETHER_SETTINGS = stringPreferencesKey("aether_settings_json")
        val KEY_DNS_SETTINGS = stringPreferencesKey("dns_tunnel_settings_json")
        val KEY_MHR_SETTINGS = stringPreferencesKey("mhr_settings_json")
    }

    val appSettingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        prefs[KEY_APP_SETTINGS]?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
        } ?: AppSettings()
    }

    val aetherSettingsFlow: Flow<AetherSettings> = dataStore.data.map { prefs ->
        prefs[KEY_AETHER_SETTINGS]?.let { raw ->
            runCatching { json.decodeFromString<AetherSettings>(raw) }.getOrNull()
        } ?: AetherSettings()
    }

    val dnsSettingsFlow: Flow<DnsTunnelSettings> = dataStore.data.map { prefs ->
        prefs[KEY_DNS_SETTINGS]?.let { raw ->
            runCatching { json.decodeFromString<DnsTunnelSettings>(raw) }.getOrNull()
        } ?: DnsTunnelSettings()
    }

    val mhrSettingsFlow: Flow<ir.pnifox.core.model.MhrSettings> = dataStore.data.map { prefs ->
        prefs[KEY_MHR_SETTINGS]?.let { raw ->
            runCatching { json.decodeFromString<ir.pnifox.core.model.MhrSettings>(raw) }.getOrNull()
        } ?: ir.pnifox.core.model.MhrSettings()
    }

    suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_APP_SETTINGS]?.let { raw ->
                runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
            } ?: AppSettings()
            prefs[KEY_APP_SETTINGS] = json.encodeToString(transform(current))
        }
    }

    suspend fun updateAetherSettings(transform: (AetherSettings) -> AetherSettings) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_AETHER_SETTINGS]?.let { raw ->
                runCatching { json.decodeFromString<AetherSettings>(raw) }.getOrNull()
            } ?: AetherSettings()
            prefs[KEY_AETHER_SETTINGS] = json.encodeToString(transform(current))
        }
    }

    suspend fun updateDnsSettings(transform: (DnsTunnelSettings) -> DnsTunnelSettings) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_DNS_SETTINGS]?.let { raw ->
                runCatching { json.decodeFromString<DnsTunnelSettings>(raw) }.getOrNull()
            } ?: DnsTunnelSettings()
            prefs[KEY_DNS_SETTINGS] = json.encodeToString(transform(current))
        }
    }

    suspend fun updateMhrSettings(transform: (ir.pnifox.core.model.MhrSettings) -> ir.pnifox.core.model.MhrSettings) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_MHR_SETTINGS]?.let { raw ->
                runCatching { json.decodeFromString<ir.pnifox.core.model.MhrSettings>(raw) }.getOrNull()
            } ?: ir.pnifox.core.model.MhrSettings()
            prefs[KEY_MHR_SETTINGS] = json.encodeToString(transform(current))
        }
    }

    suspend fun resetAllDefaults() {
        dataStore.edit { prefs ->
            prefs[KEY_APP_SETTINGS] = json.encodeToString(AppSettings())
            prefs[KEY_AETHER_SETTINGS] = json.encodeToString(AetherSettings())
            prefs[KEY_DNS_SETTINGS] = json.encodeToString(DnsTunnelSettings())
            prefs[KEY_MHR_SETTINGS] = json.encodeToString(ir.pnifox.core.model.MhrSettings())
        }
    }
}
