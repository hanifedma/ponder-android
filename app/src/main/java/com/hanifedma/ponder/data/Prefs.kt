package com.hanifedma.ponder.data

import android.content.Context
import com.hanifedma.ponder.core.SortOrder
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

    /** Whichever space was open last; kept up to date on every switch. */
    var activeSpaceKey: String
        get() = sp.getString(KEY_SPACE, Spaces.PONDER.key) ?: Spaces.PONDER.key
        set(value) = sp.edit().putString(KEY_SPACE, value).apply()

    /**
     * Which space a fresh launch opens: a space key, or [STARTUP_LAST] to carry
     * on from wherever the last session left off (the default).
     */
    var startupSpaceKey: String
        get() = sp.getString(KEY_STARTUP_SPACE, STARTUP_LAST) ?: STARTUP_LAST
        set(value) = sp.edit().putString(KEY_STARTUP_SPACE, value).apply()

    /**
     * The space to open on launch, with [STARTUP_LAST] resolved. An unrecognised
     * key — a space that no longer exists — falls back to Ponder rather than
     * leaving the app with nothing to show.
     */
    val initialSpace: Space
        get() = when (val choice = startupSpaceKey) {
            STARTUP_LAST -> Spaces.byKey(activeSpaceKey)
            else -> Spaces.byKey(choice)
        }

    /** The ordering the list starts in, both on launch and after a space switch. */
    var defaultSort: SortOrder
        get() = SortOrder.from(sp.getString(KEY_DEFAULT_SORT, null))
        set(value) = sp.edit().putString(KEY_DEFAULT_SORT, value.key).apply()

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

    companion object {
        /** [startupSpaceKey] value meaning "carry on from the last session". */
        const val STARTUP_LAST = "last"

        private const val KEY_THEME = "theme"
        private const val KEY_LANG = "lang"
        private const val KEY_SPACE = "active_space"
        private const val KEY_STARTUP_SPACE = "startup_space"
        private const val KEY_DEFAULT_SORT = "default_sort"
        private const val KEY_LOCAL_MODE = "prefers_local_mode"
        private const val KEY_MIGRATED_PREFIX = "migration_offered_"
    }
}
