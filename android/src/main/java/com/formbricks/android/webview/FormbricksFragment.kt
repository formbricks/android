package com.formbricks.android.webview

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.formbricks.android.R
import com.formbricks.android.databinding.FragmentFormbricksBinding
import com.formbricks.android.logger.Logger
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.model.error.SDKError
import com.formbricks.android.model.javascript.FileUploadData
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

class FormbricksFragment : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentFormbricksBinding
    private lateinit var surveyId: String
    private val viewModel: FormbricksViewModel by viewModels()
    private var isDismissing = false

    private var webAppInterface = WebAppInterface(object : WebAppInterface.WebAppCallback {
        override fun onClose() {
            Handler(Looper.getMainLooper()).post {
                safeDismiss()
            }
        }

        override fun onDisplayCreated() {
            try {
                SurveyManager.onNewDisplay(surveyId)
            } catch (e: Exception) {
                val error = SDKError.couldNotCreateDisplayError
                Logger.e(error)
            }
        }

        override fun onResponseCreated() {
            try {
                SurveyManager.postResponse(surveyId)
            } catch (e: Exception) {
                val error = SDKError.couldNotCreateResponseError
                Logger.e(error)
            }
        }

        override fun onFilePick(data: FileUploadData) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, data.fileUploadParams.allowedExtensionsArray())
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, data.fileUploadParams.allowMultipleFiles)

            resultLauncher.launch(intent)
        }

        override fun onSurveyLibraryLoadError() {
            val error = SDKError.unableToLoadFormbicksJs
            Logger.e(error)
            safeDismiss()
        }
    })

    var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent: Intent? = result.data
            var uriArray: MutableList<Uri> = mutableListOf()

            val dataString = intent?.dataString
            if (null != dataString) {
                uriArray = arrayOf(Uri.parse(dataString)).toMutableList()
            } else {
                val clipData = intent?.clipData
                if (null != clipData) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        uriArray.add(uri)
                    }
                }
            }

            val jsonArray = com.google.gson.JsonArray()
            uriArray.forEach { uri ->
                val type = activity?.contentResolver?.getType(uri)
                val fileName = getFileName(uri)
                val base64 = "data:${type};base64,${uriToBase64(uri)}"
                val json = JsonObject()
                json.addProperty("name", fileName)
                json.addProperty("type", type)
                json.addProperty("base64", base64)
                jsonArray.add(json)
            }
            binding.formbricksWebview.evaluateJavascript("""window.formbricksSurveys.onFilePick($jsonArray)""") { result ->
                print(result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            surveyId = it.getString(ARG_SURVEY_ID) ?: throw IllegalArgumentException("Survey ID is required")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFormbricksBinding.inflate(inflater).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        binding.viewModel = viewModel

        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        setStyle(STYLE_NO_FRAME, R.style.BottomSheetDialog)
        return super.onCreateDialog(savedInstanceState)
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        val view: FrameLayout = dialog?.findViewById(com.google.android.material.R.id.design_bottom_sheet)!!
        view.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        val behavior = BottomSheetBehavior.from(view)
        behavior.peekHeight = resources.displayMetrics.heightPixels
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.isFitToContents = false
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED)

        dialog?.setCancelable(false)

        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setDimAmount(0.0f)
        binding.formbricksWebview.setBackgroundColor(Color.TRANSPARENT)
        binding.formbricksWebview.let {
            // First configure the WebView
            it.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            it.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let { cm ->
                        val log = "[CONSOLE:${cm.messageLevel()}] \"${cm.message()}\", source: ${cm.sourceId()} (${cm.lineNumber()})"
                        Logger.d(log)
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            it.webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Logger.d("WebView Error: ${error?.description}")
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    dialog?.window?.setDimAmount(0.5f)
                    super.onPageCommitVisible(view, url)
                }
            }

            it.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
            }

            it.setInitialScale(1)
            it.addJavascriptInterface(webAppInterface, WebAppInterface.INTERFACE_NAME)
            viewModel.loadHtml(surveyId)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        activity?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun uriToBase64(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = activity?.contentResolver?.openInputStream(uri)
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var bytesRead: Int

            while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            inputStream?.close()
            outputStream.close()

            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun safeDismiss() {
        if (isDismissing) return
        isDismissing = true
        
        try {
            if (isAdded && !isStateSaved) {
                dismiss()
            } else {
                // If we can't dismiss safely, just finish the activity
                activity?.finish()
            }
        } catch (e: Exception) {
            val error = SDKError.somethingWentWrongError
            Logger.e(error)
            activity?.finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isDismissing = false
    }

    companion object {
        private val TAG: String by lazy { FormbricksFragment::class.java.simpleName }
        private const val ARG_SURVEY_ID = "survey_id"

        fun show(childFragmentManager: FragmentManager, surveyId: String) {
            val fragment = FormbricksFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SURVEY_ID, surveyId)
                }
            }
            fragment.show(childFragmentManager, TAG)
        }
    }
}