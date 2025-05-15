package com.formbricks.android.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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
} 