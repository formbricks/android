package com.formbricks.android.webview

import com.formbricks.android.model.workspace.SurveyOverlay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The no-overlay path's two decisions that need no device: which surveys take it, and how much
 * room the keyboard needs.
 */
class SurveyPassthroughTest {

    // Which path a survey takes

    @Test
    fun `a survey override beats the workspace overlay`() {
        assertEquals(SurveyOverlay.DARK, SurveyOverlay.resolve(SurveyOverlay.DARK, SurveyOverlay.NONE))
        assertEquals(SurveyOverlay.NONE, SurveyOverlay.resolve(SurveyOverlay.NONE, SurveyOverlay.LIGHT))
    }

    @Test
    fun `without an override the workspace overlay applies, and no setting means none`() {
        assertEquals(SurveyOverlay.LIGHT, SurveyOverlay.resolve(null, SurveyOverlay.LIGHT))
        // `none` is the default, so most workspaces take the pass-through path.
        assertEquals(SurveyOverlay.NONE, SurveyOverlay.resolve(null, null))
    }

    // Keyboard

    private val windowHeight = 2400

    @Test
    fun `no keyboard needs no room`() {
        assertEquals(0, SurveyPassthroughLayout.keyboardOverlap(windowHeight, windowHeight, keyboardHeight = 0))
    }

    @Test
    fun `an edge-to-edge window makes room for the whole keyboard`() {
        // The window does not shrink, so the survey still reaches the bottom of the screen.
        assertEquals(900, SurveyPassthroughLayout.keyboardOverlap(windowHeight, windowHeight, keyboardHeight = 900))
    }

    @Test
    fun `a host that already resized needs nothing more`() {
        // Adding padding on top of the resize would push the card twice as far up.
        assertEquals(0, SurveyPassthroughLayout.keyboardOverlap(1500, windowHeight, keyboardHeight = 900))
        // Ending clear of the keyboard is still zero, never negative padding.
        assertEquals(0, SurveyPassthroughLayout.keyboardOverlap(1200, windowHeight, keyboardHeight = 900))
    }

    @Test
    fun `a view ending above the bottom only makes up the part the keyboard covers`() {
        assertEquals(100, SurveyPassthroughLayout.keyboardOverlap(1600, windowHeight, keyboardHeight = 900))
    }
}
