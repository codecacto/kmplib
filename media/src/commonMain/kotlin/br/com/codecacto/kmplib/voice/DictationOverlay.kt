package br.com.codecacto.kmplib.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import br.com.codecacto.kmplib.platform.permission.createPermissionManager
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

/**
 * Textos (i18n) do [DictationOverlay]. Defaults em pt-BR; o app pode sobrescrever.
 */
data class DictationTexts(
    val title: String = "Ditar valor",
    val listeningHint: String = "Pode falar…",
    val processingHint: String = "Reconhecendo…",
    val recognizedLabel: String = "Reconhecido:",
    val confirmButton: String = "Confirmar",
    val retryButton: String = "Repetir",
    val manualButton: String = "Digitar",
    val cancelButton: String = "Cancelar",
    val permissionDenied: String = "Permissão de microfone negada. Digite o valor.",
    val noMatch: String = "Não entendi. Tente de novo ou digite.",
    val networkError: String = "Sem conexão para reconhecer. Digite o valor.",
    val unavailable: String = "Reconhecimento de voz indisponível neste aparelho.",
    val genericError: String = "Falha no reconhecimento. Tente de novo ou digite.",
    val couldNotParse: String = "Não consegui identificar um número. Repita ou digite.",
)

private sealed interface DictationUi {
    data object Requesting : DictationUi
    data object Listening : DictationUi
    data object Processing : DictationUi
    data class Recognized(val value: String, val raw: String) : DictationUi
    data class Failure(val message: String) : DictationUi
}

/**
 * Overlay de **ditado por voz** com confirmação obrigatória de 1 toque.
 *
 * Fluxo (Arroba Certa O0-1 — peso falado no curral):
 * 1. Pede a permissão de microfone (via `PermissionManager`); negada → mensagem + fallback teclado.
 * 2. Ouve (mic animado + texto parcial ao vivo).
 * 3. Ao reconhecer, extrai o número via [valueParser] e mostra o valor **grande** — **sem aceitar
 *    automaticamente**. O usuário precisa tocar **Confirmar** (1 toque) para aceitar
 *    ([onConfirm]). Erro de reconhecimento tem custo financeiro, por isso a confirmação é sempre
 *    exigida.
 * 4. Sempre há **fallback imediato ao teclado numérico** ([onManualEntry]) e "Repetir".
 *
 * O overlay **não** persiste nada; só entrega o valor confirmado. Cores/tipografia via tokens do
 * tema (sem hardcode); alvos de toque grandes (uso com luvas).
 *
 * @param recognizer reconhecedor (use [rememberSpeechRecognizer]).
 * @param onConfirm chamado com o valor **confirmado** (texto já parseado por [valueParser]).
 * @param onDismiss fecha o overlay sem confirmar (cancelar / toque fora).
 * @param modifier modificador do cartão.
 * @param config configuração da sessão (idioma, on-device). Default pt-BR on-device.
 * @param texts textos i18n.
 * @param valueParser extrai o valor final do texto reconhecido. Default: número pt-BR
 *   ([SpokenNumberParser.parseToDisplay]). Retorne `null` se o texto não contém valor válido.
 * @param unitLabel unidade exibida após o valor reconhecido (ex.: "kg", "@"). Opcional.
 * @param onManualEntry fallback para digitar no teclado; quando informado, exibe o botão "Digitar"
 *   (fecha o overlay e o app foca o campo). Quando `null`, o botão não aparece.
 */
@Composable
fun DictationOverlay(
    recognizer: SpeechRecognizer,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    config: SpeechRecognitionConfig = SpeechRecognitionConfig(),
    texts: DictationTexts = DictationTexts(),
    valueParser: (String) -> String? = { SpokenNumberParser.parseToDisplay(it) },
    unitLabel: String? = null,
    onManualEntry: (() -> Unit)? = null,
) {
    val permissionManager = remember { createPermissionManager() }
    var ui by remember { mutableStateOf<DictationUi>(DictationUi.Requesting) }
    val partial by recognizer.partialText.collectAsState()

    // Traduz uma falha do recognizer numa mensagem.
    fun messageFor(error: SpeechRecognitionError): String = when (error) {
        SpeechRecognitionError.PermissionDenied -> texts.permissionDenied
        SpeechRecognitionError.NoMatch, SpeechRecognitionError.Timeout -> texts.noMatch
        SpeechRecognitionError.Network -> texts.networkError
        SpeechRecognitionError.Unavailable -> texts.unavailable
        SpeechRecognitionError.Busy, SpeechRecognitionError.Unknown -> texts.genericError
    }

    // Coleta os eventos do recognizer enquanto o overlay existir.
    LaunchedEffect(recognizer) {
        recognizer.events.collect { ev ->
            when (ev) {
                is SpeechEvent.ReadyForSpeech -> ui = DictationUi.Listening
                is SpeechEvent.EndOfSpeech -> ui = DictationUi.Processing
                is SpeechEvent.Partial -> {
                    if (ui is DictationUi.Requesting) ui = DictationUi.Listening
                }
                is SpeechEvent.Result -> {
                    val parsed = valueParser(ev.text)
                    ui = if (parsed != null) {
                        DictationUi.Recognized(parsed, ev.text)
                    } else {
                        DictationUi.Failure(texts.couldNotParse)
                    }
                }
                is SpeechEvent.Failed -> ui = DictationUi.Failure(messageFor(ev.error))
            }
        }
    }

    // Pede permissão e inicia a escuta ao abrir.
    LaunchedEffect(Unit) {
        val status = permissionManager.checkPermission(AppPermission.MICROPHONE)
        val granted = if (status == PermissionStatus.GRANTED) {
            true
        } else {
            permissionManager.requestPermission(AppPermission.MICROPHONE)
                .first() == PermissionStatus.GRANTED
        }
        if (granted) {
            recognizer.startListening(config)
        } else {
            ui = DictationUi.Failure(texts.permissionDenied)
        }
    }

    // Ao sair da composição, cancela qualquer escuta em andamento.
    DisposableEffect(recognizer) {
        onDispose { recognizer.cancel() }
    }

    fun restart() {
        ui = DictationUi.Requesting
        recognizer.startListening(config)
    }

    Dialog(onDismissRequest = {
        recognizer.cancel()
        onDismiss()
    }) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = texts.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(20.dp))

                when (val current = ui) {
                    is DictationUi.Requesting, is DictationUi.Listening -> {
                        MicIndicator(active = true)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = partial.ifBlank { texts.listeningHint },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (partial.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Actions(
                            texts = texts,
                            onManualEntry = onManualEntry,
                            onCancel = { recognizer.cancel(); onDismiss() },
                            onStop = { recognizer.stopListening() },
                            showStop = true,
                        )
                    }

                    is DictationUi.Processing -> {
                        MicIndicator(active = false)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = texts.processingHint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is DictationUi.Recognized -> {
                        Text(
                            text = texts.recognizedLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = buildString {
                                append(current.value)
                                if (!unitLabel.isNullOrBlank()) append(" ").append(unitLabel)
                            },
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        // CONFIRMAÇÃO obrigatória de 1 toque (nunca aceita automaticamente).
                        AppButton(
                            text = texts.confirmButton,
                            onClick = { onConfirm(current.value) },
                            primaryColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppOutlinedButton(
                                text = texts.retryButton,
                                onClick = { restart() },
                                modifier = Modifier.weight(1f),
                                primaryColor = MaterialTheme.colorScheme.primary,
                            )
                            if (onManualEntry != null) {
                                AppOutlinedButton(
                                    text = texts.manualButton,
                                    onClick = { recognizer.cancel(); onManualEntry() },
                                    modifier = Modifier.weight(1f),
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }

                    is DictationUi.Failure -> {
                        Icon(
                            imageVector = Icons.Filled.MicOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Actions(
                            texts = texts,
                            onManualEntry = onManualEntry,
                            onCancel = { recognizer.cancel(); onDismiss() },
                            onRetry = { restart() },
                            showRetry = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Actions(
    texts: DictationTexts,
    onManualEntry: (() -> Unit)?,
    onCancel: () -> Unit,
    onStop: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    showStop: Boolean = false,
    showRetry: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showRetry && onRetry != null) {
            AppButton(
                text = texts.retryButton,
                onClick = onRetry,
                primaryColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onManualEntry != null) {
                AppOutlinedButton(
                    text = texts.manualButton,
                    onClick = { onManualEntry() },
                    modifier = Modifier.weight(1f),
                    primaryColor = MaterialTheme.colorScheme.primary,
                )
            }
            AppOutlinedButton(
                text = texts.cancelButton,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                primaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Ícone de microfone com pulsação (quando [active]). Realce em `primary`; disco de fundo em
 * `primary@12%`.
 */
@Composable
private fun MicIndicator(active: Boolean) {
    val scale = if (active) {
        val transition = rememberInfiniteTransition(label = "mic")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "micScale",
        ).value
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
    }
}
