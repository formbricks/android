package com.formbricks.android.helper

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.formbricks.android.logger.Logger
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The in-memory appearance state (ENG-3452). Never persisted, never sent to the server, and left
 * alone by `logout()`, so a fresh app launch starts light (ENG-3551).
 */
internal object Appearance {
    @Volatile
    var current: FormbricksAppearance = FormbricksAppearance.LIGHT
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    /** Falls back to light, and logs, for a value that is not a known appearance. */
    fun set(value: String?) {
        val parsed = FormbricksAppearance.from(value)
        if (parsed == null) {
            Logger.e(RuntimeException("Unknown appearance \"$value\", falling back to light"))
        }
        set(parsed ?: FormbricksAppearance.LIGHT)
    }

    fun set(appearance: FormbricksAppearance) {
        current = appearance
        listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /**
     * What the renderer understands: always "light" or "dark", never "system". [context] should be
     * the Activity the survey is shown in. An explicit AppCompat night mode (the activity's local
     * one, else the default) wins over the configuration: creating the first WebView in a process
     * resets the Activity's configuration to the phone's, losing the app's own night mode.
     */
    fun resolve(context: Context, appearance: FormbricksAppearance = current): String =
        resolve(appCompatUiMode(context) ?: context.resources.configuration.uiMode, appearance)

    /** AppCompat's forced night mode as a `uiMode` night flag, or null when it follows the system. */
    private fun appCompatUiMode(context: Context): Int? {
        val local = (context as? AppCompatActivity)?.delegate?.localNightMode
            ?: AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
        val mode = if (local != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) {
            local
        } else {
            AppCompatDelegate.getDefaultNightMode()
        }
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
            else -> null
        }
    }

    fun resolve(uiMode: Int, appearance: FormbricksAppearance = current): String = when (appearance) {
        FormbricksAppearance.LIGHT -> "light"
        FormbricksAppearance.DARK -> "dark"
        FormbricksAppearance.SYSTEM ->
            if (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
    }

    /**
     * JavaScript that flips an open survey in place. Optional chaining: a server whose renderer
     * predates `setAppearance` leaves the survey light instead of throwing.
     */
    fun switchScript(resolved: String): String =
        "window.formbricksSurveys?.setAppearance?.('$resolved');"

    /** Test-only: back to the cold-start state. */
    fun reset() {
        current = FormbricksAppearance.LIGHT
        listeners.clear()
    }
}
