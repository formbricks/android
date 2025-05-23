package com.formbricks.android.model.environment

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class ActionClassReference(
    @SerializedName("name") val name: String?
)