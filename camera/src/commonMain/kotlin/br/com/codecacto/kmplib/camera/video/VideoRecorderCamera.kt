package br.com.codecacto.kmplib.camera.video

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.camera.guided.CHROME_BUTTON_SIZE
import br.com.codecacto.kmplib.camera.guided.CameraChrome
import br.com.codecacto.kmplib.camera.guided.CameraGuide
import br.com.codecacto.kmplib.camera.guided.CameraLens
import br.com.codecacto.kmplib.camera.guided.CameraMessage
import br.com.codecacto.kmplib.camera.guided.ChromeIconButton
import br.com.codecacto.kmplib.camera.guided.GuideOverlay
import br.com.codecacto.kmplib.camera.guided.GuidedCameraStatus
import br.com.codecacto.kmplib.camera.guided.SHUTTER_SIZE
import br.com.codecacto.kmplib.platform.KeepScreenOn
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import br.com.codecacto.kmplib.platform.permission.rememberPermissionState
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * **Gravador de vídeo** (2.286.0) — a tela de gravar da própria lib: teto que **para sozinho**,
 * contador, guia de enquadramento sobreposto, troca de câmera e contagem regressiva opcional.
 *
 * Nasceu do pedido de vídeo do App do Personal: o aluno grava a série (60 s, câmera no chão, de
 * longe) para o personal corrigir a execução. Serve igual a vistoria, depoimento, resposta em vídeo.
 *
 * ```kotlin
 * VideoRecorderCamera(
 *     onRecorded = { video -> viewModel.onAction(Action.Gravado(video)) },   // → prévia, Refazer, Enviar
 *     maxDurationMillis = 60_000,
 *     startDelaySeconds = 3,                       // dá tempo de se posicionar
 *     guide = CameraGuide.painter(painterResource(Res.drawable.enquadramento_corpo), alpha = 0.4f),
 *     hint = "Corpo inteiro dentro da moldura",
 *     onClose = { navController.popBackStack() },
 * )
 * ```
 * Depois: prévia com o `VideoPlayer` do `kmplib-video` (`VideoMedia(url = video.uri)`), compressão
 * com o `VideoTranscoder` (`VideoTranscodeSource.fromPath(video.path)`) e [RecordedVideo.deleteFile]
 * do original quando não precisar mais.
 *
 * ## O preview mostra exatamente o vídeo
 * O vídeo é 16:9 (9:16 em pé) e o preview é desenhado **inteiro**, sem corte, numa caixa da mesma
 * proporção — como na câmera guiada, o que está dentro do guia na tela está no vídeo. O guia não é
 * gravado.
 *
 * ## Gravação
 * - Um botão só: toca para gravar, toca para parar. Antes de [minDurationMillis] o parar não
 *   responde (o servidor recusa vídeo de 0 s) e o botão diz isso ao leitor de tela.
 * - O teto é da **plataforma** (`setDurationLimitMillis` no CameraX, `maxRecordedDuration` no
 *   AVFoundation): a gravação para no ponto certo mesmo com a tela travada, e o arquivo sai inteiro.
 *   Parou no teto → [RecordedVideo.reachedMaxDuration] e o aviso "Tempo máximo atingido".
 * - A troca de câmera some durante a gravação (trocar no meio cortaria o arquivo).
 * - Tela acesa enquanto grava. Sem áudio por padrão ([recordAudio]); com ele, pede o microfone
 *   **depois** da câmera, nunca os dois diálogos juntos.
 * - Sair da tela gravando para a gravação e apaga o arquivo.
 * - A câmera continua aberta depois de cada vídeo: o app decide se navega para a prévia.
 *
 * ## Permissões e estados
 * Manifesto do APP: `android.permission.CAMERA` (+ `RECORD_AUDIO` com [recordAudio]). `Info.plist`:
 * `NSCameraUsageDescription` (+ `NSMicrophoneUsageDescription`). Negada → mensagem + "Permitir";
 * negada em definitivo → "Abrir Configurações"; sem câmera (simulador do iOS) → mensagem; sessão que
 * não sobe → "Tentar novamente". Cada caso também chega em [onError].
 *
 * ## Acessibilidade e automação
 * Botão de 76 dp com anel de progresso, descrição pelo estado (gravar / parar / aguarde / contagem);
 * o contador diz "0:12 de 1:00". Ids em [VideoRecorderTestTags].
 *
 * @param onRecorded o vídeo pronto, na main thread.
 * @param maxDurationMillis o teto (1 s..10 min); para sozinho nele.
 * @param minDurationMillis antes disto o botão não para.
 * @param recordAudio gravar o som (pede o microfone).
 * @param quality resolução da gravação; o padrão 720p é o que a compressão da lib entrega.
 * @param startDelaySeconds contagem regressiva antes de gravar (0..10; 0 = imediato). Tocar de novo
 *   durante a contagem cancela.
 * @param guide desenho sobre o preview; `null` = sem guia.
 * @param hint instrução curta sobre o preview.
 * @param onRecordingChange `true` ao começar a contagem/gravação, `false` ao terminar — para o app
 *   esconder a própria barra ou segurar o "voltar".
 * @param overlayContent slot por cima de tudo; recebe o [RecordingClock] durante a gravação.
 */
@Composable
fun VideoRecorderCamera(
    onRecorded: (RecordedVideo) -> Unit,
    modifier: Modifier = Modifier,
    maxDurationMillis: Long = DEFAULT_MAX_RECORDING_MILLIS,
    minDurationMillis: Long = DEFAULT_MIN_RECORDING_MILLIS,
    recordAudio: Boolean = false,
    quality: VideoRecordingQuality = VideoRecordingQuality.HD_720P,
    startDelaySeconds: Int = 0,
    guide: CameraGuide? = null,
    hint: String? = null,
    initialLens: CameraLens = CameraLens.BACK,
    allowLensSwitch: Boolean = true,
    texts: VideoRecorderTexts = rememberVideoRecorderTexts(),
    onError: (VideoRecorderError) -> Unit = {},
    onClose: (() -> Unit)? = null,
    onRecordingChange: (Boolean) -> Unit = {},
    overlayContent: @Composable BoxScope.(RecordingClock?) -> Unit = {},
) {
    val teto = coerceMaxRecordingMillis(maxDurationMillis)
    val minimo = coerceMinRecordingMillis(minDurationMillis, teto)
    val atraso = coerceStartDelaySeconds(startDelaySeconds)

    val camera = rememberPermissionState(AppPermission.CAMERA)
    // O microfone é pedido DEPOIS da câmera (dois diálogos ao mesmo tempo: o Android descarta um).
    val microfone = rememberPermissionState(AppPermission.MICROPHONE, requestOnFirstAppearance = false)
    LifecycleResumeEffect(camera, microfone) {
        camera.refresh()
        if (recordAudio) microfone.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(recordAudio, camera.isGranted) {
        if (recordAudio && camera.isGranted) {
            microfone.refreshNow()
            if (microfone.status == PermissionStatus.NOT_REQUESTED) microfone.request()
        }
    }

    val currentOnRecorded by rememberUpdatedState(onRecorded)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnRecordingChange by rememberUpdatedState(onRecordingChange)

    var lensOrdinal by rememberSaveable { mutableIntStateOf(initialLens.ordinal) }
    val lens = CameraLens.entries[lensOrdinal]
    var cameraStatus by remember { mutableStateOf<GuidedCameraStatus?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }
    val handle = remember { VideoRecordHandle() }

    var fase by remember { mutableStateOf<RecorderPhase>(RecorderPhase.Idle) }
    var decorrido by remember { mutableLongStateOf(0L) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val state = videoRecorderStateOf(camera.status, microfone.status, recordAudio, cameraStatus, allowLensSwitch)
    val error = videoRecorderErrorOf(camera.status, microfone.status, state)
    LaunchedEffect(error) { error?.let { currentOnError(it) } }

    // Câmera saiu do ar no meio (permissão revogada, sessão caiu): a fase volta ao início.
    val aoVivo = state is VideoRecorderState.Live
    LaunchedEffect(aoVivo) {
        if (!aoVivo) fase = RecorderPhase.Idle
    }

    val ocupado = fase != RecorderPhase.Idle
    LaunchedEffect(ocupado) { currentOnRecordingChange(ocupado) }
    KeepScreenOn(enabled = fase is RecorderPhase.Recording || fase is RecorderPhase.Countdown)

    LaunchedEffect(aviso) {
        if (aviso != null) {
            delay(NOTICE_MILLIS)
            aviso = null
        }
    }

    val activeLens = (cameraStatus as? GuidedCameraStatus.Ready)?.activeLens ?: lens

    fun onEvento(evento: VideoRecordEvent) {
        when (evento) {
            VideoRecordEvent.Started -> {
                decorrido = 0L
                fase = RecorderPhase.Recording(TimeSource.Monotonic.markNow())
            }
            is VideoRecordEvent.Finished -> {
                fase = RecorderPhase.Idle
                decorrido = 0L
                if (evento.video.reachedMaxDuration) aviso = texts.limitReached
                currentOnRecorded(evento.video)
            }
            is VideoRecordEvent.Failed -> {
                fase = RecorderPhase.Idle
                decorrido = 0L
                aviso = if (evento.failure == VideoRecordFailure.NO_SPACE) texts.noSpace else texts.recordingFailed
                currentOnError(
                    if (evento.failure == VideoRecordFailure.NO_SPACE) VideoRecorderError.NO_SPACE else VideoRecorderError.RECORDING_FAILED,
                )
            }
        }
    }

    fun iniciarGravacao() {
        fase = RecorderPhase.Starting
        handle.startRecording(::onEvento)
    }

    fun tocarBotao() {
        if (state !is VideoRecorderState.Live) return
        when (fase) {
            RecorderPhase.Idle -> {
                aviso = null
                if (atraso > 0) fase = RecorderPhase.Countdown(atraso) else iniciarGravacao()
            }
            is RecorderPhase.Countdown -> fase = RecorderPhase.Idle
            is RecorderPhase.Recording -> if (recordingClockOf(decorrido, teto, minimo).canStop) {
                fase = RecorderPhase.Finishing
                handle.stopRecording()
            }
            RecorderPhase.Starting, RecorderPhase.Finishing -> Unit
        }
    }

    // Contagem regressiva: um passo por segundo; chegando a zero, grava.
    val contagem = (fase as? RecorderPhase.Countdown)?.remaining
    LaunchedEffect(contagem) {
        if (contagem != null) {
            delay(1_000L)
            if (fase is RecorderPhase.Countdown) {
                if (contagem > 1) fase = RecorderPhase.Countdown(contagem - 1) else iniciarGravacao()
            }
        }
    }

    // O relógio do contador. O teto de verdade é da plataforma; isto só desenha (e, se a plataforma
    // não parar 2 s depois do teto, pede a parada — nunca deveria acontecer).
    val gravandoDesde = (fase as? RecorderPhase.Recording)?.since
    LaunchedEffect(gravandoDesde) {
        if (gravandoDesde != null) {
            while (true) {
                decorrido = gravandoDesde.elapsedNow().inWholeMilliseconds
                if (decorrido > teto + SAFETY_STOP_MILLIS && fase is RecorderPhase.Recording) {
                    fase = RecorderPhase.Finishing
                    handle.stopRecording()
                }
                delay(TICK_MILLIS)
            }
        }
    }

    val relogio = if (fase is RecorderPhase.Recording || fase == RecorderPhase.Finishing) {
        recordingClockOf(decorrido, teto, minimo)
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraChrome.background)
            .testTag(VideoRecorderTestTags.ROOT),
    ) {
        if (camera.isGranted && (!recordAudio || microfone.isGranted)) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ratio = videoRecorderPreviewAspectRatio(maxWidth.value, maxHeight.value)
                Box(Modifier.aspectRatio(ratio).testTag(VideoRecorderTestTags.PREVIEW)) {
                    key(retryToken, lens, recordAudio, quality, teto) {
                        VideoRecorderPreview(
                            modifier = Modifier.fillMaxSize(),
                            lens = lens,
                            recordAudio = recordAudio,
                            quality = quality,
                            maxDurationMillis = teto,
                            handle = handle,
                            onStatus = { cameraStatus = it },
                        )
                    }
                    if (guide != null && state is VideoRecorderState.Live) {
                        GuideOverlay(guide, testTag = VideoRecorderTestTags.GUIDE)
                    }
                }
            }
        }

        when (state) {
            is VideoRecorderState.Live -> Unit
            VideoRecorderState.Starting -> RecorderMessage(Icons.Outlined.Videocam, null, texts.starting)
            is VideoRecorderState.PermissionRequired -> when (state.permission) {
                VideoRecorderPermission.CAMERA -> RecorderMessage(
                    icon = Icons.Outlined.Videocam,
                    title = texts.permissionTitle,
                    message = texts.permissionMessage,
                    actionLabel = texts.permissionAllow,
                    onAction = camera::request,
                )
                VideoRecorderPermission.MICROPHONE -> RecorderMessage(
                    icon = Icons.Outlined.Mic,
                    title = texts.microphoneTitle,
                    message = texts.microphoneMessage,
                    actionLabel = texts.microphoneAllow,
                    onAction = microfone::request,
                )
            }
            is VideoRecorderState.PermissionPermanentlyDenied -> when (state.permission) {
                VideoRecorderPermission.CAMERA -> RecorderMessage(
                    icon = Icons.Outlined.NoPhotography,
                    title = texts.permissionTitle,
                    message = texts.permissionDeniedMessage,
                    actionLabel = texts.openSettings,
                    onAction = camera::openAppSettings,
                )
                VideoRecorderPermission.MICROPHONE -> RecorderMessage(
                    icon = Icons.Outlined.MicOff,
                    title = texts.microphoneTitle,
                    message = texts.microphoneDeniedMessage,
                    actionLabel = texts.openSettings,
                    onAction = microfone::openAppSettings,
                )
            }
            VideoRecorderState.CameraUnavailable ->
                RecorderMessage(Icons.Outlined.NoPhotography, null, texts.cameraUnavailable)
            is VideoRecorderState.InitializationFailed -> RecorderMessage(
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

        // Barra de cima: fechar à esquerda (escondido gravando), contador no meio.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onClose != null && !ocupado) {
                ChromeIconButton(
                    icon = Icons.Filled.Close,
                    description = texts.close,
                    testTag = VideoRecorderTestTags.CLOSE,
                    onClick = onClose,
                )
            } else {
                Spacer(Modifier.size(CHROME_BUTTON_SIZE))
            }
            if (relogio != null) RecordingTimer(relogio, texts)
            Spacer(Modifier.size(CHROME_BUTTON_SIZE))
        }

        if (contagem != null) {
            Text(
                text = contagem.toString(),
                color = CameraChrome.content,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(CameraChrome.scrim, CircleShape)
                    .padding(horizontal = 36.dp, vertical = 8.dp)
                    .semantics {
                        contentDescription = texts.countdown(contagem)
                        liveRegion = LiveRegionMode.Assertive
                    }
                    .testTag(VideoRecorderTestTags.COUNTDOWN),
            )
        }

        // Rodapé: instrução/aviso, botão de gravar e troca de câmera.
        if (state is VideoRecorderState.Live) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val texto = aviso ?: if (fase == RecorderPhase.Finishing) texts.finishing else hint
                if (texto != null) {
                    Text(
                        text = texto,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CameraChrome.content,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .background(CameraChrome.scrim, MaterialTheme.shapes.medium)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag(VideoRecorderTestTags.MESSAGE)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(CHROME_BUTTON_SIZE))
                    RecordButton(
                        fase = fase,
                        relogio = relogio,
                        texts = texts,
                        onClick = ::tocarBotao,
                    )
                    if (state.canSwitchLens && !ocupado) {
                        ChromeIconButton(
                            icon = Icons.Filled.Cameraswitch,
                            description = texts.switchLensLabel(activeLens),
                            testTag = VideoRecorderTestTags.SWITCH_LENS,
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

        overlayContent(relogio)
    }
}

/** A fase do botão. Não é salva: processo morto no meio de uma gravação não tem gravação a retomar. */
private sealed interface RecorderPhase {
    data object Idle : RecorderPhase

    data class Countdown(val remaining: Int) : RecorderPhase

    /** Pediu para gravar, a plataforma ainda não confirmou o primeiro quadro. */
    data object Starting : RecorderPhase

    data class Recording(val since: TimeSource.Monotonic.ValueTimeMark) : RecorderPhase

    /** Pediu para parar, o arquivo está sendo fechado. */
    data object Finishing : RecorderPhase
}

@Composable
private fun RecordingTimer(relogio: RecordingClock, texts: VideoRecorderTexts) {
    val decorrido = formatRecordingTime(relogio.elapsedMillis)
    val total = formatRecordingTime(relogio.maxMillis)
    Row(
        modifier = Modifier
            .background(if (relogio.isFinalStretch) RECORD_RED else CameraChrome.scrim, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = texts.recordingTime(decorrido, total) }
            .testTag(VideoRecorderTestTags.TIMER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (relogio.isFinalStretch) CameraChrome.content else RECORD_RED))
        Text(
            text = "$decorrido / $total",
            color = CameraChrome.content,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** O botão único: círculo vermelho para gravar, quadrado arredondado para parar, anel = progresso. */
@Composable
private fun RecordButton(
    fase: RecorderPhase,
    relogio: RecordingClock?,
    texts: VideoRecorderTexts,
    onClick: () -> Unit,
) {
    val gravando = fase is RecorderPhase.Recording
    val podeParar = relogio?.canStop == true
    val descricao = when (fase) {
        RecorderPhase.Idle -> texts.record
        is RecorderPhase.Countdown -> texts.cancelCountdown
        RecorderPhase.Starting -> texts.record
        is RecorderPhase.Recording -> if (podeParar) texts.stop else texts.stopDisabled
        RecorderPhase.Finishing -> texts.finishing
    }
    val habilitado = fase == RecorderPhase.Idle || fase is RecorderPhase.Countdown || (gravando && podeParar)
    Box(
        modifier = Modifier
            .size(SHUTTER_SIZE)
            .clip(CircleShape)
            .border(4.dp, CameraChrome.content, CircleShape)
            .clickable(enabled = habilitado, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = descricao }
            .testTag(VideoRecorderTestTags.RECORD),
        contentAlignment = Alignment.Center,
    ) {
        if (relogio != null) {
            Canvas(Modifier.fillMaxSize()) {
                val traco = 4.dp.toPx()
                drawArc(
                    color = RECORD_RED,
                    startAngle = -90f,
                    sweepAngle = 360f * relogio.progress,
                    useCenter = false,
                    topLeft = Offset(traco / 2, traco / 2),
                    size = Size(size.width - traco, size.height - traco),
                    style = Stroke(width = traco),
                )
            }
        }
        when {
            fase == RecorderPhase.Starting || fase == RecorderPhase.Finishing -> CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = CameraChrome.content,
                strokeWidth = 3.dp,
            )
            gravando -> Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (podeParar) RECORD_RED else RECORD_RED.copy(alpha = 0.5f)),
            )
            else -> Box(
                Modifier
                    .size(SHUTTER_SIZE - 16.dp)
                    .clip(CircleShape)
                    .background(RECORD_RED),
            )
        }
    }
}

/** As mensagens de estado do gravador, com os ids próprios. */
@Composable
private fun BoxScope.RecorderMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String?,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = CameraMessage(
    icon = icon,
    title = title,
    message = message,
    actionLabel = actionLabel,
    onAction = onAction,
    messageTestTag = VideoRecorderTestTags.MESSAGE,
    actionTestTag = VideoRecorderTestTags.PRIMARY_ACTION,
)

/**
 * O vermelho de "gravando" — convenção universal de câmera (a do sistema, a de qualquer app), como
 * o preto/branco do cromo ([CameraChrome]): independente do tema, porque sobre um vídeo qualquer a
 * cor da marca não diz "gravando".
 */
private val RECORD_RED: Color = Color(0xFFE53935)

private const val TICK_MILLIS = 100L
private const val NOTICE_MILLIS = 3_000L
private const val SAFETY_STOP_MILLIS = 2_000L
