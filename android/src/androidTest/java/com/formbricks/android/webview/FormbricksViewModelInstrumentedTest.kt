package com.formbricks.android.webview

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.formbricks.android.Formbricks
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.environment.EnvironmentDataHolder
import com.formbricks.android.model.environment.EnvironmentResponseData
import com.formbricks.android.model.environment.EnvironmentData
import com.formbricks.android.model.environment.Project
import com.formbricks.android.model.environment.Survey
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
            darkOverlay = null,
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
    }
} 