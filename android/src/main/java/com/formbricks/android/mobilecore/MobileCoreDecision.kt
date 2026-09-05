package com.formbricks.android.mobilecore

import androidx.annotation.Keep
import com.formbricks.android.model.user.Display
import com.google.gson.annotations.SerializedName

/**
 * The decision returned by the remote mobile core (the server-delivered JS "brain")
 * when asked whether a tracked action should display a survey.
 */
@Keep
data class MobileCoreDecision(
    /** Protocol version of the decision payload. Lets old shells reject decisions
     *  produced by a newer, incompatible brain instead of misinterpreting them. */
    @SerializedName("v") val v: Int?,
    @SerializedName("shouldDisplay") val shouldDisplay: Boolean?,
    @SerializedName("surveyId") val surveyId: String?,
    @SerializedName("delaySeconds") val delaySeconds: Double?,
    /** The resolved survey language code (e.g. "default" or "de"), already validated
     *  against the survey's enabled languages by the brain. */
    @SerializedName("languageCode") val languageCode: String?,
    /** Human-readable explanation of the decision, used for logging only. */
    @SerializedName("reason") val reason: String?
)

/**
 * The user-state snapshot the shell hands to the brain alongside the workspace state.
 * Mirrors what [com.formbricks.android.manager.UserManager] persists; the brain owns
 * all interpretation of it.
 */
@Keep
data class MobileCoreUserState(
    @SerializedName("userId") val userId: String?,
    @SerializedName("segments") val segments: List<String>?,
    @SerializedName("displays") val displays: List<Display>?,
    @SerializedName("responses") val responses: List<String>?,
    /** Milliseconds since epoch; JS-friendly representation of `lastDisplayedAt`. */
    @SerializedName("lastDisplayedAtMs") val lastDisplayedAtMs: Long?
)
