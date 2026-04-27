package com.cudgk.retrovhs.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.retrovhsDataStore: DataStore<Preferences> by preferencesDataStore(name = "retrovhs")

enum class Engine { SHADER, RUST }

object Settings {
    private val KEY_INTENSITY = floatPreferencesKey("intensity")
    private val KEY_FPS = intPreferencesKey("fps")
    private val KEY_LENS = intPreferencesKey("lens")
    private val KEY_ENGINE = stringPreferencesKey("engine")
    private val KEY_PRESET = stringPreferencesKey("preset")

    const val DEFAULT_INTENSITY = 0.7f
    const val DEFAULT_FPS = 30
    const val DEFAULT_LENS = 1 // CameraSelector.LENS_FACING_BACK
    val DEFAULT_ENGINE = Engine.SHADER

    fun intensity(ctx: Context): Flow<Float> = ctx.retrovhsDataStore.data.map { it[KEY_INTENSITY] ?: DEFAULT_INTENSITY }
    fun fps(ctx: Context): Flow<Int> = ctx.retrovhsDataStore.data.map { it[KEY_FPS] ?: DEFAULT_FPS }
    fun lens(ctx: Context): Flow<Int> = ctx.retrovhsDataStore.data.map { it[KEY_LENS] ?: DEFAULT_LENS }
    fun engine(ctx: Context): Flow<Engine> = ctx.retrovhsDataStore.data.map {
        it[KEY_ENGINE]?.let { name -> runCatching { Engine.valueOf(name) }.getOrNull() } ?: DEFAULT_ENGINE
    }
    fun preset(ctx: Context): Flow<String?> = ctx.retrovhsDataStore.data.map { it[KEY_PRESET] }

    suspend fun setIntensity(ctx: Context, value: Float) {
        ctx.retrovhsDataStore.edit { it[KEY_INTENSITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setFps(ctx: Context, value: Int) {
        ctx.retrovhsDataStore.edit { it[KEY_FPS] = value }
    }

    suspend fun setLens(ctx: Context, value: Int) {
        ctx.retrovhsDataStore.edit { it[KEY_LENS] = value }
    }

    suspend fun setEngine(ctx: Context, value: Engine) {
        ctx.retrovhsDataStore.edit { it[KEY_ENGINE] = value.name }
    }

    suspend fun setPreset(ctx: Context, name: String?) {
        ctx.retrovhsDataStore.edit {
            if (name == null) it.remove(KEY_PRESET) else it[KEY_PRESET] = name
        }
    }

    suspend fun applyPreset(ctx: Context, preset: Preset) {
        ctx.retrovhsDataStore.edit {
            it[KEY_INTENSITY] = preset.intensity
            it[KEY_ENGINE] = preset.engine.name
            it[KEY_PRESET] = preset.name
        }
    }
}
