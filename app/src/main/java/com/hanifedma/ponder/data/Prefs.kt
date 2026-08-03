package com.hanifedma.ponder.data

import android.content.Context
import com.hanifedma.ponder.i18n.Lang

/**
 * The handful of settings the app remembers between launches — the same ones the
 * web app keeps in localStorage.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("ponder_prefs", Context.MODE_PRIVATE)

    /** Dark by default, like the web app. */
    var darkTheme: Boolean
        get() = sp.getString(KEY_THEME, "dark") != "light"
        set(value) = sp.edit().putString(KEY_THEME, if (value) "dark" else "light").apply()

    var lang: Lang
        get() = Lang.from(sp.getString(KEY_LANG, null))
        set(value) = sp.edit().putString(KEY_LANG, value.code).apply()

    var activeSpaceKey: String
        get() = sp.getString(KEY_SPACE, Spaces.PONDER.key) ?: Spaces.PONDER.key
        set(value) = sp.edit().putString(KEY_SPACE, value).apply()

    /**
     * Set once the user picks "use on this device without an account", so
     * relaunching goes straight back to their entries instead of asking again.
     * Signing in clears it.
     */
    var prefersLocalMode: Boolean
        get() = sp.getBoolean(KEY_LOCAL_MODE, false)
        set(value) = sp.edit().putBoolean(KEY_LOCAL_MODE, value).apply()

    /**
     * Remembers that the device entries were already offered to an account, so
     * the migration prompt is not shown again after the user declines it.
     */
    fun migrationOffered(uid: String): Boolean = sp.getBoolean(KEY_MIGRATED_PREFIX + uid, false)

    fun setMigrationOffered(uid: String) {
        sp.edit().putBoolean(KEY_MIGRATED_PREFIX + uid, true).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LANG = "lang"
        const val KEY_SPACE = "active_space"
        const val KEY_LOCAL_MODE = "prefers_local_mode"
        const val KEY_MIGRATED_PREFIX = "migration_offered_"
    }
}
