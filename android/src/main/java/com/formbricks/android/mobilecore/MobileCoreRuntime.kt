package com.formbricks.android.mobilecore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.formbricks.android.logger.Logger
import com.google.gson.Gson

/**
 * Hosts the server-delivered mobile core bundle in a hidden [WebView] and exposes its
 * decision API to the shell. Google Play's device-and-network-abuse policy exempts
 * code running in a WebView/JS interpreter from the "no downloaded executable code"
 * rule — this is that path, mirroring the JavaScriptCore host on iOS.
 *
 * The runtime is intentionally dumb about survey logic: it serializes state in,
 * gets a decision out, and never interprets the rules itself. The WebView is never
 * attached to a window; it exists purely as a JS engine.
 *
 * All WebView interaction happens on the main thread ([WebView.evaluateJavascript]
 * requirement); decision callbacks are therefore invoked on the main thread too.
 */
internal class MobileCoreRuntime private constructor(private val webView: WebView) {

    fun selectSurvey(
        action: String,
        workspaceStateJson: String,
        userState: MobileCoreUserState,
        language: String,
        callback: (MobileCoreDecision?) -> Unit
    ) {
        val call = """
            JSON.stringify(globalThis.$GLOBAL_NAME.selectSurvey({
              action: ${gson.toJson(action)},
              workspaceState: $workspaceStateJson,
              userState: ${gson.toJson(userState)},
              language: ${gson.toJson(language)},
              nowMs: Date.now(),
            }))
        """.trimIndent()

        mainHandler.post {
            webView.evaluateJavascript(call) { raw ->
                // A JS exception surfaces as the literal string "null".
                val decision = try {
                    // evaluateJavascript returns the JS value JSON-encoded, so the
                    // stringified decision arrives as a quoted JSON string literal.
                    val json = gson.fromJson(raw, String::class.java)
                    json?.let { gson.fromJson(it, MobileCoreDecision::class.java) }
                } catch (e: Exception) {
                    null
                }

                if (decision == null) {
                    Logger.w("Mobile core returned an unreadable decision for action '$action'.")
                }
                callback(decision)
            }
        }
    }

    fun destroy() {
        mainHandler.post { webView.destroy() }
    }

    companion object {
        /** Global the bundle must define: `globalThis.formbricksMobileCore = { protocolVersion, selectSurvey }`. */
        private const val GLOBAL_NAME = "formbricksMobileCore"

        private val gson = Gson()
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * Creates the runtime by evaluating the bundle in a fresh off-screen WebView.
         * Calls back with `null` (from the main thread) when the bundle doesn't
         * evaluate, doesn't define the expected global, or speaks a different bridge
         * protocol than this shell — the caller then keeps using native logic.
         */
        @SuppressLint("SetJavaScriptEnabled")
        fun create(context: Context, bundleSource: String, onReady: (MobileCoreRuntime?) -> Unit) {
            mainHandler.post {
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = true
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        webView.evaluateJavascript(bundleSource) { _ ->
                            validate(webView, onReady)
                        }
                    }
                }
                // A blank page is enough; the bundle is evaluated into it once loaded.
                webView.loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
            }
        }

        private fun validate(webView: WebView, onReady: (MobileCoreRuntime?) -> Unit) {
            val probe = "globalThis.$GLOBAL_NAME && typeof globalThis.$GLOBAL_NAME.selectSurvey === 'function'" +
                " ? globalThis.$GLOBAL_NAME.protocolVersion : null"
            webView.evaluateJavascript(probe) { result ->
                val version = result?.trim()?.toIntOrNull()
                when (version) {
                    MobileCoreLoader.BRIDGE_PROTOCOL_VERSION -> onReady(MobileCoreRuntime(webView))
                    null -> {
                        Logger.e(RuntimeException("Mobile core bundle did not define $GLOBAL_NAME.selectSurvey."))
                        webView.destroy()
                        onReady(null)
                    }
                    else -> {
                        Logger.e(RuntimeException("Mobile core bundle speaks bridge protocol v$version, shell speaks v${MobileCoreLoader.BRIDGE_PROTOCOL_VERSION}. Ignoring bundle."))
                        webView.destroy()
                        onReady(null)
                    }
                }
            }
        }
    }
}
