package br.com.codecacto.kmplib.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val TAG = "CameraXPreview"

/**
 * **Infra de câmera compartilhada do módulo `camera`** — preview CameraX + análise de frames
 * (+ captura de foto opcional), com ciclo de vida correto.
 *
 * Existe para que o OCR de placa ([CameraView]) e a leitura de código de barras
 * ([br.com.codecacto.kmplib.camera.barcode.BarcodeCameraPreview]) **não dupliquem** o mesmo
 * pipeline dentro da própria lib — que é justamente o que a lib existe para evitar. Quem chama só
 * fornece o `analyzer`; bind, unbind, lanterna e reporte de falha ficam aqui.
 *
 * Três correções que os chamadores herdam (e que a versão anterior do [CameraView] não tinha):
 * - **`unbind` no `onDispose`** — sair da tela desliga a câmera. Antes, o `bindToLifecycle` ficava
 *   preso ao ciclo da Activity: a câmera seguia ligada (e o indicador do sistema aceso) depois de
 *   navegar para outra tela.
 * - **provider fora da main thread** — `ProcessCameraProvider.getInstance(...).get()` bloqueia;
 *   agora roda em `Dispatchers.IO`.
 * - **falha reportada** — erro de bind vira [onFailure] em vez de um `runCatching` mudo com tela
 *   preta.
 *
 * @param analyzer analisador de frames (ML Kit texto ou código de barras). `null` = só preview.
 * @param imageCapture caso de uso de foto, quando o chamador quiser um still nítido.
 * @param torchEnabled estado desejado da lanterna (aplicado quando o aparelho tiver uma).
 * @param onReady chamado quando a sessão sobe, informando se há lanterna utilizável.
 * @param onFailure chamado quando o bind falha.
 * @param onUnavailable chamado quando não há câmera traseira no aparelho.
 */
@Composable
internal fun CameraXPreview(
    modifier: Modifier,
    analyzer: ImageAnalysis.Analyzer?,
    imageCapture: ImageCapture? = null,
    torchEnabled: Boolean = false,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    onReady: (torchAvailable: Boolean) -> Unit = {},
    onFailure: (String?) -> Unit = {},
    onUnavailable: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)

    // Executor dedicado: análise de frame NÃO roda na main thread (recomendação do CameraX).
    // Consequência para quem chama: o `analyzer` é invocado numa thread de fundo.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundUseCases by remember { mutableStateOf<List<UseCase>>(emptyList()) }

    LaunchedEffect(analyzer, imageCapture, cameraSelector) {
        val provider = runCatching {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        }.getOrElse { error ->
            AppLogger.e(TAG, "Falha ao obter o ProcessCameraProvider", error)
            currentOnFailure(error.message)
            return@LaunchedEffect
        }

        if (!runCatching { provider.hasCamera(cameraSelector) }.getOrDefault(false)) {
            AppLogger.w(TAG, "Nenhuma câmera disponível para o seletor informado")
            currentOnUnavailable()
            return@LaunchedEffect
        }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = analyzer?.let {
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply { setAnalyzer(analysisExecutor, it) }
        }
        val useCases = listOfNotNull(preview, analysis, imageCapture)

        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, *useCases.toTypedArray())
        }.onSuccess { bound ->
            camera = bound
            boundProvider = provider
            boundUseCases = useCases
            currentOnReady(runCatching { bound.cameraInfo.hasFlashUnit() }.getOrDefault(false))
        }.onFailure { error ->
            AppLogger.e(TAG, "Falha ao vincular a câmera ao ciclo de vida", error)
            currentOnFailure(error.message)
        }
    }

    // Lanterna: reaplicada sempre que o estado desejado ou a câmera mudarem.
    LaunchedEffect(camera, torchEnabled) {
        val current = camera ?: return@LaunchedEffect
        runCatching {
            if (current.cameraInfo.hasFlashUnit()) current.cameraControl.enableTorch(torchEnabled)
        }.onFailure { AppLogger.w(TAG, "Não foi possível alternar a lanterna: ${it.message}") }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Apaga a lanterna e solta a câmera ao sair da tela — o bind é no ciclo da Activity,
            // então sem isto a câmera continuaria ligada em outra tela.
            runCatching { camera?.cameraControl?.enableTorch(false) }
            runCatching { boundProvider?.unbind(*boundUseCases.toTypedArray()) }
            camera = null
            boundProvider = null
            boundUseCases = emptyList()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
