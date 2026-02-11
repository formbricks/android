package com.formbricks.android.webview

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.formbricks.android.Formbricks
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.environment.EnvironmentDataHolder
import com.formbricks.android.model.environment.EnvironmentResponseData
import com.formbricks.android.model.environment.EnvironmentData
import com.formbricks.android.model.environment.Project
import com.formbricks.android.model.environment.Survey
import com.formbricks.android.model.environment.SurveyOverlay
import com.formbricks.android.model.environment.SurveyProjectOverwrites
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormbricksViewModelInstrumentedTest {
    @Before
    fun setup() {
        // Set up static singletons with minimal values
        Formbricks.appUrl = "https://test.formbricks.com"
        Formbricks.environmentId = "env123"
        Formbricks.language = "en"
        // Use reflection to set private contactId
        val contactIdField = UserManager::class.java.getDeclaredField("backingContactId")
        contactIdField.isAccessible = true
        contactIdField.set(UserManager, "contact123")
    }

    @Test
    fun testGetJson_minimalEnvironment() {
        // Minimal survey and environment
        val surveyId = "survey1"
        val survey = Survey(
            id = surveyId,
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = null
        )
        val project = Project(
            id = "proj1",
            recontactDays = null,
            clickOutsideClose = null,
            overlay = null,
            placement = null,
            inAppSurveyBranding = null,
            styling = null
        )
        val envData = EnvironmentData(
            surveys = listOf(survey),
            actionClasses = null,
            project = project
        )
        val envResponseData = EnvironmentResponseData(
            data = envData,
            expiresAt = null
        )
        val envHolder = EnvironmentDataHolder(
            data = envResponseData,
            originalResponseMap = mapOf()
        )
        val viewModel = FormbricksViewModel()
        val json = viewModel.javaClass.getDeclaredMethod("getJson", EnvironmentDataHolder::class.java, String::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, envHolder, surveyId) as String
        // Check that the output JSON string contains expected keys/values
        assertTrue(json.contains("\"survey\""))
        assertTrue(json.contains("\"isBrandingEnabled\":true"))
        assertTrue(json.contains("https://test.formbricks.com"))
        assertTrue(json.contains("env123"))
        assertTrue(json.contains("contact123"))
        assertTrue(json.contains("\"languageCode\":\"default\""))
        // defaults: clickOutside=false, overlay="none" when both project and survey are null
        assertTrue(json.contains("\"clickOutside\":false"))
        assertTrue(json.contains("\"overlay\":\"none\""))
    }

    // region clickOutside tests

    @Test
    fun testGetJson_clickOutside_projectTrue_noOverwrites_returnsTrue() {
        val json = invokeGetJson(
            projectClickOutsideClose = true,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"clickOutside\":true"))
    }

    @Test
    fun testGetJson_clickOutside_projectFalse_noOverwrites_returnsFalse() {
        val json = invokeGetJson(
            projectClickOutsideClose = false,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"clickOutside\":false"))
    }

    @Test
    fun testGetJson_clickOutside_projectNull_noOverwrites_returnsFalse() {
        val json = invokeGetJson(
            projectClickOutsideClose = null,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"clickOutside\":false"))
    }

    @Test
    fun testGetJson_clickOutside_surveyOverwriteTrue_overridesProjectFalse() {
        val json = invokeGetJson(
            projectClickOutsideClose = false,
            surveyOverwrites = SurveyProjectOverwrites(clickOutsideClose = true)
        )
        assertTrue(json.contains("\"clickOutside\":true"))
    }

    @Test
    fun testGetJson_clickOutside_surveyOverwriteFalse_overridesProjectTrue() {
        val json = invokeGetJson(
            projectClickOutsideClose = true,
            surveyOverwrites = SurveyProjectOverwrites(clickOutsideClose = false)
        )
        assertTrue(json.contains("\"clickOutside\":false"))
    }

    @Test
    fun testGetJson_clickOutside_surveyOverwriteNull_fallsBackToProject() {
        val json = invokeGetJson(
            projectClickOutsideClose = true,
            surveyOverwrites = SurveyProjectOverwrites(clickOutsideClose = null)
        )
        assertTrue(json.contains("\"clickOutside\":true"))
    }

    // endregion

    // region overlay tests

    @Test
    fun testGetJson_overlay_projectDark_noOverwrites_returnsDark() {
        val json = invokeGetJson(
            projectOverlay = SurveyOverlay.DARK,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"overlay\":\"dark\""))
    }

    @Test
    fun testGetJson_overlay_projectLight_noOverwrites_returnsLight() {
        val json = invokeGetJson(
            projectOverlay = SurveyOverlay.LIGHT,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"overlay\":\"light\""))
    }

    @Test
    fun testGetJson_overlay_projectNone_noOverwrites_returnsNone() {
        val json = invokeGetJson(
            projectOverlay = SurveyOverlay.NONE,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"overlay\":\"none\""))
    }

    @Test
    fun testGetJson_overlay_projectNull_noOverwrites_returnsNone() {
        val json = invokeGetJson(
            projectOverlay = null,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"overlay\":\"none\""))
    }

    @Test
    fun testGetJson_overlay_surveyOverwriteDark_overridesProjectNone() {
        val json = invokeGetJson(
            projectOverlay = SurveyOverlay.NONE,
            surveyOverwrites = SurveyProjectOverwrites(overlay = SurveyOverlay.DARK)
        )
        assertTrue(json.contains("\"overlay\":\"dark\""))
    }

    @Test
    fun testGetJson_overlay_surveyOverwriteLight_overridesProjectDark() {
        val json = invokeGetJson(
            projectOverlay = SurveyOverlay.DARK,
            surveyOverwrites = SurveyProjectOverwrites(overlay = SurveyOverlay.LIGHT)
        )
        assertTrue(json.contains("\"overlay\":\"light\""))
    }

    @Test
    fun testGetJson_overlay_surveyOverwriteNone_overridesProjectDark() {
        val json = invokeGetJson(
            projectOverlay = SurveyOverlay.DARK,
            surveyOverwrites = SurveyProjectOverwrites(overlay = SurveyOverlay.NONE)
        )
        assertTrue(json.contains("\"overlay\":\"none\""))
    }

    @Test
    fun testGetJson_overlay_surveyOverwriteNull_fallsBackToProject() {
        val json = invokeGetJson(
            projectOverlay = SurveyOverlay.DARK,
            surveyOverwrites = SurveyProjectOverwrites(overlay = null)
        )
        assertTrue(json.contains("\"overlay\":\"dark\""))
    }

    // endregion

    // region combined clickOutside + overlay tests

    @Test
    fun testGetJson_clickOutsideAndOverlay_bothFromSurveyOverwrites() {
        val json = invokeGetJson(
            projectClickOutsideClose = true,
            projectOverlay = SurveyOverlay.NONE,
            surveyOverwrites = SurveyProjectOverwrites(
                clickOutsideClose = false,
                overlay = SurveyOverlay.DARK
            )
        )
        assertTrue(json.contains("\"clickOutside\":false"))
        assertTrue(json.contains("\"overlay\":\"dark\""))
    }

    @Test
    fun testGetJson_clickOutsideAndOverlay_bothFromProject() {
        val json = invokeGetJson(
            projectClickOutsideClose = true,
            projectOverlay = SurveyOverlay.LIGHT,
            surveyOverwrites = null
        )
        assertTrue(json.contains("\"clickOutside\":true"))
        assertTrue(json.contains("\"overlay\":\"light\""))
    }

    // endregion

    // region matchedSurvey null tests (surveys=null forces matchedSurvey to null)

    @Test
    fun testGetJson_noMatchedSurvey_clickOutsideFallsBackToProject() {
        val json = invokeGetJsonWithNoSurveys(
            projectClickOutsideClose = true,
            projectOverlay = SurveyOverlay.NONE
        )
        assertTrue(json.contains("\"clickOutside\":true"))
    }

    @Test
    fun testGetJson_noMatchedSurvey_clickOutsideProjectNull_returnsFalse() {
        val json = invokeGetJsonWithNoSurveys(
            projectClickOutsideClose = null,
            projectOverlay = SurveyOverlay.NONE
        )
        assertTrue(json.contains("\"clickOutside\":false"))
    }

    @Test
    fun testGetJson_noMatchedSurvey_overlayFallsBackToProject() {
        val json = invokeGetJsonWithNoSurveys(
            projectClickOutsideClose = null,
            projectOverlay = SurveyOverlay.DARK
        )
        assertTrue(json.contains("\"overlay\":\"dark\""))
    }

    @Test
    fun testGetJson_noMatchedSurvey_overlayProjectNull_returnsNone() {
        val json = invokeGetJsonWithNoSurveys(
            projectClickOutsideClose = null,
            projectOverlay = null
        )
        assertTrue(json.contains("\"overlay\":\"none\""))
    }

    @Test
    fun testGetJson_noMatchedSurvey_bothFallBackToProject() {
        val json = invokeGetJsonWithNoSurveys(
            projectClickOutsideClose = true,
            projectOverlay = SurveyOverlay.LIGHT
        )
        assertTrue(json.contains("\"clickOutside\":true"))
        assertTrue(json.contains("\"overlay\":\"light\""))
    }

    // endregion

    // region helpers

    private fun invokeGetJson(
        projectClickOutsideClose: Boolean? = null,
        projectOverlay: SurveyOverlay? = null,
        surveyOverwrites: SurveyProjectOverwrites? = null
    ): String {
        val surveyId = "survey1"
        val survey = Survey(
            id = surveyId,
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = null,
            projectOverwrites = surveyOverwrites
        )
        val project = Project(
            id = "proj1",
            recontactDays = null,
            clickOutsideClose = projectClickOutsideClose,
            overlay = projectOverlay,
            placement = null,
            inAppSurveyBranding = null,
            styling = null
        )
        val envData = EnvironmentData(
            surveys = listOf(survey),
            actionClasses = null,
            project = project
        )
        val envResponseData = EnvironmentResponseData(
            data = envData,
            expiresAt = null
        )
        val envHolder = EnvironmentDataHolder(
            data = envResponseData,
            originalResponseMap = mapOf()
        )
        return callGetJson(envHolder, surveyId)
    }

    /**
     * Creates an environment with surveys=null so that matchedSurvey resolves to null.
     * This forces clickOutside and overlay to fall through entirely to project-level values,
     * covering the bytecode branches where matchedSurvey is null.
     */
    private fun invokeGetJsonWithNoSurveys(
        projectClickOutsideClose: Boolean? = null,
        projectOverlay: SurveyOverlay? = null
    ): String {
        val project = Project(
            id = "proj1",
            recontactDays = null,
            clickOutsideClose = projectClickOutsideClose,
            overlay = projectOverlay,
            placement = null,
            inAppSurveyBranding = null,
            styling = null
        )
        val envData = EnvironmentData(
            surveys = null,
            actionClasses = null,
            project = project
        )
        val envResponseData = EnvironmentResponseData(
            data = envData,
            expiresAt = null
        )
        val envHolder = EnvironmentDataHolder(
            data = envResponseData,
            originalResponseMap = mapOf()
        )
        return callGetJson(envHolder, "any-survey-id")
    }

    private fun callGetJson(envHolder: EnvironmentDataHolder, surveyId: String): String {
        val viewModel = FormbricksViewModel()
        return viewModel.javaClass.getDeclaredMethod(
            "getJson",
            EnvironmentDataHolder::class.java,
            String::class.java
        )
            .apply { isAccessible = true }
            .invoke(viewModel, envHolder, surveyId) as String
    }

    // endregion
}