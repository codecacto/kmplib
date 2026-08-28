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
 * - **iOS:** **AVFoundation** (`AVCaptureSession` + `AVCaptureVideoDataOutput`)
 *   para o preview + **Apple Vision** (`VNRecognizeTextRequest`) para o OCR
 *   on-device (2.78.0). Implementado espelhando o Android; **pendente de
 *   validação em host macOS** (o build iOS não roda em Linux). Info.plist:
 *   `NSCameraUsageDescription`.
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

/**
 * Variante da [CameraView] que, ao reconhecer a placa, também entrega o
 * **JPEG do frame** capturado (foto do veículo) — via [onCapture].
 *
 * É o **mesmo preview + OCR** da sobrecarga só-placa; a diferença é que, no
 * momento do reconhecimento, um still é capturado e codificado em **JPEG**
 * (compatível com `firebase/storage`/`StorageProvider`, que espera
 * `image/jpeg`) e devolvido junto da placa já **normalizada**. Assim o app
 * consegue guardar a placa E a foto de forma atômica (ex.: comprovante de
 * entrada de veículo — MeuEstacionamento RF-15), sem precisar de um segundo
 * fluxo de foto.
 *
 * Sobrecarga **aditiva** (não quebra quem usa a [CameraView] só-placa): a
 * assinatura antiga continua existindo. A resolução por arity do lambda
 * evita ambiguidade (`(String) -> Unit` vs `(String, ByteArray) -> Unit`).
 *
 * - **Android:** **CameraX** — `Preview` + `ImageAnalysis` (OCR ML Kit) +
 *   `ImageCapture`; ao detectar a placa, dispara `ImageCapture.takePicture`
 *   para obter um JPEG nítido do veículo, aplica a rotação e devolve os bytes.
 * - **iOS:** **AVFoundation** + **Apple Vision** (2.78.0) — o frame reconhecido
 *   é codificado em JPEG (`CIImage`→`UIImageJPEGRepresentation`) e devolvido via
 *   [onCapture]. Implementado espelhando o Android; **pendente de validação em
 *   host macOS**.
 *
 * @param onCapture chamado com a placa normalizada (ex.: `"ABC1D23"`) e os
 *   **bytes JPEG** do frame reconhecido.
 * @param modifier modificador Compose.
 *
 * ```kotlin
 * CameraView(
 *     onCapture = { plate, jpegBytes ->
 *         viewModel.onPlateRead(plate, foto = jpegBytes)
 *     },
 *     modifier = Modifier.fillMaxSize()
 * )
 * ```
 */
@Composable
expect fun CameraView(
    onCapture: (placa: String, jpegBytes: ByteArray) -> Unit,
    modifier: Modifier = Modifier
)
