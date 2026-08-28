package br.com.codecacto.kmplib.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * **Efeito sonoro curto empacotado no app** — o bipe de confirmação, disparado no instante do toque
 * e repetido à vontade.
 *
 * Não confundir com o [AudioPlayer], que é **mídia**: arquivo local escolhido pelo usuário, uma
 * reprodução por vez, preparo assíncrono, barra de progresso. Um bipe a cada 300 ms por aquele
 * caminho recriaria um `MediaPlayer` a cada volta e cortaria o som anterior — e o `AudioPlayer` nem
 * abre recurso empacotado, porque `setDataSource(String)` não lê `file:///android_asset/...`.
 *
 * ## Como o app entrega o som
 *
 * Por **bytes**, não por caminho de arquivo — é o que o Compose Resources sabe dar em código comum,
 * e é o que evita o app escrever `expect/actual` só para achar um `Context` ou um `Bundle`:
 *
 * ```kotlin
 * class ContadorViewModel : ViewModel() {
 *     private val sfx = createSoundEffectPlayer()
 *
 *     init {
 *         viewModelScope.launch {
 *             sfx.load(SOM_VOLTA, Res.readBytes("files/beep.wav"))
 *             sfx.load(SOM_META, Res.readBytes("files/meta.wav"))
 *         }
 *     }
 *
 *     fun onVolta() {
 *         sfx.play(SOM_VOLTA)   // não bloqueia, não lança
 *     }
 *
 *     override fun onCleared() { sfx.release(); super.onCleared() }
 * }
 * ```
 *
 * A lib materializa os bytes num arquivo do **diretório temporário do próprio app** (é o que as duas
 * APIs nativas recebem) e o apaga em [unload]/[release].
 *
 * ## Formato do áudio
 *
 * **WAV PCM 16-bit, mono, 44.1 kHz** é o recomendado — o único que toca nas duas plataformas
 * (ver [SoundEffectFormat.isCrossPlatform]). MP3/OGG/M4A carregam no Android e **falham no iOS**,
 * cujo *System Sound Services* aceita só Linear PCM/IMA4 em `.wav`, `.caf` ou `.aif`. Duração de
 * efeito: até ~1 s; acima de 1 MB rende aviso no log (o áudio fica decodificado em memória).
 *
 * ## Modo silencioso — o comportamento é DIFERENTE nas duas plataformas
 *
 * - **Android:** o pool usa `USAGE_ASSISTANCE_SONIFICATION` / `CONTENT_TYPE_SONIFICATION`, o que
 *   roteia o efeito para o **stream de sistema**. No modo Silencioso/Vibrar, e sob "Não perturbe",
 *   o som **não sai**; o volume é o do toque, não o de mídia.
 * - **iOS:** sons de sistema seguem o interruptor **Silencioso** e o volume do sistema. O módulo
 *   **não** ativa nem reconfigura a `AVAudioSession` de propósito — mexer nela para forçar o bipe
 *   interromperia a música que o usuário estiver ouvindo, e um app inteiro passaria a se comportar
 *   como reprodutor de mídia por causa de um clique. Consequência: se o app já ativou a categoria
 *   `Playback` (por exemplo, usando o [AudioPlayer] na mesma sessão), o efeito passa a seguir
 *   aquela categoria.
 *
 * Em nenhuma das duas o app controla o volume do efeito — é o do sistema. **Nada disso é falha**:
 * é a razão de o produto confirmar a ação também por vibração e por sinal visual.
 *
 * ## Ciclo de vida
 *
 * Carregar uma vez (início da sessão), [release] ao sair. Quem cria, libera — sem isso o pool
 * nativo e os arquivos temporários ficam pendurados. Numa tela só, use [rememberSoundEffectPlayer].
 *
 * ## Threading
 *
 * [play] é **síncrono, curto e não bloqueante** — dispara e volta, porque o tempo entre o toque e o
 * som é a métrica do produto. [load] é `suspend` porque decodificar é I/O de verdade (no Android o
 * `SoundPool` só avisa por callback quando o sample está pronto — tocar antes disso é o silêncio
 * mais confuso que existe). A instância tem **um dono**: não chame [load] de várias coroutines ao
 * mesmo tempo.
 */
interface SoundEffectPlayer {

    /** Chaves atualmente carregadas e prontas para [play]. */
    val loadedKeys: Set<String>

    /**
     * Carrega (ou recarrega) o efeito de [key] a partir de [bytes], e **só volta quando ele está
     * pronto para tocar**.
     *
     * Recarregar uma chave já carregada descarrega a versão anterior — sem vazar o recurso nativo.
     *
     * @return [SoundEffectOutcome.Success], ou [SoundEffectOutcome.Failure] com
     *   [SoundEffectError.InvalidAudio] (bytes não são áudio utilizável),
     *   [SoundEffectError.StorageFailure], [SoundEffectError.NotInitialized],
     *   [SoundEffectError.InvalidKey] ou [SoundEffectError.Released]. **Nunca lança.**
     */
    suspend fun load(key: String, bytes: ByteArray): SoundEffectOutcome

    /** `true` se [key] já pode ser tocada. */
    fun isLoaded(key: String): Boolean

    /**
     * Toca o efeito de [key] **agora**, sem bloquear e sem cortar um disparo anterior (até
     * [SoundEffectDefaults.MAX_STREAMS] simultâneos no Android; no iOS o mixer do sistema cuida da
     * sobreposição).
     *
     * Chave não carregada devolve [SoundEffectError.NotLoaded] e registra log — não lança, e não
     * interrompe a ação que estava sendo confirmada.
     */
    fun play(key: String): SoundEffectOutcome

    /** Descarrega [key], libera o recurso nativo e apaga o arquivo temporário. No-op se não existe. */
    fun unload(key: String)

    /**
     * Libera o pool nativo, todos os efeitos e os arquivos temporários. **Obrigatório** ao descartar
     * o dono (`onCleared`, `onDispose`). Idempotente; depois disto a instância não deve ser
     * reutilizada.
     */
    fun release()
}

/**
 * Cria o reprodutor de efeitos da plataforma atual.
 *
 * @param maxStreams vozes simultâneas no Android (ver [SoundEffectDefaults.MAX_STREAMS]).
 *   **Ignorado no iOS**, onde a sobreposição é do mixer do sistema e não há pool a dimensionar.
 */
expect fun createSoundEffectPlayer(
    maxStreams: Int = SoundEffectDefaults.MAX_STREAMS,
): SoundEffectPlayer

/**
 * Reprodutor com ciclo de vida atrelado à composição: criado uma vez e **liberado no `onDispose`**.
 * Use quando os efeitos pertencem a esta tela; se devem sobreviver à navegação, guarde um
 * [createSoundEffectPlayer] no ViewModel.
 *
 * O carregamento continua sendo do app (`LaunchedEffect { player.load(...) }`), porque só ele sabe
 * quais recursos empacotou.
 */
@Composable
fun rememberSoundEffectPlayer(
    maxStreams: Int = SoundEffectDefaults.MAX_STREAMS,
): SoundEffectPlayer {
    val player = remember(maxStreams) { createSoundEffectPlayer(maxStreams) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

/**
 * Reprodutor inerte, para quando a plataforma não entrega efeito sonoro utilizável (no Android,
 * `KmpLib.init(context)` não chamado).
 *
 * Existe para o app receber um [SoundEffectError] tipado em vez de um crash ou de um reprodutor nulo
 * que cada tela teria de checar — e para o produto seguir contando voltas em silêncio.
 */
internal class UnavailableSoundEffectPlayer(
    private val error: SoundEffectError = SoundEffectError.NotInitialized,
) : SoundEffectPlayer {

    override val loadedKeys: Set<String> = emptySet()

    override suspend fun load(key: String, bytes: ByteArray): SoundEffectOutcome = failWith(error)

    override fun isLoaded(key: String): Boolean = false

    override fun play(key: String): SoundEffectOutcome = failWith(error)

    override fun unload(key: String) = Unit

    override fun release() = Unit
}
