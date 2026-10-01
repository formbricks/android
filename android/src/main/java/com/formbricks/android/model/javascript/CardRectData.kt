package com.formbricks.android.model.javascript

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Where the survey card is, as the shared renderer measures it.
 *
 * A WebView hit-tests its whole rectangle and ignores the `pointer-events: none` the renderer
 * puts outside the card, so a full-screen WebView swallows every touch even when nothing is
 * painted. To let touches through, the native side has to mask them itself — and only the web
 * layer knows where the card is, because CSS decides that.
 *
 * Values are CSS pixels relative to the viewport, so they must be scaled by the WebView's display
 * density before being compared against Android touch coordinates, which are in physical pixels.
 */
data class CardRect(
    @SerializedName("x") val x: Float,
    @SerializedName("y") val y: Float,
    @SerializedName("width") val width: Float,
    @SerializedName("height") val height: Float,
)

/**
 * `onCardRectChange` payload. [rect] is absent or null when no card is on screen — while it
 * animates out, or before the first paint.
 */
data class CardRectData(
    @SerializedName("rect") val rect: CardRect?,
) {
    companion object {
        fun from(string: String): CardRectData {
            return try {
                Gson().fromJson(string, CardRectData::class.java)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON format: ${e.message}", e)
            }
        }
    }
}
