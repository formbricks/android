package com.formbricks.android.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.model.workspace.InteractionRefresh
import com.formbricks.android.model.workspace.InteractionSource
import com.formbricks.android.model.workspace.Settings
import com.formbricks.android.model.workspace.Survey
import com.formbricks.android.model.workspace.WorkspaceData
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import com.formbricks.android.model.workspace.WorkspaceResponseData
import com.formbricks.android.network.queue.UpdateQueue
import com.formbricks.android.webview.SurveyInteractionForwarder
import com.google.gson.Gson
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the delivery path for interaction-based segment refreshes:
 * bridge event -> [SurveyInteractionForwarder] -> [SurveyManager.onSurveyInteraction] ->
 * [UserManager.refreshSegmentsAfterInteraction] -> [UpdateQueue].
 *
 * The observation point is [UpdateQueue]'s private `userId`, which
 * `requestUserStateRefresh` sets. That keeps the assertions deterministic — no debounce
 * waits and no network — while still proving the whole chain is connected.
 */
@RunWith(AndroidJUnit4::class)
class SurveyInteractionRefreshInstrumentedTest {

    private val gson = Gson()

    @Before
    fun setup() {
        UpdateQueue.reset()
        setUserId(null)
        setBackingWorkspaceDataHolder(null)
    }

    @After
    fun tearDown() {
        UpdateQueue.reset()
        setUserId(null)
        setBackingWorkspaceDataHolder(null)
    }

    // MARK: - Decoding

    @Test
    fun surveyWithoutInteractionRefreshDecodesToNull() {
        val survey = gson.fromJson("""{"id":"survey-a"}""", Survey::class.java)
        assertNull(survey.interactionRefresh)
    }

    @Test
    fun surveyDecodesFullInteractionRefresh() {
        val json = """{"id":"survey-a","interactionRefresh":{"onDisplay":true,"onResponse":false,"onFinished":true}}"""
        val refresh = gson.fromJson(json, Survey::class.java).interactionRefresh
        assertNotNull(refresh)
        assertTrue(refresh!!.shouldRefresh(InteractionSource.ON_DISPLAY))
        assertFalse(refresh.shouldRefresh(InteractionSource.ON_RESPONSE))
        assertTrue(refresh.shouldRefresh(InteractionSource.ON_FINISHED))
    }

    /**
     * A partial object must not fail or leave a flag undefined. Gson does not run Kotlin
     * default-value initialisers, which is why the flags are nullable.
     */
    @Test
    fun partialInteractionRefreshTreatsMissingKeysAsFalse() {
        val json = """{"id":"survey-a","interactionRefresh":{"onDisplay":true}}"""
        val refresh = gson.fromJson(json, Survey::class.java).interactionRefresh
        assertNotNull(refresh)
        assertTrue(refresh!!.shouldRefresh(InteractionSource.ON_DISPLAY))
        assertFalse(refresh.shouldRefresh(InteractionSource.ON_RESPONSE))
        assertFalse(refresh.shouldRefresh(InteractionSource.ON_FINISHED))
    }

    /** The server attaches an all-false object to every survey in such a workspace. */
    @Test
    fun allFalseInteractionRefreshIsPresentButNeverRefreshes() {
        val json = """{"id":"survey-a","interactionRefresh":{"onDisplay":false,"onResponse":false,"onFinished":false}}"""
        val refresh = gson.fromJson(json, Survey::class.java).interactionRefresh
        assertNotNull(refresh)
        InteractionSource.entries.forEach { assertFalse(refresh!!.shouldRefresh(it)) }
    }

    @Test
    fun unknownKeyInsideInteractionRefreshIsIgnored() {
        val json = """{"id":"survey-a","interactionRefresh":{"onDisplay":true,"onSomethingNew":true}}"""
        val refresh = gson.fromJson(json, Survey::class.java).interactionRefresh
        assertTrue(refresh!!.shouldRefresh(InteractionSource.ON_DISPLAY))
    }

    // MARK: - The gate

    @Test
    fun anonymousUserNeverRefreshes() {
        seedWorkspace(survey("survey-a", InteractionRefresh(onDisplay = true)))
        setUserId(null)

        SurveyManager.onSurveyInteraction("survey-a", InteractionSource.ON_DISPLAY)

        assertNull(queuedUserId())
    }

    @Test
    fun absentInteractionRefreshDoesNotRefresh() {
        seedWorkspace(survey("survey-a", null))
        setUserId("user-1")

        SurveyManager.onSurveyInteraction("survey-a", InteractionSource.ON_DISPLAY)

        assertNull(queuedUserId())
    }

    @Test
    fun allFalseFlagsDoNotRefresh() {
        seedWorkspace(survey("survey-a", InteractionRefresh()))
        setUserId("user-1")

        SurveyManager.onSurveyInteraction("survey-a", InteractionSource.ON_DISPLAY)

        assertNull(queuedUserId())
    }

    @Test
    fun mismatchedSourceDoesNotRefresh() {
        seedWorkspace(survey("survey-a", InteractionRefresh(onDisplay = true)))
        setUserId("user-1")

        SurveyManager.onSurveyInteraction("survey-a", InteractionSource.ON_RESPONSE)

        assertNull(queuedUserId())
    }

    @Test
    fun matchingFlagRefreshes() {
        seedWorkspace(survey("survey-a", InteractionRefresh(onFinished = true)))
        setUserId("user-1")

        SurveyManager.onSurveyInteraction("survey-a", InteractionSource.ON_FINISHED)

        assertEquals("user-1", queuedUserId())
    }

    // MARK: - Survey lookup

    /**
     * The survey id must actually be matched. If the lookup degraded to "just take the first
     * survey", an unknown id would wrongly consult another survey's flags.
     */
    @Test
    fun unknownSurveyIdDoesNotRefresh() {
        seedWorkspace(survey("survey-a", InteractionRefresh(onFinished = true)))
        setUserId("user-1")

        SurveyManager.onSurveyInteraction("no-such-survey", InteractionSource.ON_FINISHED)

        assertNull(queuedUserId())
    }

    /** The correct survey's flags are consulted, not the first one in the list. */
    @Test
    fun theMatchingSurveysFlagsAreUsed() {
        seedWorkspace(
            survey("survey-a", InteractionRefresh(onFinished = false)),
            survey("survey-b", InteractionRefresh(onFinished = true))
        )
        setUserId("user-1")

        SurveyManager.onSurveyInteraction("survey-b", InteractionSource.ON_FINISHED)
        assertEquals("user-1", queuedUserId())

        UpdateQueue.reset()
        SurveyManager.onSurveyInteraction("survey-a", InteractionSource.ON_FINISHED)
        assertNull(queuedUserId())
    }

    @Test
    fun nullSurveyIdDoesNotRefresh() {
        seedWorkspace(survey("survey-a", InteractionRefresh(onFinished = true)))
        setUserId("user-1")

        SurveyManager.onSurveyInteraction(null, InteractionSource.ON_FINISHED)

        assertNull(queuedUserId())
    }

    // MARK: - One-shot forwarding

    @Test
    fun forwarderRefreshesOncePerSource() {
        seedWorkspace(survey("survey-a", InteractionRefresh(onFinished = true, onDisplay = true)))
        setUserId("user-1")
        val forwarder = SurveyInteractionForwarder()

        forwarder.refreshOnce("survey-a", InteractionSource.ON_FINISHED)
        assertEquals("user-1", queuedUserId())

        // A repeat of the same source must not reach the queue again.
        UpdateQueue.reset()
        forwarder.refreshOnce("survey-a", InteractionSource.ON_FINISHED)
        assertNull(queuedUserId())

        // A different source still gets through.
        forwarder.refreshOnce("survey-a", InteractionSource.ON_DISPLAY)
        assertEquals("user-1", queuedUserId())
    }

    @Test
    fun aNewShowingCanRefreshAgain() {
        seedWorkspace(survey("survey-a", InteractionRefresh(onFinished = true)))
        setUserId("user-1")

        SurveyInteractionForwarder().refreshOnce("survey-a", InteractionSource.ON_FINISHED)
        assertEquals("user-1", queuedUserId())

        UpdateQueue.reset()
        SurveyInteractionForwarder().refreshOnce("survey-a", InteractionSource.ON_FINISHED)
        assertEquals("user-1", queuedUserId())
    }

    // MARK: - Helpers

    private fun survey(id: String, refresh: InteractionRefresh?) = Survey(
        id = id,
        triggers = null,
        recontactDays = null,
        displayLimit = null,
        delay = null,
        displayPercentage = null,
        displayOption = null,
        segment = null,
        styling = null,
        languages = null,
        projectOverwrites = null,
        interactionRefresh = refresh
    )

    private fun seedWorkspace(vararg surveys: Survey) {
        val holder = WorkspaceDataHolder(
            data = WorkspaceResponseData(
                data = WorkspaceData(
                    surveys = surveys.toList(),
                    actionClasses = null,
                    settings = emptySettings()
                ),
                expiresAt = null
            ),
            originalResponseMap = emptyMap()
        )
        setBackingWorkspaceDataHolder(holder)
    }

    private fun emptySettings() = Settings(
        id = null,
        recontactDays = null,
        clickOutsideClose = null,
        overlay = null,
        placement = null,
        inAppSurveyBranding = null,
        styling = null
    )

    private fun queuedUserId(): String? {
        val field = UpdateQueue::class.java.getDeclaredField("userId")
        field.isAccessible = true
        return field.get(UpdateQueue) as String?
    }

    /**
     * Sets the in-memory user id *and* the persisted one, because the getter falls back to
     * SharedPreferences when the backing field is null — so clearing only the field would let a
     * value written by another test leak in.
     */
    private fun setUserId(value: String?) {
        val field = UserManager::class.java.getDeclaredField("backingUserId")
        field.isAccessible = true
        field.set(UserManager, value)

        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("formbricks_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (value == null) remove("userIdKey") else putString("userIdKey", value)
            commit()
        }
    }

    private fun setBackingWorkspaceDataHolder(value: WorkspaceDataHolder?) {
        val field = SurveyManager::class.java.getDeclaredField("backingWorkspaceDataHolder")
        field.isAccessible = true
        field.set(SurveyManager, value)
    }
}
