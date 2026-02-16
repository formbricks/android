package com.formbricks.android

import androidx.fragment.app.FragmentManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.api.FormbricksApi
import com.formbricks.android.helper.FormbricksConfig
import com.formbricks.android.logger.Logger
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.user.AttributeValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class FormbricksInstrumentedTest {

    private val environmentId = "environmentId"
    private val appUrl = "https://example.com"
    private val userId = "6CCCE716-6783-4D0F-8344-9C7DFA43D8F7"
    private val surveyID = "cm6ovw6j7000gsf0kduf4oo4i"

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.applicationContext = appContext
        Formbricks.isInitialized = false
        Formbricks.language = "default"
        UserManager.logout()
        SurveyManager.environmentDataHolder = null
        FormbricksApi.service = MockFormbricksApiService()
    }

    @Test
    fun testFormbricks() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.formbricks.android.test", appContext.packageName)

        // Everything should be in the default state
        assertFalse(Formbricks.isInitialized)
        assertEquals(0, SurveyManager.filteredSurveys.size)
        assertNull(SurveyManager.environmentDataHolder)
        assertNull(UserManager.userId)
        assertEquals("default", Formbricks.language)

        // Use methods before init should have no effect
        Formbricks.setUserId("userId")
        Formbricks.setLanguage("de")
        Formbricks.setAttributes(mapOf("testA" to AttributeValue.string("testB")))
        Formbricks.setAttribute("test", "testKey")
        assertNull(UserManager.userId)
        assertEquals("default", Formbricks.language)
        Formbricks.track("click_demo_button")
        waitForSeconds(1)
        assertFalse(SurveyManager.isShowingSurvey)
        Formbricks.logout()
        Formbricks.setFragmentManager(MockFragmentManager())
        Formbricks.setLanguage("")

        // Call the setup and initialize the SDK
        Formbricks.setup(appContext, FormbricksConfig.Builder(appUrl, environmentId).setLoggingEnabled(true).build())
        waitForSeconds(1)

        // Should be ignored, becuase we don't have user ID yet
        Formbricks.setAttributes(mapOf("testA" to AttributeValue.string("testB")))
        Formbricks.setAttribute("test", "testKey")
        assertNull(UserManager.userId)

        // Verify the base variables are set properly
        assertTrue(Formbricks.isInitialized)
        assertEquals(appUrl, Formbricks.appUrl)
        assertEquals(environmentId, Formbricks.environmentId)

        // User manager default state. There is no user yet.
        assertEquals(UserManager.displays?.count(), 0)
        assertEquals(UserManager.responses?.count(), 0)
        assertEquals(UserManager.segments?.count(), 0)

        // Check error state handling
        (FormbricksApi.service as MockFormbricksApiService).isErrorResponseNeeded = true
        assertFalse(SurveyManager.hasApiError)
        SurveyManager.refreshEnvironmentIfNeeded(true)
        waitForSeconds(3) // Increased wait time to 3 seconds
        assertTrue(SurveyManager.hasApiError)
        (FormbricksApi.service as MockFormbricksApiService).isErrorResponseNeeded = false

        // Authenticate the user
        Formbricks.setUserId(userId)
        waitForSeconds(2)
        assertEquals(userId, UserManager.userId)
        assertNotNull(UserManager.syncTimer)

        // The environment should be fetched already
        assertNotNull(SurveyManager.environmentDataHolder)

        // Check if the filter method works properly
        assertEquals(1, SurveyManager.filteredSurveys.size)
        assertFalse(SurveyManager.isShowingSurvey)

        // Track an unknown event, shouldn't show the survey
        Formbricks.track("unknown_event")
        assertFalse(SurveyManager.isShowingSurvey)

        // Track a known event, thus, the survey should be shown.
        SurveyManager.isShowingSurvey = false
        
        // Track the event but don't show the survey
        val firstSurveyBeforeTrack = SurveyManager.filteredSurveys.firstOrNull()
        assertNotNull("Should have a survey before tracking", firstSurveyBeforeTrack)
        assertEquals("Should have the correct survey ID", surveyID, firstSurveyBeforeTrack?.id)
        
        val actionClasses = SurveyManager.environmentDataHolder?.data?.data?.actionClasses ?: listOf()
        val clickDemoButtonAction = actionClasses.firstOrNull { it.key == "click_demo_button" }
        assertNotNull("Should have click_demo_button action class", clickDemoButtonAction)
        
        val triggers = firstSurveyBeforeTrack?.triggers ?: listOf()
        val matchingTrigger = triggers.firstOrNull { it.actionClass?.name == clickDemoButtonAction?.name }
        assertNotNull("Survey should have matching trigger", matchingTrigger)
        
        // Now track the event
        Formbricks.track("click_demo_button")
        waitForSeconds(1)
        assertTrue("Survey should be marked as showing", SurveyManager.isShowingSurvey)

        // Validate display and response
        SurveyManager.onNewDisplay(surveyID)
        SurveyManager.postResponse(surveyID)
        assertEquals(1, UserManager.responses?.size)
        assertEquals(1, UserManager.displays?.size)

        // Track a valid event, but the survey should not shown, because we already gave a response.
        SurveyManager.isShowingSurvey = false
        Formbricks.track("click_demo_button")
        waitForSeconds(1)
        assertFalse(SurveyManager.isShowingSurvey)

        // Validate logout
        assertNotNull(UserManager.userId)
        assertNotNull(UserManager.lastDisplayedAt)
        assertNotEquals(UserManager.displays?.count(), 0)
        assertNotEquals(UserManager.responses?.count(), 0)
        assertNotEquals(UserManager.segments?.count(), 0)
        assertNotNull(UserManager.expiresAt)
        Formbricks.logout()
        assertNull(UserManager.userId)
        assertNull(UserManager.lastDisplayedAt)
        assertNull(UserManager.displays)
        assertEquals(UserManager.responses?.count(), 0)
        assertEquals(UserManager.segments?.count(), 0)
        assertNull(UserManager.expiresAt)

        // Setting the language
        assertEquals("default", Formbricks.language)
        Formbricks.setLanguage("de")
        assertEquals("de", Formbricks.language)

        // Clear the responses
        Formbricks.logout()
        SurveyManager.filterSurveys()

        Formbricks.track("click_demo_button")
        waitForSeconds(1)
        assertTrue(SurveyManager.isShowingSurvey)
    }

    @Test
    fun testSetAttributesWithUserId() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.setup(appContext, FormbricksConfig.Builder(appUrl, environmentId).setLoggingEnabled(true).build())
        waitForSeconds(1)

        // Set userId first, then set attributes - exercises UpdateQueue.setAttributes with a valid userId
        Formbricks.setUserId(userId)
        waitForSeconds(2)
        assertEquals(userId, UserManager.userId)

        Formbricks.setAttributes(mapOf(
            "plan" to AttributeValue.string("premium"),
            "score" to AttributeValue.number(99.5)
        ))
        waitForSeconds(1)

        // User should still be synced
        assertEquals(userId, UserManager.userId)
    }

    @Test
    fun testAddAttributeWithUserId() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.setup(appContext, FormbricksConfig.Builder(appUrl, environmentId).setLoggingEnabled(true).build())
        waitForSeconds(1)

        // Set userId first, then add attributes - exercises UpdateQueue.addAttribute with a valid userId
        Formbricks.setUserId(userId)
        waitForSeconds(2)
        assertEquals(userId, UserManager.userId)

        Formbricks.setAttribute("John", "name")
        Formbricks.setAttribute(42.0, "age")
        Formbricks.setAttribute(99, "level")
        waitForSeconds(1)

        assertEquals(userId, UserManager.userId)
    }

    @Test
    fun testSetLanguageWithUserId() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.setup(appContext, FormbricksConfig.Builder(appUrl, environmentId).setLoggingEnabled(true).build())
        waitForSeconds(1)

        // Set userId first, then set language - exercises the if-branch in UpdateQueue.setLanguage
        Formbricks.setUserId(userId)
        waitForSeconds(2)
        assertEquals(userId, UserManager.userId)

        Formbricks.setLanguage("de")
        waitForSeconds(1)

        assertEquals("de", Formbricks.language)
        assertEquals(userId, UserManager.userId)
    }

    @Test
    fun testSetUserIdSameValueIsNoOp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.setup(appContext, FormbricksConfig.Builder(appUrl, environmentId).setLoggingEnabled(true).build())
        waitForSeconds(1)

        Formbricks.setUserId(userId)
        waitForSeconds(2)
        assertEquals(userId, UserManager.userId)

        // Same userId again — should be a no-op
        Formbricks.setUserId(userId)
        assertEquals(userId, UserManager.userId)
    }

    @Test
    fun testSetUserIdDifferentValueOverridesPrevious() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.setup(appContext, FormbricksConfig.Builder(appUrl, environmentId).setLoggingEnabled(true).build())
        waitForSeconds(1)

        Formbricks.setUserId(userId)
        waitForSeconds(2)
        assertEquals(userId, UserManager.userId)
        assertNotNull(UserManager.expiresAt)

        // Different userId — should clean up previous state and re-sync
        // (Previously this would error and return without doing anything)
        val newUserId = "NEW-USER-ID-12345"
        Formbricks.setUserId(newUserId)

        // Verify that logout was called: expiresAt should be cleared immediately
        assertNull("expiresAt should be cleared by logout", UserManager.expiresAt)

        // After sync completes, the mock returns the hardcoded userId from User.json,
        // so we just verify the SDK is still functional (sync completed without errors)
        waitForSeconds(2)
        assertNotNull("userId should be set after re-sync", UserManager.userId)
    }

    @Test
    fun testLogoutWithoutUserIdDoesNotError() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.setup(appContext, FormbricksConfig.Builder(appUrl, environmentId).setLoggingEnabled(true).build())
        waitForSeconds(1)

        // Logout without ever setting a userId — should not crash
        assertNull(UserManager.userId)
        Formbricks.logout()
        assertNull(UserManager.userId)
    }

    private fun waitForSeconds(seconds: Long) {
        val latch = CountDownLatch(1)
        latch.await(seconds, TimeUnit.SECONDS)
    }
}

class MockFragmentManager : FragmentManager()