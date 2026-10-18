package com.bsfreetv.launcher.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bsfreetv.launcher.domain.LauncherSettings
import com.bsfreetv.launcher.domain.layout.LauncherLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.KSerializer

private val Context.launcherStore by preferencesDataStore(name = "launcher")

/**
 * Layout and settings as two JSON documents in one DataStore. A document that
 * fails to decode falls back to defaults instead of crashing: this app may be
 * the only Home on the device, so it must always start.
 */
@Singleton
class LauncherStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val layoutKey = stringPreferencesKey("layout")
    private val settingsKey = stringPreferencesKey("settings")

    val layout: Flow<LauncherLayout> = document(layoutKey, LauncherLayout.serializer(), LauncherLayout())
    val settings: Flow<LauncherSettings> = document(settingsKey, LauncherSettings.serializer(), LauncherSettings())

    suspend fun updateLayout(transform: (LauncherLayout) -> LauncherLayout) =
        update(layoutKey, LauncherLayout.serializer(), LauncherLayout(), transform)

    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) =
        update(settingsKey, LauncherSettings.serializer(), LauncherSettings(), transform)

    private fun <T> decode(raw: String?, serializer: KSerializer<T>, default: T): T =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.onFailure { e -> Log.w(TAG, "bad stored document", e) }.getOrNull() } ?: default

    private fun <T> document(key: androidx.datastore.preferences.core.Preferences.Key<String>, serializer: KSerializer<T>, default: T): Flow<T> =
        context.launcherStore.data
            .catch { e -> Log.w(TAG, "datastore read failed", e); emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map { decode(it[key], serializer, default) }
            .distinctUntilChanged()

    private suspend fun <T> update(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        serializer: KSerializer<T>,
        default: T,
        transform: (T) -> T,
    ) {
        context.launcherStore.edit { prefs ->
            val current = decode(prefs[key], serializer, default)
            val next = transform(current)
            if (next != current) prefs[key] = json.encodeToString(serializer, next)
        }
    }

    private companion object {
        const val TAG = "LauncherStore"
    }
}
