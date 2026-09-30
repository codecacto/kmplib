package br.com.codecacto.kmplib.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.StateFlow

/**
 * **Ambiente sonoro em laço** — o fundo que toca enquanto a pessoa está numa tela (chuva, mata,
 * atabaque baixinho), com **volume** e **fade**. Desde 2.228.0.
 *
 * Não confundir com os vizinhos:
 * - [AudioPlayer] é **mídia**: arquivo escolhido pelo usuário, uma reprodução com barra de progresso,
 *   sem laço e sem fade.
 * - [SoundEffectPlayer] é **efeito curto** (bipe de confirmação), disparado e esquecido.
 *
 * ## Plataforma (padrão-ouro de cada uma)
 *
 * - **Android: Media3/ExoPlayer** com `REPEAT_MODE_ONE` — o laço do ExoPlayer é **sem emenda**
 *   (a próxima volta já está decodificada quando a anterior termina), e os bytes são lidos da
 *   memória (`ByteArrayDataSource`), sem arquivo temporário. `AudioAttributes` de mídia/música.
 * - **iOS: `AVAudioPlayer`** com `numberOfLoops = -1` e a **sessão de áudio `.ambient`** — a
 *   categoria que a Apple indica para som que acompanha o app: **mistura** com a música de outro app
 *   e **respeita o interruptor Silencioso**.
 *
 * ## Convive com o áudio de outros apps ([AmbientSoundConfig.mixWithOthers], default `true`)
 *
 * Com `true`, quem está ouvindo um podcast continua ouvindo: no Android o player **não pede foco de
 * áudio**; no iOS a sessão é `.ambient`. Com `false`, o ambiente passa a ser o áudio principal:
 * Android pede foco (e a própria Media3 pausa/abaixa quando outro app o toma); iOS usa `.playback`
 * (toca no Silencioso e interrompe a música de outro app). Tirar o fone do ouvido **pausa** nas duas.
 *
 * ## Formato
 *
 * Laço sem emenda depende do arquivo tanto quanto do player: **AAC em `.m4a`** (com os metadados de
 * *gapless* que todo encoder atual grava) ou **WAV PCM** tocam sem clique nas duas plataformas. **MP3
 * tem silêncio de *padding*** do encoder no começo e no fim: o ExoPlayer o remove quando o arquivo
 * traz o cabeçalho LAME, o `AVAudioPlayer` não — no iPhone, a emenda aparece. Prefira `.m4a`.
 *
 * ## Uso
 *
 * ```kotlin
 * @Composable
 * fun TelaDoRitual() {
 *     val ambiente = rememberAmbientSoundPlayer()   // pausa em ON_STOP, retoma em ON_START
 *     LaunchedEffect(Unit) {
 *         if (ambiente.load(Res.readBytes("files/mata.m4a")).isSuccess) ambiente.play()
 *     }
 *     AppSlider(value = volume, onValueChange = { ambiente.setVolume(it) })
 * }
 * ```
 *
 * No ViewModel (o som sobrevive à rotação): [createAmbientSoundPlayer] + [release] no `onCleared`, e
 * [AmbientSoundBackgroundPause] na tela para pausar no segundo plano.
 *
 * ## Threading
 *
 * Chame da **main thread** (como todo player de mídia das duas plataformas). [load] é `suspend`
 * (decodifica e prepara); os demais voltam na hora — o fade corre em corrotina própria. **Nunca
 * lança**: erro vem em [AmbientSoundOutcome].
 */
interface AmbientSoundPlayer {

    /** Estado atual: [AmbientSoundStatus] + volume escolhido. */
    val state: StateFlow<AmbientSoundState>

    /**
     * Carrega (ou troca) o som a partir de [bytes] e **só volta quando ele está pronto**. Troca com
     * o anterior tocando: o anterior para na hora (sem fade) e o novo fica [AmbientSoundStatus.READY].
     */
    suspend fun load(bytes: ByteArray): AmbientSoundOutcome

    /**
     * Toca em laço, subindo de 0 até o volume em [fadeInMillis] (0 = entra no volume cheio). Chamado
     * durante um fade-out de [pause]/[stop], **reverte** o fade a partir do ponto em que estava.
     */
    fun play(fadeInMillis: Long = AmbientSoundDefaults.FADE_IN_MILLIS): AmbientSoundOutcome

    /** Pausa depois de descer a 0 em [fadeOutMillis]. Retomar ([play]) continua do ponto em que parou. */
    fun pause(fadeOutMillis: Long = AmbientSoundDefaults.FADE_OUT_MILLIS)

    /** Como [pause], e volta ao começo do som. */
    fun stop(fadeOutMillis: Long = AmbientSoundDefaults.FADE_OUT_MILLIS)

    /**
     * Volume do ambiente, de `0f` a `1f` (relativo ao volume de mídia do aparelho, que continua do
     * usuário). Tocando, chega ao novo valor em [fadeMillis]; parado, vale para o próximo [play].
     */
    fun setVolume(volume: Float, fadeMillis: Long = 0L)

    /** Libera o player nativo. Obrigatório ao descartar o dono; idempotente. */
    fun release()
}

/** Situação do [AmbientSoundPlayer]. */
enum class AmbientSoundStatus {
    /** Nada carregado. */
    EMPTY,

    /** [AmbientSoundPlayer.load] em andamento. */
    LOADING,

    /** Carregado e parado no começo. */
    READY,

    /** Tocando (ou subindo o fade-in). */
    PLAYING,

    /** Pausado pelo app, por um fade-out em curso, pelo segundo plano ou pelo sistema (fone, ligação). */
    PAUSED,

    /** [AmbientSoundPlayer.release] já chamado. */
    RELEASED,
}

/** Estado observável. [volume] é o escolhido pelo app, não o ponto do fade. */
data class AmbientSoundState(
    val status: AmbientSoundStatus = AmbientSoundStatus.EMPTY,
    val volume: Float = AmbientSoundDefaults.VOLUME,
) {
    val isPlaying: Boolean get() = status == AmbientSoundStatus.PLAYING
}

/** Por que uma operação do [AmbientSoundPlayer] não foi aplicada. */
sealed interface AmbientSoundError {
    /** Android sem `KmpLib.init`/`initKmpLibMedia(context)`. */
    data object NotInitialized : AmbientSoundError

    /** Bytes vazios ou que o decodificador da plataforma recusou. */
    data object InvalidAudio : AmbientSoundError

    /** [AmbientSoundPlayer.play] sem som carregado. */
    data object NotLoaded : AmbientSoundError

    /** A instância já sofreu `release`. */
    data object Released : AmbientSoundError

    /** Falha não classificada; [message] é diagnóstico (log), não texto de tela. */
    data class Unknown(val message: String?) : AmbientSoundError
}

/** Resultado de uma operação do [AmbientSoundPlayer]. */
sealed interface AmbientSoundOutcome {
    data object Success : AmbientSoundOutcome
    data class Failure(val error: AmbientSoundError) : AmbientSoundOutcome

    val isSuccess: Boolean get() = this is Success
    val errorOrNull: AmbientSoundError? get() = (this as? Failure)?.error
}

/**
 * Configuração do [AmbientSoundPlayer].
 *
 * @param mixWithOthers `true` (default) = convive com o áudio de outros apps e, no iOS, respeita o
 *   Silencioso. `false` = vira o áudio principal. Ver o KDoc de [AmbientSoundPlayer].
 */
data class AmbientSoundConfig(
    val mixWithOthers: Boolean = true,
)

/** Defaults do [AmbientSoundPlayer]. */
object AmbientSoundDefaults {
    /** Subida ao tocar: entra sem susto, e curta o bastante para não parecer atraso. */
    const val FADE_IN_MILLIS: Long = 1_500L

    /** Descida ao pausar/parar. */
    const val FADE_OUT_MILLIS: Long = 800L

    /** Descida ao ir para o segundo plano — curta: a tela já sumiu. */
    const val BACKGROUND_FADE_OUT_MILLIS: Long = 250L

    /** Volume inicial. */
    const val VOLUME: Float = 1f

    /** Intervalo entre dois passos da rampa de volume (~50 passos por segundo). */
    const val FADE_STEP_MILLIS: Long = 20L
}

/** Cria o ambiente sonoro da plataforma atual. Chame da main thread. */
expect fun createAmbientSoundPlayer(config: AmbientSoundConfig = AmbientSoundConfig()): AmbientSoundPlayer

/**
 * Ambiente sonoro atrelado à composição: criado uma vez, **pausado em `ON_STOP`** (com fade curto) e
 * retomado em `ON_START` se estava tocando, e **liberado no `onDispose`**.
 */
@Composable
fun rememberAmbientSoundPlayer(config: AmbientSoundConfig = AmbientSoundConfig()): AmbientSoundPlayer {
    val player = remember(config) { createAmbientSoundPlayer(config) }
    DisposableEffect(player) { onDispose { player.release() } }
    AmbientSoundBackgroundPause(player)
    return player
}

/**
 * Pausa [player] quando a tela vai para o segundo plano (`ON_STOP`) e retoma na volta (`ON_START`) —
 * **só** se ele estava tocando. Para o player que mora no ViewModel; o [rememberAmbientSoundPlayer]
 * já o aplica.
 *
 * `ON_STOP`, não `ON_PAUSE`: o diálogo de permissão e a folha de compartilhar pausam a tela sem ela
 * sair da frente, e o som não deve piscar por isso.
 */
@Composable
fun AmbientSoundBackgroundPause(player: AmbientSoundPlayer) {
    val resume = remember(player) { BackgroundResumeFlag() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        resume.value = player.state.value.isPlaying
        if (resume.value) player.pause(AmbientSoundDefaults.BACKGROUND_FADE_OUT_MILLIS)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (resume.value) player.play()
        resume.value = false
    }
}

private class BackgroundResumeFlag(var value: Boolean = false)
