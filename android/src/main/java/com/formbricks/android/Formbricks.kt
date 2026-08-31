package com.formbricks.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import androidx.fragment.app.FragmentManager
import com.formbricks.android.api.FormbricksApi
import com.formbricks.android.helper.FormbricksConfig
import com.formbricks.android.logger.Logger
import com.formbricks.android.manager.EmbeddedDataManager
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.embeddeddata.EmbeddedDataValue
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


        // Validate HTTPS URL
        if (!config.appUrl.startsWith("https://", ignoreCase = true)) {
            val error = RuntimeException("Only HTTPS URLs are allowed for security reasons. HTTP URLs are not permitted. Provided URL: ${config.appUrl}")
            Logger.e(error)
            return
        }


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
            // An identity switch: the ambient Embedded Data bag may carry the previous user's
            // context, which must not ride onto the next user's responses on a shared device.
            // First-time identification keeps the bag - a host legitimately pushes context before
            // it knows who the user is.
            EmbeddedDataManager.clear()
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
        // Same identity-switch rule as setUserId: logout must not let the previous user's ambient
        // context leak onto whoever uses the app next.
        EmbeddedDataManager.clear()
    }

    /**
     * Attaches Embedded Data to future responses without tying it to a trigger.
     *
     * Merges into an in-memory bag - last write wins per key, and an explicit `null` removes a key.
     * Values land only on the survey's declared *ingested* fields; anything else is dropped and
     * logged by the survey renderer, never fatal.
     *
     * Deliberately callable **before** [setup], unlike the methods above: a host that pushes context
     * at launch must not have that value silently dropped because initialization had not finished.
     * The bag is pure memory - nothing here needs the SDK to be running.
     *
     * The bag is snapshotted when a survey is displayed and frozen for its lifetime, so a value set
     * while a survey is on screen reaches the *next* response, not that one. It is never persisted:
     * a cold app start begins empty and the host re-pushes.
     *
     * ```kotlin
     * Formbricks.setEmbeddedData(mapOf(
     *     "plan" to EmbeddedDataValue.string("pro"),
     *     "seats" to EmbeddedDataValue.number(25.0),
     *     "screen" to null,   // removes the key
     * ))
     * ```
     */
    fun setEmbeddedData(data: Map<String, EmbeddedDataValue?>) {
        EmbeddedDataManager.set(data)
    }

    /**
     * Removes one Embedded Data key. A key that was never set is a no-op.
     *
     * The single-key and clear-everything forms are separate overloads on purpose: a `String` that
     * cannot be null means a host reading the key from its own state cannot accidentally wipe the
     * whole bag.
     *
     * ```kotlin
     * Formbricks.clearEmbeddedData("plan")
     * ```
     */
    fun clearEmbeddedData(key: String) {
        EmbeddedDataManager.remove(key)
    }

    /**
     * Clears the whole Embedded Data bag - logout, or a hard context switch.
     *
     * ```kotlin
     * Formbricks.clearEmbeddedData()
     * ```
     */
    fun clearEmbeddedData() {
        EmbeddedDataManager.clear()
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

    /// Assembles the survey fragment and presents it.
    ///
    /// This is called from `SurveyManager`'s display timer, which runs on a
    /// `java.util.Timer` thread. `DialogFragment.show()` commits a fragment
    /// transaction, and androidx requires that on the main thread — committing from
    /// the timer thread crashed the host app. The stored `FragmentManager` can also
    /// outlive the Activity it came from, in which case committing throws
    /// `IllegalStateException: FragmentManager has been destroyed`. See
    /// https://github.com/formbricks/android/issues/43.
    internal fun showSurvey(id: String) {
        Handler(Looper.getMainLooper()).post {
            // Read the manager here rather than at schedule time: the host may have handed us
            // a newer one via `setFragmentManager` in between, and using the stale reference
            // would report "destroyed" while a usable manager was available.
            val manager = fragmentManager
            if (manager == null) {
                Logger.e(SDKError.fragmentManagerIsNotSet)
                return@post
            }

            // The Activity that owned this manager is gone. Showing is impossible, and
            // the host has to hand us a live one.
            if (manager.isDestroyed) {
                Logger.e(SDKError.fragmentManagerIsDestroyed)
                return@post
            }

            // A commit after onSaveInstanceState throws. Skipping is the right call:
            // the host is on its way to the background, so there is nothing to show.
            if (manager.isStateSaved) {
                Logger.d("Skipping survey $id: the host has already saved its state.")
                return@post
            }

            try {
                FormbricksFragment.show(manager, surveyId = id)
            } catch (e: IllegalStateException) {
                // Backstop for any remaining commit-time race: failing to show a survey
                // must never take the host app down.
                Logger.e(RuntimeException("Unable to show survey $id: ${e.message}"))
            }
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
