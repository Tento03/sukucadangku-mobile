package com.sukucadangku.mobile.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import timber.log.Timber

class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == -1L) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            cursor?.use {
                if (it.moveToFirst()) {
                    val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val titleIndex = it.getColumnIndex(DownloadManager.COLUMN_TITLE)

                    val status = if (statusIndex != -1) it.getInt(statusIndex) else -1
                    val title = if (titleIndex != -1) it.getString(titleIndex) else "File"

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Timber.d("Download selesai: $title (ID: $downloadId)")
                        Toast.makeText(context, "Unduhan selesai: $title", Toast.LENGTH_SHORT).show()
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        Timber.e("Download gagal: $title (ID: $downloadId)")
                        Toast.makeText(context, "Gagal mengunduh: $title", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

