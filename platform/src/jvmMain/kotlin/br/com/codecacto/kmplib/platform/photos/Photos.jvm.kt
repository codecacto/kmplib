package br.com.codecacto.kmplib.platform.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.DesktopFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private const val TAG = "OriginalPhotos"

/**
 * Desktop (2.353.0): o PC não tem "galeria" — a foto original vem do seletor NATIVO de arquivo,
 * filtrado para imagem. O arquivo é COPIADO byte a byte para a pasta temporária da lib (mesmo
 * contrato do Android/iOS: o app recebe uma cópia sua, que pode mover para a fila ou apagar).
 */
@Composable
actual fun rememberOriginalPhotoPicker(
    maxItems: Int,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit = rememberDesktopOriginalPhotoPicker(maxItems, onResult)

@Composable
actual fun rememberOriginalPhotoFilePicker(
    maxItems: Int,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit = rememberDesktopOriginalPhotoPicker(maxItems, onResult)

@Composable
private fun rememberDesktopOriginalPhotoPicker(
    maxItems: Int,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit {
    val entregar by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return {
        val escolhidos = DesktopFiles.chooseFiles("Selecionar fotos", listOf("image/*"), multiple = maxItems != 1)
            .let { if (maxItems > 0) it.take(maxItems) else it }
        if (escolhidos.isEmpty()) {
            entregar(OriginalPhotoPickResult(emptyList()))
        } else {
            scope.launch {
                val r = withContext(Dispatchers.IO) { copyOriginals(escolhidos) }
                entregar(r)
            }
        }
    }
}

internal fun copyOriginals(paths: List<Path>): OriginalPhotoPickResult {
    var falhas = 0
    val fotos = paths.mapNotNull { origem ->
        val nome = origem.fileName.toString()
        val mime = resolveOriginalPhotoMimeType(runCatching { Files.probeContentType(origem) }.getOrNull(), nome)
        if (mime == null) {
            falhas++
            return@mapNotNull null
        }
        runCatching {
            val pasta = DesktopFiles.tempDirectory(ORIGINAL_PHOTO_TEMP_DIRECTORY).resolve(java.util.UUID.randomUUID().toString())
            Files.createDirectories(pasta)
            val destino = pasta.resolve(nome)
            Files.copy(origem, destino, StandardCopyOption.REPLACE_EXISTING)
            OriginalPhoto(destino.toString(), nome, mime, Files.size(destino))
        }.getOrElse {
            AppLogger.w(TAG, "foto ilegível: ${it::class.simpleName}")
            falhas++
            null
        }
    }
    return OriginalPhotoPickResult(fotos, falhas)
}

internal actual fun deleteOriginalPhotoFile(path: String): Boolean =
    runCatching { Files.deleteIfExists(Paths.get(path)) }.getOrDefault(false)

/**
 * Desktop (2.353.0): a "galeria" do PC é a pasta Imagens/Vídeos do usuário
 * (`~/Pictures`/`~/Videos`; com [album], numa subpasta). Copia o arquivo sem recomprimir.
 */
internal object DesktopGallerySaver : GallerySaver {
    override suspend fun save(path: String, fileName: String, mimeType: String, album: String?): GallerySaveResult =
        withContext(Dispatchers.IO) {
            val tipo = galleryMediaKindOf(mimeType) ?: return@withContext GallerySaveResult.UnsupportedType
            runCatching {
                val base = Paths.get(System.getProperty("user.home"), if (tipo == GalleryMediaKind.IMAGE) "Pictures" else "Videos")
                val seguro = { s: String -> s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_") }
                val pasta = album?.takeIf { it.isNotBlank() }?.let { base.resolve(seguro(it)) } ?: base
                Files.createDirectories(pasta)
                var destino = pasta.resolve(seguro(fileName).ifBlank { "arquivo" })
                var n = 1
                while (Files.exists(destino)) {
                    val nome = seguro(fileName)
                    val ext = nome.substringAfterLast('.', "")
                    val raiz = if (ext.isEmpty()) nome else nome.removeSuffix(".$ext")
                    destino = pasta.resolve(if (ext.isEmpty()) "$raiz ($n)" else "$raiz ($n).$ext")
                    n++
                }
                Files.copy(Paths.get(path), destino)
                GallerySaveResult.Saved
            }.getOrElse {
                AppLogger.w("GallerySaver", "não salvou: ${it::class.simpleName}")
                GallerySaveResult.Failed
            }
        }
}

actual fun getGallerySaver(): GallerySaver = DesktopGallerySaver
