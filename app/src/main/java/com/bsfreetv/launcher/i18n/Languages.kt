package com.bsfreetv.launcher.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** A language the app ships strings for, named in its own language. */
data class Language(val tag: String, val nativeName: String)

/**
 * Every shipped translation, in picker order. A test checks this against the
 * values-* folders, so a language added on Weblate cannot be missed here.
 */
object SupportedLanguages {
    val all = listOf(
        Language("en", "English"),
        Language("nl", "Nederlands"),
        Language("de", "Deutsch"),
        Language("es", "Español"),
        Language("fr", "Français"),
        Language("it", "Italiano"),
        Language("pt", "Português"),
        Language("pl", "Polski"),
        Language("uk", "Українська"),
        Language("ar", "العربية"),
        Language("ja", "日本語"),
        Language("ko", "한국어"),
        Language("zh-CN", "简体中文"),
    )

    /** Next value for the Settings row: System default (null), then each language. */
    fun next(current: String?): String? {
        val tags = listOf<String?>(null) + all.map { it.tag }
        return tags[(tags.indexOf(current).coerceAtLeast(0) + 1) % tags.size]
    }

    fun nameOf(tag: String?): String? = all.firstOrNull { it.tag == tag }?.nativeName
}

/**
 * The in-app language override. Android 11 has no per-app language setting, so
 * the activity wraps its own context. This is a SharedPreferences value rather
 * than part of the DataStore settings because attachBaseContext needs it
 * synchronously, before anything else runs.
 */
object LanguageStore {
    private const val PREFS = "language"
    private const val KEY = "tag"

    fun get(context: Context): String? = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    }.getOrNull()?.takeIf { tag -> SupportedLanguages.all.any { it.tag == tag } }

    fun set(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (tag == null) remove(KEY) else putString(KEY, tag)
        }.commit()
    }

    /** [base] with the override applied, or [base] itself when following the system. */
    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag == null) {
            // Following the system again after an override: undo the process default too.
            base.resources.configuration.locales[0]?.let(Locale::setDefault)
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }
}
