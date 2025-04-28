package com.formbricks.android.model.environment

import com.formbricks.android.model.BaseFormbricksResponse
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class EnvironmentResponse(
    @SerializedName("data") val data: EnvironmentResponseData,
): BaseFormbricksResponse