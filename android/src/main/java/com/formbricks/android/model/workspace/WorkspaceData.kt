package com.formbricks.android.model.workspace

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WorkspaceData(
    @SerializedName("surveys") val surveys: List<Survey>?,
    @SerializedName("actionClasses") val actionClasses: List<ActionClass>?,
    @SerializedName(value = "settings", alternate = ["workspace", "project"])
    @SerialName("settings")
    @JsonNames("workspace", "project")
    val settings: Settings
)
