package br.com.codecacto.kmplib.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Implementação iOS (placeholder) do [CameraView].
 *
 * TODO(GAP-ME-01/iOS): substituir por `UIViewControllerRepresentable`
 * wrappando `AVCaptureSession` + `VNRecognizeTextRequest` (Apple Vision),
 * delegando a extração ao [PlateOcrAnalyzer]/[extractPlate]. Requer host macOS.
 *
 * Não chama [onPlateCaptured] e nunca lança: exibe apenas UI estática.
 */
@Composable
actual fun CameraView(
    onPlateCaptured: (String) -> Unit,
    modifier: Modifier
) {
    CameraPlaceholder(modifier)
}

/**
 * Implementação iOS (placeholder) da [CameraView] com captura de foto.
 *
 * TODO(GAP-ME-01/iOS): substituir por `UIViewControllerRepresentable`
 * wrappando `AVCaptureSession` (com `AVCaptureVideoDataOutput` p/ OCR ao vivo
 * via `VNRecognizeTextRequest`/Apple Vision e `AVCapturePhotoOutput` p/ o
 * still), delegando a extração a [extractPlate] e devolvendo o JPEG
 * (`AVCapturePhoto.fileDataRepresentation()`) via [onCapture]. Requer host
 * macOS.
 *
 * Não chama [onCapture] e nunca lança: exibe apenas UI estática (degradação
 * honesta — assinatura preservada para paridade com o Android).
 */
@Composable
actual fun CameraView(
    onCapture: (placa: String, jpegBytes: ByteArray) -> Unit,
    modifier: Modifier
) {
    CameraPlaceholder(modifier)
}

@Composable
private fun CameraPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Câmera disponível apenas em iOS nativo",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
