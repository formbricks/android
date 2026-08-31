package com.formbricks.android.manager

import com.formbricks.android.extensions.dateString
import com.formbricks.android.logger.Logger
import com.formbricks.android.model.embeddeddata.EmbeddedDataValue
import com.google.gson.JsonObject

/**
 * The in-memory Embedded Data bag: context a host app attaches to future responses without tying it
 * to a trigger — `Formbricks.setEmbeddedData(mapOf("screen" to ...))` once, instead of repeating the
 * same values on every possible `track(...)` call.
 *
 * Mirrors the JS SDK's store key for key, so web and mobile behave identically.
 *
 * Lifetime rules, all deliberate:
 *
 * - **In-memory, process scoped, never persisted.** Not `SharedPreferences`: persisting this bag
 *   would blur the Embedded Data ↔ contact-attribute boundary and create a stale-data / PII-at-rest
 *   surface. A cold app start begins empty; the host re-pushes.
 * - **Snapshot at display, then frozen.** [FormbricksViewModel][com.formbricks.android.webview.FormbricksViewModel]
 *   copies the bag into the WebView payload when the survey is shown, so a later `setEmbeddedData`
 *   affects the next response, never the one on screen.
 * - **No filtering here.** The SDK is a dumb pipe: the survey renderer applies the ingest contract —
 *   allow-list, coercion, `locked`, size caps — and logs what it refuses, and the server re-runs all
 *   of it on ingest. Filtering here would ship a second copy of those rules for the four mobile SDKs
 *   to drift from.
 * - **Independent of `setup`.** A host legitimately pushes context before the SDK finishes
 *   initializing, and silently dropping that write is the failure this API exists to avoid.
 * - **No network.** Every method is a synchronous memory write, so calling it on every screen change
 *   is free. Values ride the existing response payload.
 */
object EmbeddedDataManager {
    private val lock = Any()
    private val data = LinkedHashMap<String, EmbeddedDataValue>()

    /**
     * Merge — never replace — so refreshing a volatile field (`screen`) cannot wipe the stable ones
     * (`plan`) set at launch. Per key: last write wins, and an explicit `null` removes the key.
     *
     * A key the caller simply leaves out is untouched; that is how a host skips a field it has no
     * value for this screen. `null` is the deliberate "remove this" spelling, matching the JS SDK's
     * `{ key: null }`.
     */
    fun set(values: Map<String, EmbeddedDataValue?>) {
        val setKeys = mutableListOf<String>()
        val removedKeys = mutableListOf<String>()
        val held: List<String>
        synchronized(lock) {
            for ((key, value) in values) {
                if (value == null) {
                    data.remove(key)
                    removedKeys.add(key)
                    continue
                }
                // Refused rather than stored: a non-finite Double serializes as bare `NaN` or
                // `Infinity`, which is not valid JSON, so `JSON.parse` in the WebView would throw
                // and the survey would never render. One bad value must cost the field, not the
                // survey. Never fatal, always logged.
                if (value is EmbeddedDataValue.NumberValue && !value.value.isFinite()) {
                    Logger.w("setEmbeddedData: \"$key\" is not a finite number - the key was skipped")
                    continue
                }
                data[key] = value
                setKeys.add(key)
            }
            held = data.keys.toList()
        }
        // Built and logged outside the lock, so a log write never holds it.
        Logger.d(setTrace(setKeys, removedKeys, held))
    }

    /**
     * The success trace, because the bag is otherwise invisible: it lives in memory (nothing in
     * `SharedPreferences` to inspect) and the API has no getter, so without this line a host wiring
     * up `setEmbeddedData` gets no confirmation until a survey happens to display. Logged at debug,
     * which [Logger] gates on `Formbricks.loggingEnabled`.
     *
     * Keys only, never values: the documented use of this bag includes hashed identity fields.
     * Separated from the logging call so that property is directly assertable in a test.
     */
    internal fun setTrace(setKeys: List<String>, removedKeys: List<String>, held: List<String>): String {
        val removed = if (removedKeys.isEmpty()) "" else ", removed [${removedKeys.joinToString(", ")}]"
        return "setEmbeddedData: set [${setKeys.joinToString(", ")}]$removed - the bag now holds " +
            "[${held.joinToString(", ")}]. Keys land on a response only if the survey declares them " +
            "as ingested Embedded Data fields."
    }

    /** Removes one key. A key that is not set is a no-op. */
    fun remove(key: String) {
        val held: List<String>
        synchronized(lock) {
            data.remove(key)
            held = data.keys.toList()
        }
        Logger.d("clearEmbeddedData: removed \"$key\" - the bag now holds [${held.joinToString(", ")}]")
    }

    /** Removes everything - logout, or a hard context switch. */
    fun clear() {
        val clearedCount: Int
        synchronized(lock) {
            clearedCount = data.size
            data.clear()
        }
        Logger.d("clearEmbeddedData: cleared the whole bag ($clearedCount keys)")
    }

    /**
     * A detached, JSON-safe copy for the display-time snapshot: mutating the bag after a survey has
     * rendered must not reach that survey's response.
     */
    fun snapshot(): JsonObject {
        val json = JsonObject()
        synchronized(lock) {
            for ((key, value) in data) {
                when (value) {
                    is EmbeddedDataValue.StringValue -> json.addProperty(key, value.value)
                    is EmbeddedDataValue.NumberValue -> json.addProperty(key, value.value)
                    is EmbeddedDataValue.BooleanValue -> json.addProperty(key, value.value)
                    // ISO 8601 is what the renderer's ingest contract accepts for a `date` field.
                    is EmbeddedDataValue.DateValue -> json.addProperty(key, value.value.dateString())
                }
            }
        }
        return json
    }
}
