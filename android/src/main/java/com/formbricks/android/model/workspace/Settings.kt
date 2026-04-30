package com.formbricks.android.model.workspace

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    @SerializedName("id") val id: String?,
    @SerializedName("recontactDays") val recontactDays: Double?,
    @SerializedName("clickOutsideClose") val clickOutsideClose: Boolean?,
    @SerializedName("overlay") val overlay: SurveyOverlay?,
    @SerializedName("placement") val placement: String?,
    @SerializedName("inAppSurveyBranding") val inAppSurveyBranding: Boolean?,
    @SerializedName("styling") val styling: Styling?
)
