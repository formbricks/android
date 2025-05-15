package com.formbricks.android.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.formbricks.android.Formbricks
import com.formbricks.android.model.environment.*
import com.formbricks.android.model.user.Display
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SurveyManagerInstrumentedTest {
    @Test
    fun testShouldDisplayBasedOnPercentage_null_returnsTrue() {
        assertTrue(SurveyManager.invokeShouldDisplayBasedOnPercentage(null))
    }

    @Test
    fun testShouldDisplayBasedOnPercentage_zero_returnsFalse() {
        repeat(10) {
            assertFalse(SurveyManager.invokeShouldDisplayBasedOnPercentage(0.0))
        }
    }

    @Test
    fun testShouldDisplayBasedOnPercentage_hundred_returnsTrue() {
        repeat(10) {
            assertTrue(SurveyManager.invokeShouldDisplayBasedOnPercentage(100.0))
        }
    }

    @Test
    fun testShouldDisplayBasedOnPercentage_fifty_isReasonable() {
        var trueCount = 0
        repeat(1000) {
            if (SurveyManager.invokeShouldDisplayBasedOnPercentage(50.0)) trueCount++
        }
        assertTrue(trueCount in 400..600)
    }

    @Test
    fun testGetLanguageCode_nullLanguage_returnsDefault() {
        val survey = Survey(
            id = "test",
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = listOf(
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "en-id",
                        code = "en",
                        alias = "English",
                        projectId = "test-project"
                    )
                )
            )
        )
        assertEquals("default", SurveyManager.getLanguageCode(survey, null))
    }

    @Test
    fun testGetLanguageCode_emptyLanguage_returnsDefault() {
        val survey = Survey(
            id = "test",
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = listOf(
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "en-id",
                        code = "en",
                        alias = "English",
                        projectId = "test-project"
                    )
                )
            )
        )
        assertEquals("default", SurveyManager.getLanguageCode(survey, ""))
    }

    @Test
    fun testGetLanguageCode_explicitDefault_returnsDefault() {
        val survey = Survey(
            id = "test",
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = listOf(
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "en-id",
                        code = "en",
                        alias = "English",
                        projectId = "test-project"
                    )
                )
            )
        )
        assertEquals("default", SurveyManager.getLanguageCode(survey, "default"))
    }

    @Test
    fun testGetLanguageCode_matchByCode_returnsCode() {
        val survey = Survey(
            id = "test",
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = listOf(
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "en-id",
                        code = "en",
                        alias = "English",
                        projectId = "test-project"
                    )
                ),
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "de-id",
                        code = "de",
                        alias = "German",
                        projectId = "test-project"
                    )
                )
            )
        )
        assertEquals("de", SurveyManager.getLanguageCode(survey, "de"))
    }

    @Test
    fun testGetLanguageCode_matchByAlias_returnsCode() {
        val survey = Survey(
            id = "test",
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = listOf(
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "en-id",
                        code = "en",
                        alias = "English",
                        projectId = "test-project"
                    )
                ),
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "de-id",
                        code = "de",
                        alias = "German",
                        projectId = "test-project"
                    )
                )
            )
        )
        assertEquals("de", SurveyManager.getLanguageCode(survey, "German"))
    }

    @Test
    fun testGetLanguageCode_disabledLanguage_returnsNull() {
        val survey = Survey(
            id = "test",
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = listOf(
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "en-id",
                        code = "en",
                        alias = "English",
                        projectId = "test-project"
                    )
                ),
                SurveyLanguage(
                    enabled = false,
                    default = false,
                    language = LanguageDetail(
                        id = "de-id",
                        code = "de",
                        alias = "German",
                        projectId = "test-project"
                    )
                )
            )
        )
        assertNull(SurveyManager.getLanguageCode(survey, "de"))
    }

    @Test
    fun testGetLanguageCode_defaultLanguage_returnsDefault() {
        val survey = Survey(
            id = "test",
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = listOf(
                SurveyLanguage(
                    enabled = true,
                    default = true,
                    language = LanguageDetail(
                        id = "en-id",
                        code = "en",
                        alias = "English",
                        projectId = "test-project"
                    )
                ),
                SurveyLanguage(
                    enabled = true,
                    default = false,
                    language = LanguageDetail(
                        id = "de-id",
                        code = "de",
                        alias = "German",
                        projectId = "test-project"
                    )
                )
            )
        )
        assertEquals("default", SurveyManager.getLanguageCode(survey, "en"))
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_respondMultiple_alwaysReturnsSurvey() {
        val survey = createTestSurvey(displayOption = "respondMultiple")
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = listOf(Display("test", "2024-03-20T12:00:00Z")),
            responses = listOf("test")
        )
        assertEquals(1, result.size)
        assertEquals(survey.id, result[0].id)
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_displayOnce_withDisplay_returnsEmpty() {
        val survey = createTestSurvey(displayOption = "displayOnce")
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = listOf(Display(survey.id, "2024-03-20T12:00:00Z")),
            responses = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_displayOnce_withoutDisplay_returnsSurvey() {
        val survey = createTestSurvey(displayOption = "displayOnce")
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = emptyList(),
            responses = emptyList()
        )
        assertEquals(1, result.size)
        assertEquals(survey.id, result[0].id)
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_displayMultiple_withResponse_returnsEmpty() {
        val survey = createTestSurvey(displayOption = "displayMultiple")
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = emptyList(),
            responses = listOf(survey.id)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_displayMultiple_withoutResponse_returnsSurvey() {
        val survey = createTestSurvey(displayOption = "displayMultiple")
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = emptyList(),
            responses = emptyList()
        )
        assertEquals(1, result.size)
        assertEquals(survey.id, result[0].id)
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_displaySome_withResponse_returnsEmpty() {
        val survey = createTestSurvey(displayOption = "displaySome", displayLimit = 2.0)
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = emptyList(),
            responses = listOf(survey.id)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_displaySome_belowLimit_returnsSurvey() {
        val survey = createTestSurvey(displayOption = "displaySome", displayLimit = 2.0)
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = listOf(Display(survey.id, "2024-03-20T12:00:00Z")),
            responses = emptyList()
        )
        assertEquals(1, result.size)
        assertEquals(survey.id, result[0].id)
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_displaySome_atLimit_returnsEmpty() {
        val survey = createTestSurvey(displayOption = "displaySome", displayLimit = 1.0)
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = listOf(Display(survey.id, "2024-03-20T12:00:00Z")),
            responses = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun testFilterSurveysBasedOnDisplayType_invalidOption_returnsEmpty() {
        val survey = createTestSurvey(displayOption = "invalid")
        val result = SurveyManager.filterSurveysBasedOnDisplayType(
            surveys = listOf(survey),
            displays = emptyList(),
            responses = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    private fun createTestSurvey(
        id: String = "test",
        displayOption: String? = null,
        displayLimit: Double? = null
    ): Survey {
        return Survey(
            id = id,
            name = "Test Survey",
            triggers = null,
            recontactDays = null,
            displayLimit = displayLimit,
            delay = null,
            displayPercentage = null,
            displayOption = displayOption,
            segment = null,
            styling = null,
            languages = null
        )
    }
} 