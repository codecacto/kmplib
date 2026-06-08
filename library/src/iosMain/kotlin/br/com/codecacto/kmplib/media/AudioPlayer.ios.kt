package br.com.codecacto.kmplib.media

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * Implementação iOS do [AudioPlayer] usando `AVAudioPlayer` (AVFAudio).
 *
 * A posição é emitida a cada ~0.25s via [NSTimer] agendado no run loop principal.
 * O caminho aceito pode ser um path de arquivo simples ou uma URL `file://`.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : AudioPlayer {

    companion object {
        private const val TAG = "AudioPlayer"
        private const val PROGRESS_INTERVAL_S = 0.25
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

    private var player: AVAudioPlayer? = null
    private var currentPath: String? = null
    private var timer: NSTimer? = null

    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            _isPlaying.value = false
            _currentPosition.value = _duration.value
            _state.value = AudioPlayerState.COMPLETED
            stopTimer()
        }

        override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
            reportError(error?.localizedDescription ?: "Erro de decodificação")
        }
    }

    override fun play(filePath: String) {
        if (filePath == currentPath && player != null) {
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

        val url = if (filePath.startsWith("file://")) {
            NSURL.URLWithString(filePath)
        } else {
            NSURL.fileURLWithPath(filePath)
        }
        if (url == null) {
            reportError("Caminho de áudio inválido: $filePath")
            return
        }

        try {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)

            val p = AVAudioPlayer(contentsOfURL = url, error = null)
            p.setDelegate(delegate)
            if (!p.prepareToPlay()) {
                reportError("Falha ao preparar o áudio")
                return
            }
            player = p
            _duration.value = (p.duration * 1000.0).toLong()
            if (p.play()) {
                _isPlaying.value = true
                _state.value = AudioPlayerState.PLAYING
                startTimer()
            } else {
                reportError("Falha ao iniciar a reprodução")
            }
        } catch (e: Exception) {
            reportError(e.message ?: "Falha ao carregar áudio")
        }
    }

    private fun resume() {
        val p = player ?: return
        if (p.play()) {
            _isPlaying.value = true
            _state.value = AudioPlayerState.PLAYING
            startTimer()
        } else {
            reportError("Falha ao retomar")
        }
    }

    override fun pause() {
        val p = player ?: return
        p.pause()
        _currentPosition.value = (p.currentTime * 1000.0).toLong()
        _isPlaying.value = false
        _state.value = AudioPlayerState.PAUSED
        stopTimer()
    }

    override fun seekTo(positionMs: Long) {
        val p = player ?: return
        val clamped = positionMs.coerceIn(0L, _duration.value.coerceAtLeast(0L))
        p.setCurrentTime(clamped / 1000.0)
        _currentPosition.value = clamped
        if (_state.value == AudioPlayerState.COMPLETED) {
            _state.value = AudioPlayerState.PAUSED
        }
    }

    override fun stop() {
        val p = player ?: return
        p.stop()
        p.setCurrentTime(0.0)
        _isPlaying.value = false
        _currentPosition.value = 0L
        _state.value = AudioPlayerState.IDLE
        stopTimer()
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
        stopTimer()
        player?.stop()
        player?.setDelegate(null)
        player = null
    }

    private fun reportError(message: String) {
        AppLogger.e(TAG, message)
        _lastError.value = message
        _state.value = AudioPlayerState.ERROR
        _isPlaying.value = false
        stopTimer()
    }

    private fun startTimer() {
        stopTimer()
        val t = NSTimer.timerWithTimeInterval(
            interval = PROGRESS_INTERVAL_S,
            repeats = true
        ) {
            val p = player
            if (p != null && p.playing) {
                _currentPosition.value = (p.currentTime * 1000.0).toLong()
            }
        }
        NSRunLoop.mainRunLoop.addTimer(t, NSRunLoopCommonModes)
        timer = t
    }

    private fun stopTimer() {
        timer?.invalidate()
        timer = null
    }
}

actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()
