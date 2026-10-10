package br.com.codecacto.kmplib.platform

import android.content.Context
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "KmpLibTempFiles"

@OptIn(KmpLibPlatformInternalApi::class)
internal actual fun clearPlatformTemporaryFiles(olderThanMillis: Long): Int {
    val context = ShareHandlerHolder.getContext()
    if (context == null) {
        AppLogger.w(TAG, "initKmpLibPlatform não foi chamado — temporários não limpos")
        return 0
    }
    return clearKmpLibTemporaryFiles(context, olderThanMillis)
}

/** O mesmo que [clearKmpLibTemporaryFiles], com o [Context] explícito (antes do init, em teste). */
@OptIn(KmpLibPlatformInternalApi::class)
fun clearKmpLibTemporaryFiles(context: Context, olderThanMillis: Long = DEFAULT_SHARED_FILE_TTL_MILLIS): Int {
    val compartilhados = runCatching { AndroidShareHandler(context).clearSharedFiles(olderThanMillis) }
        .onFailure { AppLogger.w(TAG, "falha ao limpar compartilhados: ${it::class.simpleName}") }
        .getOrDefault(0)
    val pdf = KmpLibTempFiles.purge(KmpLibTempFiles.directory(context, PDF_VIEWER_TEMP_DIRECTORY), olderThanMillis)
    val impressao = KmpLibTempFiles.purge(KmpLibTempFiles.directory(context, PRINT_TEMP_DIRECTORY), olderThanMillis)
    val gravados = KmpLibTempFiles.purge(KmpLibTempFiles.directory(context, VIDEO_CAPTURE_TEMP_DIRECTORY), olderThanMillis)
    val preparados = KmpLibTempFiles.purge(KmpLibTempFiles.directory(context, VIDEO_PREPARED_TEMP_DIRECTORY), olderThanMillis)
    val voz = KmpLibTempFiles.purge(KmpLibTempFiles.directory(context, AUDIO_CAPTURE_TEMP_DIRECTORY), olderThanMillis)
    val vozBaixada = KmpLibTempFiles.purge(KmpLibTempFiles.directory(context, AUDIO_PLAYBACK_TEMP_DIRECTORY), olderThanMillis)
    return compartilhados + pdf + impressao + gravados + preparados + voz + vozBaixada
}

/**
 * Temporários da lib no `cacheDir` (2.280.0), com o registro do que está **em uso neste processo**.
 *
 * Arquivo criado por [create] fica marcado até [release]; a [purge] nunca apaga um marcado. Assim
 * "abrir um PDF novo limpa as sobras" não derruba o PDF que outro visualizador está abrindo no mesmo
 * instante, e o logout não apaga o arquivo que o spooler ainda vai ler. O que sobra no disco sem
 * marca é resíduo de processo morto (ou de falha) — e sai.
 */
@KmpLibPlatformInternalApi
object KmpLibTempFiles {
    private val emUso: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A pasta [name] dentro do `cacheDir` (não cria). */
    fun directory(context: Context, name: String): File = File(context.cacheDir, name)

    /** Cria o temporário em [dir] (criando a pasta) e o marca como em uso. */
    fun create(dir: File, prefix: String, suffix: String): File {
        dir.mkdirs()
        val arquivo = File.createTempFile(prefix, suffix, dir)
        emUso += arquivo.absolutePath
        return arquivo
    }

    /** Desmarca [file] (e o apaga se [delete]). Idempotente. */
    fun release(file: File, delete: Boolean = true) {
        emUso -= file.absolutePath
        if (delete) runCatching { file.delete() }
    }

    /** `true` se [file] está marcado como em uso. */
    fun isInUse(file: File): Boolean = file.absolutePath in emUso

    /**
     * Apaga os arquivos de [dir] não marcados e com idade ≥ [olderThanMillis] (`0` = todos os não
     * marcados). Nunca lança; devolve quantos apagou.
     */
    fun purge(dir: File, olderThanMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
        val arquivos = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        var apagados = 0
        arquivos.forEach { arquivo ->
            if (!arquivo.isFile || isInUse(arquivo)) return@forEach
            if (!shouldPurgeSharedFile(arquivo.lastModified(), nowMillis, olderThanMillis)) return@forEach
            if (runCatching { arquivo.delete() }.getOrDefault(false)) {
                apagados++
            } else {
                AppLogger.w(TAG, "não foi possível apagar um temporário")
            }
        }
        return apagados
    }
}
