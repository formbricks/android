package com.formbricks.android.mobilecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.model.user.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MobileCoreRuntimeInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * A minimal stand-in for the server-delivered bundle, speaking bridge protocol v1.
     * Echoes enough of the payload back to prove state crosses the bridge intact.
     */
    private val stubBrain = """
        globalThis.formbricksMobileCore = {
          protocolVersion: 1,
          selectSurvey: function (payload) {
            var surveys = (payload.workspaceState.data && payload.workspaceState.data.data.surveys) || [];
            var alreadyDisplayed = (payload.userState.displays || []).length > 0;
            if (surveys.length === 0 || alreadyDisplayed) {
              return { v: 1, shouldDisplay: false, surveyId: null, delaySeconds: null, languageCode: null, reason: "stub: nothing to show" };
            }
            return {
              v: 1,
              shouldDisplay: true,
              surveyId: surveys[0].id,
              delaySeconds: 2,
              languageCode: payload.language,
              reason: "stub: action=" + payload.action + " userId=" + payload.userState.userId
            };
          }
        };
    """.trimIndent()

    private val workspaceStateJson = """{ "data": { "data": { "surveys": [ { "id": "survey_123" } ] } } }"""

    private fun createRuntime(bundleSource: String): MobileCoreRuntime? {
        val latch = CountDownLatch(1)
        var runtime: MobileCoreRuntime? = null
        MobileCoreRuntime.create(context, bundleSource) {
            runtime = it
            latch.countDown()
        }
        assertTrue("runtime creation timed out", latch.await(10, TimeUnit.SECONDS))
        return runtime
    }

    private fun selectSurvey(
        runtime: MobileCoreRuntime,
        userState: MobileCoreUserState,
        language: String = "default"
    ): MobileCoreDecision? {
        val latch = CountDownLatch(1)
        var decision: MobileCoreDecision? = null
        runtime.selectSurvey("button_clicked", workspaceStateJson, userState, language) {
            decision = it
            latch.countDown()
        }
        assertTrue("selectSurvey timed out", latch.await(10, TimeUnit.SECONDS))
        return decision
    }

    @Test
    fun runtimeInitializesWithValidBundle() {
        val runtime = createRuntime(stubBrain)
        assertNotNull(runtime)
        runtime?.destroy()
    }

    @Test
    fun runtimeRejectsBundleWithoutGlobal() {
        assertNull(createRuntime("var x = 1;"))
    }

    @Test
    fun runtimeRejectsBundleWithWrongProtocolVersion() {
        assertNull(createRuntime(stubBrain.replace("protocolVersion: 1", "protocolVersion: 2")))
    }

    @Test
    fun runtimeRejectsBundleThatFailsToEvaluate() {
        assertNull(createRuntime("this is not javascript {{{"))
    }

    @Test
    fun selectSurveyReturnsDecisionAndPassesStateThrough() {
        val runtime = createRuntime(stubBrain)!!
        val userState = MobileCoreUserState(
            userId = "user_1",
            segments = emptyList(),
            displays = emptyList(),
            responses = emptyList(),
            lastDisplayedAtMs = null
        )

        val decision = selectSurvey(runtime, userState, language = "de")

        assertNotNull(decision)
        assertEquals(true, decision?.shouldDisplay)
        assertEquals("survey_123", decision?.surveyId)
        assertEquals(2.0, decision?.delaySeconds)
        assertEquals("de", decision?.languageCode)
        assertEquals("stub: action=button_clicked userId=user_1", decision?.reason)
        runtime.destroy()
    }

    @Test
    fun selectSurveyRespectsUserStateAcrossBridge() {
        val runtime = createRuntime(stubBrain)!!
        val userState = MobileCoreUserState(
            userId = "user_1",
            segments = emptyList(),
            displays = listOf(Display(surveyId = "survey_123", createdAt = "2026-07-02T00:00:00Z")),
            responses = emptyList(),
            lastDisplayedAtMs = null
        )

        val decision = selectSurvey(runtime, userState)

        assertNotNull(decision)
        assertEquals(false, decision?.shouldDisplay)
        assertNull(decision?.surveyId)
        runtime.destroy()
    }

    @Test
    fun selectSurveyReturnsNullWhenBrainThrows() {
        val throwingBrain = """
            globalThis.formbricksMobileCore = {
              protocolVersion: 1,
              selectSurvey: function () { throw new Error("boom"); }
            };
        """.trimIndent()
        val runtime = createRuntime(throwingBrain)!!
        val userState = MobileCoreUserState(null, null, null, null, null)

        val decision = selectSurvey(runtime, userState)

        assertNull(decision)
        runtime.destroy()
    }

    @Test
    fun loaderBuildsProtocolVersionedUrl() {
        assertEquals(
            "https://app.formbricks.com/js/mobile/v1/core.umd.cjs",
            MobileCoreLoader.bundleUrl("https://app.formbricks.com/")
        )
        assertFalse(MobileCoreLoader.bundleUrl("http://localhost:3000").contains("//js"))
    }
}
