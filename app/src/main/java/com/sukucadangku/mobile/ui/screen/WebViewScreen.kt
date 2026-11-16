package com.sukucadangku.mobile.ui.screen

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.web.*
import com.sukucadangku.mobile.BuildConfig
import com.sukucadangku.mobile.MainViewModel
import com.sukucadangku.mobile.R
import com.sukucadangku.mobile.ui.component.rememberFileChooserLauncher
import com.sukucadangku.mobile.util.Constants
import com.sukucadangku.mobile.util.WebViewJavascriptBridge
import timber.log.Timber
import androidx.core.net.toUri

private val INTERNAL_DOMAINS = listOf(
    "sukucadangku.com",
    "www.sukucadangku.com",
    "api.sukucadangku.com",
    "dev.sukucadangku.com",
    "staging.sukucadangku.com",
    "localhost",
    "10.0.2.2",
    "https://authorix.id/"
)

// Allowed embedded domains for Midtrans Snap popups
private val SNAP_POPUP_DOMAINS = listOf(
    "app.midtrans.com",
    "app.sandbox.midtrans.com",
    "api.midtrans.com"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val url by viewModel.webViewUrl.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasError by viewModel.hasError.collectAsStateWithLifecycle()

    val webViewState = rememberWebViewState(url = url)
    val navigator = rememberWebViewNavigator()

    // JS dialog states
    var jsAlertState by remember { mutableStateOf<JsDialogState?>(null) }
    var jsConfirmState by remember { mutableStateOf<JsDialogState?>(null) }
    var jsPromptState by remember { mutableStateOf<JsPromptDialogState?>(null) }

    // File chooser
    val fileChooserLauncher = rememberFileChooserLauncher()

    // Progress
    var loadingProgress by remember { mutableFloatStateOf(0f) }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoading)

    // Update canGoBack in ViewModel
    LaunchedEffect(navigator.canGoBack) {
        viewModel.updateCanGoBack(navigator.canGoBack)
    }

    // Pass navigator reference to ViewModel for back press & retry
    LaunchedEffect(navigator) {
        viewModel.setNavigator(navigator)
    }

    Box(modifier = modifier.fillMaxSize()) {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                navigator.reload()
            }
        ) {
            WebView(
                state = webViewState,
                navigator = navigator,
                modifier = Modifier.fillMaxSize(),
                onCreated = { webView ->
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        setSupportMultipleWindows(true)
                        @Suppress("DEPRECATION")
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mediaPlaybackRequiresUserGesture = false
                        val defaultUserAgent = userAgentString
                        userAgentString = "$defaultUserAgent ${Constants.USER_AGENT_SUFFIX}"
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        databaseEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                    }

                    // Cookie settings - keep JWT session persistent
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webView, true)
                    }

                    // JavaScript Bridge to native
                    webView.addJavascriptInterface(
                        WebViewJavascriptBridge(context) {
                            (context as? android.app.Activity)?.finish()
                        },
                        Constants.JS_BRIDGE_NAME
                    )

                    // Download listener
                    webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                        try {
                            val filename = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                                setMimeType(mimetype)
                                addRequestHeader("User-Agent", userAgent)
                                setDescription("Mengunduh $filename...")
                                setTitle(filename)
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    filename
                                )
                            }
                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            dm.enqueue(request)
                            Timber.d("Download enqueued: $downloadUrl")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to enqueue download")
                            handleExternalUri(context, downloadUrl)
                        }
                    }
                },
                client = remember {
                    object : AccompanistWebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest?
                        ): Boolean {
                            val requestUrl = request?.url?.toString() ?: return false
                            return handleUrlOverride(context, view, requestUrl)
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            viewModel.onPageStarted()
                            Timber.d("Page started: $url")
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            viewModel.onPageFinished()
                            // Sync cookies to persist JWT
                            CookieManager.getInstance().flush()
                            Timber.d("Page finished: $url")
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                val errorCode =
                                    error?.errorCode ?: -1
                                val description =
                                    error?.description?.toString() ?: "Unknown error"
                                Timber.e("WebView error: code=$errorCode, desc=$description")
                                viewModel.onError()
                            }
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) {
                            if (BuildConfig.DEBUG) {
                                Timber.w("SSL Error ignored in debug build: ${error?.primaryError}")
                                handler?.proceed()
                            } else {
                                Timber.e("SSL Error (RELEASE - cancelling): ${error?.primaryError}")
                                handler?.cancel()
                                viewModel.onError()
                            }
                        }
                    }
                },
                chromeClient = remember {
                    object : AccompanistWebChromeClient() {
                        override fun onShowFileChooser(
                            view: WebView,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            fileChooserLauncher.launch(filePathCallback, fileChooserParams)
                            return true
                        }

                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            loadingProgress = newProgress / 100f
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.let { permReq ->
                                val granted = permReq.resources.filter { resource ->
                                    resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                                    resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                                }.toTypedArray()
                                if (granted.isNotEmpty()) {
                                    permReq.grant(granted)
                                } else {
                                    permReq.deny()
                                }
                            }
                        }

                        override fun onCreateWindow(
                            view: WebView,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            if (resultMsg == null) return false

                            // Handle Midtrans Snap popup window
                            val newWebView = WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.setSupportMultipleWindows(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        innerView: WebView?,
                                        innerRequest: WebResourceRequest?
                                    ): Boolean {
                                        val innerUrl = innerRequest?.url?.toString() ?: return false
                                        return handleUrlOverride(context, innerView, innerUrl)
                                    }
                                }
                            }

                            val transport = resultMsg.obj as? WebView.WebViewTransport
                            transport?.webView = newWebView
                            resultMsg.sendToTarget()
                            return true
                        }

                        override fun onJsAlert(
                            view: WebView,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            jsAlertState = JsDialogState(message ?: "", result)
                            return true
                        }

                        override fun onJsConfirm(
                            view: WebView,
                            url: String?,
                            message: String?,
                            result: JsResult?
                        ): Boolean {
                            jsConfirmState = JsDialogState(message ?: "", result)
                            return true
                        }

                        override fun onJsPrompt(
                            view: WebView,
                            url: String?,
                            message: String?,
                            defaultValue: String?,
                            result: JsPromptResult?
                        ): Boolean {
                            jsPromptState = JsPromptDialogState(message ?: "", defaultValue ?: "", result)
                            return true
                        }
                    }
                }
            )
        }

        // Loading progress bar
        AnimatedVisibility(
            visible = isLoading && loadingProgress < 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { loadingProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Loading overlay screen (initial load only)
        AnimatedVisibility(
            visible = isLoading && loadingProgress == 0f,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingScreen()
        }

        // Error screen
        AnimatedVisibility(
            visible = hasError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ErrorScreen(
                onRetry = {
                    viewModel.onRetry()
                }
            )
        }
    }

    // Native JS Dialogs
    jsAlertState?.let { state ->
        AlertDialog(
            onDismissRequest = {
                state.result?.cancel()
                jsAlertState = null
            },
            title = { Text(stringResource(R.string.dialog_title_alert)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = {
                    state.result?.confirm()
                    jsAlertState = null
                }) {
                    Text(stringResource(R.string.btn_ok))
                }
            }
        )
    }

    jsConfirmState?.let { state ->
        AlertDialog(
            onDismissRequest = {
                state.result?.cancel()
                jsConfirmState = null
            },
            title = { Text(stringResource(R.string.dialog_title_confirm)) },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = {
                    state.result?.confirm()
                    jsConfirmState = null
                }) {
                    Text(stringResource(R.string.btn_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    state.result?.cancel()
                    jsConfirmState = null
                }) {
                    Text(stringResource(R.string.btn_no))
                }
            }
        )
    }

    jsPromptState?.let { state ->
        var promptInput by remember { mutableStateOf(state.defaultValue) }
        AlertDialog(
            onDismissRequest = {
                state.result?.cancel()
                jsPromptState = null
            },
            title = { Text(stringResource(R.string.dialog_title_prompt)) },
            text = {
                Column {
                    Text(state.message)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.result?.confirm(promptInput)
                    jsPromptState = null
                }) {
                    Text(stringResource(R.string.btn_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    state.result?.cancel()
                    jsPromptState = null
                }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * Handle URL redirection logic:
 * 1. Android Intent schemes (e.g. intent:// for GoPay, ShopeePay, Dana, Bank apps)
 * 2. Deep links and non-HTTP schemes (gojek://, tel:, mailto:, whatsapp:)
 * 3. Midtrans Snap popups (kept in WebView)
 * 4. Internal website domains (kept in WebView)
 * 5. External third-party links (opened in external browser)
 */
@SuppressLint("QueryPermissionsNeeded")
private fun handleUrlOverride(context: Context, view: WebView?, requestUrl: String): Boolean {
    // 1. Handle intent:// URLs (standard e-wallet deep link)
    if (requestUrl.startsWith("intent://")) {
        try {
            val intent = Intent.parseUri(requestUrl, Intent.URI_INTENT_SCHEME)
            if (intent != null) {
                val packageManager = context.packageManager
                if (intent.resolveActivity(packageManager) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
                // Try browser fallback url if app is not installed
                val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                if (!fallbackUrl.isNullOrEmpty()) {
                    view?.loadUrl(fallbackUrl)
                    return true
                }
                // Fallback to Google Play Store
                val appPackage = intent.`package`
                if (!appPackage.isNullOrEmpty()) {
                    val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$appPackage".toUri())
                    marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(marketIntent)
                    return true
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve intent scheme: $requestUrl")
        }
        return true
    }

    // 2. Handle non-HTTP app schemes (e.g. gojek://, shopeepay://, tel:, mailto:, whatsapp:)
    if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://") && !requestUrl.startsWith("javascript:")) {
        return handleExternalUri(context, requestUrl)
    }

    val uri = requestUrl.toUri()
    val host = uri.host ?: return false

    // 3. Keep Midtrans Snap popup domains inside WebView
    val isSnapDomain = SNAP_POPUP_DOMAINS.any { domain ->
        host == domain || host.endsWith(".$domain")
    }
    if (isSnapDomain) {
        return false // Let WebView load it
    }

    // 4. Keep internal app domains inside WebView
    val isInternalDomain = INTERNAL_DOMAINS.any { domain ->
        host == domain || host.endsWith(".$domain")
    }
    if (isInternalDomain) {
        return false // Let WebView load it
    }

    // 5. External domain -> open in external browser
    handleExternalUri(context, requestUrl)
    return true
}

private fun handleExternalUri(context: Context, url: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Timber.e(e, "No app found to handle URI: $url")
        false
    }
}

// ---------------------------------------------------------------------------
// Data classes for JS dialogs
// ---------------------------------------------------------------------------

private data class JsDialogState(
    val message: String,
    val result: JsResult?
)

private data class JsPromptDialogState(
    val message: String,
    val defaultValue: String,
    val result: JsPromptResult?
)

// ---------------------------------------------------------------------------
// LoadingScreen
// ---------------------------------------------------------------------------

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ErrorScreen
// ---------------------------------------------------------------------------

@Composable
fun ErrorScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_no_connection),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.error_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_retry),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
