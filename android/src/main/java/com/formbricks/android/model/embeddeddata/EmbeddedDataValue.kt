package com.formbricks.android.model.embeddeddata

import java.util.Date

/**
 * A value a host app may attach to future responses with
 * [com.formbricks.android.Formbricks.setEmbeddedData].
 *
 * Confined to the four scalars the Embedded Data ingest contract can store. A sealed class rather
 * than `Any` on purpose: the bag is serialized into the survey WebView's payload, so an
 * unrepresentable value would not be a dropped field but a malformed payload that takes the whole
 * survey down with it.
 *
 * ```kotlin
 * Formbricks.setEmbeddedData(mapOf(
 *     "plan" to EmbeddedDataValue.string("pro"),
 *     "seats" to EmbeddedDataValue.number(25.0),
 *     "isTrial" to EmbeddedDataValue.boolean(false),
 *     "screen" to null,   // removes the key
 * ))
 * ```
 *
 * Dates serialize as ISO 8601, which is what the ingest contract accepts for a `date` field.
 */
public sealed class EmbeddedDataValue {
    public data class StringValue(val value: String) : EmbeddedDataValue()
    public data class NumberValue(val value: Double) : EmbeddedDataValue()
    public data class BooleanValue(val value: Boolean) : EmbeddedDataValue()
    public data class DateValue(val value: Date) : EmbeddedDataValue()

    public companion object {
        public fun string(value: String): EmbeddedDataValue = StringValue(value)
        public fun number(value: Double): EmbeddedDataValue = NumberValue(value)
        public fun boolean(value: Boolean): EmbeddedDataValue = BooleanValue(value)
        public fun date(value: Date): EmbeddedDataValue = DateValue(value)
    }
}
