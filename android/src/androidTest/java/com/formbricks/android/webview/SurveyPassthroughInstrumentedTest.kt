package com.formbricks.android.webview

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.model.workspace.SurveyOverlay
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The no-overlay path: a survey in the host's own content view that hands touches outside the
 * card to whatever is underneath.
 */
@RunWith(AndroidJUnit4::class)
class SurveyPassthroughInstrumentedTest {

    /** Counts touch-downs that reach it, and takes every one it gets. */
    private class CountingView(context: Context) : View(context) {
        var downs = 0
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) downs++
            return true
        }
    }

    /**
     * A host screen with the survey layered on top of it in the same content FrameLayout — the
     * arrangement FormbricksFragment builds for `overlay: none`. The card is the bottom half.
     */
    private fun withHostAndSurvey(
        region: (SurveyPassthroughLayout) -> Unit,
        block: (content: ViewGroup, host: CountingView, survey: CountingView) -> Unit,
    ) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = CountingView(activity)
                activity.setContentView(host)
                val content = activity.findViewById<ViewGroup>(android.R.id.content)

                val survey = CountingView(activity)
                val layout = SurveyPassthroughLayout(activity)
                layout.addView(survey, MATCH_PARENT, MATCH_PARENT)
                content.addView(layout, MATCH_PARENT, MATCH_PARENT)
                content.measure(exactly(400), exactly(800))
                content.layout(0, 0, 400, 800)
                region(layout)

                block(content, host, survey)
            }
        }
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private fun tap(parent: ViewGroup, x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(time, time, action, x, y, 0)
            parent.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    // Touch routing

    @Test
    fun touchesOutsideTheCardReachTheHostAndTouchesOnItReachTheSurvey() {
        withHostAndSurvey({ it.touchRegion = SurveyTouchRegion.Card(0f, 400f, 400f, 800f) }) { content, host, survey ->
            tap(content, 200f, 100f)
            assertEquals("above the card belongs to the host app", 1, host.downs)
            assertEquals(0, survey.downs)

            tap(content, 200f, 600f)
            assertEquals("the card belongs to the survey", 1, survey.downs)
            assertEquals(1, host.downs)
        }
    }

    @Test
    fun beforeAnyRectArrivesTheSurveyBlocksTheHost() {
        // The layout's own default: an older server never reports a rect.
        withHostAndSurvey({ }) { content, host, survey ->
            tap(content, 200f, 100f)

            assertEquals(1, survey.downs)
            assertEquals(0, host.downs)
        }
    }

    @Test
    fun onceTheCardHasGoneEveryTouchReachesTheHost() {
        withHostAndSurvey({ it.touchRegion = SurveyTouchRegion.Nothing }) { content, host, survey ->
            tap(content, 200f, 600f)

            assertEquals(1, host.downs)
            assertEquals(0, survey.downs)
        }
    }

    // Keyboard

    private fun keyboard(height: Int): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, height))
        .build()

    @Test
    fun theLayoutMakesRoomForTheKeyboardAndGivesItBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val layout = SurveyPassthroughLayout(context)
        layout.measure(exactly(400), exactly(800))
        layout.layout(0, 0, 400, 800)

        // Edge to edge: the layout reaches the bottom of the window, so it pads by the whole keyboard.
        ViewCompat.dispatchApplyWindowInsets(layout, keyboard(300))
        assertEquals(300, layout.paddingBottom)

        ViewCompat.dispatchApplyWindowInsets(layout, keyboard(0))
        assertEquals("no gap left once the keyboard closes", 0, layout.paddingBottom)
    }

    @Test
    fun keyboardOverlapCoversOnlyTheCoveredPartAndIsNeverNegative() {
        val window = 2400
        assertEquals(0, SurveyPassthroughLayout.keyboardOverlap(window, window, keyboardHeight = 0))
        assertEquals(900, SurveyPassthroughLayout.keyboardOverlap(window, window, keyboardHeight = 900))
        // A host that already resized: padding on top would push the card twice as far up.
        assertEquals(0, SurveyPassthroughLayout.keyboardOverlap(1500, window, keyboardHeight = 900))
        assertEquals(0, SurveyPassthroughLayout.keyboardOverlap(1200, window, keyboardHeight = 900))
        assertEquals(100, SurveyPassthroughLayout.keyboardOverlap(1600, window, keyboardHeight = 900))
    }

    // Which path a survey takes

    @Test
    fun aSurveyOverrideBeatsTheWorkspaceOverlay() {
        assertEquals(SurveyOverlay.DARK, SurveyOverlay.resolve(SurveyOverlay.DARK, SurveyOverlay.NONE))
        assertEquals(SurveyOverlay.NONE, SurveyOverlay.resolve(SurveyOverlay.NONE, SurveyOverlay.LIGHT))
    }

    @Test
    fun withoutAnOverrideTheWorkspaceOverlayAppliesAndNoSettingMeansNone() {
        assertEquals(SurveyOverlay.LIGHT, SurveyOverlay.resolve(null, SurveyOverlay.LIGHT))
        // `none` is the default, so most workspaces take the pass-through path.
        assertEquals(SurveyOverlay.NONE, SurveyOverlay.resolve(null, null))
    }
}
