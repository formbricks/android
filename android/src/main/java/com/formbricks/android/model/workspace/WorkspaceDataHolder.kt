package com.formbricks.android.model.workspace

import com.google.gson.Gson
import com.google.gson.JsonElement

public data class WorkspaceDataHolder(
    val data: WorkspaceResponseData?,
    val originalResponseMap: Map<String, Any>
)

@Suppress("UNCHECKED_CAST")
public fun WorkspaceDataHolder.getSurveyJson(surveyId: String): JsonElement? {
    val responseMap = originalResponseMap["data"] as? Map<*, *>
    val dataMap = responseMap?.get("data") as? Map<*, *>
    val surveyArray = dataMap?.get("surveys") as? ArrayList<Map<String, Any?>>
    val firstSurvey = surveyArray?.firstOrNull { it["id"] == surveyId }
    firstSurvey?.let {
        return Gson().toJsonTree(it)
    }

    return null
}

@Suppress("UNCHECKED_CAST")
public fun WorkspaceDataHolder.getStyling(surveyId: String): JsonElement? {
    val responseMap = originalResponseMap["data"] as? Map<*, *>
    val dataMap = responseMap?.get("data") as? Map<*, *>
    val surveyArray = dataMap?.get("surveys") as? ArrayList<Map<String, Any?>>
    val firstSurvey = surveyArray?.firstOrNull { it["id"] == surveyId }
    firstSurvey?.get("styling")?.let {
        return Gson().toJsonTree(it)
    }

    return null
}

@Suppress("UNCHECKED_CAST")
public fun WorkspaceDataHolder.getSettingsStylingJson(): JsonElement? {
    val responseMap = originalResponseMap["data"] as? Map<*, *>
    val dataMap = responseMap?.get("data") as? Map<*, *>
    // Server may respond with `settings`, `workspace`, or legacy `project` — all carry the same shape.
    val settingsMap = (dataMap?.get("settings") as? Map<*, *>)
        ?: (dataMap?.get("workspace") as? Map<*, *>)
        ?: (dataMap?.get("project") as? Map<*, *>)
    val stylingMap = settingsMap?.get("styling") as? Map<String, Any?>
    stylingMap?.let {
        return Gson().toJsonTree(it)
    }

    return null
}
