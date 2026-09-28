package com.formbricks.android.network.queue

import com.formbricks.android.logger.Logger
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.error.SDKError
import com.formbricks.android.model.user.AttributeValue
import java.util.Timer
import java.util.TimerTask

/**
 * Update queue. This class is used to queue updates to the user.
 * The given properties will be sent to the backend and updated in
 * the user object when the debounce interval is reached.
 */
object UpdateQueue {
    /**
     * Window over which `setUserId` / `setAttribute` calls are coalesced into a single
     * `POST /user`. A `var` only so tests can shorten it; the SDK never writes to it.
     */
    internal var DEBOUNCE_INTERVAL_MS: Long = 500

    /**
     * Ceiling on how long `track()` waits for a queued user update to land before it gives up
     * and evaluates targeting anyway. Bounds the delay a dead network can add to a track call —
     * without it a host that tracks right after `setAttribute` would hang on a request that is
     * never going to answer. A `var` only so tests can shorten it; the SDK never writes to it.
     */
    internal var PENDING_UPDATE_TIMEOUT_MS: Long = 5_000

    private val lock = Any()

    private var userId: String? = null
    private var attributes: MutableMap<String, AttributeValue>? = null
    private var language: String? = null
    private var timer: Timer? = null

    /**
     * True while a commit-triggered sync is airborne. A repeat nudge joins that request instead
     * of starting a second one: two concurrent `POST /user` calls would race and whichever
     * response landed last would overwrite `segments` / `displays` / `responses` wholesale.
     */
    private var isSyncInFlight = false

    /**
     * A refresh that arrived while a sync was already airborne, replayed once that sync
     * finishes. Only the latest is kept, so many interactions behind a slow sync still cost a
     * single follow-up request.
     */
    private var pendingRefreshUserId: String? = null

    /**
     * The values the in-flight request is carrying. [commit] *moves* them out of [userId] /
     * [attributes] rather than copying, so anything a host sets while the request is out
     * accumulates separately and cannot be dropped when the response lands. Kept so a failed
     * request can hand them back to be retried.
     */
    private var inFlightUserId: String? = null
    private var inFlightAttributes: Map<String, AttributeValue>? = null

    /**
     * Callbacks parked by [waitForPendingWork], keyed so a timeout resolves only its own waiter.
     * Drained atomically under [lock], so a waiter is never called twice.
     */
    private val pendingWaiters = mutableMapOf<Int, (Boolean) -> Unit>()
    private var waiterToken = 0
    private val timeoutTimer = Timer("pendingUpdateTimeout", true)

    /**
     * The call that actually sends the request. A `var` only so tests can swap in a recorder and
     * drive the queue without a network; the SDK never writes to it.
     */
    internal var syncUser: (String, Map<String, AttributeValue>?) -> Unit =
        { id, attributes -> UserManager.syncUser(id, attributes) }

    fun setUserId(userId: String) {
        synchronized(lock) { this.userId = userId }
        startDebounceTimer()
    }

    fun setAttributes(attributes: Map<String, AttributeValue>) {
        synchronized(lock) { this.attributes = attributes.toMutableMap() }
        startDebounceTimer()
    }

    fun addAttribute(key: String, attribute: AttributeValue) {
        // Under the lock: `commit()` moves the map out and nulls the field, so an unguarded
        // `put` could land on a map that is already on its way to the server, or on one that
        // nothing will ever send.
        synchronized(lock) {
            val current = attributes ?: mutableMapOf<String, AttributeValue>().also { attributes = it }
            current[key] = attribute
        }
        startDebounceTimer()
    }

    fun setLanguage(language: String) {
        val effectiveUserId = synchronized(lock) { effectiveUserIdLocked() }

        if (effectiveUserId != null) {
            addAttribute("language", AttributeValue.string(language))
        } else {
            Logger.d("UpdateQueue - updating language locally: $language")
        }
    }

    /**
     * Asks for the user state to be re-read from the server. Carries no new data — it exists so
     * an interaction that can change segment membership doesn't have to wait for the state to
     * expire.
     *
     * While a sync is airborne the nudge is deferred rather than sent, because two concurrent
     * `POST /user` calls would race and the later response would overwrite segments, displays
     * and responses wholesale. It is replayed by [syncDidFinish].
     */
    fun requestUserStateRefresh(userId: String) {
        synchronized(lock) {
            if (isSyncInFlight) {
                Logger.d("UpdateQueue - refresh deferred, a sync is already in flight")
                // The in-flight request was built before this interaction, so its response
                // cannot reflect it. Dropping the nudge would leave segments stale until the
                // next trigger.
                pendingRefreshUserId = userId
                return
            }
            this.userId = userId
        }
        startDebounceTimer()
    }

    /** Whether anything is queued for the server, or a request carrying such values is still out. */
    fun hasPendingWork(): Boolean = synchronized(lock) { hasPendingWorkLocked() }

    /**
     * Calls [completion] once the queued user updates have reached the server, so the caller can
     * decide against the resulting user state rather than the state that predates it.
     *
     * `true` means there was nothing to wait for, or the sync succeeded — `segments` can be
     * trusted. `false` means the update failed or timed out, so segment membership is still
     * whatever it was and any segment-targeted decision would be made on stale data.
     *
     * Callback-based rather than blocking: `track()` is normally called from the host's main
     * thread, which must never be parked on a network round trip. The callback runs on whichever
     * thread settles the request — the sync coroutine, the timeout timer, or the caller itself
     * when nothing is pending — so it must not assume a thread.
     */
    fun waitForPendingWork(completion: (Boolean) -> Unit) {
        var token = 0
        var shouldFlush = false

        val resolveNow = synchronized(lock) {
            when {
                !hasPendingWorkLocked() -> true

                // Nothing can be sent for an anonymous user — `commit()` bails before it reaches
                // the network, so nothing would ever resolve the waiter and it would sit until
                // the timeout. `filterSurveys()` already excludes anonymous users from
                // segment-targeted surveys, so there is no stale-membership risk to guard against.
                effectiveUserIdLocked() == null -> true

                else -> {
                    token = ++waiterToken
                    pendingWaiters[token] = completion
                    // A request carrying these values is already out; join it instead of
                    // starting a second concurrent one.
                    shouldFlush = !isSyncInFlight
                    false
                }
            }
        }

        if (resolveNow) {
            completion(true)
            return
        }

        if (shouldFlush) {
            flushNow()
        }
        scheduleWaiterTimeout(token)
    }

    /**
     * Called by the user manager once a sync finishes. Releases the in-flight lock, resolves
     * everyone waiting on that request, and gives whatever queued up meanwhile its turn.
     *
     * `success = false` hands the failed request's values back to the queue so they are retried
     * on the next commit — but deliberately does *not* re-arm the debounce timer for them. A
     * self-retrying failure would turn a dead network into a request every half second;
     * `UserManager.scheduleSyncRetry()` owns the backoff instead.
     */
    fun syncDidFinish(success: Boolean = true) {
        val waiters: List<(Boolean) -> Unit>
        val deferredUserId: String?
        val hasNewWork: Boolean

        synchronized(lock) {
            isSyncInFlight = false

            // Anything queued while the request was out is genuinely new and needs its own
            // commit. Read before the hand-back below, so a failure's own values don't look new.
            hasNewWork = userId != null || attributes != null

            if (!success) {
                if (userId == null) userId = inFlightUserId
                inFlightAttributes?.let { carried ->
                    // Anything set since the request went out is newer, so it wins the key clash.
                    val merged = carried.toMutableMap()
                    attributes?.let { merged.putAll(it) }
                    attributes = merged
                }
            } else if (hasNewWork && userId == null) {
                // A write that arrived mid-request belongs to the same user, but `commit()` moved
                // the id out with the request. Put it back so the follow-up carries an identity
                // of its own rather than depending on the response having already reached the
                // user manager.
                userId = inFlightUserId
            }
            inFlightUserId = null
            inFlightAttributes = null

            waiters = pendingWaiters.values.toList()
            pendingWaiters.clear()

            deferredUserId = pendingRefreshUserId
            pendingRefreshUserId = null
        }

        // Outside the lock, so a waiter that calls back into the queue cannot deadlock on it.
        waiters.forEach { it(success) }

        // Both calls below take `lock`, so they have to run outside the block above.
        if (deferredUserId != null) {
            Logger.d("UpdateQueue - replaying a refresh that arrived mid-sync")
            requestUserStateRefresh(deferredUserId)
        } else if (hasNewWork) {
            Logger.d("UpdateQueue - re-arming for updates queued during the sync")
            startDebounceTimer()
        }
    }

    /**
     * Drops everything: queued and in-flight values, the debounce timer, the in-flight lock, and
     * anyone waiting on a sync — they are told the update did not go through rather than left
     * parked until their timeout. `pendingRefreshUserId` is left to [clearPendingRefresh]; the
     * two are called together at logout.
     */
    fun reset() {
        val waiters: List<(Boolean) -> Unit>

        synchronized(lock) {
            timer?.cancel()
            timer = null
            userId = null
            attributes = null
            language = null
            isSyncInFlight = false
            inFlightUserId = null
            inFlightAttributes = null
            waiters = pendingWaiters.values.toList()
            pendingWaiters.clear()
        }

        waiters.forEach { it(false) }
    }

    /** Drops a deferred refresh instead of replaying it. */
    fun clearPendingRefresh() {
        synchronized(lock) {
            pendingRefreshUserId = null
        }
    }

    /** Must be read under [lock]. */
    private fun hasPendingWorkLocked(): Boolean =
        userId != null || attributes != null || isSyncInFlight

    /** The id a commit would send. Must be read under [lock]. */
    private fun effectiveUserIdLocked(): String? =
        userId ?: inFlightUserId ?: UserManager.userId

    private fun startDebounceTimer() {
        synchronized(lock) {
            timer?.cancel()
            // One-shot rather than the previous repeating timer that cancelled itself from
            // inside its own task: that read the shared `timer` field from the timer thread, so
            // a newer timer scheduled in the meantime could be cancelled instead of this one.
            val newTimer = Timer("debounceTimer", false)
            timer = newTimer
            newTimer.schedule(object : TimerTask() {
                override fun run() {
                    commit()
                }
            }, DEBOUNCE_INTERVAL_MS)
        }
    }

    /**
     * Sends what is queued now instead of waiting the debounce window out. Everything queued so
     * far still goes in a single request, so calls stay coalesced.
     */
    private fun flushNow() {
        synchronized(lock) {
            timer?.cancel()
            timer = null
        }
        commit()
    }

    private fun scheduleWaiterTimeout(token: Int) {
        val timeout = PENDING_UPDATE_TIMEOUT_MS
        timeoutTimer.schedule(object : TimerTask() {
            override fun run() {
                val waiter = synchronized(lock) { pendingWaiters.remove(token) } ?: return
                Logger.d("UpdateQueue - a queued user update did not land within ${timeout}ms")
                waiter(false)
            }
        }, timeout)
    }

    private fun commit() {
        val effectiveUserId: String?
        val effectiveAttributes: Map<String, AttributeValue>?

        synchronized(lock) {
            // Never two at once: two concurrent `POST /user` calls would race and the later
            // response would overwrite `segments` / `displays` / `responses` wholesale.
            // `syncDidFinish()` re-arms for whatever is queued by then.
            if (isSyncInFlight) {
                Logger.d("UpdateQueue - commit deferred, a sync is already in flight")
                return
            }

            effectiveUserId = userId ?: UserManager.userId
            effectiveAttributes = attributes?.toMap()

            // Only mark a sync in flight when one is actually about to be sent — otherwise a
            // commit with no user id would leave the flag stuck and swallow every later refresh
            // nudge.
            if (effectiveUserId != null) {
                isSyncInFlight = true
                // Move, don't copy. Leaving these queued meant the success path's reset cleared
                // whatever a host had set while the request was out, and the value was then
                // never sent — a silent write loss.
                inFlightUserId = userId
                inFlightAttributes = effectiveAttributes
                userId = null
                attributes = null
            }
        }

        if (effectiveUserId == null) {
            val error = SDKError.noUserIdSetError
            Logger.e(error)
            return
        }

        Logger.d("UpdateQueue - commit() called on UpdateQueue with $effectiveUserId and $effectiveAttributes")
        syncUser(effectiveUserId, effectiveAttributes)
    }
}
