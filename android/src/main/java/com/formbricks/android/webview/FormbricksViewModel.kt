package com.formbricks.android.webview

import android.webkit.WebView
import androidx.databinding.BindingAdapter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.formbricks.android.Formbricks
import com.formbricks.android.extensions.guard
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
                const json = `{{WEBVIEW_DATA}}`;

                function onClose() {
                    FormbricksJavascript.message(JSON.stringify({ event: "onClose" }));
                };

                function onDisplayCreated() {
                    FormbricksJavascript.message(JSON.stringify({ event: "onDisplayCreated" }));
                };

                function onResponseCreated() {
                    FormbricksJavascript.message(JSON.stringify({ event: "onResponseCreated" }));
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
        val htmlString = htmlTemplate.replace("{{WEBVIEW_DATA}}", json)
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

        val matchedSurvey = workspaceDataHolder.data?.data?.surveys?.first { it.id == surveyId }
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

        return jsonObject.toString()
            .replace("#", "%23") // Hex color code's # breaks the JSON
            .replace("\\\"","'") // " is replaced to ' in the html codes in the JSON
    }
}

@BindingAdapter("htmlText")
fun WebView.setHtmlText(htmlString: String?) {
    loadData(htmlString ?: "", "text/html", "UTF-8")
}
