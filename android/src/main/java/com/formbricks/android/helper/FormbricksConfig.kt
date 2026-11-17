package com.formbricks.android.helper

import androidx.annotation.Keep
import androidx.fragment.app.FragmentManager
import com.formbricks.android.model.user.AttributeValue

/**
 * Configuration options for the SDK
 *
 * Use the [Builder] to configure the options, then pass the result of [build] to the setup method.
 */
@Keep
public class FormbricksConfig private constructor(
    public val appUrl: String,
    public val workspaceId: String,
    public val userId: String?,
    public val attributes: Map<String, AttributeValue>?,
    public val loggingEnabled: Boolean,
    public val fragmentManager: FragmentManager?,
    /** True if this config was built using the deprecated `environmentId` entry point. */
    public val usedDeprecatedEnvironmentId: Boolean,
    /** How surveys render; null leaves whatever [com.formbricks.android.Formbricks.setAppearance] already set. */
    public val appearance: FormbricksAppearance? = null
) {
    /** Backward-compatible alias for [workspaceId]. */
    @Deprecated(
        message = "Use workspaceId instead. environmentId will be removed in a future version.",
        replaceWith = ReplaceWith("workspaceId")
    )
    public val environmentId: String
        get() = workspaceId

    public class Builder(private val appUrl: String, private val workspaceId: String) {
        private var userId: String? = null
        private var attributes: MutableMap<String, AttributeValue> = mutableMapOf()
        private var loggingEnabled = false
        private var fragmentManager: FragmentManager? = null
        private var usedDeprecatedEnvironmentId: Boolean = false
        private var appearance: FormbricksAppearance? = null

        public fun setUserId(userId: String): Builder {
            this.userId = userId
            return this
        }

        /**
         * Sets the attributes for the Builder object.
         *
         * ```kotlin
         * .setAttributes(mutableMapOf(
         *     "name" to AttributeValue.string("John"),
         *     "age" to AttributeValue.number(30.0)
         * ))
         * ```
         */
        public fun setAttributes(attributes: MutableMap<String, AttributeValue>): Builder {
            this.attributes = attributes
            return this
        }

        /**
         * Sets the attributes for the Builder object using string values.
         *
         * ```kotlin
         * .setStringAttributes(mutableMapOf("name" to "John", "plan" to "free"))
         * ```
         */
        public fun setStringAttributes(attributes: MutableMap<String, String>): Builder {
            this.attributes =
                attributes.mapValues { AttributeValue.string(it.value) }.toMutableMap()
            return this
        }

        /**
         * Adds a string attribute to the Builder object.
         */
        public fun addAttribute(attribute: String, key: String): Builder {
            this.attributes[key] = AttributeValue.string(attribute)
            return this
        }

        public fun setLoggingEnabled(loggingEnabled: Boolean): Builder {
            this.loggingEnabled = loggingEnabled
            return this
        }

        public fun setFragmentManager(fragmentManager: FragmentManager): Builder {
            this.fragmentManager = fragmentManager
            return this
        }

        /** Sets how surveys render: light (default), dark, or system. */
        public fun setAppearance(appearance: FormbricksAppearance): Builder {
            this.appearance = appearance
            return this
        }

        public fun build(): FormbricksConfig = FormbricksConfig(
            appUrl = appUrl,
            workspaceId = workspaceId,
            userId = userId,
            attributes = attributes,
            loggingEnabled = loggingEnabled,
            fragmentManager = fragmentManager,
            usedDeprecatedEnvironmentId = usedDeprecatedEnvironmentId,
            appearance = appearance
        )

        public companion object {
            /**
             * Deprecated factory that accepts the legacy `environmentId` parameter.
             * The value is stored as `workspaceId` internally, and the resulting config is
             * flagged so the SDK can log a deprecation warning on setup.
             */
            @JvmStatic
            @Deprecated(
                message = "Use Builder(appUrl, workspaceId) instead. " +
                    "environmentId will be removed in a future version.",
                replaceWith = ReplaceWith("FormbricksConfig.Builder(appUrl, environmentId)")
            )
            public fun withEnvironmentId(appUrl: String, environmentId: String): Builder =
                Builder(appUrl, environmentId).also {
                    it.usedDeprecatedEnvironmentId = true
                }
        }
    }
}
