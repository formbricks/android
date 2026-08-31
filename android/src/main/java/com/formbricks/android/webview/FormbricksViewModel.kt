package com.formbricks.android.webview

import android.util.Base64
import android.webkit.WebView
import androidx.databinding.BindingAdapter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.formbricks.android.Formbricks
import com.formbricks.android.extensions.guard
import com.formbricks.android.manager.EmbeddedDataManager
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.workspace.WorkspaceDataHolder
import com.formbricks.android.model.workspace.SurveyOverlay
import com.formbricks.android.model.workspace.getSettingsStylingJson
import com.formbricks.android.model.workspace.getStyling
import com.formbricks.android.model.workspace.getSurveyJson
import com.google.gson.JsonObject

/**
 * A view model for the Formbricks WebView.
 * It generates the HTML string with the necessary data to render the survey.
 */
class FormbricksViewModel : ViewModel() {
    var html = MutableLiveData<String>()

    /**
     * The HTML template to render the Formbricks WebView.
     */
    private val htmlTemplate = """
 <!doctype html>
        <html>
            <meta name="viewport" content="initial-scale=1.0, maximum-scale=1.0">

            <head>
                <title>Formbricks WebView Survey</title>
            </head>

            <body style="overflow: hidden; height: 100vh; display: flex; flex-direction: column; justify-content: flex-end;">
                <div id="formbricks-android" style="width: 100%;"></div>
            </body>

            <script type="text/javascript">
                // {{WEBVIEW_DATA}} is a base64-encoded JSON string (see loadHtml). Decoding
                // it here means the survey payload is never parsed as JS source, so survey
                // content cannot break out of a string literal and execute as code.
                const json = new TextDecoder().decode(Uint8Array.from(atob("{{WEBVIEW_DATA}}"), c => c.charCodeAt(0)));

                function onClose() {
                    FormbricksJavascript.message(JSON.stringify({ event: "onClose" }));
                };

                function onDisplayCreated() {
                    FormbricksJavascript.message(JSON.stringify({ event: "onDisplayCreated" }));
                };

                function onResponseCreated() {
                    FormbricksJavascript.message(JSON.stringify({ event: "onResponseCreated" }));
                };

                // Fires once the finished response has been accepted by the backend — the
                // surveys library gates this on `isResponseSendingFinished`, and we supply
                // `getSetIsResponseSendingFinished` below, so it starts out false.
                function onFinished() {
                    FormbricksJavascript.message(JSON.stringify({ event: "onFinished" }));
                };

                let setResponseFinished = null;
                function getSetIsResponseSendingFinished(callback) {
                    setResponseFinished = callback;
                }

                function loadSurvey() {
                    const options = JSON.parse(json);
                    const surveyProps = {
                        ...options,
                        getSetIsResponseSendingFinished,
                        onDisplayCreated,
                        onResponseCreated,
                        onFinished,
                        onClose,
                    };

                    window.formbricksSurveys.renderSurvey(surveyProps);
                };

              function attachFilePickerOverride() {
                const inputs = document.querySelectorAll('input[type="file"]');
                  inputs.forEach(input => {
                    if (!input.getAttribute('data-file-picker-overridden')) {
                      input.setAttribute('data-file-picker-overridden', 'true');

                      const allowedFileExtensions = input.getAttribute('data-accept-extensions');
                      const allowMultipleFiles = input.getAttribute('data-accept-multiple');

                      input.addEventListener('click', function (e) {
                        e.preventDefault();
                        FormbricksJavascript.message(JSON.stringify({
                          event: "onFilePick",
                          fileUploadParams: {
                            allowedFileExtensions: allowedFileExtensions,
                            allowMultipleFiles: allowMultipleFiles === "true",
                          }
                        }));
                      });
                    }
                  });
                };

              attachFilePickerOverride();

              const observer = new MutationObserver(function (mutations) {
                attachFilePickerOverride();
              });

              observer.observe(document.body, { childList: true, subtree: true });
                const script = document.createElement("script");
                script.src = "${Formbricks.appUrl}/js/surveys.umd.cjs";
                script.async = true;
                script.onload = () => loadSurvey();
                script.onerror = (error) => {
                    FormbricksJavascript.message(JSON.stringify({ event: "onSurveyLibraryLoadError" }));
                    console.error("Failed to load Formbricks Surveys library:", error);
                };
                document.head.appendChild(script);
            </script>
        </html>
"""

    fun loadHtml(surveyId: String) {
        val workspace = SurveyManager.workspaceDataHolder.guard { return }
        val json = getJson(workspace, surveyId)
        // Base64-encode the payload before embedding it in the HTML. Base64 output is
        // limited to [A-Za-z0-9+/=], so survey content can no longer contain characters
        // (backticks, `${...}`, quotes, "</script>") that would break out of the
        // surrounding JS string and execute. The WebView decodes it back to JSON.
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val htmlString = htmlTemplate.replace("{{WEBVIEW_DATA}}", encoded)
        html.postValue(htmlString)
    }

    private fun getJson(workspaceDataHolder: WorkspaceDataHolder, surveyId: String): String {
        val jsonObject = JsonObject()
        workspaceDataHolder.getSurveyJson(surveyId).let { jsonObject.add("survey", it) }
        jsonObject.addProperty("isBrandingEnabled", workspaceDataHolder.data?.data?.settings?.inAppSurveyBranding ?: true)
        jsonObject.addProperty("appUrl", Formbricks.appUrl)
        jsonObject.addProperty("workspaceId", Formbricks.workspaceId)
        // Keep `environmentId` in the payload for backward compatibility with older
        // survey-script versions that still read it.
        jsonObject.addProperty("environmentId", Formbricks.workspaceId)
        jsonObject.addProperty("contactId", UserManager.contactId)
        jsonObject.addProperty("isWebEnvironment", false)
        // The Embedded Data bag, snapshotted here - loadHtml runs when the survey is actually
        // presented, after any configured delay - and frozen for the survey's life. Passed raw and
        // unfiltered: the ingest contract (allow-list, coercion, `locked`, size caps) lives in the
        // renderer, so all four mobile SDKs inherit the same rules without each shipping a copy, and
        // the server re-runs all of it on ingest.
        jsonObject.add("hiddenFieldsRecord", EmbeddedDataManager.snapshot())

        val matchedSurvey = workspaceDataHolder.data?.data?.surveys?.firstOrNull { it.id == surveyId }
        val settings = workspaceDataHolder.data?.data?.settings

        val isMultiLangSurvey =
            (matchedSurvey?.languages?.size
                ?: 0) > 1

        if (isMultiLangSurvey) {
            jsonObject.addProperty("languageCode", Formbricks.language)
        } else {
            jsonObject.addProperty("languageCode", "default")
        }

        val hasCustomStyling = matchedSurvey?.styling != null

        val placement = matchedSurvey?.projectOverwrites?.placement ?: settings?.placement
        if (placement != null) jsonObject.addProperty("placement", placement)

        val clickOutside = matchedSurvey?.projectOverwrites?.clickOutsideClose ?: settings?.clickOutsideClose ?: false
        jsonObject.addProperty("clickOutside", clickOutside)

        val overlay = (matchedSurvey?.projectOverwrites?.overlay ?: settings?.overlay ?: SurveyOverlay.NONE).value
        jsonObject.addProperty("overlay", overlay)

        val enabled = settings?.styling?.allowStyleOverwrite ?: false
        if (hasCustomStyling && enabled) {
            workspaceDataHolder.getStyling(surveyId)?.let { jsonObject.add("styling", it) }
        } else {
            workspaceDataHolder.getSettingsStylingJson()?.let { jsonObject.add("styling", it) }
        }

        // Return valid JSON as-is. It is base64-encoded before being embedded in the
        // HTML (see loadHtml), so no character-level escaping is needed here (the old
        // #->%23 and \"->' workarounds corrupted survey text and are no longer required).
        return jsonObject.toString()
    }
}

@BindingAdapter("htmlText")
fun WebView.setHtmlText(htmlString: String?) {
    // loadDataWithBaseURL (null base URL) loads the document verbatim, without the
    // URL-decoding that loadData applies to its data: URL. That keeps the base64-encoded
    // survey payload (which may contain +, / and =) intact. Mirrors iOS's baseURL: nil.
    loadDataWithBaseURL(null, htmlString ?: "", "text/html", "UTF-8", null)
}
