package com.formbricks.android.webview

import android.webkit.JavascriptInterface
import com.formbricks.android.logger.Logger
import com.formbricks.android.model.javascript.JsMessageData
import com.formbricks.android.model.javascript.EventType
import com.formbricks.android.model.javascript.FileUploadData
import com.google.gson.JsonParseException
import java.lang.RuntimeException

class WebAppInterface(private val callback: WebAppCallback?) {

    interface WebAppCallback {
        fun onClose()
        fun onDisplayCreated()
        fun onResponseCreated()

        /**
         * Defaulted rather than abstract: [WebAppCallback] is public, so adding an abstract
         * member would stop any existing implementor from compiling. Only the SDK implements
         * this today, but that would make an additive change a breaking one.
         */
        fun onFinished() {}
        fun onFilePick(data: FileUploadData)
        fun onSurveyLibraryLoadError()
    }

    /**
     * Javascript interface to get messages from the WebView's embedded JS
     */
    @JavascriptInterface
    fun message(data: String) {
        Logger.d(data)

        try {
            val jsMessage = JsMessageData.from(data)
            when (jsMessage.event) {
                EventType.ON_CLOSE -> callback?.onClose()
                EventType.ON_DISPLAY_CREATED -> callback?.onDisplayCreated()
                EventType.ON_RESPONSE_CREATED -> callback?.onResponseCreated()
                EventType.ON_FINISHED -> callback?.onFinished()
                EventType.ON_FILE_PICK -> { callback?.onFilePick(FileUploadData.from(data)) }
                EventType.ON_SURVEY_LIBRARY_LOAD_ERROR -> { callback?.onSurveyLibraryLoadError() }
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

    companion object {
        const val INTERFACE_NAME = "FormbricksJavascript"
    }
}