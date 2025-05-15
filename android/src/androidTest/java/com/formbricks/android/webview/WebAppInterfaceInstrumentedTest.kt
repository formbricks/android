package com.formbricks.android.webview

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.formbricks.android.model.javascript.EventType
import com.formbricks.android.model.javascript.FileUploadData
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebAppInterfaceInstrumentedTest {
    private lateinit var callback: FakeCallback
    private lateinit var webAppInterface: WebAppInterface

    class FakeCallback : WebAppInterface.WebAppCallback {
        var closed = false
        var displayCreated = false
        var responseCreated = false
        var filePick: FileUploadData? = null
        var surveyLibraryLoadError = false
        override fun onClose() { closed = true }
        override fun onDisplayCreated() { displayCreated = true }
        override fun onResponseCreated() { responseCreated = true }
        override fun onFilePick(data: FileUploadData) { filePick = data }
        override fun onSurveyLibraryLoadError() { surveyLibraryLoadError = true }
    }

    @Before
    fun setup() {
        callback = FakeCallback()
        webAppInterface = WebAppInterface(callback)
    }

    @Test
    fun testMessage_onClose() {
        val json = "{\"event\":\"onClose\"}"
        webAppInterface.message(json)
        assertTrue(callback.closed)
    }

    @Test
    fun testMessage_onDisplayCreated() {
        val json = "{\"event\":\"onDisplayCreated\"}"
        webAppInterface.message(json)
        assertTrue(callback.displayCreated)
    }

    @Test
    fun testMessage_onResponseCreated() {
        val json = "{\"event\":\"onResponseCreated\"}"
        webAppInterface.message(json)
        assertTrue(callback.responseCreated)
    }

    @Test
    fun testMessage_onFilePick() {
        val json = "{\"event\":\"onFilePick\",\"fileUploadParams\":{\"allowedFileExtensions\":\"jpg\",\"allowMultipleFiles\":true}}"
        webAppInterface.message(json)
        assertNotNull(callback.filePick)
        assertEquals("jpg", callback.filePick?.fileUploadParams?.allowedFileExtensions)
        assertEquals(true, callback.filePick?.fileUploadParams?.allowMultipleFiles)
    }

    @Test
    fun testMessage_onSurveyLibraryLoadError() {
        val json = "{\"event\":\"onSurveyLibraryLoadError\"}"
        webAppInterface.message(json)
        assertTrue(callback.surveyLibraryLoadError)
    }

    @Test
    fun testMessage_invalidJson() {
        // Should not throw, should not call any callback
        webAppInterface.message("not a json")
        assertFalse(callback.closed)
        assertFalse(callback.displayCreated)
        assertFalse(callback.responseCreated)
        assertNull(callback.filePick)
        assertFalse(callback.surveyLibraryLoadError)
    }

    @Test
    fun testMessage_unknownEvent() {
        val json = "{\"event\":\"unknown_event\"}"
        webAppInterface.message(json)
        // Should not call any callback
        assertFalse(callback.closed)
        assertFalse(callback.displayCreated)
        assertFalse(callback.responseCreated)
        assertNull(callback.filePick)
        assertFalse(callback.surveyLibraryLoadError)
    }
} 