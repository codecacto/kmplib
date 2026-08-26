package br.com.codecacto.kmplib.platform.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * **Captura do microfone com medição de nível sonoro** — PCM cru do hardware, sem gravar nada em
 * disco.
 *
 * Entrega **duas** coisas, e é de propósito:
 * - [levels]: o nível já calculado (RMS ponderado, pico, saturação), que é o que um decibelímetro
 *   precisa e nada mais;
 * - [frames]: as **amostras cruas**, opt-in por [AudioCaptureConfig.emitFrames], para quem analisa
 *   o sinal (afinador por microfone, detecção de frequência). Sem elas, o segundo consumidor teria
 *   de reimplementar a captura inteira do zero — e a promoção para a lib não teria valido nada.
 *
 * **Padrão-ouro por plataforma:**
 * - **Android:** `AudioRecord` com PCM 16-bit mono, na fonte que menos processa o sinal
 *   (`UNPROCESSED` quando o aparelho declara suporte, senão `VOICE_RECOGNITION`). Nunca
 *   `MediaRecorder.getMaxAmplitude()`, que grava arquivo em disco só para ler amplitude, tem
 *   resolução grosseira e não dá acesso ao buffer.
 * - **iOS:** `AVAudioEngine` com tap no nó de entrada e `AVAudioSession` em categoria `record` e
 *   **modo `measurement`** — o modo que a Apple documenta como o que minimiza o processamento do
 *   sistema sobre a entrada.
 *
 * **A unidade é dBFS**, não dB SPL — ver [AudioLevel] e [SplCalibration].
 *
 * **Permissão é do app, não da lib.** Este módulo **verifica** a permissão de microfone e falha com
 * [AudioCaptureError.PermissionDenied]; **pedir** é do app, via
 * `PermissionManager` + `AppPermission.MICROPHONE`, depois da tela que explica por que o microfone
 * é necessário. O app também declara `RECORD_AUDIO` no manifesto (Android) e
 * `NSMicrophoneUsageDescription` no `Info.plist` (iOS) — a lib **não** declara permissão perigosa
 * em nome de quem nem usa o módulo.
 *
 * **Ciclo de vida é do app.** A lib **não observa** primeiro/segundo plano: quem sabe quando pausar
 * é a tela, e o caminho é o `enabled` de [rememberAudioCapture] (mesmo desenho do
 * `rememberShakeDetector`). Embutir isso aqui exigiria `ProcessLifecycleOwner` no Android e
 * observadores de `UIApplication` no iOS, brigando com o controle do app. **Interrupção do
 * sistema** (ligação, Siri) é outra história: essa o app não tem como tratar, e o `actual` do iOS a
 * trata sozinho, reportando [AudioCaptureState.Interrupted].
 *
 * **Nada é gravado.** O áudio existe só em memória, pelo tempo de calcular o nível.
 *
 * Exemplo:
 * ```kotlin
 * val capture = rememberAudioCapture(enabled = temPermissao && telaVisivel)
 * val level by capture.levels.collectAsState(initial = null)
 * val state by capture.state.collectAsState()
 * ```
 */
interface AudioCapture {

    /**
     * `false` quando não há como capturar neste aparelho/estado — sem microfone, sem permissão
     * concedida, ou `KmpLib.init(context)` não foi chamado no Android. A tela usa isto para
     * oferecer o caminho de conserto em vez de um medidor que nunca sai de -120 dB.
     */
    val isAvailable: Boolean

    /** Estado corrente. Sempre tem valor, inclusive depois de falha. */
    val state: StateFlow<AudioCaptureState>

    /**
     * Leituras de nível, uma a cada [AudioCaptureConfig.emitIntervalMillis] enquanto
     * [AudioCaptureState.Running]. É *hot*: quem chega depois recebe as próximas, não as passadas.
     */
    val levels: Flow<AudioLevel>

    /**
     * Blocos de amostras cruas. **Só emite** com [AudioCaptureConfig.emitFrames] ligado; caso
     * contrário é um fluxo vazio — e não um erro, para o consumidor poder coletá-lo sem checar
     * configuração.
     */
    val frames: Flow<AudioFrame>

    /**
     * Abre o microfone e começa a medir.
     *
     * @return `false` quando não deu — e o motivo **já está** em [state] como
     *   [AudioCaptureState.Failed], para a tela não precisar de um segundo canal de erro. Chamar
     *   com a captura já rodando é no-op e devolve `true`.
     */
    fun start(): Boolean

    /**
     * Para a captura e solta o microfone, mantendo o objeto reutilizável: um novo [start] volta a
     * medir. Idempotente.
     */
    fun stop()

    /**
     * **Terminal:** solta o recurso nativo de vez e leva o estado para
     * [AudioCaptureState.Released]. Depois disto, [start] não funciona mais — é preciso um novo
     * [createAudioCapture]. Idempotente.
     */
    fun release()

    /**
     * Troca ponderação, integração e cadência **sem reiniciar a captura** — é o que a tela de
     * Configurações usa: mexer no ajuste não pode fechar e reabrir o microfone (o hardware demora,
     * o número some da tela e o iOS chega a piscar a rota de áudio).
     *
     * `null` em qualquer parâmetro significa **manter o valor corrente**. (A interface não tem como
     * escrever "o valor de agora" como default de parâmetro; nulo é o jeito de dizer isso sem
     * inventar um sentinela.)
     *
     * O que **não** muda aqui: [AudioCaptureConfig.preferredSampleRate] e
     * [AudioCaptureConfig.emitFrames], que são decididos na abertura do recurso nativo e exigem um
     * novo [createAudioCapture].
     */
    fun updateProcessing(
        weighting: AudioWeighting? = null,
        timeWeighting: AudioTimeWeighting? = null,
        emitIntervalMillis: Long? = null,
    )
}

/** Cria a captura de áudio da plataforma atual. */
expect fun createAudioCapture(config: AudioCaptureConfig = AudioCaptureConfig()): AudioCapture

/**
 * Captura com ciclo de vida atrelado à composição: liga e desliga por [enabled] e **libera o
 * recurso no `onDispose`**.
 *
 * É por [enabled] que o app cumpre "não fica ouvindo em segundo plano": basta passar `false`
 * quando a tela sai de vista (ex.: `lifecycleState != RESUMED`) e o microfone é solto na hora.
 *
 * Trocar [AudioCaptureConfig.weighting], [AudioCaptureConfig.timeWeighting] ou
 * [AudioCaptureConfig.emitIntervalMillis] **não reabre** o microfone (vai por
 * [AudioCapture.updateProcessing]); trocar `preferredSampleRate` ou `emitFrames` cria uma captura
 * nova e libera a anterior, porque esses dois são decididos na abertura do recurso nativo.
 */
@Composable
fun rememberAudioCapture(
    enabled: Boolean,
    config: AudioCaptureConfig = AudioCaptureConfig(),
): AudioCapture {
    val capture = remember(config.preferredSampleRate, config.emitFrames) {
        createAudioCapture(config)
    }

    // Ajustes que mudam a quente: nunca reiniciam a captura.
    DisposableEffect(capture, config.weighting, config.timeWeighting, config.emitIntervalMillis) {
        capture.updateProcessing(
            weighting = config.weighting,
            timeWeighting = config.timeWeighting,
            emitIntervalMillis = config.emitIntervalMillis,
        )
        onDispose { }
    }

    DisposableEffect(capture, enabled) {
        if (enabled) capture.start()
        onDispose { capture.stop() }
    }

    DisposableEffect(capture) {
        onDispose { capture.release() }
    }

    return capture
}

/**
 * Captura inerte: o aparelho não tem microfone utilizável, a permissão não foi concedida, ou
 * `KmpLib.init(context)` não foi chamado no Android.
 *
 * Ela **existe** em vez de a factory devolver `null` para a tela não ter dois caminhos de código —
 * consulta-se [isAvailable] uma vez e o resto do app segue igual, coletando fluxos que não emitem.
 */
internal class UnavailableAudioCapture(
    error: AudioCaptureError = AudioCaptureError.DeviceUnavailable,
) : AudioCapture {

    private val _state = MutableStateFlow<AudioCaptureState>(AudioCaptureState.Failed(error))

    override val isAvailable: Boolean = false
    override val state: StateFlow<AudioCaptureState> = _state.asStateFlow()
    override val levels: Flow<AudioLevel> = emptyFlow()
    override val frames: Flow<AudioFrame> = emptyFlow()

    override fun start(): Boolean = false

    override fun stop() = Unit

    override fun release() {
        _state.value = AudioCaptureState.Released
    }

    override fun updateProcessing(
        weighting: AudioWeighting?,
        timeWeighting: AudioTimeWeighting?,
        emitIntervalMillis: Long?,
    ) = Unit
}
