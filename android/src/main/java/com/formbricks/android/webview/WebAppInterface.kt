package com.formbricks.android.webview

import android.webkit.JavascriptInterface
import com.formbricks.android.logger.Logger
import com.formbricks.android.model.javascript.CardRect
import com.formbricks.android.model.javascript.CardRectData
import com.formbricks.android.model.javascript.EventType
import com.formbricks.android.model.javascript.FileUploadData
import com.formbricks.android.model.javascript.JsMessageData
import com.google.gson.JsonParseException
import java.lang.RuntimeException

public class WebAppInterface(private val callback: WebAppCallback?) {

    public interface WebAppCallback {
        public fun onClose()
        public fun onDisplayCreated()
        public fun onResponseCreated()

        /**
         * Defaulted rather than abstract: [WebAppCallback] is public, so adding an abstract
         * member would stop any existing implementor from compiling. Only the SDK implements
         * this today, but that would make an additive change a breaking one.
         */
        public fun onFinished() {
            // Intentionally empty. The default exists so implementors that predate this
            // callback keep compiling; FormbricksFragment overrides it to do the real work.
        }
        public fun onFilePick(data: FileUploadData)
        public fun onSurveyLibraryLoadError()

        /**
         * The survey card moved, resized, or left the screen ([rect] null).
         *
         * Defaulted for the same reason as [onFinished]: [WebAppCallback] is public, so an
         * abstract member would stop existing implementors compiling.
         */
        public fun onCardRectChange(rect: CardRect?) {
            // Intentionally empty; FormbricksFragment overrides it.
        }

        public fun onSurveyRendered() {
            // Intentionally empty; FormbricksFragment overrides it.
        }
    }

    /**
     * Javascript interface to get messages from the WebView's embedded JS
     */
    @JavascriptInterface
    public fun message(data: String) {
        Logger.d(data)

        try {
            val jsMessage = JsMessageData.from(data)
            when (jsMessage.event) {
                EventType.ON_CLOSE -> callback?.onClose()

                EventType.ON_DISPLAY_CREATED -> callback?.onDisplayCreated()

                EventType.ON_RESPONSE_CREATED -> callback?.onResponseCreated()

                EventType.ON_FINISHED -> callback?.onFinished()

                EventType.ON_FILE_PICK -> {
                    callback?.onFilePick(FileUploadData.from(data))
                }

                EventType.ON_SURVEY_LIBRARY_LOAD_ERROR -> {
                    callback?.onSurveyLibraryLoadError()
                }

                EventType.ON_CARD_RECT_CHANGE -> {
                    callback?.onCardRectChange(CardRectData.from(data).rect)
                }

                EventType.ON_SURVEY_RENDERED -> callback?.onSurveyRendered()
            }
        } catch (e: Exception) {
            Logger.e(RuntimeException(e.message))
        } catch (e: JsonParseException) {
            Logger.e(RuntimeException("Failed to parse JSON message: $data"))
        } catch (e: IllegalArgumentException) {
            Logger.e(RuntimeException("Invalid message format: $data"))
        } catch (e: Exception) {
            Logger.e(RuntimeException("Unexpected error processing message: $data"))
        }
    }

    public companion object {
        public const val INTERFACE_NAME: String = "FormbricksJavascript"
    }
}
