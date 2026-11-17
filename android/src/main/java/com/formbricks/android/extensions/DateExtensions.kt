package com.formbricks.android.extensions

import com.formbricks.android.model.user.UserState
import com.formbricks.android.model.user.UserStateData
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

/**
 * Formats as ISO 8601 UTC for the wire — a machine-facing value, never shown to a user.
 *
 * `Locale.ROOT`, not `Locale.getDefault()`: `SimpleDateFormat` renders digits in the locale's own
 * numbering system, so on a device set to `fa`, `fa-IR`, `ar-EG`, `hi-IN-u-nu-deva` or `ne-NP` the
 * "ISO 8601" string comes out in non-ASCII digits. Nothing downstream accepts those — an Embedded
 * Data `date` field would be flagged `coercion_failed` and stored raw, for those users only.
 *
 * Only the formatting direction needs this. The parsers in this file keep `Locale.getDefault()`
 * because `DecimalFormat` falls back to `Character.digit` and reads the server's ASCII digits under
 * any locale.
 */
public fun Date.dateString(): String {
    val dateFormat = SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.ROOT)
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    return dateFormat.format(this)
}

public fun UserStateData.lastDisplayAt(): Date? = lastDisplayAt?.let(::parseUtcDate)

public fun UserState.expiresAt(): Date? = expiresAt?.let(::parseUtcDate)

public fun WorkspaceDataHolder.expiresAt(): Date? = data?.expiresAt?.let(::parseUtcDate)

/** Parses a server timestamp in [DATE_FORMAT_PATTERN]; null when it is not in that format. */
private fun parseUtcDate(value: String): Date? = try {
    val formatter = SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    formatter.parse(value)
} catch (e: ParseException) {
    null
}
