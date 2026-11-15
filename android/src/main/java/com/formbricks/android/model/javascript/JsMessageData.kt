package com.formbricks.android.model.javascript

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

public data class JsMessageData(@SerializedName("event") val event: EventType) {
    public companion object {
        public fun from(string: String): JsMessageData = try {
            Gson().fromJson(string, JsMessageData::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON format: ${e.message}", e)
        }
    }
}
