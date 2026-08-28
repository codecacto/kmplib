package br.com.codecacto.kmplib.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de alto nível do reconhecimento de fala ([SpeechRecognizer]).
 *
 * - [Idle]: parado / pronto (não está ouvindo).
 * - [Listening]: capturando áudio do microfone (usuário falando).
 * - [Processing]: parou de ouvir e está transcrevendo o que capturou.
 * - [Error]: falha (ver [SpeechRecognizer.lastError]); volta a [Idle] ao reiniciar.
 */
enum class SpeechRecognitionState {
    Idle,
    Listening,
    Processing,
    Error,
}

/**
 * Causa de falha do reconhecimento (mapeada uniformemente das duas plataformas).
 *
 * - [PermissionDenied]: permissão de microfone negada — o app deve solicitar/abrir Ajustes.
 * - [NoMatch]: áudio capturado, mas nada reconhecido (fale novamente).
 * - [Network]: reconhecimento online exigido e sem rede (no curral, preferir on-device).
 * - [Busy]: já há um reconhecimento em andamento.
 * - [Unavailable]: dispositivo/idioma sem suporte a reconhecimento de fala.
 * - [Timeout]: silêncio prolongado / tempo de fala esgotado.
 * - [Unknown]: outra falha do motor.
 */
enum class SpeechRecognitionError {
    PermissionDenied,
    NoMatch,
    Network,
    Busy,
    Unavailable,
    Timeout,
    Unknown,
}

/**
 * Evento emitido pelo fluxo [SpeechRecognizer.events] durante uma sessão de ditado.
 */
sealed interface SpeechEvent {
    /** Reconhecedor pronto e ouvindo (usuário pode falar). */
    data object ReadyForSpeech : SpeechEvent

    /** Fim de fala detectado (transição para [SpeechRecognitionState.Processing]). */
    data object EndOfSpeech : SpeechEvent

    /** Transcrição **parcial** ao vivo (hipótese corrente; pode mudar). */
    data class Partial(val text: String) : SpeechEvent

    /** Transcrição **final** de uma sessão (texto reconhecido consolidado). */
    data class Result(val text: String) : SpeechEvent

    /** Falha na sessão. */
    data class Failed(val error: SpeechRecognitionError) : SpeechEvent
}

/**
 * Configuração de uma sessão de reconhecimento ([SpeechRecognizer.startListening]).
 *
 * @param languageTag idioma BCP-47 (default `pt-BR`, caso de uso do curral).
 * @param partialResults emitir [SpeechEvent.Partial] ao vivo (default `true` — a UI mostra o texto
 *   sendo reconhecido).
 * @param preferOffline preferir reconhecimento **on-device** (Android `EXTRA_PREFER_OFFLINE`; iOS
 *   `requiresOnDeviceRecognition`). Default `true` — o curral costuma estar sem sinal; on-device
 *   também é mais rápido e privado. Best-effort: se o idioma não tiver modelo on-device, a
 *   plataforma pode cair para o online.
 */
data class SpeechRecognitionConfig(
    val languageTag: String = "pt-BR",
    val partialResults: Boolean = true,
    val preferOffline: Boolean = true,
)

/**
 * Reconhecimento de fala **on-device** (speech-to-text), multiplataforma.
 *
 * Padrão-ouro = API oficial de cada plataforma, via `expect/actual`:
 * - Android: `android.speech.SpeechRecognizer` (`RecognitionListener`).
 * - iOS: framework `Speech` (`SFSpeechRecognizer` + `SFSpeechAudioBufferRecognitionRequest` +
 *   `AVAudioEngine`).
 *
 * Sem WebView, sem serviço de terceiros. Focado no caso "capturar um número (peso) falado no curral,
 * pt-BR" — daí o default [SpeechRecognitionConfig.preferOffline].
 *
 * ### Contrato de robustez (best-effort — NADA lança)
 * Falta de permissão, ausência de rede, idioma sem suporte etc. **nunca** derrubam o app: viram
 * [SpeechRecognitionState.Error] + [SpeechEvent.Failed] e [lastError]. O consumidor sempre tem um
 * **fallback ao teclado numérico** (ver `DictationOverlay`/`VoiceCaptureButton`).
 *
 * ### Confirmação obrigatória (regra de produto)
 * Erro de reconhecimento tem custo financeiro (peso errado → preço errado). A UI da lib
 * (`DictationOverlay`) **sempre** exige 1 toque de confirmação antes de aceitar o valor. O
 * [SpeechRecognizer] apenas entrega o texto — não decide aceitar.
 *
 * Obtenha via [createSpeechRecognizer] (ou o helper Compose [rememberSpeechRecognizer], que chama
 * [release] no `onDispose`). Em ViewModels, chame [release] no `onCleared()`.
 *
 * Exemplo:
 * ```kotlin
 * val recognizer = rememberSpeechRecognizer()
 * val state by recognizer.state.collectAsState()
 * val partial by recognizer.partialText.collectAsState()
 *
 * LaunchedEffect(Unit) {
 *     recognizer.events.collect { ev ->
 *         if (ev is SpeechEvent.Result) {
 *             SpokenNumberParser.parseToDisplay(ev.text)?.let { onWeightRecognized(it) }
 *         }
 *     }
 * }
 * recognizer.startListening()   // pt-BR, on-device
 * ```
 */
interface SpeechRecognizer {

    /** Estado atual da sessão. */
    val state: StateFlow<SpeechRecognitionState>

    /** Última transcrição parcial ao vivo (vazio quando ocioso). Conveniência de UI. */
    val partialText: StateFlow<String>

    /** Última falha, ou `null` se a última sessão não falhou. */
    val lastError: StateFlow<SpeechRecognitionError?>

    /** Fluxo de eventos da sessão (parciais/final/falha). Quente enquanto o recognizer viver. */
    val events: Flow<SpeechEvent>

    /**
     * Reporta se o dispositivo suporta reconhecimento de fala para o idioma da [config]
     * (best-effort; nunca lança). Não solicita permissão.
     */
    suspend fun isRecognitionAvailable(config: SpeechRecognitionConfig = SpeechRecognitionConfig()): Boolean

    /**
     * Verifica se a permissão de microfone já está concedida (sincronamente, sem solicitar).
     * A solicitação em si é feita pela UI via `PermissionManager`/`DictationOverlay`.
     */
    fun hasMicrophonePermission(): Boolean

    /**
     * Inicia uma sessão de escuta. Idempotente: se já estiver ouvindo, é no-op (emite
     * [SpeechEvent.Failed] com [SpeechRecognitionError.Busy] só se houver conflito real).
     * Sem permissão de microfone → [SpeechRecognitionError.PermissionDenied] (não lança).
     */
    fun startListening(config: SpeechRecognitionConfig = SpeechRecognitionConfig())

    /**
     * Encerra a captura de áudio e processa o que já foi falado (dispara a transcrição final).
     * Sem efeito se já estiver ocioso.
     */
    fun stopListening()

    /** Cancela a sessão imediatamente, **sem** produzir resultado. Volta a [SpeechRecognitionState.Idle]. */
    fun cancel()

    /** Libera os recursos nativos. Após chamar, crie outro via [createSpeechRecognizer]. */
    fun release()
}

/**
 * Cria a implementação de [SpeechRecognizer] para a plataforma atual.
 *
 * No Android requer que [br.com.codecacto.kmplib.KmpLib.init] (ou `SpeechRecognizerHolder.init`)
 * tenha sido chamado no `Application.onCreate()` para fornecer o `Context`.
 */
expect fun createSpeechRecognizer(): SpeechRecognizer

/**
 * Cria e memoriza um [SpeechRecognizer] no escopo do composable atual, liberando-o
 * automaticamente ([SpeechRecognizer.release]) quando o composable sai da composição.
 */
@Composable
fun rememberSpeechRecognizer(): SpeechRecognizer {
    val recognizer = remember { createSpeechRecognizer() }
    DisposableEffect(recognizer) {
        onDispose { recognizer.release() }
    }
    return recognizer
}
