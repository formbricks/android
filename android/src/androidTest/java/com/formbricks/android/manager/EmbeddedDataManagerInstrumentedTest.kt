package com.formbricks.android.manager

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.Formbricks
import com.formbricks.android.MockFormbricksApiService
import com.formbricks.android.api.FormbricksApi
import com.formbricks.android.extensions.dateString
import com.formbricks.android.model.embeddeddata.EmbeddedDataValue
import com.formbricks.android.network.queue.UpdateQueue
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The Embedded Data bag (ENG-1844 / ENG-2472): host-supplied context attached to future responses
 * without tying it to a trigger. These pin the contract all four SDKs share, so a divergence here is
 * a divergence from the JS SDK too.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddedDataManagerInstrumentedTest {

    private val workspaceId = "workspaceId"
    private val appUrl = "https://example.com"

    @Before
    fun setUp() {
        Formbricks.applicationContext = InstrumentationRegistry.getInstrumentation().targetContext
        Formbricks.isInitialized = false
        // Assigned directly rather than through `Formbricks.setup`: these tests need no workspace
        // fetch, and running setup here would write the workspace cache that other classes assert
        // on. `workspaceId`/`appUrl` are lateinit, so a queued update touching them must not find
        // them unset - an exception on the UpdateQueue's timer thread cancels that Timer for the
        // whole process, which would take later tests down with it.
        Formbricks.appUrl = appUrl
        Formbricks.workspaceId = workspaceId
        FormbricksApi.service = MockFormbricksApiService()
        UserManager.logout()
        UpdateQueue.reset()
        EmbeddedDataManager.clear()
    }

    @After
    fun tearDown() {
        // Leave nothing running for the next class: logout cancels the sync task and resets the
        // queue, so a debounced commit from an identity test cannot fire during someone else's.
        UserManager.logout()
        UpdateQueue.reset()
        Formbricks.isInitialized = false
        EmbeddedDataManager.clear()
    }

    /**
     * Seeds a persisted identity, the way a previous app session would have left one.
     *
     * Not `Formbricks.setUserId`: that only enqueues into the debounced [UpdateQueue], so
     * [UserManager.userId] stays null until a network sync lands, and a test driving it that way
     * would silently take the first-identification branch and pass for the wrong reason. Writing the
     * same key the getter reads is exact, needs no timer or request, and models the honest scenario
     * - the app relaunches already identified, then a different user signs in.
     *
     * The key names are `UserManager`'s own private constants, repeated here because that is the
     * storage contract this seeds; [assertEquals] below fails loudly if either ever changes.
     */
    private fun seedPersistedUserId(userId: String) {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("formbricks_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("userIdKey", userId)
            .commit()
        assertEquals(userId, UserManager.userId)
    }

    /** The snapshot as plain strings — `asString` renders numbers and booleans too, so one
     *  comparison shape covers every value type without quoting noise. */
    private fun snapshotMap(): Map<String, String> =
        EmbeddedDataManager.snapshot().entrySet().associate { it.key to it.value.asString }

    // region Merge semantics

    @Test
    fun mergesInsteadOfReplacing() {
        Formbricks.setEmbeddedData(
            mapOf(
                "plan" to EmbeddedDataValue.string("pro"),
                "screen" to EmbeddedDataValue.string("product")
            )
        )
        Formbricks.setEmbeddedData(mapOf("screen" to EmbeddedDataValue.string("checkout")))

        assertEquals(mapOf("plan" to "pro", "screen" to "checkout"), snapshotMap())
    }

    @Test
    fun nullRemovesTheKey() {
        Formbricks.setEmbeddedData(
            mapOf(
                "plan" to EmbeddedDataValue.string("pro"),
                "screen" to EmbeddedDataValue.string("product")
            )
        )
        Formbricks.setEmbeddedData(mapOf("screen" to null))

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
    }

    @Test
    fun lastWriteWinsPerKey() {
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("free")))
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
    }

    @Test
    fun omittedKeysAreUntouched() {
        // Kotlin has no `undefined`, so "skip this field" is spelled by leaving the key out - and
        // that must not disturb what an earlier call set. `null` is the explicit "remove" spelling.
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))
        Formbricks.setEmbeddedData(mapOf("seats" to EmbeddedDataValue.number(4.0)))

        assertEquals(mapOf("plan" to "pro", "seats" to "4.0"), snapshotMap())
    }

    // endregion

    // region Clearing

    @Test
    fun clearOneKeyLeavesTheRest() {
        Formbricks.setEmbeddedData(
            mapOf(
                "plan" to EmbeddedDataValue.string("pro"),
                "screen" to EmbeddedDataValue.string("product")
            )
        )

        Formbricks.clearEmbeddedData("screen")

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
    }

    @Test
    fun clearingAnUnsetKeyIsANoOp() {
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        Formbricks.clearEmbeddedData("neverSet")

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
    }

    @Test
    fun clearEverything() {
        Formbricks.setEmbeddedData(
            mapOf(
                "plan" to EmbeddedDataValue.string("pro"),
                "screen" to EmbeddedDataValue.string("product")
            )
        )

        Formbricks.clearEmbeddedData()

        assertTrue(snapshotMap().isEmpty())
    }

    // endregion

    // region Value types

    @Test
    fun everyScalarSurvivesInItsJsonForm() {
        val signedUpAt = Date(1_787_000_000_000L)

        Formbricks.setEmbeddedData(
            mapOf(
                "plan" to EmbeddedDataValue.string("pro"),
                "seats" to EmbeddedDataValue.number(25.0),
                "isTrial" to EmbeddedDataValue.boolean(false),
                "signedUpAt" to EmbeddedDataValue.date(signedUpAt)
            )
        )

        val json = EmbeddedDataManager.snapshot()
        assertEquals("pro", json.get("plan").asString)
        assertEquals(25.0, json.get("seats").asDouble, 0.0)
        assertFalse(json.get("isTrial").asBoolean)
        // ISO 8601 is what the renderer's ingest contract accepts for a `date` field.
        assertEquals(signedUpAt.dateString(), json.get("signedUpAt").asString)
    }

    @Test
    fun aSnapshotIsAlwaysParseableJson() {
        // The snapshot is embedded in the survey WebView's payload and parsed there with
        // JSON.parse. If it were ever malformed, the failure would not be a missing field - it
        // would be no survey at all.
        Formbricks.setEmbeddedData(
            mapOf(
                "plan" to EmbeddedDataValue.string("pro"),
                "quote" to EmbeddedDataValue.string("he said \"hi\""),
                "seats" to EmbeddedDataValue.number(25.0),
                "isTrial" to EmbeddedDataValue.boolean(true),
                "signedUpAt" to EmbeddedDataValue.date(Date())
            )
        )

        val parsed = JsonParser.parseString(EmbeddedDataManager.snapshot().toString())
        assertTrue(parsed.isJsonObject)
        assertEquals("he said \"hi\"", parsed.asJsonObject.get("quote").asString)
    }

    @Test
    fun aNonFiniteNumberIsSkippedRatherThanCostingTheSurvey() {
        // THE guard: a bare NaN or Infinity is not valid JSON, so JSON.parse in the WebView would
        // throw and no survey would render. Dropping the key is the only safe answer.
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        Formbricks.setEmbeddedData(
            mapOf(
                "broken" to EmbeddedDataValue.number(Double.NaN),
                "alsoBroken" to EmbeddedDataValue.number(Double.POSITIVE_INFINITY)
            )
        )

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
        assertTrue(JsonParser.parseString(EmbeddedDataManager.snapshot().toString()).isJsonObject)
    }

    // endregion

    // region Lifetime

    @Test
    fun snapshotIsDetachedFromLaterWrites() {
        // What "a value set after a survey is displayed does not change that response" rests on:
        // the WebView payload holds this object for the life of the survey.
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))
        val snapshot = EmbeddedDataManager.snapshot()

        Formbricks.setEmbeddedData(
            mapOf(
                "plan" to EmbeddedDataValue.string("enterprise"),
                "extra" to EmbeddedDataValue.string("later")
            )
        )

        assertEquals("pro", snapshot.get("plan").asString)
        assertFalse(snapshot.has("extra"))
    }

    @Test
    fun worksBeforeSetup() {
        // Deliberately unlike the other public methods: a host that pushes context at launch must
        // not have the value dropped because initialization had not finished yet.
        assertFalse(Formbricks.isInitialized)

        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
    }

    @Test
    fun isNotPersisted() {
        // A cold start begins empty. Nothing host-supplied may reach SharedPreferences, where it
        // would outlive the session and blur the Embedded Data / contact-attribute boundary.
        val marker = "fb-embedded-probe-${System.nanoTime()}"
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        Formbricks.setEmbeddedData(mapOf("probe" to EmbeddedDataValue.string(marker)))

        val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
        val files = prefsDir.listFiles() ?: emptyArray()
        for (file in files) {
            val contents = runCatching { file.readText() }.getOrDefault("")
            assertFalse(
                "${file.name} holds Embedded Data - the bag must stay in memory",
                contents.contains(marker)
            )
        }
    }

    // endregion

    // region Identity changes

    @Test
    fun switchingUserClearsTheBag() {
        Formbricks.isInitialized = true
        seedPersistedUserId("user-a")
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        Formbricks.setUserId("user-b")

        assertTrue(snapshotMap().isEmpty())
    }

    @Test
    fun firstIdentificationKeepsTheBag() {
        // The host pushes context before it knows who the user is - that is the normal order, and
        // clearing here would throw away the value the API exists to carry.
        Formbricks.isInitialized = true
        // `userId` is persisted, so an id left by an earlier test would make this take the switch
        // branch. setUp() logs out, so this only pins the precondition the assertion depends on.
        assertNull(UserManager.userId)
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        Formbricks.setUserId("user-a")

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
    }

    @Test
    fun settingTheSameUserIdKeepsTheBag() {
        Formbricks.isInitialized = true
        seedPersistedUserId("user-a")
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        Formbricks.setUserId("user-a")

        assertEquals(mapOf("plan" to "pro"), snapshotMap())
    }

    @Test
    fun logoutClearsTheBag() {
        Formbricks.isInitialized = true
        Formbricks.setEmbeddedData(mapOf("plan" to EmbeddedDataValue.string("pro")))

        Formbricks.logout()

        assertTrue(snapshotMap().isEmpty())
    }

    // endregion

    @Test
    fun concurrentWritesDoNotCorruptTheBag() {
        // The host may call from any thread while the main thread reads the snapshot to present a
        // survey. Without the lock this trips ConcurrentModificationException.
        val threads = 8
        val perThread = 200
        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)

        repeat(threads) { threadIndex ->
            pool.execute {
                repeat(perThread) { i ->
                    Formbricks.setEmbeddedData(
                        mapOf("key$threadIndex" to EmbeddedDataValue.number(i.toDouble()))
                    )
                    EmbeddedDataManager.snapshot()
                }
                done.countDown()
            }
        }

        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(threads, snapshotMap().size)
    }
}
