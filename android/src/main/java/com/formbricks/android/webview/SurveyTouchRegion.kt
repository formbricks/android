package com.formbricks.android.webview

import com.formbricks.android.model.javascript.CardRect

/**
 * Which touches over the survey's full-screen WebView belong to the survey, and which should
 * reach the host app underneath.
 *
 * A WebView hit-tests its entire rectangle. The shared renderer already sets
 * `pointer-events: none` outside the card, but that is a *web* hit test — Android never sees it,
 * so a transparent full-screen WebView still swallows every touch and the host app appears frozen.
 *
 * Three states rather than two, because "no rect has arrived" and "the card is not on screen"
 * need opposite answers. Conflating them is what made the Flutter SDK's card untappable when its
 * DOM probe stopped matching: a missing rect was read as "claim nothing", so the survey itself
 * stopped responding.
 */
sealed interface SurveyTouchRegion {

    /** Whether a touch at ([x], [y]) in physical pixels belongs to the survey. */
    fun accepts(x: Float, y: Float): Boolean

    /**
     * Every touch belongs to the survey.
     *
     * Correct for a `light` or `dark` overlay, whose visible backdrop is meant to block the host
     * app. Also the starting state for a no-overlay survey, and it stays that way if the renderer
     * never reports a rect — an older self-hosted server serves a bundle without
     * `onCardRectChange`, and behaving exactly as the SDK always did is the safe answer there.
     */
    data object Everything : SurveyTouchRegion {
        override fun accepts(x: Float, y: Float): Boolean = true
    }

    /**
     * Nothing belongs to the survey, because no card is on screen.
     *
     * The renderer reports this while the card animates out, and the card is hidden for a full
     * second before the close arrives. Without this state the SDK leaves a dead patch over a host
     * app that looks perfectly usable.
     */
    data object Nothing : SurveyTouchRegion {
        override fun accepts(x: Float, y: Float): Boolean = false
    }

    /**
     * Only touches inside these bounds (physical pixels) belong to the survey.
     *
     * Plain floats rather than `RectF` on purpose: `android.graphics` is stubbed to throw in JVM
     * unit tests, so a framework type here would force this logic onto a device or Robolectric for
     * no benefit. The fragment converts at the edge.
     *
     * Right and bottom are exclusive, matching `RectF.contains`.
     */
    data class Card(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : SurveyTouchRegion {
        override fun accepts(x: Float, y: Float): Boolean =
            x >= left && x < right && y >= top && y < bottom
    }

    companion object {
        /**
         * Maps a rect reported by the renderer onto a region.
         *
         * [density] converts the renderer's CSS pixels into the physical pixels Android reports
         * touches in; passing the wrong one silently offsets the whole mask, which looks like the
         * survey ignoring taps near its edges.
         *
         * A missing rect means the card is not on screen — deliberately *not* [Everything], which
         * means the renderer never reported at all.
         */
        fun forReported(rect: CardRect?, density: Float): SurveyTouchRegion {
            if (rect == null) return Nothing
            return Card(
                left = rect.x * density,
                top = rect.y * density,
                right = (rect.x + rect.width) * density,
                bottom = (rect.y + rect.height) * density,
            )
        }
    }
}
