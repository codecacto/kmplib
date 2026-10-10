package br.com.codecacto.kmplib.media.voicenote

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.KmpLibCoreInternalApi
import br.com.codecacto.kmplib.core.util.redactMediaUrl
import br.com.codecacto.kmplib.media.AudioPlayer
import br.com.codecacto.kmplib.media.AudioPlayerState
import br.com.codecacto.kmplib.media.createAudioPlayer
import br.com.codecacto.kmplib.media.formatVoiceNoteDuration
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** De onde vem a nota de voz. */
sealed interface VoiceNoteSource {
    /** Arquivo local (a que acabou de ser gravada). */
    data class File(val path: String) : VoiceNoteSource {
        override fun toString(): String = "File(…)"
    }

    /**
     * URL (a assinada que o servidor devolve para o áudio recebido). Baixada uma vez para a pasta
     * temporária da lib (`kmplib_audio_playback`, varrida por `clearKmpLibTemporaryFiles`) e tocada
     * de lá. [cacheKey] identifica o áudio **sem** a assinatura (o id do asset), para a URL renovada
     * reaproveitar o arquivo.
     */
    class Url(val url: String, val cacheKey: String) : VoiceNoteSource {
        @OptIn(KmpLibCoreInternalApi::class)
        override fun toString(): String = "Url(${redactMediaUrl(url)})"
        override fun equals(other: Any?): Boolean = other is Url && other.cacheKey == cacheKey
        override fun hashCode(): Int = cacheKey.hashCode()
    }

    /**
     * Áudio remoto cuja URL só se obtém **na hora** — o asset da conversa, que pede `read-urls` ao
     * servidor. [resolveUrl] é chamado ao baixar e de novo se a URL vencer (401/403/410); `null` =
     * sem acesso (erro no balão).
     */
    class Remote(val cacheKey: String, val resolveUrl: suspend () -> String?) : VoiceNoteSource {
        override fun toString(): String = "Remote(…)"
        override fun equals(other: Any?): Boolean = other is Remote && other.cacheKey == cacheKey
        override fun hashCode(): Int = cacheKey.hashCode()
    }
}

/** Em que pé está o player. */
enum class VoiceNotePlayerStatus { LOADING, READY, ERROR }

/**
 * Estado de um player de nota de voz (um por balão). Toca **um por vez** no processo: dar play num
 * pausa o que estava tocando. Crie com [rememberVoiceNotePlayerState].
 */
@Stable
class VoiceNotePlayerState internal constructor(
    private val source: VoiceNoteSource,
    knownDurationMillis: Long?,
    knownWaveform: List<Float>?,
    internal val player: AudioPlayer,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient?,
    private val onRenewUrl: (suspend () -> String?)?,
    private val prefetch: Boolean,
) {
    var status: VoiceNotePlayerStatus by mutableStateOf(VoiceNotePlayerStatus.LOADING)
        private set

    /** A onda (do recurso recebido ou extraída do arquivo). Vazia enquanto não há. */
    var waveform: List<Float> by mutableStateOf(knownWaveform.orEmpty())
        private set

    /** Duração conhecida (do contrato) até o player ler a do arquivo. */
    var durationMillis: Long by mutableStateOf(knownDurationMillis ?: 0L)
        internal set

    private var caminho: String? = (source as? VoiceNoteSource.File)?.path
    private var tocarAoCarregar = false

    internal fun start() {
        if (caminho != null) {
            status = VoiceNotePlayerStatus.READY
            if (waveform.isEmpty()) extrairOnda()
        } else if (prefetch) {
            carregar(tocarDepois = false)
        } else {
            status = VoiceNotePlayerStatus.READY
        }
    }

    /** Play/pause. Remoto ainda não baixado: baixa e toca. */
    fun toggle() {
        if (player.isPlaying.value) {
            player.pause()
            return
        }
        val local = caminho
        if (local == null) {
            carregar(tocarDepois = true)
            return
        }
        VoiceNotePlayback.claim(this)
        player.play(local)
    }

    fun pause() = player.pause()

    /** Pula para [fraction] (0..1) da duração. */
    fun seekTo(fraction: Float) {
        val d = player.duration.value.takeIf { it > 0L } ?: durationMillis
        if (d <= 0L || caminho == null) return
        player.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
    }

    /** Depois de [VoiceNotePlayerStatus.ERROR]: tenta baixar/tocar de novo. */
    fun retry() {
        status = VoiceNotePlayerStatus.LOADING
        carregar(tocarDepois = true)
    }

    private fun carregar(tocarDepois: Boolean) {
        tocarAoCarregar = tocarAoCarregar || tocarDepois
        if (source is VoiceNoteSource.File) return
        if (status == VoiceNotePlayerStatus.LOADING && caminho == null && carregando) return
        carregando = true
        status = VoiceNotePlayerStatus.LOADING
        scope.launch {
            val baixado = try {
                baixar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "áudio não baixou: ${e::class.simpleName}")
                null
            }
            carregando = false
            if (baixado == null) {
                status = VoiceNotePlayerStatus.ERROR
                return@launch
            }
            caminho = baixado
            status = VoiceNotePlayerStatus.READY
            if (waveform.isEmpty()) extrairOnda()
            if (tocarAoCarregar) {
                tocarAoCarregar = false
                VoiceNotePlayback.claim(this@VoiceNotePlayerState)
                player.play(baixado)
            }
        }
    }

    private var carregando = false

    private suspend fun baixar(): String? {
        val (chave, primeira, renovar) = when (source) {
            is VoiceNoteSource.Url -> Triple(source.cacheKey, source.url, onRenewUrl)
            is VoiceNoteSource.Remote -> Triple(source.cacheKey, null, source.resolveUrl)
            is VoiceNoteSource.File -> return source.path
        }
        val destino = audioPlaybackCachePath(voiceNoteCacheFileName(chave)) ?: return null
        if (audioFileSize(destino) > 0L) return destino
        val cliente = httpClient ?: return null
        var alvo = primeira ?: renovar?.invoke() ?: return null
        repeat(2) { tentativa ->
            val resposta = cliente.get(alvo)
            val codigo = resposta.status.value
            when {
                codigo in 200..299 -> {
                    val bytes = resposta.readRawBytes()
                    if (bytes.isEmpty()) return null
                    return withContext(Dispatchers.Default) { if (writeAudioFile(destino, bytes)) destino else null }
                }
                // URL assinada vencida: pede outra uma vez (mesma regra do VideoPlayer).
                (codigo == 401 || codigo == 403 || codigo == 410) && tentativa == 0 && renovar != null ->
                    alvo = renovar.invoke() ?: return null
                else -> return null
            }
        }
        return null
    }

    private fun extrairOnda() {
        val local = caminho ?: return
        scope.launch {
            val onda = runCatching { extractAudioWaveform(local) }.getOrNull()
            if (!onda.isNullOrEmpty() && waveform.isEmpty()) waveform = onda
        }
    }

    private companion object {
        const val TAG = "VoiceNotePlayer"
    }
}

/** Garante "um áudio por vez" no processo. */
internal object VoiceNotePlayback {
    val current = MutableStateFlow<VoiceNotePlayerState?>(null)

    fun claim(state: VoiceNotePlayerState) {
        val anterior = current.value
        if (anterior != null && anterior !== state) anterior.pause()
        current.value = state
    }
}

/**
 * Cria o estado do player de uma nota de voz.
 *
 * @param durationMillis a duração que o servidor já informou (`audioDurationSeconds`), para o balão
 *   mostrar o tempo antes de baixar.
 * @param waveform a onda, se o app a tem (a nota que esta pessoa gravou: [RecordedAudio.levels][br.com.codecacto.kmplib.media.RecordedAudio.levels]).
 *   Sem ela, a onda é extraída do arquivo.
 * @param httpClient cliente para baixar [VoiceNoteSource.Url] — **sem** Bearer (URL assinada).
 * @param onRenewUrl pede uma URL nova quando a assinada venceu (401/403/410). Uma vez por carga.
 * @param prefetch baixa ao aparecer (default). `false`: só ao tocar.
 */
@Composable
fun rememberVoiceNotePlayerState(
    source: VoiceNoteSource,
    durationMillis: Long? = null,
    waveform: List<Float>? = null,
    httpClient: HttpClient? = null,
    onRenewUrl: (suspend () -> String?)? = null,
    prefetch: Boolean = true,
): VoiceNotePlayerState {
    val scope = rememberCoroutineScope()
    val player = remember(source) { createAudioPlayer() }
    val state = remember(source) {
        VoiceNotePlayerState(source, durationMillis, waveform, player, scope, httpClient, onRenewUrl, prefetch)
    }
    LaunchedEffect(state) { state.start() }
    DisposableEffect(state) {
        onDispose {
            if (VoiceNotePlayback.current.value === state) VoiceNotePlayback.current.value = null
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.pause() }
    return state
}

/** Cores do [VoiceNotePlayer] — o balão "meu" e o "do outro" pedem contrastes diferentes. */
data class VoiceNotePlayerColors(
    val button: Color,
    val played: Color,
    val unplayed: Color,
    val text: Color,
)

/** Cores default sobre `surfaceVariant` (balão do outro). */
@Composable
fun voiceNotePlayerColors(
    button: Color = MaterialTheme.colorScheme.primary,
    played: Color = MaterialTheme.colorScheme.primary,
    unplayed: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    text: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): VoiceNotePlayerColors = VoiceNotePlayerColors(button, played, unplayed, text)

/**
 * Player de nota de voz com onda: play/pause, onda que pinta o que já tocou, toque/arrasto na onda
 * pula, tempo (total parado, decorrido tocando). Para leitor de tela: um controle com "Áudio de
 * 0:07", ação de tocar/pausar no botão e ajuste de posição por `setProgress`.
 */
@Composable
fun VoiceNotePlayer(
    state: VoiceNotePlayerState,
    modifier: Modifier = Modifier,
    colors: VoiceNotePlayerColors = voiceNotePlayerColors(),
    texts: VoiceNoteTexts = rememberVoiceNoteTexts(),
) {
    val posicao by state.player.currentPosition.collectAsState()
    val duracaoPlayer by state.player.duration.collectAsState()
    val tocando by state.player.isPlaying.collectAsState()
    val estadoPlayer by state.player.state.collectAsState()
    val duracao = duracaoPlayer.takeIf { it > 0L } ?: state.durationMillis
    val progresso = if (duracao > 0L && (tocando || posicao > 0L) && estadoPlayer != AudioPlayerState.COMPLETED) {
        (posicao.toFloat() / duracao).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tempo = formatVoiceNoteDuration(if (tocando || posicao > 0L && estadoPlayer == AudioPlayerState.PAUSED) posicao else duracao)

    Row(
        modifier = modifier.testTag(VoiceNoteTestTags.PLAYER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when {
                state.status == VoiceNotePlayerStatus.ERROR -> IconButton(onClick = state::retry) {
                    Icon(Icons.Filled.Refresh, contentDescription = texts.playError + ". " + texts.retry, tint = colors.button)
                }
                state.status == VoiceNotePlayerStatus.LOADING && !tocando ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).semantics { contentDescription = texts.loading },
                        strokeWidth = 2.dp,
                        color = colors.button,
                    )
                else -> IconButton(onClick = state::toggle, modifier = Modifier.testTag(VoiceNoteTestTags.PLAY)) {
                    Icon(
                        if (tocando) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (tocando) texts.pause else texts.play,
                        tint = colors.button,
                    )
                }
            }
        }
        AudioWaveform(
            levels = state.waveform,
            progress = progresso,
            activeColor = colors.played,
            inactiveColor = colors.unplayed,
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .semantics {
                    contentDescription = texts.description(formatVoiceNoteDuration(duracao))
                    progressBarRangeInfo = ProgressBarRangeInfo(progresso, 0f..1f)
                    setProgress { alvo -> state.seekTo(alvo); true }
                }
                .pointerInput(state, rtl) {
                    detectTapGestures { ponto ->
                        val f = (ponto.x / size.width).coerceIn(0f, 1f)
                        state.seekTo(if (rtl) 1f - f else f)
                    }
                },
        )
        Text(tempo, style = MaterialTheme.typography.labelMedium, color = colors.text)
    }
}

/** Nome do arquivo de cache para [cacheKey] — só caracteres seguros, sem o id cru virar caminho. */
fun voiceNoteCacheFileName(cacheKey: String): String {
    var h1 = 0x811C9DC5.toInt()
    var h2 = 0x01000193
    cacheKey.encodeToByteArray().forEach { b ->
        h1 = (h1 xor b.toInt()) * 0x01000193
        h2 = (h2 xor b.toInt()) * 0x5BD1E995
    }
    val hex = { v: Int -> (v.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0') }
    return "voz-" + hex(h1) + hex(h2) + ".m4a"
}

/** `<tmp>/kmplib_audio_playback/<nome>` (cria a pasta). `null` sem contexto. */
internal expect fun audioPlaybackCachePath(fileName: String): String?

internal expect fun audioFileSize(path: String): Long

internal expect fun writeAudioFile(path: String, bytes: ByteArray): Boolean

/**
 * A onda de um arquivo de áudio local: decodifica para PCM e devolve [bars] picos 0..1
 * (Android `MediaExtractor` + `MediaCodec`; iOS `AVAssetReader` com saída Linear PCM). Roda fora da
 * main thread; `null` se o arquivo não decodifica.
 */
expect suspend fun extractAudioWaveform(path: String, bars: Int = 48): List<Float>?
