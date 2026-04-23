package com.formbricks.android.model.workspace

import com.formbricks.android.model.BaseFormbricksResponse
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceResponse(
    @SerializedName("data") val data: WorkspaceResponseData,
): BaseFormbricksResponse
