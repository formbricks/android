package com.formbricks.android.model.workspace

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/** Compiled custom CSS for one scope; either mode may be absent. Forwarded to the renderer untouched. */
@Serializable
data class CustomCss(
    @SerializedName("light") val light: String? = null,
    @SerializedName("dark") val dark: String? = null
) {
    /** The scope's non-empty strings, or null when there is nothing to send. */
    internal fun toJson(): JsonObject? {
        val json = JsonObject()
        if (!light.isNullOrEmpty()) json.addProperty("light", light)
        if (!dark.isNullOrEmpty()) json.addProperty("dark", dark)
        return if (json.size() == 0) null else json
    }

    internal companion object {
        /**
         * The renderer's `customCss` prop, or null (no key at all) when neither scope has CSS. Empty
         * fields are omitted rather than sent as null: the renderer rejects the whole prop on a
         * null inside a scope.
         */
        fun props(workspace: CustomCss?, survey: CustomCss?): JsonObject? {
            val json = JsonObject()
            workspace?.toJson()?.let { json.add("workspace", it) }
            survey?.toJson()?.let { json.add("survey", it) }
            return if (json.size() == 0) null else json
        }
    }
}
