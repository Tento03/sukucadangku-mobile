package com.sukucadangku.mobile.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*
import com.sukucadangku.mobile.R
import timber.log.Timber

/**
 * Composable untuk menangani runtime permission kamera dan storage.
 *
 * Gunakan composable ini di root Activity/Screen untuk memastikan permission
 * tersedia sebelum fitur kamera / file chooser dipakai WebChromeClient.
 *
 * @param onAllGranted Callback dipanggil ketika semua permission diberikan.
 * @param content Konten yang ditampilkan setelah permission diberikan (atau
 *                langsung tampil jika sudah ada izin sebelumnya).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(
    onAllGranted: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(
        permission = android.Manifest.permission.CAMERA
    )

    // Untuk READ_EXTERNAL_STORAGE hanya diperlukan < API 33
    val storagePermissionState = rememberPermissionState(
        permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    val allGranted = cameraPermissionState.status.isGranted &&
            storagePermissionState.status.isGranted

    // Notify caller
    LaunchedEffect(allGranted) {
        if (allGranted) onAllGranted()
    }

    when {
        allGranted -> content()

        // Kamera belum diberikan
        !cameraPermissionState.status.isGranted -> {
            PermissionRationaleContent(
                title = stringResource(R.string.permission_camera_title),
                rationale = if (cameraPermissionState.status.shouldShowRationale) {
                    stringResource(R.string.permission_camera_rationale)
                } else {
                    stringResource(R.string.permission_camera_description)
                },
                onRequest = {
                    Timber.d("Requesting CAMERA permission")
                    cameraPermissionState.launchPermissionRequest()
                },
                // Jika sudah ditolak permanen, tampilkan opsi ke Settings
                showSettingsOption = !cameraPermissionState.status.shouldShowRationale &&
                        !cameraPermissionState.status.isGranted
            )
        }

        // Storage belum diberikan
        !storagePermissionState.status.isGranted -> {
            PermissionRationaleContent(
                title = stringResource(R.string.permission_storage_title),
                rationale = if (storagePermissionState.status.shouldShowRationale) {
                    stringResource(R.string.permission_storage_rationale)
                } else {
                    stringResource(R.string.permission_storage_description)
                },
                onRequest = {
                    Timber.d("Requesting STORAGE permission")
                    storagePermissionState.launchPermissionRequest()
                },
                showSettingsOption = !storagePermissionState.status.shouldShowRationale &&
                        !storagePermissionState.status.isGranted
            )
        }

        else -> content()
    }
}

/**
 * UI rationale dialog / card yang ditampilkan sebelum meminta permission.
 */
@Composable
private fun PermissionRationaleContent(
    title: String,
    rationale: String,
    onRequest: () -> Unit,
    showSettingsOption: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (showSettingsOption) {
                    // Permission denied permanently — arahkan ke Settings
                    Button(
                        onClick = {
                            Timber.d("Opening app settings for permission")
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            ).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_open_settings))
                    }
                } else {
                    Button(
                        onClick = onRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_grant_permission))
                    }
                }
            }
        }
    }
}

/**
 * Versi lebih ringan: hanya meminta permission kamera tanpa UI rationale,
 * cocok untuk trigger otomatis sebelum membuka kamera dari WebView.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberCameraPermissionState(): PermissionState {
    return rememberPermissionState(android.Manifest.permission.CAMERA)
}
