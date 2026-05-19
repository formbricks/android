package com.formbricks.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.Keep
import androidx.fragment.app.FragmentManager
import com.formbricks.android.api.FormbricksApi
import com.formbricks.android.helper.FormbricksConfig
import com.formbricks.android.logger.Logger
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.error.SDKError
import com.formbricks.android.model.user.AttributeValue
import com.formbricks.android.webview.FormbricksFragment
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Keep
object Formbricks {
    internal lateinit var applicationContext: Context

    internal lateinit var workspaceId: String
    internal lateinit var appUrl: String
    internal var language: String = "default"
    internal var loggingEnabled: Boolean = true
    private var fragmentManager: FragmentManager? = null
    internal var isInitialized = false

    /** Backward-compatible alias for [workspaceId]. */
    @Deprecated(
        message = "Use workspaceId instead. environmentId will be removed in a future version.",
        replaceWith = ReplaceWith("workspaceId")
    )
    internal var environmentId: String
        get() = workspaceId
        set(value) {
            workspaceId = value
        }

    /**
     * Initializes the Formbricks SDK with the given [Context] config [FormbricksConfig].
     * This method is mandatory to be called, and should be only once per application lifecycle.
     * To show a survey, the SDK needs a [FragmentManager] instance.
     *
     * ```
     * class MainActivity : FragmentActivity() {
     *
     *     override fun onCreate() {
     *         super.onCreate()
     *         val config = FormbricksConfig.Builder("http://localhost:3000","my_workspace_id")
     *             .setLoggingEnabled(true)
     *             .setFragmentManager(supportFragmentManager)
     *            .build())
     *         Formbricks.setup(this, config.build())
     *     }
     * }
     * ```
     *
     */
    fun setup(context: Context, config: FormbricksConfig, forceRefresh: Boolean = false) {
        if (isInitialized && !forceRefresh) {
            val error = SDKError.sdkIsAlreadyInitialized
            Logger.e(error)
            return
        }


        // Validate HTTPS URL — disabled for local testing
//        if (!config.appUrl.startsWith("https://", ignoreCase = true)) {
//            val error = RuntimeException("Only HTTPS URLs are allowed for security reasons. HTTP URLs are not permitted. Provided URL: ${config.appUrl}")
//            Logger.e(error)
//            return
//        }


        applicationContext = context

        appUrl = config.appUrl
        workspaceId = config.workspaceId
        loggingEnabled = config.loggingEnabled
        fragmentManager = config.fragmentManager

        if (config.usedDeprecatedEnvironmentId) {
            Logger.w("environmentId is deprecated and will be removed in a future version. Please use workspaceId instead.")
        }

        config.userId?.let { UserManager.set(it) }
        config.attributes?.let { UserManager.setAttributes(it) }
        config.attributes?.get("language")?.stringValue?.let {
            UserManager.setLanguage(it)
            language = it
        }

        FormbricksApi.initialize()
        SurveyManager.migrateLegacyCacheIfNeeded()
        SurveyManager.refreshWorkspaceIfNeeded(force = forceRefresh)
        UserManager.syncUserStateIfNeeded()

        isInitialized = true
    }

    /**
     * Sets the user id for the current user with the given [String].
     *
     * - If the same userId is already set, this is a no-op.
     * - If a different userId is already set, the previous user state is cleaned up first
     *   before setting the new userId.
     *
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setUserId("my_user_id")
     * ```
     *
     */
    fun setUserId(userId: String) {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }

        // If the same userId is already set, no-op
        val existing = UserManager.userId
        if (existing != null && existing == userId) {
            Logger.d("UserId is already set to the same value, skipping")
            return
        }

        // If a different userId is set, clean up the previous user state first
        if (existing != null && existing.isNotEmpty()) {
            Logger.d("Different userId is being set, cleaning up previous user state")
            UserManager.logout()
        }

        UserManager.set(userId)
    }

    /**
     * Adds a string attribute for the current user.
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setAttribute("John", "name")
     * ```
     *
     */
    fun setAttribute(attribute: String, key: String) {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }
        UserManager.addAttribute(AttributeValue.string(attribute), key)
    }

    /**
     * Adds a numeric attribute for the current user.
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setAttribute(42.0, "age")
     * ```
     *
     */
    fun setAttribute(attribute: Double, key: String) {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }
        UserManager.addAttribute(AttributeValue.number(attribute), key)
    }

    /**
     * Adds an integer attribute for the current user.
     * The value is converted to a [Double] internally.
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setAttribute(42, "age")
     * ```
     *
     */
    fun setAttribute(attribute: Int, key: String) {
        setAttribute(attribute.toDouble(), key)
    }

    /**
     * Adds a date attribute for the current user.
     * The date is converted to an ISO 8601 string. The backend will detect the format and treat it as a date type.
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setAttribute(Date(), "signupDate")
     * ```
     *
     */
    fun setAttribute(attribute: Date, key: String) {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }
        val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        UserManager.addAttribute(AttributeValue.string(iso8601Format.format(attribute)), key)
    }

    /**
     * Sets the user attributes for the current user.
     *
     * Attribute types are determined by the value:
     * - String values -> string attribute
     * - Number values -> number attribute
     * - Use ISO 8601 date strings for date attributes
     *
     * On first write to a new attribute, the type is set based on the value type.
     * On subsequent writes, the value must match the existing attribute type.
     *
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setAttributes(mapOf(
     *     "name" to AttributeValue.string("John"),
     *     "age" to AttributeValue.number(30.0),
     *     "score" to AttributeValue.number(9.5)
     * ))
     * ```
     *
     */
    fun setAttributes(attributes: Map<String, AttributeValue>) {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }
        UserManager.setAttributes(attributes)
    }

    /**
     * Sets the language for the current user with the given [String].
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setLanguage("de")
     * ```
     *
     */
    fun setLanguage(language: String) {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }
        Formbricks.language = language
        UserManager.setLanguage(language)
    }

    /**
     * Tracks an action with the given [String]. The SDK will process the action and it will present the survey if any of them can be triggered.
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.track("button_clicked")
     * ```
     *
     */
    fun track(action: String) {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }

        if (!isInternetAvailable()) {
            val error = SDKError.connectionIsNotAvailable
            Logger.e(error)
            return
        }

        SurveyManager.track(action)
    }

    /**
     * Logs out the current user. This will clear the user attributes and the user id.
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.logout()
     * ```
     *
     */
    fun logout() {
        if (!isInitialized) {
            val error = SDKError.sdkIsNotInitialized
            Logger.e(error)
            return
        }

        UserManager.logout()
    }

    /**
     * Sets the [FragmentManager] instance. The SDK always needs the actual [FragmentManager] to
     * display surveys, so make sure you update it whenever it changes.
     * The SDK must be initialized before calling this method.
     *
     * ```
     * Formbricks.setFragmentManager(supportFragmentMananger)
     * ```
     *
     */
    fun setFragmentManager(fragmentManager: FragmentManager) {
        this.fragmentManager = fragmentManager
    }

    /// Assembles the survey fragment and presents it
    internal fun showSurvey(id: String) {
        if (fragmentManager == null) {
            val error = SDKError.fragmentManagerIsNotSet
            Logger.e(error)
            return
        }

        fragmentManager?.let {
            FormbricksFragment.show(it, surveyId = id)
        }
    }

    /// Checks if the phone has active network connection
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
