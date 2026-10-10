package br.com.codecacto.kmplib.media.voicenote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.media.AudioRecorder
import br.com.codecacto.kmplib.media.AudioRecorderConfig
import br.com.codecacto.kmplib.media.AudioRecorderState
import br.com.codecacto.kmplib.media.AudioRecordingResult
import br.com.codecacto.kmplib.media.RecordedAudio
import br.com.codecacto.kmplib.media.createAudioRecorder
import br.com.codecacto.kmplib.media.formatVoiceNoteDuration
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionState
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import br.com.codecacto.kmplib.platform.permission.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado do gravador de nota de voz — o par do [VoiceNoteMicButton] com a [VoiceNoteRecordingBar].
 * Os dois leem o MESMO estado: no compositor da conversa, a barra ocupa o lugar do campo de texto
 * enquanto grava, e o botão de microfone **fica onde está** (é ele que tem o dedo em cima).
 *
 * Crie com [rememberVoiceNoteRecorderState].
 */
@Stable
class VoiceNoteRecorderState internal constructor(
    internal val recorder: AudioRecorder,
    private val permission: PermissionState,
    private val scope: CoroutineScope,
    private val gestureConfig: VoiceNoteGestureConfig,
    private val onRecorded: () -> (RecordedAudio) -> Unit,
    private val onError: () -> (VoiceNoteError) -> Unit,
) {
    /** O gesto em curso. */
    var gesture: VoiceNoteGestureState by mutableStateOf(VoiceNoteGestureState.Idle)
        private set

    /** `true` enquanto grava (com o dedo ou travado). */
    val isRecording: Boolean get() = gesture != VoiceNoteGestureState.Idle

    /** `true` se a permissão foi negada de vez (a saída é abrir as configurações). */
    val permissionPermanentlyDenied: Boolean get() = permission.status == PermissionStatus.PERMANENTLY_DENIED

    /** Abre as configurações do app (permissão de microfone negada de vez). */
    fun openSettings() = permission.openSettings()

    /** Entrada do gesto (o botão chama; também serve a teste e a um gatilho próprio). */
    fun onEvent(event: VoiceNoteGestureEvent) {
        if (event is VoiceNoteGestureEvent.Press && gesture == VoiceNoteGestureState.Idle && !permission.isGranted) {
            // Pede e NÃO começa: gravar exige que a pessoa encoste de novo, já com o microfone liberado.
            if (permission.status == PermissionStatus.PERMANENTLY_DENIED) {
                onError()(VoiceNoteError.PERMISSION_DENIED)
            } else {
                permission.request()
            }
            return
        }
        aplicar(reduceVoiceNoteGesture(gesture, event, gestureConfig))
    }

    internal fun onRecorderState(state: AudioRecorderState) {
        when (state) {
            is AudioRecorderState.LimitReached -> aplicar(voiceNoteGestureOnLimitReached(gesture))
            is AudioRecorderState.Failed -> if (gesture != VoiceNoteGestureState.Idle) {
                gesture = VoiceNoteGestureState.Idle
                onError()(state.error.toVoiceNoteError())
            }
            else -> Unit
        }
    }

    private fun aplicar(passo: VoiceNoteGestureStep) {
        gesture = passo.state
        when (passo.effect) {
            VoiceNoteGestureEffect.START -> scope.launch {
                if (!recorder.start()) {
                    gesture = VoiceNoteGestureState.Idle
                    val falha = recorder.state.value as? AudioRecorderState.Failed
                    onError()(falha?.error?.toVoiceNoteError() ?: VoiceNoteError.FAILED)
                } else if (gesture == VoiceNoteGestureState.Idle) {
                    // Soltou/cancelou antes de o gravador terminar de abrir.
                    recorder.cancel()
                }
            }
            VoiceNoteGestureEffect.SEND -> scope.launch {
                when (val r = recorder.stop()) {
                    is AudioRecordingResult.Recorded -> onRecorded()(r.audio)
                    AudioRecordingResult.TooShort -> onError()(VoiceNoteError.TOO_SHORT)
                    is AudioRecordingResult.Failed -> onError()(r.error.toVoiceNoteError())
                    AudioRecordingResult.NotRecording -> Unit
                }
            }
            VoiceNoteGestureEffect.CANCEL -> recorder.cancel()
            VoiceNoteGestureEffect.NONE -> Unit
        }
    }
}

/**
 * Cria o estado do gravador de nota de voz.
 *
 * @param onRecorded a nota pronta (arquivo temporário — envie e apague, ou mova para a sua fila).
 * @param onError o que mostrar quando não saiu ([VoiceNoteTexts.errorMessage] tem a frase).
 * @param recorder o gravador (default: o da plataforma, liberado ao sair da composição).
 */
@Composable
fun rememberVoiceNoteRecorderState(
    onRecorded: (RecordedAudio) -> Unit,
    onError: (VoiceNoteError) -> Unit = {},
    config: AudioRecorderConfig = AudioRecorderConfig(),
    gestureConfig: VoiceNoteGestureConfig = VoiceNoteGestureConfig(),
    recorder: AudioRecorder? = null,
): VoiceNoteRecorderState {
    val gravador = remember(recorder, config) { recorder ?: createAudioRecorder(config) }
    val permissao = rememberPermissionState(AppPermission.MICROPHONE, requestOnFirstAppearance = false)
    val scope = rememberCoroutineScope()
    val gravado by rememberUpdatedState(onRecorded)
    val erro by rememberUpdatedState(onError)
    val state = remember(gravador, permissao, gestureConfig) {
        VoiceNoteRecorderState(gravador, permissao, scope, gestureConfig, { gravado }, { erro })
    }
    LaunchedEffect(gravador) { gravador.state.collect { state.onRecorderState(it) } }
    DisposableEffect(gravador) {
        onDispose { if (recorder == null) gravador.release() else gravador.cancel() }
    }
    return state
}

/**
 * O botão de microfone: **segurar** grava e soltar envia (arrastar para o lado cancela, para cima
 * trava); **tocar** começa a gravar sem segurar e um segundo toque envia — que é também o que o
 * TalkBack/VoiceOver faz com um toque duplo, então o gravador é acessível sem gesto especial.
 */
@Composable
fun VoiceNoteMicButton(
    state: VoiceNoteRecorderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    texts: VoiceNoteTexts = rememberVoiceNoteTexts(),
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gravando = state.isRecording
    val travado = state.gesture is VoiceNoteGestureState.Locked
    val fundo = if (gravando) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val conteudo = if (gravando) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) fundo else fundo.copy(alpha = 0.38f))
            .testTag(if (travado) VoiceNoteTestTags.SEND else VoiceNoteTestTags.MIC)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (travado) texts.send else texts.record
                onClick {
                    if (!enabled) return@onClick false
                    val t = currentTimeMillis()
                    state.onEvent(VoiceNoteGestureEvent.Press(t))
                    state.onEvent(VoiceNoteGestureEvent.Release(t))
                    true
                }
            }
            .pointerInput(state, enabled, rtl) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val toque = awaitFirstDown(requireUnconsumed = false)
                    toque.consume()
                    state.onEvent(VoiceNoteGestureEvent.Press(toque.uptimeMillis))
                    var total = Offset.Zero
                    while (true) {
                        val evento = awaitPointerEvent()
                        val mudanca = evento.changes.firstOrNull { it.id == toque.id }
                        if (mudanca == null) {
                            state.onEvent(VoiceNoteGestureEvent.GestureLost)
                            break
                        }
                        if (!mudanca.pressed) {
                            state.onEvent(VoiceNoteGestureEvent.Release(mudanca.uptimeMillis))
                            break
                        }
                        total += mudanca.positionChange()
                        mudanca.consume()
                        state.onEvent(VoiceNoteGestureEvent.Drag(if (rtl) -total.x else total.x, total.y))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (travado) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
            contentDescription = null,
            tint = if (enabled) conteudo else conteudo.copy(alpha = 0.38f),
        )
    }
}

/**
 * A barra que aparece enquanto grava: ponto pulsando + tempo + onda ao vivo, e
 * - com o dedo no botão: "Deslize para cancelar" (esmaece conforme o arrasto) e o cadeado subindo;
 * - travada: botão **Cancelar** (o envio é o próprio botão de microfone, que virou "Enviar").
 *
 * Fica no lugar do campo de texto, com `Modifier.weight(1f)` no compositor.
 */
@Composable
fun VoiceNoteRecordingBar(
    state: VoiceNoteRecorderState,
    modifier: Modifier = Modifier,
    texts: VoiceNoteTexts = rememberVoiceNoteTexts(),
) {
    val gravador by state.recorder.state.collectAsState()
    val (decorrido, niveis) = when (val g = gravador) {
        is AudioRecorderState.Recording -> g.elapsedMillis to g.levels
        is AudioRecorderState.LimitReached -> g.elapsedMillis to g.levels
        else -> 0L to emptyList()
    }
    val tempo = formatVoiceNoteDuration(decorrido)
    val gesto = state.gesture
    val pulso by rememberInfiniteTransition(label = "voz-pulso").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "voz-pulso-alpha",
    )
    Row(
        modifier = modifier
            .height(48.dp)
            .testTag(VoiceNoteTestTags.RECORDING)
            .semantics(mergeDescendants = false) {
                contentDescription = texts.recording(tempo)
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (gesto is VoiceNoteGestureState.Locked) {
            IconButton(
                onClick = { state.onEvent(VoiceNoteGestureEvent.CancelTapped) },
                modifier = Modifier.testTag(VoiceNoteTestTags.CANCEL),
            ) {
                Icon(Icons.Filled.Close, contentDescription = texts.cancel, tint = MaterialTheme.colorScheme.error)
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.size(10.dp).alpha(pulso).clip(CircleShape).background(MaterialTheme.colorScheme.error),
        )
        Text(tempo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        val holding = gesto as? VoiceNoteGestureState.Holding
        if (holding != null) {
            Text(
                text = "‹ " + texts.slideToCancel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).alpha(1f - holding.cancelProgress * 0.8f),
                maxLines = 1,
            )
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = (holding.lockProgress * 12f).dp)
                    .alpha(0.4f + holding.lockProgress * 0.6f),
            )
        } else {
            AudioWaveform(
                levels = niveis,
                mode = AudioWaveformMode.TAIL,
                modifier = Modifier.weight(1f).height(28.dp),
                activeColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}
