package com.formbricks.android.helper

import androidx.annotation.Keep

/**
 * How surveys render. [SYSTEM] follows the host app's own theme (its night mode, including
 * `AppCompatDelegate.setDefaultNightMode`), not the phone's.
 */
@Keep
enum class FormbricksAppearance(internal val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    internal companion object {
        fun from(value: String?): FormbricksAppearance? =
            entries.firstOrNull { it.value == value?.trim()?.lowercase() }
    }
}
