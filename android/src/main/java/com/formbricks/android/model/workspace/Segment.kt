package com.formbricks.android.model.workspace

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import kotlinx.serialization.Serializable
import java.lang.reflect.Type

/**
 * Public client API returns the minimal `{ id, hasFilters }` shape — full
 * filter logic (titles, descriptions, conditions) is evaluated server-side
 * and must not reach the device.
 *
 * The custom deserializer also accepts legacy cached payloads that still
 * carry a `filters` array (written by older SDK versions before the API was
 * slimmed down). In that case `hasFilters` is derived from the array length
 * so anonymous users continue to be excluded from segment-targeted surveys
 * during the cache window after an SDK upgrade.
 */
@Serializable
data class Segment(
    val id: String,
    val hasFilters: Boolean
)

class SegmentDeserializer : JsonDeserializer<Segment> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Segment {
        val obj = json.asJsonObject
        val id = obj.get("id").asString
        // Fail-closed: ambiguous payload (neither `hasFilters` nor `filters` present)
        // is treated as "has filters" so anonymous users are excluded from segment-
        // targeted surveys rather than over-shown.
        val hasFilters = when {
            obj.has("hasFilters") && !obj.get("hasFilters").isJsonNull ->
                obj.get("hasFilters").asBoolean
            obj.has("filters") && obj.get("filters").isJsonArray ->
                obj.get("filters").asJsonArray.size() > 0
            else -> true
        }
        return Segment(id = id, hasFilters = hasFilters)
    }
}
