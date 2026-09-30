package br.com.codecacto.kmplib.media

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Cria o ambiente sonoro do Android (Media3/ExoPlayer). Sem `KmpLib.init`/`initKmpLibMedia(context)`,
 * devolve um player que responde [AmbientSoundError.NotInitialized] em vez de estourar.
 */
actual fun createAmbientSoundPlayer(config: AmbientSoundConfig): AmbientSoundPlayer {
    // O `Context` do módulo media é registrado uma vez, no holder dos efeitos sonoros.
    val context = SoundEffectPlayerHolder.getContext()
    val engine: AmbientAudioEngine = if (context == null) {
        AppLogger.w(AMBIENT_TAG, "createAmbientSoundPlayer sem initKmpLibMedia(context) — ambiente inerte.")
        UnavailableAmbientEngine
    } else {
        ExoAmbientEngine(context, config)
    }
    return DefaultAmbientSoundPlayer(engine, Dispatchers.Main.immediate)
}

private const val AMBIENT_TAG = "AmbientSoundPlayer"

/**
 * **Padrão-ouro do Android: Media3/ExoPlayer.** `REPEAT_MODE_ONE` dá o laço sem emenda (a volta
 * seguinte é pré-carregada pelo próprio player), e o `ByteArrayDataSource` lê o áudio da memória —
 * sem materializar arquivo. `AudioAttributes` `USAGE_MEDIA`/`AUDIO_CONTENT_TYPE_MUSIC`; o foco de
 * áudio é gerido pela Media3 só quando [AmbientSoundConfig.mixWithOthers] é `false`.
 * `setHandleAudioBecomingNoisy(true)`: tirar o fone pausa (é o que o Android pede a todo player).
 *
 * O player é preso à **main looper**: todas as chamadas chegam por ela ([DefaultAmbientSoundPlayer]
 * roda em `Dispatchers.Main.immediate`, e o [load] troca para a main).
 */
@OptIn(UnstableApi::class)
private class ExoAmbientEngine(context: Context, config: AmbientSoundConfig) : AmbientAudioEngine {

    override var onSystemPause: (() -> Unit)? = null
    override var onSystemResumeAllowed: (() -> Unit)? = null

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setLooper(Looper.getMainLooper())
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ !config.mixWithOthers,
            )
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    // Fone desconectado / perda definitiva de foco: o player se pausou sozinho.
                    if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        onSystemPause?.invoke()
                    }
                }
            })
        }

    override suspend fun load(bytes: ByteArray): AmbientSoundError? = withContext(Dispatchers.Main.immediate) {
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        val source = ProgressiveMediaSource.Factory(DataSource.Factory { ByteArrayDataSource(bytes) })
            .createMediaSource(MediaItem.fromUri(Uri.parse("kmplib-ambient://sound")))
        suspendCancellableCoroutine { cont ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && cont.isActive) {
                        player.removeListener(this)
                        cont.resume(null)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!cont.isActive) return
                    player.removeListener(this)
                    AppLogger.w(AMBIENT_TAG, "Áudio recusado pela Media3: ${error.errorCodeName}")
                    cont.resume(
                        if (error.errorCode in PARSING_OR_DECODING) AmbientSoundError.InvalidAudio
                        else AmbientSoundError.Unknown(error.errorCodeName),
                    )
                }
            }
            player.addListener(listener)
            cont.invokeOnCancellation { player.removeListener(listener) }
            player.setMediaSource(source)
            player.prepare()
        }
    }

    override fun start() {
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun rewind() {
        player.seekTo(0L)
    }

    override fun setGain(gain: Float) {
        player.volume = gain
    }

    override fun release() {
        player.release()
    }

    private companion object {
        val PARSING_OR_DECODING = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        )
    }
}

/** Sem contexto: tudo responde [AmbientSoundError.NotInitialized]. */
private object UnavailableAmbientEngine : AmbientAudioEngine {
    override var onSystemPause: (() -> Unit)? = null
    override var onSystemResumeAllowed: (() -> Unit)? = null
    override suspend fun load(bytes: ByteArray): AmbientSoundError = AmbientSoundError.NotInitialized
    override fun start() = Unit
    override fun pause() = Unit
    override fun rewind() = Unit
    override fun setGain(gain: Float) = Unit
    override fun release() = Unit
}
