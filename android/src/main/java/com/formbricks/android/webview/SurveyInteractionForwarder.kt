package com.formbricks.android.webview

import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.model.workspace.InteractionSource
import java.util.Collections

/**
 * Forwards in-survey interactions to the survey manager at most once per source.
 *
 * One instance lives per survey showing, so the de-duplication is scoped to that showing. The
 * surveys library guards `onResponseCreated` itself, but `onFinished` is not guarded there, and
 * a self-hosted server may serve an older bundle — so the refresh is gated on our side too.
 * Only the refresh is gated; the existing displays/responses bookkeeping keeps its behaviour.
 *
 * Bridge callbacks arrive on the WebView's JavaBridge thread, hence the synchronised set.
 */
internal class SurveyInteractionForwarder {
    private val refreshedSources = Collections.synchronizedSet(mutableSetOf<InteractionSource>())

    fun refreshOnce(surveyId: String, source: InteractionSource) {
        if (!refreshedSources.add(source)) return
        SurveyManager.onSurveyInteraction(surveyId, source)
    }
}
