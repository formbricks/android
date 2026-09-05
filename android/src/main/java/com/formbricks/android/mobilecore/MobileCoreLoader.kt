package com.formbricks.android.mobilecore

import android.content.Context
import com.formbricks.android.Formbricks
import com.formbricks.android.logger.Logger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the server-delivered mobile core bundle (the JS "brain") and caches the
 * last successfully fetched copy, so the SDK keeps working offline and survives a
 * temporarily unreachable server. The bundle is versioned by bridge protocol in its
 * URL path: a v1 shell only ever asks for a v1-compatible bundle.
 */
internal object MobileCoreLoader {

    /** Bridge protocol version this shell speaks. Bump only on breaking bridge changes. */
    const val BRIDGE_PROTOCOL_VERSION = 1

    private const val FORMBRICKS_PREFS = "formbricks_prefs"
    internal const val PREF_BUNDLE = "mobileCoreBundleKey"
    internal const val PREF_BUNDLE_URL = "mobileCoreBundleURLKey"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val prefManager
        get() = Formbricks.applicationContext.getSharedPreferences(FORMBRICKS_PREFS, Context.MODE_PRIVATE)

    fun bundleUrl(appUrl: String): String =
        "${appUrl.trimEnd('/')}/js/mobile/v$BRIDGE_PROTOCOL_VERSION/core.umd.cjs"

    /**
     * Fetches the bundle from the server, falling back to the cached copy on any failure.
     * The completion is called from a background thread with the JS source, or `null`
     * when neither network nor cache can provide one (the shell then falls back to its
     * built-in native logic).
     */
    fun load(appUrl: String, completion: (String?) -> Unit) {
        val url = bundleUrl(appUrl)
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            completion(cachedBundle(appUrl))
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.w("Unable to fetch mobile core bundle from $url, falling back to cached copy.")
                completion(cachedBundle(appUrl))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val source = if (it.isSuccessful) it.body?.string() else null
                    if (source.isNullOrEmpty()) {
                        Logger.w("Unable to fetch mobile core bundle from $url, falling back to cached copy.")
                        completion(cachedBundle(appUrl))
                        return
                    }

                    cache(source, appUrl)
                    completion(source)
                }
            }
        })
    }

    private fun cache(bundle: String, appUrl: String) {
        prefManager.edit()
            .putString(PREF_BUNDLE, bundle)
            .putString(PREF_BUNDLE_URL, appUrl)
            .apply()
    }

    private fun cachedBundle(appUrl: String): String? {
        // A cached brain from a different host must not run against this one.
        if (prefManager.getString(PREF_BUNDLE_URL, null) != appUrl) return null
        return prefManager.getString(PREF_BUNDLE, null)
    }
}
