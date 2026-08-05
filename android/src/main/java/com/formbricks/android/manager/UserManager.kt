package com.formbricks.android.manager

import android.content.Context
import com.formbricks.android.Formbricks
import com.formbricks.android.api.FormbricksApi
import com.formbricks.android.extensions.dateString
import com.formbricks.android.extensions.expiresAt
import com.formbricks.android.extensions.guard
import com.formbricks.android.extensions.lastDisplayAt
import com.formbricks.android.logger.Logger
import com.formbricks.android.model.error.SDKError
import com.formbricks.android.model.user.AttributeValue
import com.formbricks.android.model.user.Display
import com.formbricks.android.model.workspace.InteractionSource
import com.formbricks.android.model.workspace.Survey
import com.formbricks.android.network.queue.UpdateQueue
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Timer
import java.util.TimerTask

/**
 * Store and manage user state and sync with the server when needed.
 */
object UserManager {
    private const val FORMBROCKS_PERFS = "formbricks_prefs"
    private const val USER_ID_KEY = "userIdKey"
    private const val CONTACT_ID_KEY = "contactIdKey"
    private const val SEGMENTS_KEY = "segmentsKey"
    private const val DISPLAYS_KEY = "displaysKey"
    private const val RESPONSES_KEY = "responsesKey"
    private const val LAST_DISPLAYED_AT_KEY = "lastDisplayedAtKey"
    private const val EXPIRES_AT_KEY = "expiresAtKey"
    /**
     * Floor for the gap between two user-state syncs. Guards against a device clock running
     * ahead of the server, where every `expiresAt` the server returns is already in the
     * device's past and an unclamped timer would sync in a tight loop.
     */
    internal var MINIMUM_SYNC_INTERVAL_MS: Long = 60_000
    private val prefManager by lazy { Formbricks.applicationContext.getSharedPreferences(FORMBROCKS_PERFS, Context.MODE_PRIVATE) }

    private var backingUserId: String? = null
    private var backingContactId: String? = null
    private var backingSegments: List<String>? = null
    private var backingDisplays: List<Display>? = null
    private var backingResponses: List<String>? = null
    private var backingLastDisplayedAt: Date? = null
    private var backingExpiresAt: Date? = null
    internal val syncTimer = Timer()
    /** The pending expiry-driven sync, so it can be replaced or cancelled on logout. */
    private var syncTask: TimerTask? = null

    /**
     * Starts an update queue with the given user id.
     *
     * @param userId
     */
    fun set(userId: String) {
        UpdateQueue.setUserId(userId)
    }

    /**
     * Starts an update queue with the given attribute.
     *
     * @param attribute
     * @param key
     */
    fun addAttribute(attribute: AttributeValue, key: String) {
        UpdateQueue.addAttribute(key, attribute)
    }

    /**
     * Starts an update queue with the given attributes.
     *
     * @param attributes
     */
    fun setAttributes(attributes: Map<String, AttributeValue>) {
        UpdateQueue.setAttributes(attributes)
    }

    /**
     * Starts an update queue with the given language..
     *
     * @param language
     */
    fun setLanguage(language: String) {
        UpdateQueue.setLanguage(language)
    }

    /**
     * Saves [surveyId] to the [displays] property and the the current date to the [lastDisplayedAt] property.
     *
     * @param surveyId
     */
    fun onDisplay(surveyId: String) {
        val lastDisplayedAt = Date()
        val newDisplays = displays?.toMutableList() ?: mutableListOf()
        newDisplays.add(Display(surveyId, lastDisplayedAt.dateString()))
        displays = newDisplays
        this.lastDisplayedAt = lastDisplayedAt
        SurveyManager.filterSurveys()
    }

    /**
     * Saves [surveyId] to the [responses] property.
     *
     * @param surveyId
     */
    fun onResponse(surveyId: String) {
        val newResponses = responses?.toMutableList() ?: mutableListOf()
        newResponses.add(surveyId)
        responses = newResponses
        SurveyManager.filterSurveys()
    }

    /**
     * Pulls fresh server-computed `segments` after an interaction that can flip segment
     * membership, instead of waiting for the state to expire.
     *
     * A `surveyInteraction` segment filter ("have seen X", "have completed X", ...) can change
     * who a contact is the moment they interact with a survey. The local bookkeeping in
     * [onDisplay] / [onResponse] keeps display caps and recontact days correct on device, but
     * segment membership is only ever computed by the server, so it has to be refetched.
     *
     * The refresh is deliberately gated twice, because a `/user` sync is not cheap:
     *  - no-op for anonymous users, who never receive segments in the first place;
     *  - no-op unless the server set the bit for this survey and this event.
     *
     * It is routed through the [UpdateQueue] rather than calling [syncUser] directly, so a
     * display -> response -> finish burst is debounced into a single request.
     */
    fun refreshSegmentsAfterInteraction(survey: Survey, source: InteractionSource) {
        val id = userId ?: return
        if (survey.interactionRefresh?.shouldRefresh(source) != true) return

        Logger.d("Refreshing segments after ${source.value} on survey ${survey.id}")
        UpdateQueue.requestUserStateRefresh(id)
    }

    /**
     * Syncs the user state with the server if the user id is set and the expiration date has passed.
     */
    fun syncUserStateIfNeeded() {
        val id = userId
        val expiresAt = expiresAt
        if (id != null && expiresAt != null && !Date().before(expiresAt)) {
            syncUser(id)
        } else {
            // Drop only the in-memory caches. Assigning emptyList() would mask the
            // persisted SharedPreferences arrays because the lazy getters fall back
            // to disk only when the backing is null; a non-null empty list
            // short-circuits the elvis/null-check and the persisted values never load.
            backingSegments = null
            backingDisplays = null
            backingResponses = null

            // The state is still valid, but nothing has been scheduled to refresh it when it
            // does expire — `startSyncTimer` is otherwise only reached from a successful sync,
            // so a launch that finds a warm cache would never refresh segments again.
            startSyncTimer()
        }
    }

    /**
     * Syncs the user state with the server, calls the [SurveyManager.filterSurveys] method and starts the sync timer.
     *
     * @param id
     * @param attributes
     */
    fun syncUser(id: String, attributes: Map<String, AttributeValue>? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userResponse = FormbricksApi.postUser(id, attributes).getOrThrow()
                userId = userResponse.data.state.data.userId
                contactId = userResponse.data.state.data.contactId
                segments = userResponse.data.state.data.segments
                displays = userResponse.data.state.data.displays
                responses = userResponse.data.state.data.responses
                lastDisplayedAt = userResponse.data.state.data.lastDisplayAt()
                expiresAt = userResponse.data.state.expiresAt()
                val languageFromUserResponse = userResponse.data.state.data.language

                if (languageFromUserResponse != null) {
                    Formbricks.language = languageFromUserResponse
                }

                // Log errors (always visible) - e.g., invalid attribute keys, type mismatches
                userResponse.data.errors?.forEach { error ->
                    Logger.e(RuntimeException(error))
                }

                // Log informational messages (debug only)
                userResponse.data.messages?.forEach { message ->
                    Logger.d("User update message: $message")
                }

                UpdateQueue.reset()
                SurveyManager.filterSurveys()
                startSyncTimer()
            } catch (e: Exception) {
                // Release the in-flight lock so a later refresh nudge isn't swallowed.
                // `UpdateQueue.reset()` already does this on the success path.
                UpdateQueue.syncDidFinish()
                val error = SDKError.unableToPostResponse
                Logger.e(error)
            }
        }
    }

    /**
     * Logs out the user and clears the user state.
     */
    fun logout() {
        Logger.d("Logging out and cleaning user state")

        prefManager.edit().apply {
            remove(CONTACT_ID_KEY)
            remove(USER_ID_KEY)
            remove(SEGMENTS_KEY)
            remove(DISPLAYS_KEY)
            remove(RESPONSES_KEY)
            remove(LAST_DISPLAYED_AT_KEY)
            remove(EXPIRES_AT_KEY)
            apply()
        }

        backingUserId = null
        backingContactId = null
        backingSegments = null
        backingDisplays = null
        backingResponses = null
        backingLastDisplayedAt = null
        backingExpiresAt = null
        Formbricks.language = "default"
        UpdateQueue.reset()

        // Drop any pending expiry-driven sync; its captured user id is gone.
        syncTask?.cancel()
        syncTask = null

        SurveyManager.filterSurveys()
    }

    /**
     * Schedules the next user-state sync for when the cached state expires.
     *
     * Two things this guards against:
     *  - A device clock running ahead of the server makes every `expiresAt` we receive land in
     *    the device's past. [Timer.schedule] runs a past-dated task immediately, which would
     *    sync, get another past-dated expiry, and loop. Hence the delay floor.
     *  - [syncTimer] is a single shared [Timer]; once cancelled it throws on every later
     *    `schedule`. Catch that rather than tearing down the SDK.
     */
    private fun startSyncTimer() {
        val expiresAt = expiresAt ?: return
        val id = userId ?: return

        val delay = (expiresAt.time - System.currentTimeMillis())
            .coerceAtLeast(MINIMUM_SYNC_INTERVAL_MS)

        syncTask?.cancel()
        val task = object : TimerTask() {
            override fun run() {
                // The user may have been logged out or swapped while this was pending.
                if (userId != id) return
                syncUser(id)
            }
        }
        syncTask = task

        try {
            syncTimer.schedule(task, delay)
        } catch (e: IllegalStateException) {
            Logger.d("User state sync timer is no longer schedulable: ${e.message}")
        }
    }


    var userId: String?
        get() = backingUserId ?: prefManager.getString(USER_ID_KEY, null).also { backingUserId = it }
        private set(value) {
            backingUserId = value
            prefManager.edit().putString(USER_ID_KEY, value).apply()
        }

    var contactId: String?
        get() = backingContactId ?: prefManager.getString(CONTACT_ID_KEY, null).also { backingContactId = it }
        private set(value) {
            backingContactId = value
            prefManager.edit().putString(CONTACT_ID_KEY, value).apply()
        }

    var segments: List<String>?
        get() = backingSegments ?: prefManager.getStringSet(SEGMENTS_KEY, emptySet())?.toList().also { backingSegments = it }
        private set(value) {
            backingSegments = value
            prefManager.edit().putStringSet(SEGMENTS_KEY, value?.toSet()).apply()
        }

    var displays: List<Display>?
        get() {
            if (backingDisplays == null) {
                val json = prefManager.getString(DISPLAYS_KEY, null)
                if (json != null) {
                    backingDisplays = Gson().fromJson(json, Array<Display>::class.java).toList()
                }
            }
            return backingDisplays
        }
        private set(value) {
            backingDisplays = value
            prefManager.edit().putString(DISPLAYS_KEY, Gson().toJson(value)).apply()
        }

    var responses: List<String>?
        get() = backingResponses ?: prefManager.getStringSet(RESPONSES_KEY, emptySet())?.toList().also { backingResponses = it }
        private set(value) {
            backingResponses = value
            prefManager.edit().putStringSet(RESPONSES_KEY, value?.toSet()).apply()
        }

    var lastDisplayedAt: Date?
        get() = backingLastDisplayedAt ?: prefManager.getLong(LAST_DISPLAYED_AT_KEY, 0L).takeIf { it > 0 }?.let { Date(it) }.also { backingLastDisplayedAt = it }
        internal set(value) {
            backingLastDisplayedAt = value
            prefManager.edit().putLong(LAST_DISPLAYED_AT_KEY, value?.time ?: 0L).apply()
        }

    /**
     * Test-only getter for lastDisplayedAt
     */
    internal fun getLastDisplayedAtForTesting(): Date? = lastDisplayedAt

    var expiresAt: Date?
        get() = backingExpiresAt ?: prefManager.getLong(EXPIRES_AT_KEY, 0L).takeIf { it > 0 }?.let { Date(it) }.also { backingExpiresAt = it }
        private set(value) {
            backingExpiresAt = value
            prefManager.edit().putLong(EXPIRES_AT_KEY, value?.time ?: 0L).apply()
        }
}