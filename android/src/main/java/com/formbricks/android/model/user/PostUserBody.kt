package com.formbricks.android.model.user

import com.google.gson.annotations.SerializedName

data class PostUserBody(
    @SerializedName("userId") val userId: String,
    @SerializedName("attributes") val attributes: Map<String, AttributeValue>?
) {
    companion object {
        fun create(userId: String, attributes: Map<String, AttributeValue>?): PostUserBody {
            return PostUserBody(userId, attributes)
        }
    }
}