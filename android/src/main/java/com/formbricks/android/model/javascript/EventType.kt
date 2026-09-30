package com.formbricks.android.model.javascript

import com.google.gson.annotations.SerializedName

enum class EventType {
    @SerializedName("onClose")  ON_CLOSE,
    @SerializedName("onDisplayCreated") ON_DISPLAY_CREATED,
    @SerializedName("onResponseCreated") ON_RESPONSE_CREATED,
    @SerializedName("onFinished") ON_FINISHED,
    @SerializedName("onFilePick") ON_FILE_PICK,
    @SerializedName("onSurveyLibraryLoadError") ON_SURVEY_LIBRARY_LOAD_ERROR,
    /** The survey card moved or resized; carries its rect. See [com.formbricks.android.webview.SurveyTouchRegion]. */
    @SerializedName("onCardRectChange") ON_CARD_RECT_CHANGE
}