package com.formbricks.android.network.queue

import com.formbricks.android.logger.Logger
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.error.SDKError
import com.formbricks.android.model.user.AttributeValue
import java.util.*
import kotlin.concurrent.timer

/**
 * Update queue. This class is used to queue updates to the user.
 * The given properties will be sent to the backend and updated in
 * the user object when the debounce interval is reached.
 */
object UpdateQueue {
    private const val DEBOUNCE_INTERVAL: Long = 500 // 500 ms

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

    fun setUserId(userId: String) {
        this.userId = userId
        startDebounceTimer()
    }

    fun setAttributes(attributes: Map<String, AttributeValue>) {
        this.attributes = attributes.toMutableMap()
        startDebounceTimer()
    }

    fun addAttribute(key: String, attribute: AttributeValue) {
        if (attributes == null) {
            attributes = mutableMapOf()
        }
        attributes?.put(key, attribute)
        startDebounceTimer()
    }

    fun setLanguage(language: String) {
        val effectiveUserId = userId ?: UserManager.userId

        if (effectiveUserId != null) {
            addAttribute("language", AttributeValue.string(language))
            startDebounceTimer()
        } else {
            Logger.d("UpdateQueue - updating language locally: $language")
            return
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

    /**
     * Called by the user manager once a sync finishes. Releases the in-flight lock and replays a
     * refresh that arrived while the request was out.
     */
    fun syncDidFinish() {
        val deferredUserId = synchronized(lock) {
            isSyncInFlight = false
            pendingRefreshUserId.also { pendingRefreshUserId = null }
        } ?: return

        Logger.d("UpdateQueue - replaying a refresh that arrived mid-sync")
        // Outside the block above: `requestUserStateRefresh` takes the same lock.
        requestUserStateRefresh(deferredUserId)
    }

    fun reset() {
        synchronized(lock) {
            userId = null
            attributes = null
            language = null
            isSyncInFlight = false
            // `pendingRefreshUserId` is deliberately kept: reset() runs on the sync success
            // path, and syncDidFinish() still has to replay it.
        }
    }

    /** Teardown, unlike [reset]: drop the deferred refresh instead of replaying it. */
    fun clearPendingRefresh() {
        synchronized(lock) {
            pendingRefreshUserId = null
        }
    }

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
            }, DEBOUNCE_INTERVAL)
        }
    }

    private fun commit() {
        val effectiveUserId: String?
        val effectiveAttributes: Map<String, AttributeValue>?

        // Capture a consistent snapshot, and only mark a sync in flight when one is actually
        // about to be sent — otherwise a commit with no user id would leave the flag stuck and
        // swallow every later refresh nudge.
        synchronized(lock) {
            effectiveUserId = userId ?: UserManager.userId
            effectiveAttributes = attributes?.toMap()
            if (effectiveUserId != null) {
                isSyncInFlight = true
            }
        }

        if (effectiveUserId == null) {
            val error = SDKError.noUserIdSetError
            Logger.e(error)
            return
        }

        Logger.d("UpdateQueue - commit() called on UpdateQueue with $effectiveUserId and $effectiveAttributes")
        UserManager.syncUser(effectiveUserId, effectiveAttributes)
    }
}
