package com.formbricks.android.model.workspace

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class Trigger(
    @SerializedName("actionClass") val actionClass: ActionClassReference?
)
