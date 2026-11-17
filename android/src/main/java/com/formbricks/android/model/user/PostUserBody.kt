package com.formbricks.android.model.user

import com.google.gson.annotations.SerializedName

public data class PostUserBody(
    @SerializedName("userId") val userId: String,
    @SerializedName("attributes") val attributes: Map<String, AttributeValue>?
) {
    public companion object {
        public fun create(userId: String, attributes: Map<String, AttributeValue>?): PostUserBody =
            PostUserBody(userId, attributes)
    }
}
