package br.com.codecacto.kmplib.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.media.SoundEffectDefaults.TAG
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.lang.ref.WeakReference

/**
 * Holder do [Context] da aplicação para os efeitos sonoros no Android. Inicializado por
 * `KmpLib.init(context)` — nenhum app precisa chamar isto à mão.
 */
object SoundEffectPlayerHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Cria o reprodutor de efeitos do Android.
 *
 * Sem `KmpLib.init(context)` no `Application.onCreate()`, devolve um reprodutor inerte que reporta
 * [SoundEffectError.NotInitialized] — em vez de estourar no primeiro toque.
 */
actual fun createSoundEffectPlayer(maxStreams: Int): SoundEffectPlayer {
    val context = SoundEffectPlayerHolder.getContext() ?: run {
        AppLogger.e(TAG, "KmpLib.init(context) não foi chamado — efeitos sonoros indisponíveis")
        return UnavailableSoundEffectPlayer()
    }
    return AndroidSoundEffectPlayer(context, maxStreams)
}

/**
 * **Padrão-ouro do Android: `SoundPool`** — a API que a própria documentação do Android indica para
 * "efeitos sonoros curtos e repetidos" em jogos e interface.
 *
 * Por que não `MediaPlayer` (que é o que o [AudioPlayer] usa, para mídia):
 * - o `SoundPool` **decodifica uma vez, no `load`**, e mantém o PCM em memória; cada `play` é só
 *   despachar para o mixer. O `MediaPlayer` refaz `setDataSource` + `prepare` a cada disparo, o que
 *   num bipe por toque significa latência variável e picotes;
 * - o pool toca **vozes simultâneas** (`maxStreams`), então dois toques a 300 ms se sobrepõem em vez
 *   de o segundo cortar o primeiro;
 * - `play` retorna na hora — não há callback de preparo no caminho quente.
 *
 * **`AudioAttributes` com `USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SONIFICATION`**: é a
 * classificação correta para som de interface, e é o que faz o efeito seguir o volume/silencioso do
 * **sistema** em vez de se apresentar como mídia (o que abaixaria a música do usuário por *audio
 * focus* a cada bipe).
 *
 * O `SoundPool` carrega de um **caminho de arquivo** ou de um `AssetFileDescriptor`. Como a API
 * comum recebe bytes (recurso do Compose Resources), a lib materializa o áudio em
 * `cacheDir/kmplib_sfx/` e apaga no `unload`/`release`.
 */
internal class AndroidSoundEffectPlayer(
    context: Context,
    maxStreams: Int,
) : SoundEffectPlayer {

    private companion object {
        /**
         * Teto de espera pelo callback de carga. O `SoundPool` avisa em milissegundos para um efeito
         * curto; o limite existe só para que um sample recusado de forma anômala não deixe a
         * coroutine de preparo da sessão pendurada para sempre.
         */
        const val LOAD_TIMEOUT_MS = 5_000L
    }

    private val appContext = context.applicationContext

    private val directory = File(appContext.cacheDir, SoundEffectDefaults.CACHE_DIRECTORY)

    private val registry = SoundEffectRegistry<Int>()

    /** Arquivo materializado de cada chave, para apagar no `unload`/`release`. */
    private val files = LinkedHashMap<String, File>()

    private val lock = Any()

    /** Cargas em voo, por `sampleId`. */
    private val pending = HashMap<Int, CompletableDeferred<Int>>()

    /** Callbacks que chegaram **antes** de a coroutine registrar a espera (corrida real). */
    private val completedEarly = HashMap<Int, Int>()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(maxStreams.coerceAtLeast(1))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                synchronized(lock) {
                    val waiter = pending.remove(sampleId)
                    if (waiter != null) waiter.complete(status) else completedEarly[sampleId] = status
                }
            }
        }

    override val loadedKeys: Set<String> get() = registry.keys

    override suspend fun load(key: String, bytes: ByteArray): SoundEffectOutcome {
        registry.rejectionFor(key)?.let { return reject(it, "load '$key'") }

        val format = detectSoundEffectFormat(bytes)
        if (!format.isPlayable) {
            return reject(SoundEffectError.InvalidAudio, "load '$key': bytes não são áudio conhecido")
        }
        warnAboutFormatAndSize(key, format, bytes.size)

        val file = withContext(Dispatchers.IO) { materialize(key, bytes, format) }
            ?: return reject(SoundEffectError.StorageFailure, "load '$key'")

        val sampleId = runCatching { soundPool.load(file.absolutePath, 1) }.getOrDefault(0)
        if (sampleId == 0) {
            file.delete()
            return reject(SoundEffectError.InvalidAudio, "load '$key': SoundPool recusou o arquivo")
        }

        val status = awaitLoad(sampleId)
        if (status != 0) {
            runCatching { soundPool.unload(sampleId) }
            file.delete()
            return reject(SoundEffectError.InvalidAudio, "load '$key': status=$status")
        }

        // Recarga da mesma chave: o sample anterior TEM de sair, senão o PCM antigo fica na memória
        // do pool para sempre.
        registry.put(key, sampleId)?.let { previous -> runCatching { soundPool.unload(previous) } }
        files.put(key, file)?.let { previous -> if (previous != file) previous.delete() }
        return SoundEffectOutcome.Success
    }

    override fun isLoaded(key: String): Boolean = registry.isLoaded(key)

    override fun play(key: String): SoundEffectOutcome {
        registry.rejectionFor(key)?.let { return reject(it, "play '$key'") }
        val sampleId = registry.handleOf(key)
            ?: return reject(SoundEffectError.NotLoaded, "play '$key': efeito não carregado")

        // Volume 1f nos dois canais: o volume audível é o do stream de sistema (o app não controla,
        // e não deve — quem decide é o usuário no aparelho). loop=0, rate=1f: efeito toca uma vez.
        val streamId = runCatching {
            soundPool.play(sampleId, 1f, 1f, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
        }.getOrDefault(0)

        return if (streamId == 0) {
            reject(SoundEffectError.Unknown("SoundPool não alocou stream"), "play '$key'")
        } else {
            SoundEffectOutcome.Success
        }
    }

    override fun unload(key: String) {
        registry.remove(key)?.let { sampleId -> runCatching { soundPool.unload(sampleId) } }
        files.remove(key)?.let { file -> runCatching { file.delete() } }
    }

    override fun release() {
        registry.releaseAll().forEach { sampleId -> runCatching { soundPool.unload(sampleId) } }
        files.values.forEach { file -> runCatching { file.delete() } }
        files.clear()
        synchronized(lock) {
            pending.values.forEach { it.complete(-1) }
            pending.clear()
            completedEarly.clear()
        }
        runCatching { soundPool.release() }
            .onFailure { AppLogger.w(TAG, "falha ao liberar o SoundPool: ${it.message}") }
    }

    private suspend fun awaitLoad(sampleId: Int): Int {
        val waiter = synchronized(lock) {
            completedEarly.remove(sampleId)?.let { return it }
            CompletableDeferred<Int>().also { pending[sampleId] = it }
        }
        return withTimeoutOrNull(LOAD_TIMEOUT_MS) { waiter.await() } ?: run {
            synchronized(lock) { pending.remove(sampleId) }
            AppLogger.w(TAG, "tempo esgotado carregando sample $sampleId")
            -1
        }
    }

    private fun materialize(key: String, bytes: ByteArray, format: SoundEffectFormat): File? =
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) {
                error("não foi possível criar ${directory.path}")
            }
            File(directory, SoundEffectDefaults.fileNameFor(key, format)).apply { writeBytes(bytes) }
        }.getOrElse {
            AppLogger.e(TAG, "falha ao materializar o efeito '$key'", it)
            null
        }

    private fun warnAboutFormatAndSize(key: String, format: SoundEffectFormat, size: Int) {
        if (!format.isCrossPlatform) {
            AppLogger.w(
                TAG,
                "efeito '$key' está em $format — toca no Android, mas o iOS aceita só WAV/CAF/AIFF",
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
