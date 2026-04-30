package com.cudgk.retrovhs.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val KEY_TINT = stringPreferencesKey("tint")
    private val KEY_QUALITY = stringPreferencesKey("quality")
    private val KEY_DATE_STAMP = booleanPreferencesKey("date_stamp")
    private val KEY_STAMP_POSITION = stringPreferencesKey("stamp_position")
    private val KEY_STAMP_ROTATION = stringPreferencesKey("stamp_rotation")
    private val KEY_VARIANT = stringPreferencesKey("variant")
    private val KEY_ASPECT = stringPreferencesKey("aspect")
    private val KEY_LANGUAGE = stringPreferencesKey("language")

    const val MAX_INTENSITY = 1.5f

    const val DEFAULT_INTENSITY = 0.6f
    const val DEFAULT_FPS = 30
    const val DEFAULT_LENS = 1 // CameraSelector.LENS_FACING_BACK
    val DEFAULT_ENGINE = Engine.SHADER
    val DEFAULT_TINT = Tint.NONE
    val DEFAULT_QUALITY = Quality.FHD1080
    val DEFAULT_STAMP_POSITION = StampPosition.BOTTOM_RIGHT
    val DEFAULT_STAMP_ROTATION = StampRotation.DEG_0
    val DEFAULT_VARIANT = ShaderVariant.STANDARD
    val DEFAULT_ASPECT = AspectRatio.RATIO_4_3
    val DEFAULT_LANGUAGE = com.cudgk.retrovhs.i18n.Language.JA

    fun intensity(ctx: Context): Flow<Float> = ctx.retrovhsDataStore.data.map { it[KEY_INTENSITY] ?: DEFAULT_INTENSITY }
    fun fps(ctx: Context): Flow<Int> = ctx.retrovhsDataStore.data.map { it[KEY_FPS] ?: DEFAULT_FPS }
    fun lens(ctx: Context): Flow<Int> = ctx.retrovhsDataStore.data.map { it[KEY_LENS] ?: DEFAULT_LENS }
    fun engine(ctx: Context): Flow<Engine> = ctx.retrovhsDataStore.data.map {
        it[KEY_ENGINE]?.let { name -> runCatching { Engine.valueOf(name) }.getOrNull() } ?: DEFAULT_ENGINE
    }
    fun preset(ctx: Context): Flow<String?> = ctx.retrovhsDataStore.data.map { it[KEY_PRESET] }

    fun tint(ctx: Context): Flow<Tint> = ctx.retrovhsDataStore.data.map {
        it[KEY_TINT]?.let { name -> runCatching { Tint.valueOf(name) }.getOrNull() } ?: DEFAULT_TINT
    }

    suspend fun setTint(ctx: Context, value: Tint) {
        ctx.retrovhsDataStore.edit { it[KEY_TINT] = value.name }
    }

    fun quality(ctx: Context): Flow<Quality> = ctx.retrovhsDataStore.data.map {
        it[KEY_QUALITY]?.let { name -> runCatching { Quality.valueOf(name) }.getOrNull() } ?: DEFAULT_QUALITY
    }

    suspend fun setQuality(ctx: Context, value: Quality) {
        ctx.retrovhsDataStore.edit { it[KEY_QUALITY] = value.name }
    }

    fun dateStamp(ctx: Context): Flow<Boolean> = ctx.retrovhsDataStore.data.map { it[KEY_DATE_STAMP] ?: false }

    suspend fun setDateStamp(ctx: Context, value: Boolean) {
        ctx.retrovhsDataStore.edit { it[KEY_DATE_STAMP] = value }
    }

    fun stampPosition(ctx: Context): Flow<StampPosition> = ctx.retrovhsDataStore.data.map {
        it[KEY_STAMP_POSITION]?.let { name -> runCatching { StampPosition.valueOf(name) }.getOrNull() } ?: DEFAULT_STAMP_POSITION
    }

    suspend fun setStampPosition(ctx: Context, value: StampPosition) {
        ctx.retrovhsDataStore.edit { it[KEY_STAMP_POSITION] = value.name }
    }

    fun stampRotation(ctx: Context): Flow<StampRotation> = ctx.retrovhsDataStore.data.map {
        it[KEY_STAMP_ROTATION]?.let { name -> runCatching { StampRotation.valueOf(name) }.getOrNull() } ?: DEFAULT_STAMP_ROTATION
    }

    suspend fun setStampRotation(ctx: Context, value: StampRotation) {
        ctx.retrovhsDataStore.edit { it[KEY_STAMP_ROTATION] = value.name }
    }

    fun variant(ctx: Context): Flow<ShaderVariant> = ctx.retrovhsDataStore.data.map {
        it[KEY_VARIANT]?.let { name -> runCatching { ShaderVariant.valueOf(name) }.getOrNull() } ?: DEFAULT_VARIANT
    }

    suspend fun setVariant(ctx: Context, value: ShaderVariant) {
        ctx.retrovhsDataStore.edit { it[KEY_VARIANT] = value.name }
    }

    fun aspect(ctx: Context): Flow<AspectRatio> = ctx.retrovhsDataStore.data.map {
        it[KEY_ASPECT]?.let { name -> runCatching { AspectRatio.valueOf(name) }.getOrNull() } ?: DEFAULT_ASPECT
    }

    suspend fun setAspect(ctx: Context, value: AspectRatio) {
        ctx.retrovhsDataStore.edit { it[KEY_ASPECT] = value.name }
    }

    fun language(ctx: Context): Flow<com.cudgk.retrovhs.i18n.Language> = ctx.retrovhsDataStore.data.map {
        it[KEY_LANGUAGE]?.let { name -> runCatching { com.cudgk.retrovhs.i18n.Language.valueOf(name) }.getOrNull() } ?: DEFAULT_LANGUAGE
    }

    suspend fun setLanguage(ctx: Context, value: com.cudgk.retrovhs.i18n.Language) {
        ctx.retrovhsDataStore.edit { it[KEY_LANGUAGE] = value.name }
    }

    suspend fun setIntensity(ctx: Context, value: Float) {
        ctx.retrovhsDataStore.edit { it[KEY_INTENSITY] = value.coerceIn(0f, MAX_INTENSITY) }
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
