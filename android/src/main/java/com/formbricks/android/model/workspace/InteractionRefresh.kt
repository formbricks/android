package com.formbricks.android.model.workspace

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

/**
 * The three survey-lifecycle moments that can flip interaction-based segment membership.
 * The names match the source names used by the JS SDK so both platforms key the gate off
 * the same vocabulary.
 */
enum class InteractionSource(val value: String) {
    ON_DISPLAY("onDisplay"),
    ON_RESPONSE("onResponse"),
    ON_FINISHED("onFinished")
}

/**
 * Per-survey gate for the post-interaction segment refresh.
 *
 * Each flag says whether interacting with *this* survey via that event can change some live
 * survey's segment membership — e.g. a survey referenced only by a "have seen" filter
 * refreshes on display but not on response or finish, and a survey no interaction filter
 * points at never refreshes at all.
 *
 * The client API attaches this only for workspaces that use survey-interaction targeting, so
 * it is absent for everyone else, and present-but-all-false for surveys in such a workspace
 * that no interaction filter references.
 *
 * The flags are nullable on purpose: Gson does not run Kotlin default-value initialisers, so
 * a partial object from the server would otherwise leave a non-nullable `Boolean` in an
 * undefined state. Nullable plus `== true` treats anything missing as "do not refresh".
 */
@Serializable
data class InteractionRefresh(
    @SerializedName("onDisplay") val onDisplay: Boolean? = null,
    @SerializedName("onResponse") val onResponse: Boolean? = null,
    @SerializedName("onFinished") val onFinished: Boolean? = null
) {
    /** Whether an interaction of this kind should trigger a user-state refresh. */
    fun shouldRefresh(source: InteractionSource): Boolean {
        return when (source) {
            InteractionSource.ON_DISPLAY -> onDisplay == true
            InteractionSource.ON_RESPONSE -> onResponse == true
            InteractionSource.ON_FINISHED -> onFinished == true
        }
    }
}
