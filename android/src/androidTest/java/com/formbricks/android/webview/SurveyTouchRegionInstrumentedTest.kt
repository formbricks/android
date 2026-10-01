package com.formbricks.android.webview

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.formbricks.android.model.javascript.CardRect
import com.formbricks.android.model.javascript.CardRectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A transparent full-screen WebView still swallows every touch — `pointer-events: none` is a web
 * hit test Android never sees. These pin which touches the survey claims in each state, because
 * getting it wrong is invisible in review and obvious to a user: either the host app freezes, or
 * the survey itself stops responding.
 */
@RunWith(AndroidJUnit4::class)
class SurveyTouchRegionInstrumentedTest {

    private val density = 3f
    private val reported = CardRect(x = 0f, y = 200f, width = 130f, height = 80f)

    // Physical pixels, i.e. CSS pixels * density.
    private val insideCard = Pair(195f, 700f)
    private val outsideCard = Pair(195f, 200f)

    @Test
    fun anOverlaidSurveyClaimsEveryTouch() {
        // A visible backdrop is meant to block the host app.
        assertTrue(SurveyTouchRegion.Everything.accepts(insideCard.first, insideCard.second))
        assertTrue(SurveyTouchRegion.Everything.accepts(outsideCard.first, outsideCard.second))
        assertTrue(SurveyTouchRegion.Everything.accepts(0f, 0f))
    }

    @Test
    fun aReportedRectClaimsOnlyTheCard() {
        val region = SurveyTouchRegion.forReported(reported, density)

        assertTrue("the survey must stay usable", region.accepts(insideCard.first, insideCard.second))
        assertFalse("the host app must stay usable", region.accepts(outsideCard.first, outsideCard.second))
    }

    @Test
    fun theReportedRectIsScaledByDisplayDensity() {
        // The renderer measures in CSS pixels; Android reports touches in physical pixels. Getting
        // this wrong offsets the whole mask, which looks like the survey ignoring nearby taps.
        val region = SurveyTouchRegion.forReported(reported, density) as SurveyTouchRegion.Card

        assertEquals(0f, region.left, 0.01f)
        assertEquals(600f, region.top, 0.01f)
        assertEquals(390f, region.right, 0.01f)
        assertEquals(840f, region.bottom, 0.01f)
    }

    @Test
    fun noCardOnScreenClaimsNothing() {
        val region = SurveyTouchRegion.forReported(null, density)

        assertEquals(SurveyTouchRegion.Nothing, region)
        assertFalse(region.accepts(insideCard.first, insideCard.second))
        assertFalse(region.accepts(outsideCard.first, outsideCard.second))
    }

    @Test
    fun absenceOfACardAndAbsenceOfTheFeatureAreDifferent() {
        // An older self-hosted server serves a renderer that never calls `onCardRectChange`, so no
        // rect ever arrives and the region stays Everything — the SDK keeps behaving as it always
        // did. Treating that as "claim nothing" is what made the Flutter card untappable.
        assertTrue(SurveyTouchRegion.Everything.accepts(outsideCard.first, outsideCard.second))
        assertFalse(SurveyTouchRegion.forReported(null, density) == SurveyTouchRegion.Everything)
    }

    @Test
    fun cardEdgesAreHalfOpenMatchingRectFContains() {
        // Right and bottom are exclusive, so the bottom-right corner belongs to the host app.
        // Pinned because a later inset or rounding change would move it silently.
        val region = SurveyTouchRegion.forReported(reported, density)

        assertTrue(region.accepts(0f, 600f))
        assertFalse(region.accepts(390f, 840f))
        assertFalse(region.accepts(-1f, 600f))
    }

    @Test
    fun aZeroAreaCardClaimsNothing() {
        val region = SurveyTouchRegion.forReported(CardRect(x = 10f, y = 10f, width = 0f, height = 0f), density)

        assertFalse(region.accepts(30f, 30f))
    }

    @Test
    fun theBridgePayloadDecodesNullRectIncluded() {
        val withRect = CardRectData.from(
            """{"event":"onCardRectChange","rect":{"x":12.5,"y":600,"width":390,"height":240.25}}"""
        )
        assertEquals(12.5f, withRect.rect!!.x, 0.01f)
        assertEquals(240.25f, withRect.rect!!.height, 0.01f)

        // `rect: null` is how the renderer says the card has gone. It must decode, not throw — a
        // throw would leave the SDK masking touches to a card that is no longer there.
        val withoutRect = CardRectData.from("""{"event":"onCardRectChange","rect":null}""")
        assertNull(withoutRect.rect)
        assertEquals(SurveyTouchRegion.Nothing, SurveyTouchRegion.forReported(withoutRect.rect, density))
    }
}
