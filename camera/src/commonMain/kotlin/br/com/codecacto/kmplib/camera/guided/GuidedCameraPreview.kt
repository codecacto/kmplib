package br.com.codecacto.kmplib.camera.guided

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.ui.components.PickedImage

/** O resultado de um disparo, já na main thread. */
internal sealed interface GuidedCaptureResult {
    data class Success(val image: PickedImage) : GuidedCaptureResult

    /** [message] é diagnóstico para o log, nunca texto de tela. */
    data class Failure(val message: String?) : GuidedCaptureResult
}

/**
 * A ponte entre o botão (código comum) e a sessão (plataforma): a plataforma preenche [capture]
 * quando a sessão está no ar e o esvazia ao sair. Disparo com a sessão fora do ar vira falha, não
 * silêncio.
 */
internal class GuidedCaptureHandle {
    var capture: ((onResult: (GuidedCaptureResult) -> Unit) -> Unit)? = null

    fun trigger(onResult: (GuidedCaptureResult) -> Unit) {
        val disparo = capture
        if (disparo == null) {
            onResult(GuidedCaptureResult.Failure("sessão de câmera fora do ar"))
        } else {
            disparo(onResult)
        }
    }
}

/**
 * A sessão de câmera da plataforma, desenhada **inteira** (sem corte) na caixa que a recebe — a
 * caixa já tem a proporção da foto (4:3, [guidedPreviewAspectRatio]).
 *
 * - **Android:** CameraX — `Preview` + `ImageCapture` (4:3) no `PreviewView` em `FIT_CENTER`,
 *   ligados ao ciclo de vida e soltos no `onDispose`.
 * - **iOS:** AVFoundation — `AVCaptureSession` (preset `Photo`, 4:3) + `AVCapturePhotoOutput`, com o
 *   `AVCaptureVideoPreviewLayer` em `resizeAspect` dentro de um `UIKitView`; sessão parada e
 *   desmontada no `onDispose`.
 *
 * A foto sai pelo mesmo codificador do seletor de imagem: JPEG em pé, sem EXIF, maior lado ≤
 * [maxDimension].
 */
@Composable
internal expect fun GuidedCameraPreview(
    modifier: Modifier,
    lens: CameraLens,
    flashMode: CameraFlashMode,
    maxDimension: Int,
    jpegQuality: Int,
    handle: GuidedCaptureHandle,
    onStatus: (GuidedCameraStatus) -> Unit,
)
