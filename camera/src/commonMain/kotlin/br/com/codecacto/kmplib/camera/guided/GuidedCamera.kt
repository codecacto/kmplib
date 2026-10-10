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
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.platform.permission.AppPermission
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
 * ## Acessibilidade e automação
 * Botão de disparo com 76 dp, descrição "Tirar foto" e anúncio "Tirando a foto…"; trocar de câmera
 * diz para qual vai; o flash diz o modo. Ids em [GuidedCameraTestTags].
 *
 * @param onCaptured a foto pronta, na main thread.
 * @param guide o desenho sobre o preview; `null` = câmera sem guia.
 * @param hint instrução curta mostrada sobre o preview (ex.: a pose). `null` = nenhuma.
 * @param initialLens a câmera ao abrir; sem ela no aparelho, abre a outra.
 * @param allowLensSwitch mostrar o botão de trocar de câmera (só aparece se houver as duas).
 * @param allowFlash mostrar o botão de flash (só aparece se a câmera em uso tiver flash).
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
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    val permission = rememberPermissionState(AppPermission.CAMERA)
    // Voltando das Configurações com a câmera liberada, a tela sai da mensagem sozinha.
    LifecycleResumeEffect(permission) {
        permission.refresh()
        onPauseOrDispose { }
    }

    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)

    // Ordinal (Int) e não o enum: `rememberSaveable` de Int vale igual nas duas plataformas.
    var lensOrdinal by rememberSaveable { mutableIntStateOf(initialLens.ordinal) }
    var flashOrdinal by rememberSaveable { mutableIntStateOf(CameraFlashMode.OFF.ordinal) }
    val lens = CameraLens.entries[lensOrdinal]
    val flashMode = CameraFlashMode.entries[flashOrdinal]

    var cameraStatus by remember { mutableStateOf<GuidedCameraStatus?>(null) }
    // "Tentar novamente" precisa RECRIAR a sessão: o token entra como `key` do preview.
    var retryToken by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var captureFailedShown by remember { mutableStateOf(false) }
    val handle = remember { GuidedCaptureHandle() }

    val state = guidedCameraStateOf(permission.status, cameraStatus, allowLensSwitch)
    val error = guidedCameraErrorOf(permission.status, state)
    LaunchedEffect(error) { error?.let { currentOnError(it) } }
    LaunchedEffect(captureFailedShown) {
        if (captureFailedShown) {
            delay(CAPTURE_FAILED_MESSAGE_MILLIS)
            captureFailedShown = false
        }
    }

    val activeLens = (cameraStatus as? GuidedCameraStatus.Ready)?.activeLens ?: lens

    fun disparar() {
        if (capturing || state !is GuidedCameraState.Live) return
        capturing = true
        handle.trigger { resultado ->
            capturing = false
            when (resultado) {
                is GuidedCaptureResult.Success -> currentOnCaptured(resultado.image)
                is GuidedCaptureResult.Failure -> {
                    captureFailedShown = true
                    currentOnError(GuidedCameraError.CAPTURE_FAILED)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraChrome.background)
            .testTag(GuidedCameraTestTags.ROOT),
    ) {
        if (permission.isGranted) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = guidedPreviewAspectRatio(maxWidth.value, maxHeight.value)
                Box(Modifier.aspectRatio(ratio).testTag(GuidedCameraTestTags.PREVIEW)) {
                    key(retryToken, lens) {
                        GuidedCameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            lens = lens,
                            flashMode = flashMode,
                            maxDimension = maxDimension,
                            jpegQuality = jpegQuality,
                            handle = handle,
                            onStatus = { cameraStatus = it },
                        )
                    }
                    if (guide != null && state is GuidedCameraState.Live) {
                        GuideOverlay(guide)
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
                onAction = permission::request,
            )
            GuidedCameraState.PermissionPermanentlyDenied -> CameraMessage(
                icon = Icons.Outlined.NoPhotography,
                title = texts.permissionTitle,
                message = texts.permissionDeniedMessage,
                actionLabel = texts.openSettings,
                onAction = permission::openAppSettings,
            )
            GuidedCameraState.CameraUnavailable ->
                CameraMessage(Icons.Outlined.NoPhotography, null, texts.cameraUnavailable)
            is GuidedCameraState.InitializationFailed -> CameraMessage(
                icon = Icons.Outlined.NoPhotography,
                title = null,
                message = texts.initializationFailed,
                actionLabel = texts.retry,
                onAction = {
                    cameraStatus = null
                    retryToken++
                },
            )
        }

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
            if (allowFlash && live?.flashAvailable == true) {
                ChromeIconButton(
                    icon = when (flashMode) {
                        CameraFlashMode.OFF -> Icons.Filled.FlashOff
                        CameraFlashMode.AUTO -> Icons.Filled.FlashAuto
                        CameraFlashMode.ON -> Icons.Filled.FlashOn
                    },
                    description = texts.flashLabel(flashMode),
                    testTag = GuidedCameraTestTags.FLASH,
                    onClick = { flashOrdinal = flashMode.next().ordinal },
                )
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
            val aviso = if (captureFailedShown) texts.captureFailed else hint
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
                    ShutterButton(
                        capturing = capturing,
                        description = texts.capture,
                        capturingDescription = texts.capturing,
                        onClick = ::disparar,
                    )
                    if (live.canSwitchLens) {
                        ChromeIconButton(
                            icon = Icons.Filled.Cameraswitch,
                            description = texts.switchLensLabel(activeLens),
                            testTag = GuidedCameraTestTags.SWITCH_LENS,
                            enabled = !capturing,
                            onClick = {
                                cameraStatus = null
                                lensOrdinal = activeLens.toggled().ordinal
                            },
                        )
                    } else {
                        Spacer(Modifier.size(CHROME_BUTTON_SIZE))
                    }
                }
            }
        }

        overlayContent()
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
) {
    Box(
        modifier = Modifier
            .size(SHUTTER_SIZE)
            .clip(CircleShape)
            .border(4.dp, CameraChrome.content, CircleShape)
            .clickable(enabled = !capturing, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (capturing) capturingDescription else description }
            .testTag(GuidedCameraTestTags.CAPTURE),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SHUTTER_SIZE - 16.dp)
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
