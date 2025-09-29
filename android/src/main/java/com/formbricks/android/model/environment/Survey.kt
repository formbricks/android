package com.formbricks.android.model.environment

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class SurveyLanguage(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("default") val default: Boolean,
    @SerializedName("language") val language: LanguageDetail
)

@Serializable
data class LanguageDetail(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String,
    @SerializedName("alias") val alias: String?,
    @SerializedName("projectId") val projectId: String
)

@Serializable
data class Survey(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("triggers") val triggers: List<Trigger>?,
    @SerializedName("recontactDays") val recontactDays: Double?,
    @SerializedName("displayLimit") val displayLimit: Double?,
    @SerializedName("delay") val delay: Double?,
    @SerializedName("displayPercentage") val displayPercentage: Double?,
    @SerializedName("displayOption") val displayOption: String?,
    @SerializedName("segment") val segment: Segment?,
    @SerializedName("styling") val styling: Styling?,
    @SerializedName("languages") val languages: List<SurveyLanguage>?,
    @SerializedName("projectOverwrites") val projectOverwrites: SurveyProjectOverwrites?
)

@Serializable
data class SurveyProjectOverwrites(
    @SerializedName("brandColor") val brandColor: String? = null,
    @SerializedName("highlightBorderColor") val highlightBorderColor: String? = null,
    @SerializedName("clickOutsideClose") val clickOutsideClose: Boolean? = null,
    @SerializedName("placement") val placement: String? = null,
    @SerializedName("darkOverlay") val darkOverlay: Boolean? = null
)