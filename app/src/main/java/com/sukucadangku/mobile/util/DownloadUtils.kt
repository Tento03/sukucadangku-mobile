package com.sukucadangku.mobile.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object DownloadUtils {

    /**
     * Enqueue a file download using the system DownloadManager.
     *
     * @param context    Application or Activity context.
     * @param url        The URL of the file to download.
     * @param filename   Desired filename (including extension).
     * @param mimeType   MIME type of the file (e.g. "application/pdf").
     * @param cookie     Cookie string to send with the request (optional).
     * @param userAgent  User-Agent header value (optional).
     * @param onComplete Called when download completes; passes the downloaded File (or null on failure).
     * @return           The DownloadManager download ID (use to cancel if needed).
     */
    fun downloadFile(
        context: Context,
        url: String,
        filename: String,
        mimeType: String? = null,
        cookie: String? = null,
        userAgent: String? = null,
        onComplete: ((File?) -> Unit)? = null
    ): Long {
        val resolvedMimeType = mimeType
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(filename.substringAfterLast('.', ""))
            ?: "application/octet-stream"

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(filename)
            setDescription("Mengunduh $filename…")
            setMimeType(resolvedMimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            if (!cookie.isNullOrBlank()) addRequestHeader("Cookie", cookie)
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            // Allow download over metered connections
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Register a one-shot receiver to open the file when done
        if (onComplete != null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        ctx.unregisterReceiver(this)
                        val file = getDownloadedFile(ctx, downloadId)
                        onComplete(file)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        }

        return downloadId
    }

    /**
     * Open a downloaded file using its content URI (FileProvider for Android 7+).
     */
    fun openFile(context: Context, file: File, mimeType: String? = null) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                Constants.FILE_PROVIDER_AUTHORITY,
                file
            )
        } else {
            Uri.fromFile(file)
        }

        val resolvedMimeType = mimeType
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension)
            ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Buka dengan"))
    }

    /**
     * Query DownloadManager for the local file once download is complete.
     */
    private fun getDownloadedFile(context: Context, downloadId: Long): File? {
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        return try {
            if (cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val status = cursor.getInt(statusIdx)
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUri = cursor.getString(localUriIdx)
                    if (!localUri.isNullOrBlank()) File(Uri.parse(localUri).path!!) else null
                } else null
            } else null
        } finally {
            cursor.close()
        }
    }

    /**
     * Cancel an in-progress or pending download.
     */
    fun cancelDownload(context: Context, downloadId: Long) {
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadId)
    }
}
