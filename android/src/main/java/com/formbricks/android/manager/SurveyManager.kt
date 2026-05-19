package com.formbricks.android.manager

import android.content.Context
import com.formbricks.android.Formbricks
import com.formbricks.android.api.FormbricksApi
import com.formbricks.android.extensions.expiresAt
import com.formbricks.android.extensions.guard
import com.formbricks.android.logger.Logger
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import com.formbricks.android.model.workspace.Segment
import com.formbricks.android.model.workspace.SegmentDeserializer
import com.formbricks.android.model.workspace.Survey
import com.formbricks.android.model.error.SDKError
import com.formbricks.android.model.user.Display
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.RuntimeException
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

/**
 *  The SurveyManager is responsible for managing the surveys that are displayed to the user.
 *  Filtering surveys based on the user's segments, responses, and displays.
 */
object SurveyManager {
    private const val REFRESH_STATE_ON_ERROR_TIMEOUT_IN_MINUTES = 10
    private const val FORMBRICKS_PREFS = "formbricks_prefs"
    internal const val PREF_FORMBRICKS_WORKSPACE_DATA_HOLDER = "formbricksWorkspaceDataHolder"
    /** Pre-workspace-rename storage key. Read on first access so existing installs can be migrated. */
    internal const val PREF_LEGACY_ENVIRONMENT_DATA_HOLDER = "formbricksDataHolder"

    internal val refreshTimer = Timer()
    internal var displayTimer = Timer()
    internal var hasApiError = false
    internal var isShowingSurvey = false
    private val prefManager by lazy { Formbricks.applicationContext.getSharedPreferences(FORMBRICKS_PREFS, Context.MODE_PRIVATE) }
    internal var filteredSurveys: MutableList<Survey> = mutableListOf()

    val gson = GsonBuilder()
        .registerTypeAdapter(Segment::class.java, SegmentDeserializer())
        .create()

    private var workspaceDataHolderJson: String?
        get() = prefManager.getString(PREF_FORMBRICKS_WORKSPACE_DATA_HOLDER, null)
        set(value) {
            val editor = prefManager.edit()
            if (null != value) {
                editor.putString(PREF_FORMBRICKS_WORKSPACE_DATA_HOLDER, value)
            } else {
                editor.remove(PREF_FORMBRICKS_WORKSPACE_DATA_HOLDER)
            }
            // Drop the legacy cache key once we've written to the new one.
            editor.remove(PREF_LEGACY_ENVIRONMENT_DATA_HOLDER)
            editor.apply()
        }

    /**
     * One-shot migration of the pre-rename SharedPreferences cache. Call once during
     * SDK setup before any reads. If a legacy blob exists and the new key is empty,
     * copy it over; always drop the legacy key afterwards.
     */
    internal fun migrateLegacyCacheIfNeeded() {
        if (!prefManager.contains(PREF_LEGACY_ENVIRONMENT_DATA_HOLDER)) return
        val legacy = prefManager.getString(PREF_LEGACY_ENVIRONMENT_DATA_HOLDER, null)
        val editor = prefManager.edit().remove(PREF_LEGACY_ENVIRONMENT_DATA_HOLDER)
        if (legacy != null && !prefManager.contains(PREF_FORMBRICKS_WORKSPACE_DATA_HOLDER)) {
            editor.putString(PREF_FORMBRICKS_WORKSPACE_DATA_HOLDER, legacy)
        }
        editor.apply()
    }

    private var backingWorkspaceDataHolder: WorkspaceDataHolder? = null
    var workspaceDataHolder: WorkspaceDataHolder?
        get() {
            if (null != backingWorkspaceDataHolder) {
                return backingWorkspaceDataHolder
            }
            synchronized(this) {
                backingWorkspaceDataHolder = workspaceDataHolderJson?.let { json ->
                    try {
                        gson.fromJson(json, WorkspaceDataHolder::class.java)
                    } catch (e: Exception) {
                        Logger.e(RuntimeException("Unable to retrieve workspace data from the local storage."))
                        null
                    }
                }
                return backingWorkspaceDataHolder
            }
        }
        set(value) {
            synchronized(this) {
                backingWorkspaceDataHolder = value
                workspaceDataHolderJson = Gson().toJson(value)
            }
        }

    /**
     * Fills up the [filteredSurveys] array
     */
    fun filterSurveys() {
        val surveys = workspaceDataHolder?.data?.data?.surveys.guard { return }
        val displays = UserManager.displays ?: listOf()
        val responses = UserManager.responses ?: listOf()
        val segments = UserManager.segments ?: listOf()

        filteredSurveys = filterSurveysBasedOnDisplayType(surveys, displays, responses).toMutableList()
        filteredSurveys = filterSurveysBasedOnRecontactDays(filteredSurveys, workspaceDataHolder?.data?.data?.settings?.recontactDays?.toInt()).toMutableList()

        if (UserManager.userId == null) {
            filteredSurveys = filteredSurveys.filter { survey ->
                // Only include surveys that have no segment filters or null segment.
                // `hasFilters` is decoded directly from the server response, or
                // derived from a legacy cached `filters` array (see SegmentDeserializer).
                !(survey.segment?.hasFilters ?: false)
            }.toMutableList()
        }

        if (UserManager.userId != null) {
            if (segments.isEmpty()) {
                filteredSurveys = mutableListOf()
                return
            }

            filteredSurveys = filterSurveysBasedOnSegments(filteredSurveys, segments).toMutableList()
        }
    }

    /**
     * Checks if the workspace state needs to be refreshed based on its [expiresAt] property,
     * and if so, refreshes it, starts the refresh timer, and filters the surveys.
     */
    fun refreshWorkspaceIfNeeded(force: Boolean = false) {
        if (!force) {
            workspaceDataHolder?.expiresAt()?.let {
                if (it.after(Date())) {
                    Logger.d("Workspace state is still valid until $it")
                    filterSurveys()
                    return
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                workspaceDataHolder = FormbricksApi.getWorkspaceState().getOrThrow()
                startRefreshTimer(workspaceDataHolder?.expiresAt())
                filterSurveys()
                hasApiError = false
            } catch (e: Exception) {
                hasApiError = true
                val error = SDKError.unableToRefreshEnvironment
                Logger.e(error)
                startErrorTimer()
            }
        }
    }

    /**
     * Checks if there are any surveys to display, based in the track action, and if so, displays the first one.
     * Handles the display percentage and the delay of the survey.
     */
    fun track(action: String) {
        val actionClasses = workspaceDataHolder?.data?.data?.actionClasses ?: listOf()
        val codeActionClasses = actionClasses.filter { it.type == "code" }
        val actionClass = codeActionClasses.firstOrNull { it.key == action }
        if (actionClass == null) {
            val error = RuntimeException("Action with identifier '$action' is unknown. Please add this action in Formbricks in order to use it via the SDK action tracking.")
            Logger.e(error)
            return
        }
        val firstSurveyWithActionClass = filteredSurveys.firstOrNull { survey ->
            val triggers = survey.triggers ?: listOf()
            triggers.firstOrNull { trigger ->
                trigger.actionClass?.name == actionClass?.name
            } != null
        }

        if (firstSurveyWithActionClass == null) {
            val error = SDKError.surveyNotFoundError
            Logger.e(error)
            return
        }

        val isMultiLangSurvey = (firstSurveyWithActionClass.languages?.size ?: 0) > 1
        if(isMultiLangSurvey) {
            val currentLanguage = Formbricks.language
            val languageCode = getLanguageCode(firstSurveyWithActionClass, currentLanguage)

            if (languageCode == null) {
                val error = RuntimeException("Survey “${firstSurveyWithActionClass.id}” is not available in language “$currentLanguage”. Skipping.")
                Logger.e(error)
                return
            }

            Formbricks.setLanguage(languageCode)
        }

        val shouldDisplay = shouldDisplayBasedOnPercentage(firstSurveyWithActionClass.displayPercentage)

        if (shouldDisplay) {
            firstSurveyWithActionClass.id.let {
                isShowingSurvey = true
                val timeout = firstSurveyWithActionClass.delay ?: 0.0
                if (timeout > 0.0) {
                    Logger.d("Delaying survey \"${firstSurveyWithActionClass.id}\" by $timeout seconds")
                }
                stopDisplayTimer()
                displayTimer.schedule(object : TimerTask() {
                    override fun run() {
                        Formbricks.showSurvey(it)
                    }

                }, Date(System.currentTimeMillis() + timeout.toLong() * 1000))
            }
        } else {
            val error = SDKError.surveyNotDisplayedError
            Logger.e(error)
        }
    }

    private fun stopDisplayTimer() {
        displayTimer.cancel()
        displayTimer = Timer()
    }

    /**
     * Posts a survey response to the Formbricks API.
     */
    fun postResponse(surveyId: String?) {
        val id = surveyId.guard {
            val error = SDKError.missingSurveyId
            Logger.e(error)
            return
        }

        UserManager.onResponse(id)
    }

    /**
     * Creates a new display for the survey. It is called when the survey is displayed to the user.
     */
    fun onNewDisplay(surveyId: String?) {
        val id = surveyId.guard {
            val error = SDKError.missingSurveyId
            Logger.e(error)
            return
        }

        UserManager.onDisplay(id)
    }

   /**
     *  Starts a timer to refresh the workspace state after the given timeout [expiresAt].
     */
    private fun startRefreshTimer(expiresAt: Date?) {
        val date = expiresAt.guard { return }
        refreshTimer.schedule(object: TimerTask() {
            override fun run() {
                Logger.d("Refreshing workspace state.")
                refreshWorkspaceIfNeeded()
            }

        }, date)
    }

    /**
     *  When an error occurs, it starts a timer to refresh the workspace state after the given timeout.
     */
    private fun startErrorTimer() {
        val targetDate = Date(System.currentTimeMillis() + 1000 * 60 * REFRESH_STATE_ON_ERROR_TIMEOUT_IN_MINUTES)
        refreshTimer.schedule(object: TimerTask() {
            override fun run() {
                Logger.d("Refreshing workspace state after an error")
                refreshWorkspaceIfNeeded()
            }

        }, targetDate)
    }

    /**
     * Filters the surveys based on the display type and limit.
     * @param surveys List of surveys to filter
     * @param displays List of displays to check against
     * @param responses List of survey responses
     * @return Filtered list of surveys
     */
    @JvmStatic
    fun filterSurveysBasedOnDisplayType(surveys: List<Survey>, displays: List<Display>, responses: List<String>): List<Survey> {
        return surveys.filter { survey ->
            when (survey.displayOption) {
                "respondMultiple" -> true

                "displayOnce" -> {
                    displays.none { it.surveyId == survey.id }
                }

                "displayMultiple" -> {
                    responses.none { it == survey.id }
                }

                "displaySome" -> {
                    survey.displayLimit?.let { limit ->
                        if (responses.any { it == survey.id }) {
                            return@filter false
                        }
                        displays.count { it.surveyId == survey.id } < limit
                    } ?: true
                }

                else -> {
                    val error = SDKError.invalidDisplayOption
                    Logger.e(error)
                    false
                }
            }
        }
    }

    /**
     * Filters the surveys based on the recontact days and the [UserManager.lastDisplayedAt] date.
     * @param surveys List of surveys to filter
     * @param defaultRecontactDays Default recontact days if not specified in survey
     * @return Filtered list of surveys
     */
    @JvmStatic
    fun filterSurveysBasedOnRecontactDays(surveys: List<Survey>, defaultRecontactDays: Int?): List<Survey> {
        return surveys.filter { survey ->
            val lastDisplayedAt = UserManager.lastDisplayedAt.guard { return@filter true }

            val recontactDays = survey.recontactDays ?: defaultRecontactDays

            if (recontactDays != null) {
                val daysBetween = TimeUnit.MILLISECONDS.toDays(Date().time - lastDisplayedAt.time)
                return@filter daysBetween >= recontactDays.toInt()
            }

            true
        }
    }

    /**
     * Filters the surveys based on the user's segments.
     */
    private fun filterSurveysBasedOnSegments(surveys: List<Survey>, segments: List<String>): List<Survey> {
        return surveys.filter { survey ->
            val segmentId = survey.segment?.id?.guard { return@filter false }
            segments.contains(segmentId)
        }
    }

    /**
     * Test helper method to invoke shouldDisplayBasedOnPercentage
     */
    @JvmStatic
    fun invokeShouldDisplayBasedOnPercentage(displayPercentage: Double?): Boolean {
        return shouldDisplayBasedOnPercentage(displayPercentage)
    }

    private fun shouldDisplayBasedOnPercentage(displayPercentage: Double?): Boolean {
        val percentage = displayPercentage.guard { return true }
        val randomNum = (0 until 10000).random() / 100.0
        return randomNum <= percentage
    }

    /**
     * Gets the language code for a survey based on the requested language.
     * Returns "default" for null, empty, or explicitly requested default language.
     * Returns the matching language code if found and enabled.
     * Returns null if language is not found or disabled.
     */
    @JvmStatic
    fun getLanguageCode(survey: Survey, language: String?): String? {
        // 1) Gather all valid codes
        val availableLanguageCodes = survey.languages
            ?.map { it.language.code }
            ?: emptyList()

        // 2) No input or explicit "default" → default
        val raw = language
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return "default"
        if (raw == "default") return "default"

        // 3) Find matching entry by code or alias
        val selected = survey.languages
            ?.firstOrNull { entry ->
                entry.language.code.lowercase() == raw ||
                        entry.language.alias?.lowercase() == raw
            }

        // 4) If that entry is marked default → default
        if (selected?.default == true) return "default"

        // 5) If missing, disabled, or not in the available list → null
        if (selected == null
            || !selected.enabled
            || !availableLanguageCodes.contains(selected.language.code)
        ) {
            return null
        }

        // 6) Otherwise return its code
        return selected.language.code
    }

}
