package com.sukucadangku.mobile.util

import android.webkit.CookieManager
import android.webkit.CookieSyncManager

object CookieUtils {

    private val cookieManager: CookieManager get() = CookieManager.getInstance()

    /**
     * Flush all in-memory cookies to persistent storage (disk).
     * Call this after each page load to keep session alive.
     */
    fun syncCookies() {
        cookieManager.flush()
    }

    /**
     * Retrieve the cookie string for a given URL.
     * Returns null if no cookies are associated with the URL.
     */
    fun getCookieForUrl(url: String): String? {
        return cookieManager.getCookie(url)
    }

    /**
     * Remove all cookies from WebView CookieManager.
     * Useful for logout flows.
     */
    fun clearCookies(callback: (() -> Unit)? = null) {
        cookieManager.removeAllCookies { success ->
            if (success) {
                cookieManager.flush()
            }
            callback?.invoke()
        }
    }

    /**
     * Enable third-party cookies for a given WebView.
     */
    fun enableThirdPartyCookies(webView: android.webkit.WebView) {
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Set a specific cookie for a URL.
     */
    fun setCookie(url: String, cookie: String, callback: (() -> Unit)? = null) {
        cookieManager.setCookie(url, cookie)
        cookieManager.flush()
        callback?.invoke()
    }
}
