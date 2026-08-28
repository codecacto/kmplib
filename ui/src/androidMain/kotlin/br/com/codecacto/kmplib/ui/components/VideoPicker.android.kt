package br.com.codecacto.kmplib.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

actual class VideoPickerLauncher(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun rememberVideoPickerLauncher(
    onVideoSelected: (SelectedVideo) -> Unit
): VideoPickerLauncher {
    val context = LocalContext.current
    var showChooser by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { readVideoFromUri(context, it, onVideoSelected) }
    }

    val captureVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success: Boolean ->
        if (success) {
            captureUri?.let { uri -> readVideoFromUri(context, uri, onVideoSelected) }
        }
    }

    fun launchCamera() {
        try {
            val videosDir = File(context.cacheDir, "videos")
            videosDir.mkdirs()
            val file = File(videosDir, "video_${System.currentTimeMillis()}.mp4")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            captureUri = uri
            captureVideo.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            launchCamera()
        }
    }

    if (showChooser) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showChooser = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Adicionar video",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                TextButton(
                    onClick = {
                        showChooser = false
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Gravar video", fontSize = 16.sp)
                    }
                }

                TextButton(
                    onClick = {
                        showChooser = false
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Escolher da galeria", fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    return remember(pickMedia, captureVideo) {
        VideoPickerLauncher {
            showChooser = true
        }
    }
}

private fun readVideoFromUri(
    context: Context,
    uri: Uri,
    onVideoSelected: (SelectedVideo) -> Unit
) {
    try {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "video/mp4"
        val name = queryDisplayName(context, uri)
            ?: "video_${System.currentTimeMillis()}.${mimeType.substringAfter('/', "mp4")}"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        onVideoSelected(SelectedVideo(bytes = bytes, mimeType = mimeType, name = name))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}
