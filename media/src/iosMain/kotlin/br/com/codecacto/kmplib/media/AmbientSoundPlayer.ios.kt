@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.media

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.create
import platform.darwin.NSObjectProtocol

/** Cria o ambiente sonoro do iOS (`AVAudioPlayer` + sessão `.ambient`). */
actual fun createAmbientSoundPlayer(config: AmbientSoundConfig): AmbientSoundPlayer =
    DefaultAmbientSoundPlayer(AvAmbientEngine(config), Dispatchers.Main.immediate)

private const val AMBIENT_TAG = "AmbientSoundPlayer"

/**
 * **Padrão-ouro do iOS: `AVAudioPlayer`** com `numberOfLoops = -1` (laço infinito, sem emenda em
 * AAC/PCM) e a **`AVAudioSession`** na categoria que a Apple indica para som que acompanha o app:
 * `.ambient` — mistura com o áudio de outros apps e silencia no interruptor Silencioso. Com
 * [AmbientSoundConfig.mixWithOthers] `false`, `.playback` (áudio principal). A categoria é aplicada
 * e a sessão ativada **no primeiro `start`**, não na criação: carregar o som não mexe no áudio de
 * ninguém.
 *
 * Interrupção (ligação, Siri, alarme): o `AVAudioPlayer` já para sozinho; ao fim, se o sistema
 * indicar `ShouldResume`, o ambiente volta.
 */
private class AvAmbientEngine(private val config: AmbientSoundConfig) : AmbientAudioEngine {

    override var onSystemPause: (() -> Unit)? = null
    override var onSystemResumeAllowed: (() -> Unit)? = null

    private var player: AVAudioPlayer? = null
    private var gain = 1f
    private var sessionConfigured = false

    private val interruptionObserver: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
        name = AVAudioSessionInterruptionNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        val info = notification?.userInfo ?: return@addObserverForName
        val type = (info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue ?: return@addObserverForName
        when (type) {
            AVAudioSessionInterruptionTypeBegan -> onSystemPause?.invoke()
            AVAudioSessionInterruptionTypeEnded -> {
                val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
                if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) onSystemResumeAllowed?.invoke()
            }
        }
    }

    override suspend fun load(bytes: ByteArray): AmbientSoundError? {
        player?.stop()
        player = null
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val created = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            // `initWithData:error:` é inicializador que pode falhar (nil + NSError). O construtor
            // Kotlin não expressa o nil no tipo, então a falha é lida pelo NSError — e a exceção
            // que o runtime levantaria para um nil também vira "áudio inválido".
            val p = try {
                AVAudioPlayer(data = data, error = error.ptr)
            } catch (e: Exception) {
                null
            }
            val failure = error.value
            if (p == null || failure != null) {
                AppLogger.w(AMBIENT_TAG, "AVAudioPlayer recusou o áudio: ${failure?.localizedDescription}")
                null
            } else {
                p
            }
        } ?: return AmbientSoundError.InvalidAudio
        created.numberOfLoops = -1
        created.volume = gain
        if (!created.prepareToPlay()) return AmbientSoundError.InvalidAudio
        player = created
        return null
    }

    private fun configureSession() {
        if (sessionConfigured) return
        val session = AVAudioSession.sharedInstance()
        val category = if (config.mixWithOthers) AVAudioSessionCategoryAmbient else AVAudioSessionCategoryPlayback
        val ok = session.setCategory(category, error = null) && session.setActive(true, error = null)
        if (!ok) AppLogger.w(AMBIENT_TAG, "AVAudioSession não aceitou a categoria $category")
        sessionConfigured = ok
    }

    override fun start() {
        val p = player ?: return
        configureSession()
        if (!p.play()) AppLogger.w(AMBIENT_TAG, "AVAudioPlayer.play() devolveu false")
    }

    override fun pause() {
        player?.pause()
    }

    override fun rewind() {
        player?.currentTime = 0.0
    }

    override fun setGain(gain: Float) {
        this.gain = gain
        player?.volume = gain
    }

    override fun release() {
        NSNotificationCenter.defaultCenter.removeObserver(interruptionObserver)
        player?.stop()
        player = null
        onSystemPause = null
        onSystemResumeAllowed = null
    }
}
