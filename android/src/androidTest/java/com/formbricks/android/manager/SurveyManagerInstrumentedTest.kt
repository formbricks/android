package com.formbricks.android.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.formbricks.android.model.environment.*
import com.formbricks.android.model.user.Display
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SurveyManagerInstrumentedTest {
    @Before
    fun setup() {
        // Reset UserManager state before each test
        UserManager.lastDisplayedAt = null
    }

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

    @Test
    fun testFilterSurveysBasedOnRecontactDays() {
        // Test case 1: lastDisplayedAt is null
        val survey1 = createMockSurvey(recontactDays = 7.0)
        val result1 = SurveyManager.filterSurveysBasedOnRecontactDays(listOf(survey1), null)
        assertEquals(1, result1.size)
        assertEquals(survey1.id, result1[0].id)

        // Test case 2: recontactDays is null
        val survey2 = createMockSurvey(recontactDays = null)
        val result2 = SurveyManager.filterSurveysBasedOnRecontactDays(listOf(survey2), 7)
        assertEquals(1, result2.size)
        assertEquals(survey2.id, result2[0].id)

        // Test case 3: Survey should be shown (enough days have passed)
        val survey3 = createMockSurvey(recontactDays = 7.0)
        val oldDate = Date(System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000) // 8 days ago
        UserManager.lastDisplayedAt = oldDate
        val result3 = SurveyManager.filterSurveysBasedOnRecontactDays(listOf(survey3), null)
        assertEquals(1, result3.size)
        assertEquals(survey3.id, result3[0].id)

        // Test case 4: Survey should not be shown (not enough days have passed)
        val survey4 = createMockSurvey(recontactDays = 7.0)
        val recentDate = Date(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000) // 3 days ago
        UserManager.lastDisplayedAt = recentDate
        val result4 = SurveyManager.filterSurveysBasedOnRecontactDays(listOf(survey4), null)
        assertTrue(result4.isEmpty())

        // Test case 5: Test with multiple surveys
        val survey5 = createMockSurvey(recontactDays = 7.0)
        val survey6 = createMockSurvey(recontactDays = 3.0)
        val testDate = Date(System.currentTimeMillis() - 4 * 24 * 60 * 60 * 1000) // 4 days ago
        UserManager.lastDisplayedAt = testDate
        val result5 = SurveyManager.filterSurveysBasedOnRecontactDays(listOf(survey5, survey6), null)
        assertEquals(1, result5.size)
        assertEquals(survey6.id, result5[0].id)
    }

    private fun createTestSurvey(
        id: String = "test",
        displayOption: String? = null,
        displayLimit: Double? = null,
        recontactDays: Double? = null
    ): Survey {
        return Survey(
            id = id,
            name = "Test Survey",
            triggers = null,
            recontactDays = recontactDays,
            displayLimit = displayLimit,
            delay = null,
            displayPercentage = null,
            displayOption = displayOption,
            segment = null,
            styling = null,
            languages = null
        )
    }

    private fun createMockSurvey(recontactDays: Double?): Survey {
        return Survey(
            id = "mockSurvey",
            name = "Mock Survey",
            triggers = null,
            recontactDays = recontactDays,
            displayLimit = null,
            delay = null,
            displayPercentage = null,
            displayOption = null,
            segment = null,
            styling = null,
            languages = null
        )
    }
} 