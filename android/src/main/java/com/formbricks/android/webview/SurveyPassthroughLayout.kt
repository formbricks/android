package com.formbricks.android.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/**
 * Hosts a no-overlay survey inside the host Activity's own content view.
 *
 * Declining a touch-down here is what lets the host app have it: the parent `FrameLayout` then
 * offers the same event to the next child down, which is the host's content. That only works
 * within one window — a `Dialog` is a window of its own, and Android picks the window by bounds
 * before any view sees the touch — which is why this path is not a dialog.
 */
@SuppressLint("ViewConstructor")
internal class SurveyPassthroughLayout(context: Context) : FrameLayout(context) {

    /** Starts at [SurveyTouchRegion.Everything]: blocks like the dialog did until a rect arrives. */
    var touchRegion: SurveyTouchRegion = SurveyTouchRegion.Everything

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateKeyboardPadding(null) }

    init {
        // Edge-to-edge windows (the default from Android 15) do not shrink for the keyboard, so
        // the survey has to make room itself. Insets changes cover that case; the layout listener
        // covers hosts that still resize, where this view simply comes out shorter.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            updateKeyboardPadding(insets)
            insets
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // The rest of a gesture follows its down event, so deciding on the down is enough.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !touchRegion.accepts(event.x, event.y)) {
            return false
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        super.onDetachedFromWindow()
    }

    /**
     * Pads the bottom by however much of this view the keyboard covers. The WebView shrinks with
     * the padding, so the renderer lays the card out above the keyboard and reports the new rect.
     */
    private fun updateKeyboardPadding(dispatched: WindowInsetsCompat?) {
        // The root insets when attached: before API 30, a host view earlier in the content
        // FrameLayout can consume the keyboard inset before it is ever dispatched to this one.
        val insets = ViewCompat.getRootWindowInsets(this) ?: dispatched ?: return
        val location = IntArray(2)
        getLocationInWindow(location)
        val padding = keyboardOverlap(
            viewBottom = location[1] + height,
            windowHeight = rootView.height,
            keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
        )
        if (padding != paddingBottom) setPadding(0, 0, 0, padding)
    }

    companion object {
        /**
         * How far the keyboard reaches up into a view whose bottom edge sits at [viewBottom], all
         * in window pixels. Zero when the host already resized the window, because the view then
         * ends above the keyboard anyway.
         */
        fun keyboardOverlap(viewBottom: Int, windowHeight: Int, keyboardHeight: Int): Int {
            if (keyboardHeight <= 0) return 0
            return max(0, viewBottom - (windowHeight - keyboardHeight))
        }
    }
}
