package com.sukucadangku.mobile.util

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.sukucadangku.mobile.BuildConfig
import timber.log.Timber

/**
 * JavaScript bridge that allows the web app to call native Android functions.
 *
 * Usage in JavaScript:
 *   SukuCadangkuBridge.showNativeToast("Hello from web!");
 *   SukuCadangkuBridge.closeApp();
 *   var version = SukuCadangkuBridge.getAppVersion();
 *
 * Register with:
 *   webView.addJavascriptInterface(WebViewJavascriptBridge(context, activity), Constants.JS_BRIDGE_NAME)
 */
class WebViewJavascriptBridge(
    private val context: Context,
    private val onCloseApp: (() -> Unit)? = null
) {

    /**
     * Display a native Android Toast message.
     * Called from JavaScript: SukuCadangkuBridge.showNativeToast("message")
     */
    @JavascriptInterface
    fun showNativeToast(message: String) {
        Timber.d("showNativeToast: $message")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Close / finish the host Activity (exit the app).
     * Called from JavaScript: SukuCadangkuBridge.closeApp()
     */
    @JavascriptInterface
    fun closeApp() {
        Timber.d("closeApp() called from JS bridge")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onCloseApp?.invoke()
        }
    }

    /**
     * Return the current app version name.
     * Called from JavaScript: var v = SukuCadangkuBridge.getAppVersion()
     */
    @JavascriptInterface
    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    /**
     * Return the current build type (debug / staging / release).
     * Called from JavaScript: var buildType = SukuCadangkuBridge.getBuildType()
     */
    @JavascriptInterface
    fun getBuildType(): String {
        return BuildConfig.BUILD_TYPE
    }

    /**
     * Return platform identifier so the web app can detect it is running inside
     * the native wrapper.
     * Called from JavaScript: var platform = SukuCadangkuBridge.getPlatform()
     */
    @JavascriptInterface
    fun getPlatform(): String = "android"

    /**
     * Log a message from JavaScript to Logcat (useful for debugging).
     * Called from JavaScript: SukuCadangkuBridge.logMessage("TAG", "message")
     */
    @JavascriptInterface
    fun logMessage(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }
}
