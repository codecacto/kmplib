package br.com.codecacto.kmplib.camera.guided

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionState
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import br.com.codecacto.kmplib.platform.permission.rememberPermissionState
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.PICKED_IMAGE_JPEG_QUALITY
import br.com.codecacto.kmplib.ui.components.PICKED_IMAGE_MAX_DIMENSION
import br.com.codecacto.kmplib.ui.components.PickedImage
import kotlinx.coroutines.delay

/**
 * **Câmera com guia sobreposto** (2.278.0) — a tela de foto da própria lib, com uma silhueta (ou
 * qualquer desenho) por cima do preview para a pessoa se alinhar antes de disparar.
 *
 * Nasceu das fotos de avaliação física do App do Personal (corpo de frente, costas e lado, sempre no
 * mesmo enquadramento para comparar mês a mês), e serve igual a documento, rosto, vistoria.
 *
 * ```kotlin
 * val silhueta = painterResource(Res.drawable.silhueta_frente)
 * GuidedCamera(
 *     onCaptured = { foto -> viewModel.onAction(Action.FotoTirada(foto)) },
 *     guide = CameraGuide.painter(silhueta, alpha = 0.5f, tint = AppTheme.colors.primary),
 *     hint = "Fique de frente, com o corpo inteiro dentro da silhueta",
 *     maxDimension = 2048,
 *     onClose = { navController.popBackStack() },
 *     onError = { erro -> AppLogger.w("Avaliacao", "Câmera: $erro") },
 * )
 * ```
 *
 * ## O preview mostra exatamente a foto
 * A sessão captura em **4:3** e o preview é desenhado **inteiro**, sem corte, numa caixa da mesma
 * proporção (3:4 em retrato) — sobram faixas pretas, como na câmera do sistema em modo foto. É isso
 * que faz a silhueta alinhada na tela estar no mesmo lugar na foto. O guia é só da tela: **não é
 * gravado** na imagem.
 *
 * ## O que volta
 * O MESMO [PickedImage] do `rememberImagePickerLauncher`: JPEG, em pé (orientação aplicada aos
 * pixels), **sem EXIF/GPS**, maior lado ≤ [maxDimension] (preso em 64..4096), qualidade
 * [jpegQuality]. Com a câmera frontal a foto sai **sem espelhar** (como a pessoa é vista), embora o
 * preview frontal apareça espelhado, como em toda câmera.
 *
 * A câmera **continua aberta** depois de cada foto — o app decide se navega, mostra a revisão ou
 * troca o [guide]/[hint] para a próxima pose (frente → costas → lado).
 *
 * ## Permissão, falta de câmera e falha: estados com saída
 * Pede a câmera na primeira abertura (`android.permission.CAMERA` no manifesto do APP;
 * `NSCameraUsageDescription` no `Info.plist`). Negada → mensagem + "Permitir câmera"; negada em
 * definitivo → "Abrir Configurações" (reconsultado ao voltar); sem câmera (emulador, simulador do
 * iOS) → mensagem; sessão que não sobe → "Tentar novamente". Cada um também chega em [onError]
 * ([GuidedCameraError]) para o app registrar ou oferecer a galeria. Foto que falha no disparo vira
 * aviso na tela + [GuidedCameraError.CAPTURE_FAILED], e a câmera segue no ar.
 *
 * ## Temporizador de disparo (2.297.0)
 * Com [captureTimerSeconds] > 0, o disparo abre uma contagem regressiva grande no centro ("5, 4, 3…",
 * anunciada ao leitor de tela) e a foto sai no fim — para quem apoia o celular e se afasta até caber
 * na silhueta. Tocar no disparo de novo durante a contagem CANCELA; trocar de câmera e mudar o
 * temporizador ficam desligados enquanto conta; a câmera sair do ar (permissão, falha) também cancela.
 * Com [allowTimerToggle], um botão no topo passa por desligado → 5 s → 10 s ([CAPTURE_TIMER_OPTIONS]),
 * começando em [captureTimerSeconds]; sem ele, o valor é o do app.
 *
 * ## Acessibilidade e automação
 * Botão de disparo com 76 dp, descrição "Tirar foto" e anúncio "Tirando a foto…"; trocar de câmera
 * diz para qual vai; o flash diz o modo; o temporizador diz o valor. Ids em [GuidedCameraTestTags].
 *
 * @param onCaptured a foto pronta, na main thread.
 * @param guide o desenho sobre o preview; `null` = câmera sem guia.
 * @param hint instrução curta mostrada sobre o preview (ex.: a pose). `null` = nenhuma.
 * @param initialLens a câmera ao abrir; sem ela no aparelho, abre a outra.
 * @param allowLensSwitch mostrar o botão de trocar de câmera (só aparece se houver as duas).
 * @param allowFlash mostrar o botão de flash (só aparece se a câmera em uso tiver flash).
 * @param captureTimerSeconds temporizador de disparo, em segundos (0..[MAX_CAPTURE_TIMER_SECONDS];
 *   0 = dispara na hora). Com [allowTimerToggle], é o valor inicial do botão.
 * @param allowTimerToggle mostrar o botão de temporizador no topo (desligado → 5 s → 10 s).
 * @param onError por que a foto não veio — além do que a tela já mostra.
 * @param onClose quando informado, mostra o "X" no topo.
 * @param overlayContent slot desenhado por cima de tudo (contador "1 de 3", miniatura da anterior).
 */
@Composable
fun GuidedCamera(
    onCaptured: (PickedImage) -> Unit,
    modifier: Modifier = Modifier,
    guide: CameraGuide? = null,
    hint: String? = null,
    initialLens: CameraLens = CameraLens.BACK,
    allowLensSwitch: Boolean = true,
    allowFlash: Boolean = true,
    maxDimension: Int = PICKED_IMAGE_MAX_DIMENSION,
    jpegQuality: Int = PICKED_IMAGE_JPEG_QUALITY,
    texts: GuidedCameraTexts = rememberGuidedCameraTexts(),
    onError: (GuidedCameraError) -> Unit = {},
    onClose: (() -> Unit)? = null,
    captureTimerSeconds: Int = 0,
    allowTimerToggle: Boolean = false,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    val controller = rememberGuidedCameraController(
        onCaptured = onCaptured,
        initialLens = initialLens,
        allowLensSwitch = allowLensSwitch,
        captureTimerSeconds = captureTimerSeconds,
        onError = onError,
    )
    val state = controller.state

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraChrome.background)
            .testTag(GuidedCameraTestTags.ROOT),
    ) {
        GuidedCameraViewport(
            controller = controller,
            modifier = Modifier.fillMaxSize(),
            guide = guide,
            maxDimension = maxDimension,
            jpegQuality = jpegQuality,
            texts = texts,
        )

        // Barra de cima: fechar à esquerda, flash à direita.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onClose != null) {
                ChromeIconButton(
                    icon = Icons.Filled.Close,
                    description = texts.close,
                    testTag = GuidedCameraTestTags.CLOSE,
                    onClick = onClose,
                )
            } else {
                Spacer(Modifier.size(CHROME_BUTTON_SIZE))
            }
            val live = state as? GuidedCameraState.Live
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowTimerToggle && live != null) {
                    TimerButton(
                        seconds = controller.timerSeconds,
                        description = texts.timerLabel(controller.timerSeconds),
                        enabled = controller.countdown == null && !controller.isCapturing,
                        onClick = controller::cycleTimer,
                    )
                }
                if (allowFlash && live?.flashAvailable == true) {
                    ChromeIconButton(
                        icon = when (controller.flashMode) {
                            CameraFlashMode.OFF -> Icons.Filled.FlashOff
                            CameraFlashMode.AUTO -> Icons.Filled.FlashAuto
                            CameraFlashMode.ON -> Icons.Filled.FlashOn
                        },
                        description = texts.flashLabel(controller.flashMode),
                        testTag = GuidedCameraTestTags.FLASH,
                        onClick = controller::cycleFlash,
                    )
                }
            }
        }

        // Rodapé: instrução, aviso de falha, disparo e troca de câmera.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val live = state as? GuidedCameraState.Live
            val aviso = if (controller.captureFailed) texts.captureFailed else hint
            if (live != null && aviso != null) {
                Text(
                    text = aviso,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CameraChrome.content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .background(CameraChrome.scrim, MaterialTheme.shapes.medium)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag(GuidedCameraTestTags.MESSAGE)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (live != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(CHROME_BUTTON_SIZE))
                    GuidedShutterButton(controller = controller, texts = texts)
                    if (live.canSwitchLens) {
                        ChromeIconButton(
                            icon = Icons.Filled.Cameraswitch,
                            description = texts.switchLensLabel(controller.activeLens),
                            testTag = GuidedCameraTestTags.SWITCH_LENS,
                            enabled = !controller.isCapturing && controller.countdown == null,
                            onClick = controller::switchLens,
                        )
                    } else {
                        Spacer(Modifier.size(CHROME_BUTTON_SIZE))
                    }
                }
            }
        }

        GuidedCameraCountdown(controller = controller, texts = texts, modifier = Modifier.align(Alignment.Center))

        overlayContent()
    }
}

/**
 * **O motor da câmera guiada, sem o cromo** (2.318.0) — permissão, sessão, lente, flash,
 * temporizador e disparo, num estado que o app (ou outro componente da lib) desenha do seu jeito.
 *
 * A [GuidedCamera] é este controlador + [GuidedCameraViewport] + o cromo padrão (fechar e flash no
 * topo, disparo embaixo). Quem precisa de OUTRO cromo — o `InspectionCamera` do Laudo Kit, com
 * régua de transparência, contador de mínimo, fila de captura e miniaturas — compõe as mesmas peças
 * em vez de sobrepor um segundo cromo ao primeiro pelo `overlayContent` (dois botões de fechar, dois
 * disparos). Mesmas regras: tocar no disparo durante a contagem cancela; a câmera saindo do ar cancela
 * a contagem; uma foto por vez.
 *
 * ```kotlin
 * val camera = rememberGuidedCameraController(onCaptured = { foto -> … })
 * Column {
 *     MeuTopo(flash = camera.flashMode, onFlash = camera::cycleFlash)
 *     GuidedCameraViewport(camera, Modifier.weight(1f), guide = CameraGuide.painter(ref, alpha = .45f))
 *     MeuDisparo(onClick = camera::shutter, ocupado = camera.isCapturing)
 * }
 * ```
 */
@Stable
class GuidedCameraController internal constructor(
    initialLens: CameraLens,
    initialTimerSeconds: Int,
    private val allowLensSwitch: Boolean,
    initialFlash: CameraFlashMode = CameraFlashMode.OFF,
) {
    internal var lensState by mutableStateOf(initialLens)
    internal var cameraStatus by mutableStateOf<GuidedCameraStatus?>(null)
    internal var retryToken by mutableIntStateOf(0)
    internal val handle = GuidedCaptureHandle()
    internal var permission: PermissionState? = null
    internal val permissionStatus: PermissionStatus
        get() = permission?.status ?: PermissionStatus.NOT_REQUESTED
    internal var onCapturedResult: (PickedImage) -> Unit = {}
    internal var onErrorResult: (GuidedCameraError) -> Unit = {}
    internal fun requestPermission() { permission?.request() }
    internal fun openSettings() { permission?.openAppSettings() }

    /** O estado da câmera (ao vivo, pedindo permissão, sem câmera, falhou…). */
    val state: GuidedCameraState
        get() = guidedCameraStateOf(permissionStatus, cameraStatus, allowLensSwitch)

    /** Modo de flash em uso. */
    var flashMode: CameraFlashMode by mutableStateOf(initialFlash)
        private set

    /** Temporizador escolhido, em segundos (0 = dispara na hora). */
    var timerSeconds: Int by mutableIntStateOf(coerceCaptureTimerSeconds(initialTimerSeconds))
        private set

    /** Número da contagem regressiva na tela; `null` = sem contagem. */
    var countdown: Int? by mutableStateOf(null)
        internal set

    /** `true` enquanto a foto está saindo. */
    var isCapturing: Boolean by mutableStateOf(false)
        private set

    /** `true` por alguns segundos depois de um disparo que falhou (para o aviso na tela). */
    var captureFailed: Boolean by mutableStateOf(false)
        internal set

    /** A câmera de fato aberta (a pedida, ou a outra se o aparelho não tiver a pedida). */
    val activeLens: CameraLens
        get() = (cameraStatus as? GuidedCameraStatus.Ready)?.activeLens ?: lensState

    /** Próximo modo de flash (desligado → automático → ligado). */
    fun cycleFlash() {
        flashMode = flashMode.next()
    }

    /** Próximo temporizador (desligado → 5 s → 10 s). Ignorado durante a contagem. */
    fun cycleTimer() {
        if (countdown != null || isCapturing) return
        timerSeconds = nextCaptureTimer(timerSeconds)
    }

    /** Fixa o temporizador (0..[MAX_CAPTURE_TIMER_SECONDS]). Ignorado durante a contagem. */
    fun setTimer(seconds: Int) {
        if (countdown != null || isCapturing) return
        timerSeconds = coerceCaptureTimerSeconds(seconds)
    }

    /** Troca frontal ↔ traseira. Ignorado durante disparo ou contagem. */
    fun switchLens() {
        if (isCapturing || countdown != null) return
        cameraStatus = null
        lensState = activeLens.toggled()
    }

    /** "Tentar novamente" depois de a sessão não subir. */
    fun retry() {
        cameraStatus = null
        retryToken++
    }

    /**
     * O botão de disparo: com temporizador, abre a contagem; tocado durante a contagem, cancela;
     * sem temporizador, tira a foto. Fora do ar ou já disparando, nada.
     */
    fun shutter() {
        when {
            countdown != null -> countdown = null
            isCapturing || state !is GuidedCameraState.Live -> Unit
            timerSeconds > 0 -> countdown = timerSeconds
            else -> captureNow()
        }
    }

    internal fun captureNow() {
        if (isCapturing || state !is GuidedCameraState.Live) return
        isCapturing = true
        handle.trigger { resultado ->
            isCapturing = false
            when (resultado) {
                is GuidedCaptureResult.Success -> onCapturedResult(resultado.image)
                is GuidedCaptureResult.Failure -> {
                    captureFailed = true
                    onErrorResult(GuidedCameraError.CAPTURE_FAILED)
                }
            }
        }
    }
}

/**
 * Cria o [GuidedCameraController] e liga a permissão de câmera, a contagem regressiva e o relato de
 * erros. Ver [GuidedCamera] para o significado de cada parâmetro. Desde 2.318.0.
 */
@Composable
fun rememberGuidedCameraController(
    onCaptured: (PickedImage) -> Unit,
    initialLens: CameraLens = CameraLens.BACK,
    allowLensSwitch: Boolean = true,
    captureTimerSeconds: Int = 0,
    onError: (GuidedCameraError) -> Unit = {},
): GuidedCameraController {
    val permission = rememberPermissionState(AppPermission.CAMERA)
    // Voltando das Configurações com a câmera liberada, a tela sai da mensagem sozinha.
    LifecycleResumeEffect(permission) {
        permission.refresh()
        onPauseOrDispose { }
    }
    // Lente, flash e temporizador sobrevivem à rotação (ordinal/Int: `rememberSaveable` igual nas
    // duas plataformas).
    var lensOrdinal by rememberSaveable { mutableIntStateOf(initialLens.ordinal) }
    var flashOrdinal by rememberSaveable { mutableIntStateOf(CameraFlashMode.OFF.ordinal) }
    var timerSaved by rememberSaveable { mutableIntStateOf(coerceCaptureTimerSeconds(captureTimerSeconds)) }
    // O temporizador do APP vale até alguém mexer no botão; se o app trocar o valor, ele volta a valer.
    var timerDoApp by rememberSaveable { mutableIntStateOf(captureTimerSeconds) }
    val controller = remember(allowLensSwitch, permission) {
        GuidedCameraController(
            initialLens = CameraLens.entries[lensOrdinal],
            initialTimerSeconds = timerSaved,
            allowLensSwitch = allowLensSwitch,
            initialFlash = CameraFlashMode.entries[flashOrdinal],
        ).also { it.permission = permission }
    }
    LaunchedEffect(controller.lensState) { lensOrdinal = controller.lensState.ordinal }
    LaunchedEffect(controller.flashMode) { flashOrdinal = controller.flashMode.ordinal }
    LaunchedEffect(controller.timerSeconds) { timerSaved = controller.timerSeconds }
    LaunchedEffect(captureTimerSeconds) {
        if (timerDoApp != captureTimerSeconds) {
            timerDoApp = captureTimerSeconds
            controller.setTimer(captureTimerSeconds)
        }
    }

    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)
    SideEffect {
        controller.onCapturedResult = { currentOnCaptured(it) }
        controller.onErrorResult = { currentOnError(it) }
    }

    val error = guidedCameraErrorOf(permission.status, controller.state)
    LaunchedEffect(error) { error?.let { currentOnError(it) } }
    LaunchedEffect(controller.captureFailed) {
        if (controller.captureFailed) {
            delay(CAPTURE_FAILED_MESSAGE_MILLIS)
            controller.captureFailed = false
        }
    }
    // A câmera saiu do ar no meio da contagem: nada de foto atrasada quando ela voltar.
    val aoVivo = controller.state is GuidedCameraState.Live
    LaunchedEffect(aoVivo) { if (!aoVivo) controller.countdown = null }
    // Contagem: um passo por segundo; depois do 1, a foto.
    LaunchedEffect(controller.countdown) {
        val atual = controller.countdown ?: return@LaunchedEffect
        delay(1_000L)
        if (controller.countdown == atual) {
            val proximo = nextCountdown(atual)
            controller.countdown = proximo
            if (proximo == null) controller.captureNow()
        }
    }
    return controller
}

/**
 * **O preview da câmera guiada, sem cromo** (2.318.0): a sessão desenhada inteira na proporção da
 * foto (o preview É a foto), o [guide] por cima e, fora do ar, a mensagem com saída (permitir,
 * abrir Configurações, tentar de novo). Preto atrás, em qualquer tema.
 *
 * @param previewOverlay desenhado DENTRO da caixa do preview (mesma moldura da foto), por cima do
 *   guia — rótulos e pílulas que precisam ficar sobre a imagem, não sobre as faixas pretas.
 */
@Composable
fun GuidedCameraViewport(
    controller: GuidedCameraController,
    modifier: Modifier = Modifier,
    guide: CameraGuide? = null,
    maxDimension: Int = PICKED_IMAGE_MAX_DIMENSION,
    jpegQuality: Int = PICKED_IMAGE_JPEG_QUALITY,
    texts: GuidedCameraTexts = rememberGuidedCameraTexts(),
    previewOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val state = controller.state
    Box(modifier.background(CameraChrome.background)) {
        if (controller.permissionStatus == PermissionStatus.GRANTED) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = guidedPreviewAspectRatio(maxWidth.value, maxHeight.value)
                Box(Modifier.aspectRatio(ratio).testTag(GuidedCameraTestTags.PREVIEW)) {
                    key(controller.retryToken, controller.lensState) {
                        GuidedCameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            lens = controller.lensState,
                            flashMode = controller.flashMode,
                            maxDimension = maxDimension,
                            jpegQuality = jpegQuality,
                            handle = controller.handle,
                            onStatus = { controller.cameraStatus = it },
                        )
                    }
                    if (state is GuidedCameraState.Live) {
                        if (guide != null) GuideOverlay(guide)
                        previewOverlay()
                    }
                }
            }
        }

        when (state) {
            is GuidedCameraState.Live -> Unit
            GuidedCameraState.Starting -> CameraMessage(Icons.Outlined.PhotoCamera, null, texts.starting)
            GuidedCameraState.PermissionRequired -> CameraMessage(
                icon = Icons.Outlined.PhotoCamera,
                title = texts.permissionTitle,
                message = texts.permissionMessage,
                actionLabel = texts.permissionAllow,
                onAction = { controller.requestPermission() },
            )
            GuidedCameraState.PermissionPermanentlyDenied -> CameraMessage(
                icon = Icons.Outlined.NoPhotography,
                title = texts.permissionTitle,
                message = texts.permissionDeniedMessage,
                actionLabel = texts.openSettings,
                onAction = { controller.openSettings() },
            )
            GuidedCameraState.CameraUnavailable ->
                CameraMessage(Icons.Outlined.NoPhotography, null, texts.cameraUnavailable)
            is GuidedCameraState.InitializationFailed -> CameraMessage(
                icon = Icons.Outlined.NoPhotography,
                title = null,
                message = texts.initializationFailed,
                actionLabel = texts.retry,
                onAction = controller::retry,
            )
        }
    }
}

/** O disparo padrão da lib (76 dp, anel branco), ligado ao [controller]. Desde 2.318.0. */
@Composable
fun GuidedShutterButton(
    controller: GuidedCameraController,
    modifier: Modifier = Modifier,
    texts: GuidedCameraTexts = rememberGuidedCameraTexts(),
    size: Dp = SHUTTER_SIZE,
) {
    ShutterButton(
        capturing = controller.isCapturing,
        description = if (controller.countdown != null) texts.cancelTimer else texts.capture,
        capturingDescription = texts.capturing,
        onClick = controller::shutter,
        modifier = modifier,
        size = size,
    )
}

/** A contagem regressiva grande (anunciada ao leitor de tela); some sem contagem. Desde 2.318.0. */
@Composable
fun GuidedCameraCountdown(
    controller: GuidedCameraController,
    modifier: Modifier = Modifier,
    texts: GuidedCameraTexts = rememberGuidedCameraTexts(),
) {
    val contagem = controller.countdown ?: return
    Text(
        text = contagem.toString(),
        color = CameraChrome.content,
        fontSize = 96.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(CameraChrome.scrim, CircleShape)
            .padding(horizontal = 36.dp, vertical = 8.dp)
            .semantics {
                contentDescription = texts.countdown(contagem)
                liveRegion = LiveRegionMode.Assertive
            }
            .testTag(GuidedCameraTestTags.COUNTDOWN),
    )
}

/** O botão de temporizador: ícone riscado desligado, o número ("5s"/"10s") ligado. */
@Composable
private fun TimerButton(seconds: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(CHROME_BUTTON_SIZE)
            .semantics { contentDescription = description }
            .testTag(GuidedCameraTestTags.TIMER),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CameraChrome.scrim,
            contentColor = CameraChrome.content,
            disabledContainerColor = CameraChrome.scrim,
            disabledContentColor = CameraChrome.content.copy(alpha = 0.4f),
        ),
    ) {
        if (seconds <= 0) {
            Icon(imageVector = Icons.Filled.TimerOff, contentDescription = null)
        } else {
            Text(
                text = "${seconds}s",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** O guia, na moldura pedida, sem receber toque nem ser lido pelo leitor de tela (é decoração). */
@Composable
internal fun BoxScope.GuideOverlay(guide: CameraGuide, testTag: String = GuidedCameraTestTags.GUIDE) {
    Box(
        modifier = Modifier
            .align(guide.alignment)
            .fillMaxWidth(guide.widthFraction)
            .fillMaxHeight(guide.heightFraction)
            .alpha(guide.alpha)
            .clearAndSetSemantics { }
            .testTag(testTag),
    ) {
        val painter = guide.painter
        val onDraw = guide.onDraw
        when {
            painter != null -> Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = guide.contentScale,
                colorFilter = guide.tint?.let { ColorFilter.tint(it) },
            )
            onDraw != null -> Canvas(Modifier.fillMaxSize()) { onDraw() }
        }
    }
}

@Composable
private fun ShutterButton(
    capturing: Boolean,
    description: String,
    capturingDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SHUTTER_SIZE,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(4.dp, CameraChrome.content, CircleShape)
            .clickable(enabled = !capturing, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (capturing) capturingDescription else description }
            .testTag(GuidedCameraTestTags.CAPTURE),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size - 16.dp)
                .clip(CircleShape)
                .background(if (capturing) CameraChrome.content.copy(alpha = 0.5f) else CameraChrome.content),
        )
        if (capturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = CameraChrome.background,
                strokeWidth = 3.dp,
            )
        }
    }
}

@Composable
internal fun ChromeIconButton(
    icon: ImageVector,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(CHROME_BUTTON_SIZE).testTag(testTag),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CameraChrome.scrim,
            contentColor = CameraChrome.content,
            disabledContainerColor = CameraChrome.scrim,
            disabledContentColor = CameraChrome.content.copy(alpha = 0.4f),
        ),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/** Os estados que não são câmera ao vivo: ícone + texto + (talvez) a ação. Também do gravador de vídeo. */
@Composable
internal fun BoxScope.CameraMessage(
    icon: ImageVector,
    title: String?,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    messageTestTag: String = GuidedCameraTestTags.MESSAGE,
    actionTestTag: String = GuidedCameraTestTags.PRIMARY_ACTION,
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .widthIn(max = 420.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = CameraChrome.content, modifier = Modifier.size(56.dp))
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CameraChrome.content,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = CameraChrome.content,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(messageTestTag),
        )
        if (actionLabel != null && onAction != null) {
            AppButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.testTag(actionTestTag),
            )
        }
    }
}

/**
 * As cores do "cromo" da câmera. Preto e branco **de propósito**, independentes do tema do app: é a
 * convenção de toda câmera (a do sistema inclusive) — o fundo escuro não compete com o preview, e o
 * branco sobre véu escuro é legível sobre qualquer cena. Botão de ação dos estados de erro usa o
 * `AppButton` (cor da marca).
 */
internal object CameraChrome {
    val background: Color = Color.Black
    val content: Color = Color.White
    val scrim: Color = Color.Black.copy(alpha = 0.45f)
}

internal val SHUTTER_SIZE = 76.dp
internal val CHROME_BUTTON_SIZE = 48.dp
private const val CAPTURE_FAILED_MESSAGE_MILLIS = 3_000L
