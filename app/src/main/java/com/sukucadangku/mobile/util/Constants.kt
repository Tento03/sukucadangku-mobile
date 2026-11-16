package com.sukucadangku.mobile.util

object Constants {

    // ─── App / Package ───────────────────────────────────────────────────────
    const val FILE_PROVIDER_AUTHORITY = "com.sukucadangku.mobile.fileprovider"
    const val USER_AGENT_SUFFIX = "SukuCadangkuApp/1.0 Android"

    // ─── Cookie / Session ────────────────────────────────────────────────────
    /** Interval in ms at which cookies are flushed from memory to disk. */
    const val COOKIE_SYNC_INTERVAL_MS = 5000L

    // ─── Payment Domains ─────────────────────────────────────────────────────
    /** Midtrans payment gateway domain. */
    const val MIDTRANS_DOMAIN = "app.midtrans.com"

    /**
     * List of domains that should be opened in the external browser
     * instead of inside the app's WebView (payment gateways, 3DS pages, etc.)
     */
    val PAYMENT_DOMAINS: List<String> = listOf(
        "app.midtrans.com",
        "api.midtrans.com",
        "payment.midtrans.com",
        "app.sandbox.midtrans.com",
        "pay.gopay.co.id",
        "www.ipaymu.com",
        "ipaymu.com",
        "snap.xendit.co",
        "checkout.xendit.co",
        "tripay.co.id",
        "payment.doku.com",
        "jokul.doku.com",
        "sandbox.doku.com",
        "ovo.id",
        "dana.id",
        "linkaja.id",
        "shopeepay.co.id"
    )

    // ─── WebView ─────────────────────────────────────────────────────────────
    /** JavaScript interface name exposed to the web page. */
    const val JS_BRIDGE_NAME = "SukuCadangkuBridge"

    /** Timeout in ms before showing the error screen if the page doesn't load. */
    const val PAGE_LOAD_TIMEOUT_MS = 30_000L

    // ─── Logging ─────────────────────────────────────────────────────────────
    const val LOG_TAG = "SukuCadangku"

    // ─── Notification ────────────────────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID = "sukucadangku_general"
    const val NOTIFICATION_CHANNEL_NAME = "SukuCadangku Notifikasi"
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "sukucadangku_download"
    const val DOWNLOAD_NOTIFICATION_CHANNEL_NAME = "Download SukuCadangku"
}
