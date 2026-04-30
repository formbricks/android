package com.formbricks.android.model.workspace

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceResponseData(
    @SerializedName("data") val data: WorkspaceData,
    @SerializedName("expiresAt") val expiresAt: String?
)
