package tv.music.player.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_music_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val accentMode = stringPreferencesKey("accent_mode")
        val animations = booleanPreferencesKey("animations")
        val blur = booleanPreferencesKey("blur_background")
        val lyricSize = stringPreferencesKey("lyric_size")
        val lyricStyle = stringPreferencesKey("lyric_style")
        val lyricTranslation = booleanPreferencesKey("lyric_translation")
        val lyricOffset = intPreferencesKey("lyric_offset_ms")
        val playerLayout = stringPreferencesKey("player_layout")
        val quality = stringPreferencesKey("quality")
        val gapless = booleanPreferencesKey("gapless")
        val fade = booleanPreferencesKey("fade_in_out")
        val companion = booleanPreferencesKey("companion_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                accentMode = prefs[Keys.accentMode].toEnumOr(defaults.accentMode),
                animationsEnabled = prefs[Keys.animations] ?: defaults.animationsEnabled,
                blurBackground = prefs[Keys.blur] ?: defaults.blurBackground,
                lyricSize = prefs[Keys.lyricSize].toEnumOr(defaults.lyricSize),
                lyricStyle = prefs[Keys.lyricStyle].toEnumOr(defaults.lyricStyle),
                showLyricTranslation = prefs[Keys.lyricTranslation] ?: defaults.showLyricTranslation,
                lyricOffsetMs = (prefs[Keys.lyricOffset] ?: defaults.lyricOffsetMs)
                    .coerceIn(-5_000, 5_000),
                playerLayout = prefs[Keys.playerLayout].toEnumOr(defaults.playerLayout),
                quality = prefs[Keys.quality].toEnumOr(defaults.quality),
                gaplessPlayback = prefs[Keys.gapless] ?: defaults.gaplessPlayback,
                fadeInOut = prefs[Keys.fade] ?: defaults.fadeInOut,
                companionEnabled = prefs[Keys.companion] ?: defaults.companionEnabled,
            )
        }

    suspend fun setAccentMode(mode: AccentMode) = put(Keys.accentMode, mode.name)
    suspend fun setAnimationsEnabled(enabled: Boolean) = put(Keys.animations, enabled)
    suspend fun setBlurBackground(enabled: Boolean) = put(Keys.blur, enabled)
    suspend fun setLyricSize(size: LyricSize) = put(Keys.lyricSize, size.name)
    suspend fun setLyricStyle(style: LyricStyle) = put(Keys.lyricStyle, style.name)
    suspend fun setShowLyricTranslation(enabled: Boolean) = put(Keys.lyricTranslation, enabled)
    suspend fun setLyricOffset(offsetMs: Int) = put(Keys.lyricOffset, offsetMs.coerceIn(-5_000, 5_000))
    suspend fun setPlayerLayout(layout: PlayerLayout) = put(Keys.playerLayout, layout.name)
    suspend fun setQuality(quality: AudioQuality) = put(Keys.quality, quality.name)
    suspend fun setGapless(enabled: Boolean) = put(Keys.gapless, enabled)
    suspend fun setFadeInOut(enabled: Boolean) = put(Keys.fade, enabled)
    suspend fun setCompanionEnabled(enabled: Boolean) = put(Keys.companion, enabled)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
