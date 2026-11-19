package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.EngineSettings
import com.example.domain.RenderQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.cosmosDataStore by preferencesDataStore(name = "cosmos_ix_prefs")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val QUALITY = stringPreferencesKey("render_quality")
        val EXPOSURE = floatPreferencesKey("exposure")
        val BLOOM = booleanPreferencesKey("hdr_bloom")
        val ORBITS = booleanPreferencesKey("show_orbits")
        val LABELS = booleanPreferencesKey("show_labels")
        val CONSTELLATIONS = booleanPreferencesKey("show_constellations")
        val ASTEROIDS = booleanPreferencesKey("show_asteroids")
        val AURORAS = booleanPreferencesKey("show_auroras")
        val DUST = booleanPreferencesKey("show_dust")
        val CITY_ID = stringPreferencesKey("selected_city")
    }

    val savedSettings: Flow<Pair<EngineSettings, String>> = context.cosmosDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val qualityName = prefs[Keys.QUALITY] ?: RenderQuality.ULTRA_8K.name
            val quality = runCatching { RenderQuality.valueOf(qualityName) }.getOrDefault(RenderQuality.ULTRA_8K)
            val settings = EngineSettings(
                quality = quality,
                exposure = prefs[Keys.EXPOSURE] ?: 1.08f,
                hdrBloom = prefs[Keys.BLOOM] ?: true,
                orbits = prefs[Keys.ORBITS] ?: true,
                labels = prefs[Keys.LABELS] ?: true,
                constellations = prefs[Keys.CONSTELLATIONS] ?: true,
                asteroidBelt = prefs[Keys.ASTEROIDS] ?: true,
                auroras = prefs[Keys.AURORAS] ?: true,
                cosmicDust = prefs[Keys.DUST] ?: true
            )
            val cityId = prefs[Keys.CITY_ID] ?: "beirut"
            settings to cityId
        }

    suspend fun saveSettings(settings: EngineSettings, cityId: String) {
        context.cosmosDataStore.edit { prefs ->
            prefs[Keys.QUALITY] = settings.quality.name
            prefs[Keys.EXPOSURE] = settings.exposure
            prefs[Keys.BLOOM] = settings.hdrBloom
            prefs[Keys.ORBITS] = settings.orbits
            prefs[Keys.LABELS] = settings.labels
            prefs[Keys.CONSTELLATIONS] = settings.constellations
            prefs[Keys.ASTEROIDS] = settings.asteroidBelt
            prefs[Keys.AURORAS] = settings.auroras
            prefs[Keys.DUST] = settings.cosmicDust
            prefs[Keys.CITY_ID] = cityId
        }
    }
}
