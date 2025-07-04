package com.formbricks.android.webview

import android.webkit.WebView
import androidx.databinding.BindingAdapter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.formbricks.android.Formbricks
import com.formbricks.android.extensions.guard
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.manager.UserManager
import com.formbricks.android.model.environment.EnvironmentDataHolder
import com.formbricks.android.model.environment.getProjectStylingJson
import com.formbricks.android.model.environment.getStyling
import com.formbricks.android.model.environment.getSurveyJson
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
        val environment = SurveyManager.environmentDataHolder.guard { return }
        val json = getJson(environment, surveyId)
        val htmlString = htmlTemplate.replace("{{WEBVIEW_DATA}}", json)
        html.postValue(htmlString)
    }

    private fun getJson(environmentDataHolder: EnvironmentDataHolder, surveyId: String): String {
        val jsonObject = JsonObject()
        environmentDataHolder.getSurveyJson(surveyId).let { jsonObject.add("survey", it) }
        jsonObject.addProperty("isBrandingEnabled", environmentDataHolder.data?.data?.project?.inAppSurveyBranding ?: true)
        jsonObject.addProperty("appUrl", Formbricks.appUrl)
        jsonObject.addProperty("environmentId", Formbricks.environmentId)
        jsonObject.addProperty("contactId", UserManager.contactId)
        jsonObject.addProperty("isWebEnvironment", false)

        val isMultiLangSurvey =
            (environmentDataHolder.data?.data?.surveys?.first { it.id == surveyId }?.languages?.size
                ?: 0) > 1

        if (isMultiLangSurvey) {
            jsonObject.addProperty("languageCode", Formbricks.language)
        } else {
            jsonObject.addProperty("languageCode", "default")
        }

        val hasCustomStyling = environmentDataHolder.data?.data?.surveys?.first { it.id == surveyId }?.styling != null
        val enabled = environmentDataHolder.data?.data?.project?.styling?.allowStyleOverwrite ?: false
        if (hasCustomStyling && enabled) {
            environmentDataHolder.getStyling(surveyId)?.let { jsonObject.add("styling", it) }
        } else {
            environmentDataHolder.getProjectStylingJson()?.let { jsonObject.add("styling", it) }
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