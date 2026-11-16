package com.sukucadangku.mobile.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private data class FileChooserSession(
    val callback: ValueCallback<Array<Uri>>,
    val params: FileChooserParams?,
    val cameraUri: Uri?
)

class FileChooserLauncher internal constructor(
    private val context: Context,
    private val galleryLauncher: ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>,
    private val cameraLauncher: ManagedActivityResultLauncher<Uri, Boolean>
) {
    private var session: FileChooserSession? = null

    fun launch(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ) {
        if (filePathCallback == null) {
            Timber.w("FileChooser: filePathCallback is null, ignoring")
            return
        }

        cancelCurrentSession()

        val rawTypes = fileChooserParams?.acceptTypes?.joinToString(",")
        val acceptTypes = if (rawTypes.isNullOrBlank()) "image/*" else rawTypes
        val captureEnabled = fileChooserParams?.isCaptureEnabled ?: false
        val isImageOnly = acceptTypes.contains("image", ignoreCase = true)

        Timber.d("FileChooser: acceptTypes=$acceptTypes, capture=$captureEnabled, imageOnly=$isImageOnly")

        val cameraUri = createCameraUri(context)

        session = FileChooserSession(
            callback = filePathCallback,
            params = fileChooserParams,
            cameraUri = cameraUri
        )

        if (captureEnabled && isImageOnly) {
            launchCamera(cameraUri)
        } else {
            launchChooser(acceptTypes, cameraUri)
        }
    }

    private fun launchCamera(cameraUri: Uri?) {
        if (cameraUri != null) {
            Timber.d("FileChooser: launching camera with URI=$cameraUri")
            cameraLauncher.launch(cameraUri)
        } else {
            Timber.e("FileChooser: cannot create camera URI, cancelling")
            cancelCurrentSession()
        }
    }

    private fun launchChooser(acceptTypes: String, cameraUri: Uri?) {
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = sanitizeAcceptType(acceptTypes)
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        val cameraIntent = if (cameraUri != null) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else null

        val chooserIntent = if (cameraIntent != null) {
            Intent.createChooser(galleryIntent, "Pilih File").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
        } else {
            Intent.createChooser(galleryIntent, "Pilih File")
        }

        Timber.d("FileChooser: launching chooser")
        galleryLauncher.launch(chooserIntent)
    }

    internal fun onGalleryResult(result: androidx.activity.result.ActivityResult) {
        val currentSession = session ?: return

        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = when {
                data?.clipData != null -> {
                    val clipData = data.clipData!!
                    (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }.toTypedArray()
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }

            if (uris != null) {
                Timber.d("FileChooser: gallery returned ${uris.size} URI(s)")
                currentSession.callback.onReceiveValue(uris)
            } else {
                Timber.w("FileChooser: gallery returned RESULT_OK but data is null")
                currentSession.callback.onReceiveValue(null)
            }
        } else {
            Timber.d("FileChooser: gallery cancelled or failed")
            currentSession.callback.onReceiveValue(null)
        }

        session = null
    }

    internal fun onCameraResult(success: Boolean) {
        val currentSession = session ?: return

        if (success && currentSession.cameraUri != null) {
            currentSession.callback.onReceiveValue(arrayOf(currentSession.cameraUri))
        } else {
            Timber.d("FileChooser: camera cancelled or failed")
            currentSession.callback.onReceiveValue(null)
        }

        session = null
    }

    private fun cancelCurrentSession() {
        session?.callback?.onReceiveValue(null)
        session = null
    }

    private fun sanitizeAcceptType(acceptTypes: String): String {
        return when {
            acceptTypes.isBlank() || acceptTypes == "*" -> "image/*"
            acceptTypes.contains("image") && !acceptTypes.contains("video") -> "image/*"
            acceptTypes.contains("video") && !acceptTypes.contains("image") -> "video/*"
            acceptTypes.contains("audio") -> "audio/*"
            else -> "image/*"
        }
    }
}

private fun createCameraUri(context: Context): Uri? {
    return try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "IMG_$timestamp.jpg"
        )
        imageFile.parentFile?.mkdirs()

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        ).also {
            Timber.d("FileChooser: created camera URI=$it for file=${imageFile.absolutePath}")
        }
    } catch (e: Exception) {
        Timber.e(e, "FileChooser: failed to create camera URI")
        null
    }
}

@Composable
fun rememberFileChooserLauncher(): FileChooserLauncher {
    val context = LocalContext.current
    var launcherRef: FileChooserLauncher? = null

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        launcherRef?.onGalleryResult(result)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        launcherRef?.onCameraResult(success)
    }

    val launcher = remember(context) {
        FileChooserLauncher(
            context = context,
            galleryLauncher = galleryLauncher,
            cameraLauncher = cameraLauncher
        ).also { launcherRef = it }
    }

    SideEffect {
        launcherRef = launcher
    }

    return launcher
}
