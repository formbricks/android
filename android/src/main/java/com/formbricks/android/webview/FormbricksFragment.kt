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
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.formbricks.android.R
import com.formbricks.android.databinding.FragmentFormbricksBinding
import com.formbricks.android.logger.Logger
import com.formbricks.android.manager.SurveyManager
import com.formbricks.android.model.error.SDKError
import com.formbricks.android.model.javascript.CardRect
import com.formbricks.android.model.javascript.FileUploadData
import com.formbricks.android.model.workspace.InteractionSource
import com.formbricks.android.model.workspace.SurveyOverlay
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Shows one survey.
 *
 * A `light` or `dark` overlay is a bottom-sheet dialog: its backdrop is meant to block the host
 * app, and a dialog window blocks everything. `overlay: none` cannot work that way — a dialog is a
 * window of its own and takes every touch inside its bounds, however transparent — so that case is
 * the same fragment without a dialog, with its view placed into the host Activity's content and
 * wrapped in [SurveyPassthroughLayout].
 */
class FormbricksFragment : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentFormbricksBinding
    private lateinit var surveyId: String
    private val viewModel: FormbricksViewModel by viewModels()
    private var isDismissing = false

    private val isPassthrough: Boolean by lazy { arguments?.getBoolean(ARG_PASSTHROUGH) ?: false }

    /** Only set on the no-overlay path. */
    private var passthroughLayout: SurveyPassthroughLayout? = null

    /** Scoped to this showing, so each interaction refreshes segments at most once. */
    private val interactionForwarder = SurveyInteractionForwarder()

    private fun refreshSegmentsOnce(source: InteractionSource) {
        interactionForwarder.refreshOnce(surveyId, source)
    }

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
            refreshSegmentsOnce(InteractionSource.ON_DISPLAY)
        }

        override fun onResponseCreated() {
            try {
                SurveyManager.postResponse(surveyId)
            } catch (e: Exception) {
                val error = SDKError.couldNotCreateResponseError
                Logger.e(error)
            }
            refreshSegmentsOnce(InteractionSource.ON_RESPONSE)
        }

        /**
         * Fires when the survey is completed and the finished response has been accepted by
         * the backend. Only used to refresh interaction-based segments — the sheet is still
         * dismissed by [onClose].
         */
        override fun onFinished() {
            refreshSegmentsOnce(InteractionSource.ON_FINISHED)
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

        override fun onCardRectChange(rect: CardRect?) {
            // JavaScript interface calls arrive on a WebView background thread.
            Handler(Looper.getMainLooper()).post {
                val layout = passthroughLayout ?: return@post
                // CSS pixels to physical pixels: the viewport is pinned at initial-scale=1.0.
                layout.touchRegion = SurveyTouchRegion.forReported(rect, layout.resources.displayMetrics.density)
            }
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
        // Has to happen here: DialogFragment decides whether to build a dialog right after
        // onCreate, and a fragment added without a container would otherwise get one.
        if (isPassthrough) showsDialog = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFormbricksBinding.inflate(inflater).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        binding.viewModel = viewModel

        if (!isPassthrough) return binding.root
        return SurveyPassthroughLayout(requireContext()).also {
            it.addView(binding.root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            passthroughLayout = it
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        setStyle(STYLE_NO_FRAME, R.style.BottomSheetDialog)
        return super.onCreateDialog(savedInstanceState)
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        // The bottom-sheet setup below needs the dialog, which the no-overlay path does not have.
        if (isPassthrough) return
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
        if (isPassthrough) attachToHostContent(view)
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

    /**
     * Puts the survey on top of the host Activity's content.
     *
     * The fragment is added without a container, so the FragmentManager creates the view but
     * places it nowhere. Placing it by hand, rather than adding the fragment into
     * `android.R.id.content`, keeps this working when the host handed us a child FragmentManager,
     * whose container lookup would not find that id and would crash the commit.
     */
    private fun attachToHostContent(view: View) {
        val content = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        content.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // The dialog swallowed back (it is not cancelable). Here back would otherwise reach the
        // host, which may navigate away and leave the survey floating over another screen, so it
        // closes the survey instead.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { safeDismiss() }
    }

    override fun onDestroyView() {
        // The FragmentManager only removes views it placed itself; this one it did not.
        (view?.parent as? ViewGroup)?.removeView(view)
        passthroughLayout = null
        super.onDestroyView()
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
        private const val ARG_PASSTHROUGH = "passthrough"

        fun show(childFragmentManager: FragmentManager, surveyId: String) {
            // The host app stays usable while a no-overlay survey is open, so it can track again
            // mid-survey. Without this a second survey would stack on top of the first.
            val showing = childFragmentManager.findFragmentByTag(TAG)
            if (showing != null && !showing.isRemoving) {
                Logger.d("Skipping survey $surveyId: a survey is already showing.")
                return
            }

            val workspace = SurveyManager.workspaceDataHolder?.data?.data
            val overlay = SurveyOverlay.resolve(
                workspace?.surveys?.firstOrNull { it.id == surveyId }?.projectOverwrites?.overlay,
                workspace?.settings?.overlay,
            )
            val passthrough = overlay == SurveyOverlay.NONE

            val fragment = FormbricksFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SURVEY_ID, surveyId)
                    putBoolean(ARG_PASSTHROUGH, passthrough)
                }
            }
            if (passthrough) {
                // No container: the fragment places its own view (see attachToHostContent).
                childFragmentManager.beginTransaction().add(fragment, TAG).commit()
            } else {
                fragment.show(childFragmentManager, TAG)
            }
        }
    }
}