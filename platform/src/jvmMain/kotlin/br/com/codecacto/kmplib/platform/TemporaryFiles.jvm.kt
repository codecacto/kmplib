package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import java.nio.file.Files
import java.nio.file.Path

internal const val OPEN_TEMP_DIRECTORY: String = "kmplib_open"
internal const val SHARE_TEMP_DIRECTORY: String = "kmplib_share"

/**
 * Arquivo novo em `<tmp>/kmplib-<appId>/<dir>/<uuid>/<nome>` — o nome que a pessoa verá no app que
 * abrir o arquivo é o original (sem prefixo), e a subpasta única evita colisão entre dois envios.
 */
internal fun desktopTempFile(dir: String, fileName: String): Path {
    val seguro = fileName.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").ifBlank { "arquivo" }
    val pasta = DesktopFiles.tempDirectory(dir).resolve(java.util.UUID.randomUUID().toString())
    Files.createDirectories(pasta)
    return pasta.resolve(seguro)
}

/** Desktop: apaga, nas pastas temporárias da lib deste app, o que passou de [olderThanMillis]. */
internal actual fun clearPlatformTemporaryFiles(olderThanMillis: Long): Int {
    val raiz = DesktopFiles.tempRoot()
    if (!Files.isDirectory(raiz)) return 0
    val agora = System.currentTimeMillis()
    var apagados = 0
    runCatching {
        Files.walk(raiz).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { p ->
                if (p == raiz) return@forEach
                runCatching {
                    if (Files.isRegularFile(p)) {
                        val idade = agora - Files.getLastModifiedTime(p).toMillis()
                        if (olderThanMillis <= 0L || idade >= olderThanMillis) {
                            if (Files.deleteIfExists(p)) apagados++
                        }
                    } else if (Files.isDirectory(p)) {
                        Files.list(p).use { if (!it.findAny().isPresent) Files.deleteIfExists(p) }
                    }
                }
            }
        }
    }.onFailure { AppLogger.w("KmpLibTempFiles", "falha ao limpar temporários: ${it::class.simpleName}") }
    return apagados
}

/** Desktop: a lib não grava foto de câmera no desktop (sem câmera do sistema). */
actual fun clearCameraCaptureFiles(olderThanMillis: Long): Int = 0
