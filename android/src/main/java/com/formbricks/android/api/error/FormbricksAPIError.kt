package com.formbricks.android.api.error

import com.google.gson.annotations.SerializedName

public data class FormbricksAPIError(
    @SerializedName("code") val code: String,
    @SerializedName("message") val messageText: String,
    @SerializedName("details") val details: Map<String, String>? = null
) : RuntimeException(messageText)
