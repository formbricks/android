package com.formbricks.android.webview

import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.Formbricks
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.model.workspace.Settings
import com.formbricks.android.model.workspace.SurveyOverlay
import com.formbricks.android.model.workspace.WorkspaceData
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import com.formbricks.android.model.workspace.WorkspaceResponseData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Closing a survey must only ever dismiss the survey. Once the host has saved its state, which it
 * does when it goes to the background, a plain `dismiss()` throws. The SDK used to finish the host
 * Activity instead, and the user came back to an app that had restarted.
 *
 * Tests that store a workspace run in one main-thread turn on purpose. A stored workspace makes the
 * WebView load the survey library, which has no network here and reports a failed load a moment
 * later. That report closes the survey through the very code under test.
 */
@RunWith(AndroidJUnit4::class)
class FormbricksFragmentDismissInstrumentedTest {

    private var workspaceBefore: WorkspaceDataHolder? = null

    @Before
    fun setUp() {
        Formbricks.applicationContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        Formbricks.appUrl = "https://test.formbricks.com"
        Formbricks.workspaceId = "test-workspace"
        workspaceBefore = SurveyManager.workspaceDataHolder
    }

    @After
    fun tearDown() {
        SurveyManager.workspaceDataHolder = workspaceBefore
    }

    /** The workspace overlay picks the path: `none` is the pass-through view, `light` the dialog. */
    private fun workspaceWith(overlay: SurveyOverlay) = WorkspaceDataHolder(
        data = WorkspaceResponseData(
            data = WorkspaceData(
                surveys = emptyList(),
                actionClasses = null,
                settings = Settings(
                    id = "test-workspace",
                    recontactDays = null,
                    clickOutsideClose = null,
                    overlay = overlay,
                    placement = null,
                    inAppSurveyBranding = null,
                    styling = null,
                ),
            ),
            expiresAt = null,
        ),
        originalResponseMap = mapOf(),
    )

    private fun inOneTurn(overlay: SurveyOverlay, block: (FragmentActivity, FormbricksFragment) -> Unit) {
        SurveyManager.workspaceDataHolder = workspaceWith(overlay)
        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                FormbricksFragment.show(activity.supportFragmentManager, "survey-one")
                block(activity, surveysIn(activity).single())
            }
        }
    }

    private fun surveysIn(activity: FragmentActivity) =
        activity.supportFragmentManager.fragments.filterIsInstance<FormbricksFragment>()

    /** Pass-through surveys put their own layer into the host's content; it must not outlive them. */
    private fun surveyLayersOn(activity: FragmentActivity): Int {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        return (0 until content.childCount).count { content.getChildAt(it) is SurveyPassthroughLayout }
    }

    /** What the framework does to a host on its way to the background, without waiting for the stop. */
    private fun saveHostState(activity: FragmentActivity) {
        InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, Bundle())
    }

    private fun closeAfterTheHostSavedItsState(overlay: SurveyOverlay) = inOneTurn(overlay) { activity, survey ->
        saveHostState(activity)
        assertTrue("the host must be past onSaveInstanceState", survey.isStateSaved)

        survey.safeDismiss()

        assertFalse("closing the survey must not close the host", activity.isFinishing)
        activity.supportFragmentManager.executePendingTransactions()
        assertTrue("the survey is dismissed", surveysIn(activity).isEmpty())
        assertEquals("no survey layer is left over the host", 0, surveyLayersOn(activity))
    }

    private fun closeInTheForeground(overlay: SurveyOverlay) = inOneTurn(overlay) { activity, survey ->
        assertFalse(survey.isStateSaved)

        survey.safeDismiss()
        activity.supportFragmentManager.executePendingTransactions()

        assertTrue("the survey is dismissed", surveysIn(activity).isEmpty())
        assertEquals("no survey layer is left over the host", 0, surveyLayersOn(activity))
        assertFalse(activity.isFinishing)
    }

    @Test
    fun aCloseAfterTheHostSavedItsStateKeepsTheHostAliveWithoutAnOverlay() =
        closeAfterTheHostSavedItsState(SurveyOverlay.NONE)

    @Test
    fun aCloseAfterTheHostSavedItsStateKeepsTheHostAliveBehindADialog() =
        closeAfterTheHostSavedItsState(SurveyOverlay.LIGHT)

    @Test
    fun aForegroundCloseDismissesASurveyWithoutAnOverlay() = closeInTheForeground(SurveyOverlay.NONE)

    @Test
    fun aForegroundCloseDismissesASurveyBehindADialog() = closeInTheForeground(SurveyOverlay.LIGHT)

    /**
     * The real thing: the host is stopped, the survey closes itself, the user comes back.
     *
     * No workspace is stored, so the view model loads nothing and the WebView has no failed load
     * to report halfway through the lifecycle changes.
     */
    @Test
    fun aSurveyClosedInTheBackgroundIsGoneWhenTheHostReturns() {
        SurveyManager.workspaceDataHolder = null
        ActivityScenario.launch(FragmentActivity::class.java).use { scenario ->
            scenario.onActivity { FormbricksFragment.show(it.supportFragmentManager, "survey-one") }

            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                val survey = surveysIn(activity).single()
                assertTrue("the host must be past onSaveInstanceState", survey.isStateSaved)

                survey.safeDismiss()

                assertFalse("closing the survey must not close the host", activity.isFinishing)
            }

            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue("the survey is gone once the host is back", surveysIn(activity).isEmpty())
                assertEquals("no survey layer is left over the host", 0, surveyLayersOn(activity))

                FormbricksFragment.show(activity.supportFragmentManager, "survey-two")
                activity.supportFragmentManager.executePendingTransactions()
                assertEquals("the host can show a survey again, and only one", 1, surveysIn(activity).size)
            }
        }
    }
}
