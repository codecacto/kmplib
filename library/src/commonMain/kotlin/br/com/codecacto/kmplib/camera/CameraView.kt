package br.com.codecacto.kmplib.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Visualização de câmera com OCR de placa em tempo real (GAP-ME-01).
 *
 * Exibe o preview da câmera e, ao reconhecer uma placa brasileira válida,
 * dispara [onPlateCaptured] com a placa já **normalizada** (ex.: `"ABC1D23"`).
 *
 * - **Android:** **CameraX** (`androidx.camera:camera-*`) com `ImageAnalysis`
 *   alimentando o [PlateOcrAnalyzer] (ML Kit), com throttle entre leituras
 *   para evitar disparos repetidos. Requer a permissão de câmera concedida
 *   pelo app consumidor (`android.permission.CAMERA`).
 * - **iOS:** placeholder estático (não chama [onPlateCaptured]) — a captura
 *   nativa entra quando construída em host macOS.
 *
 * O consumidor deve combinar com a entrada manual existente (`PlateMask`):
 * o OCR é um incremento, não substitui o campo de texto.
 *
 * @param onPlateCaptured chamado com a placa normalizada ao reconhecer uma
 *   placa válida.
 * @param modifier modificador Compose.
 *
 * ```kotlin
 * CameraView(
 *     onPlateCaptured = { plate -> viewModel.onPlateRead(plate) },
 *     modifier = Modifier.fillMaxSize()
 * )
 * ```
 */
@Composable
expect fun CameraView(
    onPlateCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
)
