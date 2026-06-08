package br.com.codecacto.kmplib.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Holder do [Context] da aplicação para o [AudioPlayer] no Android.
 * Deve ser inicializado no `Application.onCreate()`:
 *
 * ```kotlin
 * class App : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         AudioPlayerHolder.init(this)
 *     }
 * }
 * ```
 */
object AudioPlayerHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Implementação Android do [AudioPlayer] usando [MediaPlayer].
 *
 * A posição é emitida a cada ~250ms enquanto reproduzindo, via [Handler] na main thread.
 * Não há dependências externas (ExoPlayer/media3 não estão na kmplib).
 */
class AndroidAudioPlayer : AudioPlayer {

    companion object {
        private const val TAG = "AudioPlayer"
        private const val PROGRESS_INTERVAL_MS = 250L
    }

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    override val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var currentPath: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer ?: return
            try {
                if (mp.isPlaying) {
                    _currentPosition.value = mp.currentPosition.toLong()
                    handler.postDelayed(this, PROGRESS_INTERVAL_MS)
                }
            } catch (e: IllegalStateException) {
                AppLogger.w(TAG, "progress tick em estado inválido: ${e.message}")
            }
        }
    }

    override fun play(filePath: String) {
        // Mesmo arquivo já carregado: apenas retoma.
        if (filePath == currentPath && mediaPlayer != null) {
            resume()
            return
        }
        prepareAndStart(filePath)
    }

    private fun prepareAndStart(filePath: String) {
        releaseInternal()
        _state.value = AudioPlayerState.LOADING
        _lastError.value = null
        currentPath = filePath

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(filePath)
                setOnPreparedListener { mp ->
                    _duration.value = mp.duration.toLong()
                    mp.start()
                    _isPlaying.value = true
                    _state.value = AudioPlayerState.PLAYING
                    startProgressUpdates()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = _duration.value
                    _state.value = AudioPlayerState.COMPLETED
                    stopProgressUpdates()
                }
                setOnErrorListener { _, what, extra ->
                    reportError("MediaPlayer error what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            reportError(e.message ?: "Falha ao carregar áudio")
        }
    }

    private fun resume() {
        val mp = mediaPlayer ?: return
        try {
            mp.start()
            _isPlaying.value = true
            _state.value = AudioPlayerState.PLAYING
            startProgressUpdates()
        } catch (e: IllegalStateException) {
            reportError(e.message ?: "Falha ao retomar")
        }
    }

    override fun pause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                _currentPosition.value = mp.currentPosition.toLong()
            }
            _isPlaying.value = false
            _state.value = AudioPlayerState.PAUSED
            stopProgressUpdates()
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "pause em estado inválido: ${e.message}")
        }
    }

    override fun seekTo(positionMs: Long) {
        val mp = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _duration.value.coerceAtLeast(0L))
        try {
            mp.seekTo(clamped.toInt())
            _currentPosition.value = clamped
            // Sair de COMPLETED ao reposicionar.
            if (_state.value == AudioPlayerState.COMPLETED) {
                _state.value = AudioPlayerState.PAUSED
            }
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "seek em estado inválido: ${e.message}")
        }
    }

    override fun stop() {
        val mp = mediaPlayer ?: return
        try {
            mp.stop()
            mp.prepareAsync()
        } catch (e: IllegalStateException) {
            AppLogger.w(TAG, "stop em estado inválido: ${e.message}")
        }
        _isPlaying.value = false
        _currentPosition.value = 0L
        _state.value = AudioPlayerState.IDLE
        stopProgressUpdates()
    }

    override fun release() {
        releaseInternal()
        _state.value = AudioPlayerState.IDLE
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        currentPath = null
    }

    private fun releaseInternal() {
        stopProgressUpdates()
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            AppLogger.w(TAG, "erro ao liberar MediaPlayer: ${e.message}")
        }
        mediaPlayer = null
    }

    private fun reportError(message: String) {
        AppLogger.e(TAG, message)
        _lastError.value = message
        _state.value = AudioPlayerState.ERROR
        _isPlaying.value = false
        stopProgressUpdates()
    }

    private fun startProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
    }
}

actual fun createAudioPlayer(): AudioPlayer = AndroidAudioPlayer()
