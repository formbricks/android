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
     * expire. Dropped while a sync is already in flight, because that sync's response already
     * brings fresh segments.
     */
    fun requestUserStateRefresh(userId: String) {
        synchronized(lock) {
            if (isSyncInFlight) {
                Logger.d("UpdateQueue - refresh skipped, a sync is already in flight")
                return
            }
            this.userId = userId
        }
        startDebounceTimer()
    }

    /** Called by the user manager once a sync finishes, so the next nudge can start a request. */
    fun syncDidFinish() {
        synchronized(lock) {
            isSyncInFlight = false
        }
    }

    fun reset() {
        synchronized(lock) {
            userId = null
            attributes = null
            language = null
            isSyncInFlight = false
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
