@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package br.com.codecacto.kmplib.media

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.media.SoundEffectDefaults.TAG
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AudioToolbox.AudioServicesCreateSystemSoundID
import platform.AudioToolbox.AudioServicesDisposeSystemSoundID
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.CoreFoundation.CFURLRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * Cria o reprodutor de efeitos do iOS. [maxStreams] é ignorado — ver [IosSoundEffectPlayer].
 */
actual fun createSoundEffectPlayer(maxStreams: Int): SoundEffectPlayer = IosSoundEffectPlayer()

/**
 * **Padrão-ouro do iOS: *System Sound Services*** — `AudioServicesCreateSystemSoundID` no carregamento
 * e `AudioServicesPlaySystemSound` no disparo.
 *
 * ## Por que esta API, e não um pool de `AVAudioPlayer`
 *
 * A Apple documenta o *System Sound Services* como o caminho para **sons curtos** (até 30 s), sem
 * controle de volume, sem laço e sem posicionamento estéreo — exatamente o recorte de um bipe de
 * confirmação. Quatro razões concretas, na ordem em que pesam:
 *
 * 1. **Não toca na `AVAudioSession`.** Um pool de `AVAudioPlayer` exigiria ativar uma categoria
 *    (`Playback`/`Ambient`) para o som sair de forma previsível — e ativar sessão de mídia por causa
 *    de um clique **interrompe ou abaixa a música que o usuário está ouvindo**, a cada volta contada.
 *    O `AudioServices` é o mecanismo de som de interface: dispara sem assumir a sessão de áudio do
 *    app. É a diferença entre um app que bipa e um app que se comporta como reprodutor.
 * 2. **Pré-carregado de verdade.** O `SystemSoundID` é criado **uma vez**, no `load`; o disparo não
 *    decodifica, não prepara e não aloca. O `AVAudioPlayer`, mesmo com `prepareToPlay()`, precisaria
 *    de várias instâncias em rodízio para não cortar o disparo anterior — e cada `play()` sobre uma
 *    instância ainda tocando **reinicia** o som, que é o defeito a evitar.
 * 3. **Sobreposição natural.** Chamadas consecutivas de `AudioServicesPlaySystemSound` são mixadas
 *    pelo sistema; dois toques a 300 ms não se cortam, sem o app gerenciar pool nenhum. Daí
 *    `maxStreams` não existir aqui.
 * 4. **Assíncrono por contrato.** A chamada retorna imediatamente; o caminho quente (o toque) não
 *    bloqueia a main thread.
 *
 * **O preço, e por que é aceitável:** não há controle de volume nem de laço. A API comum não promete
 * nenhum dos dois — de propósito, para não expor um parâmetro que uma das plataformas ignoraria em
 * silêncio. Se um produto vier a precisar de volume por disparo, o caminho de padrão-ouro **não** é
 * o `AVAudioPlayer` (que a Apple desaconselha para efeito de latência crítica) e sim `AVAudioEngine`
 * + `AVAudioPlayerNode` com buffer PCM pré-carregado — troca interna, sem quebrar esta API.
 *
 * ## Formato
 *
 * O *System Sound Services* aceita **Linear PCM ou IMA4** em `.wav`, `.caf` ou `.aif`. MP3, M4A e OGG
 * **não** tocam por aqui (carregam no Android e falhariam calados no iPhone) — daí o aviso de
 * [SoundEffectFormat.isCrossPlatform] no log já no `load`.
 *
 * ## Silencioso
 *
 * Som de sistema segue o interruptor **Silencioso** e o volume do aparelho. Este módulo não
 * reconfigura a `AVAudioSession` para contornar isso (ver razão 1); se o app já ativou `Playback`
 * por outro motivo — o [AudioPlayer] da lib, por exemplo —, o efeito passa a seguir aquela categoria.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito conforme as APIs oficiais; o build Kotlin/Native
 * de iOS não roda no servidor Linux.
 */
internal class IosSoundEffectPlayer : SoundEffectPlayer {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String =
        NSTemporaryDirectory().trimEnd('/') + "/" + SoundEffectDefaults.CACHE_DIRECTORY

    private val registry = SoundEffectRegistry<UInt>()

    /** Caminho materializado de cada chave, para apagar no `unload`/`release`. */
    private val files = LinkedHashMap<String, String>()

    override val loadedKeys: Set<String> get() = registry.keys

    override suspend fun load(key: String, bytes: ByteArray): SoundEffectOutcome {
        registry.rejectionFor(key)?.let { return reject(it, "load '$key'") }

        val format = detectSoundEffectFormat(bytes)
        if (!format.isPlayable) {
            return reject(SoundEffectError.InvalidAudio, "load '$key': bytes não são áudio conhecido")
        }
        warnAboutFormatAndSize(key, format, bytes.size)

        val path = withContext(Dispatchers.Default) { materialize(key, bytes, format) }
            ?: return reject(SoundEffectError.StorageFailure, "load '$key'")

        val soundId = createSoundId(path)
        if (soundId == null) {
            removeFile(path)
            return reject(SoundEffectError.InvalidAudio, "load '$key': AudioServices recusou o áudio")
        }

        // Recarga da mesma chave: o SystemSoundID anterior TEM de ser descartado, senão o áudio
        // antigo fica retido pelo sistema até o processo morrer.
        registry.put(key, soundId)?.let { previous -> AudioServicesDisposeSystemSoundID(previous) }
        files.put(key, path)?.let { previous -> if (previous != path) removeFile(previous) }
        return SoundEffectOutcome.Success
    }

    override fun isLoaded(key: String): Boolean = registry.isLoaded(key)

    override fun play(key: String): SoundEffectOutcome {
        registry.rejectionFor(key)?.let { return reject(it, "play '$key'") }
        val soundId = registry.handleOf(key)
            ?: return reject(SoundEffectError.NotLoaded, "play '$key': efeito não carregado")

        AudioServicesPlaySystemSound(soundId)
        return SoundEffectOutcome.Success
    }

    override fun unload(key: String) {
        registry.remove(key)?.let { soundId -> AudioServicesDisposeSystemSoundID(soundId) }
        files.remove(key)?.let(::removeFile)
    }

    override fun release() {
        registry.releaseAll().forEach { soundId -> AudioServicesDisposeSystemSoundID(soundId) }
        files.values.forEach(::removeFile)
        files.clear()
    }

    /**
     * Cria o `SystemSoundID` do arquivo. `CFBridgingRetain` faz a ponte `NSURL` → `CFURLRef` e o
     * `CFBridgingRelease` **no mesmo caminho** devolve a posse — sem ele, cada carga vazaria a URL.
     */
    private fun createSoundId(path: String): UInt? {
        val url = NSURL.fileURLWithPath(path)
        val cfUrl = CFBridgingRetain(url) as CFURLRef?
        try {
            return memScoped {
                val holder = alloc<UIntVar>()
                val status = AudioServicesCreateSystemSoundID(cfUrl, holder.ptr)
                if (status != 0) {
                    AppLogger.w(TAG, "AudioServicesCreateSystemSoundID falhou: status=$status")
                    null
                } else {
                    holder.value
                }
            }
        } finally {
            CFBridgingRelease(cfUrl)
        }
    }

    private fun materialize(key: String, bytes: ByteArray, format: SoundEffectFormat): String? {
        if (!ensureDirectory()) {
            AppLogger.e(TAG, "não foi possível criar o diretório de efeitos")
            return null
        }
        val path = "$directory/${SoundEffectDefaults.fileNameFor(key, format)}"
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        // `atomically = true`: processo morto no meio da escrita nunca deixa um efeito truncado
        // ocupando o lugar do áudio válido.
        val ok = data.writeToFile(path, atomically = true)
        if (!ok) {
            AppLogger.e(TAG, "falha ao materializar o efeito '$key'")
            return null
        }
        return path
    }

    private fun ensureDirectory(): Boolean {
        if (fileManager.fileExistsAtPath(directory)) return true
        return fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    private fun removeFile(path: String) {
        fileManager.removeItemAtPath(path, error = null)
    }

    private fun warnAboutFormatAndSize(key: String, format: SoundEffectFormat, size: Int) {
        if (!format.isCrossPlatform) {
            AppLogger.w(
                TAG,
                "efeito '$key' está em $format — o System Sound Services aceita só WAV/CAF/AIFF",
            )
        }
        if (SoundEffectDefaults.isOversized(size)) {
            AppLogger.w(TAG, "efeito '$key' tem ${size / 1024} KB — grande para um efeito curto")
        }
    }

    private fun reject(error: SoundEffectError, context: String): SoundEffectOutcome {
        AppLogger.w(TAG, "$context — $error")
        return failWith(error)
    }
}
