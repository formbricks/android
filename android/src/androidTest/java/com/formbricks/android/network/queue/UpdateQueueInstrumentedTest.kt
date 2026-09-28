package com.formbricks.android.network.queue

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.formbricks.android.Formbricks
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.user.AttributeValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives [UpdateQueue] without a network. The sync seam is swapped for a recorder, the debounce
 * and the wait timeout are shortened where a test needs them, and [UpdateQueue.syncDidFinish] is
 * called by hand to stand in for the response landing.
 */
@RunWith(AndroidJUnit4::class)
class UpdateQueueInstrumentedTest {

    /** Stands in for `UserManager.syncUser`: records the request instead of sending it. */
    private class RecordingSyncer {
        private val calls = AtomicInteger(0)
        private val monitor = java.lang.Object()

        @Volatile var lastUserId: String? = null
        @Volatile var lastAttributes: Map<String, AttributeValue>? = null

        val callCount: Int get() = calls.get()

        fun record(id: String, attributes: Map<String, AttributeValue>?) {
            lastUserId = id
            lastAttributes = attributes
            calls.incrementAndGet()
            synchronized(monitor) { monitor.notifyAll() }
        }

        /** True once at least [count] requests were recorded, false if [timeoutMs] ran out first. */
        fun awaitCalls(count: Int, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            synchronized(monitor) {
                while (calls.get() < count) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) return false
                    monitor.wait(remaining)
                }
            }
            return true
        }
    }

    /** Captures a `waitForPendingWork` callback so its outcome can be asserted on the test thread. */
    private class Waiter {
        private val latch = CountDownLatch(1)
        @Volatile private var result: Boolean? = null

        val callback: (Boolean) -> Unit = {
            result = it
            latch.countDown()
        }

        /** The outcome, or null when the callback did not fire within [timeoutMs]. */
        fun await(timeoutMs: Long = 1_000): Boolean? {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return result
        }
    }

    private lateinit var syncer: RecordingSyncer
    private lateinit var originalSyncUser: (String, Map<String, AttributeValue>?) -> Unit
    private var originalDebounceMs = 0L
    private var originalTimeoutMs = 0L

    @Before
    fun setup() {
        Formbricks.applicationContext = InstrumentationRegistry.getInstrumentation().targetContext
        originalSyncUser = UpdateQueue.syncUser
        originalDebounceMs = UpdateQueue.DEBOUNCE_INTERVAL_MS
        originalTimeoutMs = UpdateQueue.PENDING_UPDATE_TIMEOUT_MS
        syncer = RecordingSyncer()
        UpdateQueue.syncUser = syncer::record
        resetAll()
    }

    @After
    fun tearDown() {
        resetAll()
        UpdateQueue.syncUser = originalSyncUser
        UpdateQueue.DEBOUNCE_INTERVAL_MS = originalDebounceMs
        UpdateQueue.PENDING_UPDATE_TIMEOUT_MS = originalTimeoutMs
    }

    private fun resetAll() {
        UpdateQueue.reset()
        UpdateQueue.clearPendingRefresh()
        setPersistedUserId(null)
    }

    // MARK: - waitForPendingWork

    /** The common case: a host that is not identifying pays nothing for the wait. */
    @Test
    fun waitResolvesImmediatelyWhenNothingIsQueued() {
        val waiter = Waiter()
        UpdateQueue.waitForPendingWork(waiter.callback)

        assertEquals(true, waiter.await())
        assertEquals("Nothing was queued, so nothing should be sent", 0, syncer.callCount)
    }

    /**
     * Waiting sends what is queued straight away instead of sitting out the debounce window, and
     * everything queued so far still goes in a single request.
     */
    @Test
    fun waitFlushesImmediatelyAndCoalesces() {
        UpdateQueue.setUserId("user123")
        UpdateQueue.setAttributes(mapOf("plan" to AttributeValue.string("pro")))
        UpdateQueue.addAttribute("region", AttributeValue.string("eu"))

        val waiter = Waiter()
        UpdateQueue.waitForPendingWork(waiter.callback)

        // Flushed synchronously, well inside the 500ms debounce window it skipped.
        assertEquals("Rapid writes must coalesce into one request", 1, syncer.callCount)
        assertEquals("user123", syncer.lastUserId)
        assertEquals(AttributeValue.string("pro"), syncer.lastAttributes?.get("plan"))
        assertEquals(AttributeValue.string("eu"), syncer.lastAttributes?.get("region"))

        UpdateQueue.syncDidFinish(success = true)
        assertEquals(true, waiter.await())
    }

    /**
     * A failed update must report failure, so the caller can refuse to judge segment membership
     * on state the write never reached.
     */
    @Test
    fun waitResolvesFalseWhenTheSyncFails() {
        UpdateQueue.setUserId("user123")
        UpdateQueue.setAttributes(mapOf("plan" to AttributeValue.string("pro")))

        val waiter = Waiter()
        UpdateQueue.waitForPendingWork(waiter.callback)
        UpdateQueue.syncDidFinish(success = false)

        assertEquals(false, waiter.await())
    }

    /**
     * Nothing is sent for an anonymous user, so nothing would ever resolve the waiter. It has to
     * short-circuit rather than sit parked until the timeout.
     */
    @Test
    fun waitDoesNotHangForAnAnonymousUser() {
        UpdateQueue.PENDING_UPDATE_TIMEOUT_MS = 30_000
        UpdateQueue.setAttributes(mapOf("plan" to AttributeValue.string("pro")))

        val waiter = Waiter()
        UpdateQueue.waitForPendingWork(waiter.callback)

        assertEquals(true, waiter.await())
        assertEquals(0, syncer.callCount)
    }

    /** A request that never answers must not park `track()` forever. */
    @Test
    fun waitTimesOutWhenTheSyncNeverAnswers() {
        UpdateQueue.PENDING_UPDATE_TIMEOUT_MS = 100
        UpdateQueue.setUserId("user123")

        val waiter = Waiter()
        UpdateQueue.waitForPendingWork(waiter.callback)
        assertEquals(1, syncer.callCount)

        assertEquals(false, waiter.await(2_000))
    }

    // MARK: - In-flight writes

    /**
     * An attribute written while a request is out used to be dropped: the values sat in the
     * queue during the request, the success path cleared them wholesale, and the later commit
     * sent nothing. Now the request *moves* its values out, so a mid-flight write survives and
     * gets its own request once the first one lands.
     */
    @Test
    fun attributeSetDuringAnInFlightSyncIsStillSent() {
        UpdateQueue.DEBOUNCE_INTERVAL_MS = 50
        UpdateQueue.setUserId("user123")
        UpdateQueue.setAttributes(mapOf("plan" to AttributeValue.string("pro")))

        val first = Waiter()
        UpdateQueue.waitForPendingWork(first.callback)
        assertEquals(1, syncer.callCount)
        assertEquals(AttributeValue.string("pro"), syncer.lastAttributes?.get("plan"))

        // The host writes while that request is still out.
        UpdateQueue.addAttribute("region", AttributeValue.string("eu"))

        UpdateQueue.syncDidFinish(success = true)
        assertEquals(true, first.await())

        assertTrue("The mid-flight write needs its own request", syncer.awaitCalls(2, 2_000))
        assertEquals("user123", syncer.lastUserId)
        assertEquals(AttributeValue.string("eu"), syncer.lastAttributes?.get("region"))
        assertNull("Already-sent values must not be re-sent", syncer.lastAttributes?.get("plan"))
    }

    /**
     * Two concurrent `POST /user` calls race and the later response overwrites segments,
     * displays and responses wholesale, so a commit has to wait its turn.
     */
    @Test
    fun commitIsDeferredWhileASyncIsInFlight() {
        UpdateQueue.DEBOUNCE_INTERVAL_MS = 50
        UpdateQueue.setUserId("user123")

        val first = Waiter()
        UpdateQueue.waitForPendingWork(first.callback)
        assertEquals(1, syncer.callCount)

        // A mid-flight write re-arms the debounce; its commit must wait for the airborne request.
        UpdateQueue.addAttribute("region", AttributeValue.string("eu"))
        assertFalse("Must not start a second concurrent sync", syncer.awaitCalls(2, 300))

        // A second waiter joins the airborne request rather than flushing again.
        val second = Waiter()
        UpdateQueue.waitForPendingWork(second.callback)
        assertEquals(1, syncer.callCount)

        UpdateQueue.syncDidFinish(success = true)
        assertEquals(true, first.await())
        assertEquals(true, second.await())
        assertTrue("The deferred commit is replayed once the request lands", syncer.awaitCalls(2, 2_000))
    }

    /** Failed values are handed back and ride along with the next commit, without a self-retry. */
    @Test
    fun failedValuesAreHandedBackForTheNextCommit() {
        UpdateQueue.DEBOUNCE_INTERVAL_MS = 50
        UpdateQueue.setUserId("user123")
        UpdateQueue.setAttributes(mapOf("plan" to AttributeValue.string("pro")))

        val waiter = Waiter()
        UpdateQueue.waitForPendingWork(waiter.callback)
        assertEquals(1, syncer.callCount)
        UpdateQueue.syncDidFinish(success = false)
        assertEquals(false, waiter.await())

        // No self-retry: nothing goes out on its own after a failure.
        assertFalse(syncer.awaitCalls(2, 300))

        // The next write carries the failed values with it.
        UpdateQueue.addAttribute("region", AttributeValue.string("eu"))
        assertTrue(syncer.awaitCalls(2, 2_000))
        assertEquals("user123", syncer.lastUserId)
        assertEquals(AttributeValue.string("pro"), syncer.lastAttributes?.get("plan"))
        assertEquals(AttributeValue.string("eu"), syncer.lastAttributes?.get("region"))
    }

    // MARK: - Debounce

    /** The ordinary path, no wait involved: the timer alone still sends. */
    @Test
    fun setUserIdCommitsAfterTheDebounce() {
        UpdateQueue.DEBOUNCE_INTERVAL_MS = 50
        UpdateQueue.setUserId("user123")
        UpdateQueue.addAttribute("plan", AttributeValue.string("pro"))

        assertTrue(syncer.awaitCalls(1, 2_000))
        assertEquals("user123", syncer.lastUserId)
        assertEquals(AttributeValue.string("pro"), syncer.lastAttributes?.get("plan"))
    }

    /**
     * `UserManager.userId` falls back to SharedPreferences when its backing field is null, so a
     * value left behind by another test would make the queue's anonymous short circuit vanish.
     * Clears both.
     */
    private fun setPersistedUserId(value: String?) {
        val field = UserManager::class.java.getDeclaredField("backingUserId")
        field.isAccessible = true
        field.set(UserManager, value)

        val prefs = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("formbricks_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (value == null) remove("userIdKey") else putString("userIdKey", value)
            commit()
        }
    }
}
