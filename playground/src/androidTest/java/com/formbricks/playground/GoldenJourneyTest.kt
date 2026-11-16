package com.formbricks.playground

import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.model.Atoms.castOrDie
import androidx.test.espresso.web.model.Atoms.script
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.clearElement
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The golden journey shared by every Formbricks SDK, against the E2E server harness.
 *
 * e2e/server/seed.sh created the survey: NPS (qa_nps) then open text (qa_feedback), triggered by
 * `qa-action` and targeted to plan = pro. e2e/server/assert-response.sh then checks the stored
 * response through the V1 API, so the run passes the same userId and feedback (e2e/android/lib.sh
 * `journey_gradle_args`, percent-encoded because the runner splits arguments on spaces). The server
 * URL and workspace are built into the playground (`-Pformbricks.appUrl`, `-Pformbricks.workspaceId`),
 * so the test reads them from the same BuildConfig MainActivity sets the SDK up with.
 */
@RunWith(AndroidJUnit4::class)
class GoldenJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val appUrl = BuildConfig.FORMBRICKS_APP_URL
    private val userId = arg("userId") ?: "qa-user-local-${System.currentTimeMillis()}"
    private val feedback = arg("feedback") ?: "Golden journey feedback"

    @Test
    fun goldenJourney() {
        assertServerReachable()

        // 1. Set up the SDK (MainActivity calls setup with the built-in URL and workspace).
        ActivityScenario.launch(MainActivity::class.java).use {
            // 2. setUserId, then setAttribute(plan = pro).
            onView(withId(R.id.user_id))
                .perform(scrollTo(), replaceText(userId), closeSoftKeyboard())
            onView(withId(R.id.set_user_id)).perform(scrollTo(), click())
            onView(withId(R.id.attribute_key)).perform(scrollTo(), replaceText("plan"))
            onView(withId(R.id.attribute_value))
                .perform(scrollTo(), replaceText("pro"), closeSoftKeyboard())
            onView(withId(R.id.set_attribute)).perform(scrollTo(), click())

            // 3. track('qa-action'), and 4. the survey targeted to plan = pro appears.
            onView(withId(R.id.action_key))
                .perform(scrollTo(), replaceText("qa-action"), closeSoftKeyboard())
            trackUntilTheSurveyOpens()

            // 5. Answer NPS 9 and the open text, finish, then close the ending card.
            eventually("NPS option 9") {
                onWebView().withElement(findElement(Locator.XPATH, NPS_9)).perform(webClick())
            }
            eventually("NPS 9 to be selected") {
                onWebView().check(
                    webMatches(script(READ_NPS, castOrDie(String::class.java)), equalTo("9"))
                )
            }
            eventually("Next") {
                onWebView()
                    .withElement(findElement(Locator.CSS_SELECTOR, primaryButton(0)))
                    .perform(webClick())
            }
            eventually("the open-text question") {
                onWebView()
                    .withElement(findElement(Locator.ID, FEEDBACK_INPUT_ID))
                    .perform(clearElement())
                    .perform(webKeys(feedback))
            }
            onWebView().check(
                webMatches(script(READ_FEEDBACK, castOrDie(String::class.java)), equalTo(feedback))
            )
            eventually("Finish") {
                onWebView()
                    .withElement(findElement(Locator.CSS_SELECTOR, primaryButton(1)))
                    .perform(webClick())
            }
            // The ending card renders its headline only once the server accepted the response.
            eventually("the Thanks! card", RESPONSE_TIMEOUT_MS) {
                onWebView()
                    .withElement(findElement(Locator.XPATH, THANKS_HEADLINE))
                    .check(webMatches(getText(), containsString("Thanks!")))
            }
            closeTheSurvey()
        }
    }

    /** Fails fast, with the reason, when the emulator cannot reach the server over TLS. */
    private fun assertServerReachable() {
        val url = URL("$appUrl/health/ready")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GET $url returned ${connection.responseCode}"
            }
        } catch (e: java.io.IOException) {
            throw AssertionError(
                "Cannot reach $url from the emulator. Is the TLS proxy up and its CA installed " +
                    "(e2e/android/https-proxy.sh, install-ca.sh)?",
                e
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Taps "Track action" until the survey opens. An early tap can land before the SDK has fetched
     * the workspace (it then logs "unknown action" and drops it), so a tap that opens nothing is
     * repeated, as a person would.
     */
    private fun trackUntilTheSurveyOpens() {
        val deadline = SystemClock.uptimeMillis() + SURVEY_OPEN_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            onView(withId(R.id.track_action)).perform(scrollTo(), click())
            val tapDeadline = SystemClock.uptimeMillis() + TRACK_RETRY_INTERVAL_MS
            while (SystemClock.uptimeMillis() < tapDeadline) {
                if (surveyIsOpen()) return
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
        }
        throw AssertionError(
            "No survey opened within ${SURVEY_OPEN_TIMEOUT_MS / 1000}s of tracking qa-action"
        )
    }

    /**
     * Taps the close button until the survey is gone. Closing dismisses the WebView the tap runs
     * in, so the tap itself may report an error even though it worked; only the outcome counts.
     */
    private fun closeTheSurvey() {
        eventually("the survey to close") {
            if (surveyIsOpen()) {
                runCatching {
                    onWebView()
                        .withElement(findElement(Locator.CSS_SELECTOR, CLOSE_BUTTON))
                        .perform(webClick())
                }
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
            onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
        }
    }

    private fun surveyIsOpen(): Boolean = runCatching {
        onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed()))
    }.isSuccess

    private fun arg(name: String): String? =
        args.getString(name)?.let { URLDecoder.decode(it, "UTF-8") }

    /** Retries [block] until it stops throwing: the survey renders and animates asynchronously. */
    private fun eventually(what: String, timeoutMs: Long = STEP_TIMEOUT_MS, block: () -> Unit) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                block()
                return
            } catch (e: RuntimeException) {
                lastError = e
            } catch (e: AssertionError) {
                lastError = e
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Timed out after ${timeoutMs / 1000}s waiting for $what", lastError)
    }

    private companion object {
        // Selectors: the survey renderer's DOM, the same in every SDK's WebView.
        const val NPS_9 = "//input[@name='qa_nps' and @value='9']/parent::label"

        // The value of the selected NPS option, or "" when none is selected.
        const val READ_NPS =
            "var o = document.querySelector(\"label[data-checked='true'] > " +
                "input[name='qa_nps']\"); return o ? o.value : '';"
        const val FEEDBACK_INPUT_ID = "qa_feedback-input"
        const val READ_FEEDBACK = "return document.getElementById('$FEEDBACK_INPUT_ID').value;"
        const val THANKS_HEADLINE =
            "//*[@data-fb-part='headline'][contains(normalize-space(.), 'Thanks!')]"
        const val CLOSE_BUTTON = "[data-fb-part='button-close']"

        fun primaryButton(card: Int) = "#questionCard-$card [data-fb-part='button-primary']"

        const val NETWORK_TIMEOUT_MS = 15_000
        const val SURVEY_OPEN_TIMEOUT_MS = 120_000L
        const val TRACK_RETRY_INTERVAL_MS = 10_000L
        const val STEP_TIMEOUT_MS = 30_000L
        const val RESPONSE_TIMEOUT_MS = 60_000L
        const val POLL_INTERVAL_MS = 500L
    }
}
