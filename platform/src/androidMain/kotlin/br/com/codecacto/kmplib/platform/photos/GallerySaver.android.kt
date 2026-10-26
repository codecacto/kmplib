package br.com.codecacto.kmplib.platform.photos

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.platform.ShareHandlerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GallerySaver"
private const val COPY_BUFFER_BYTES = 256 * 1024

actual fun getGallerySaver(): GallerySaver = AndroidGallerySaver { ShareHandlerHolder.getContext() ?: AndroidAppContext.get() }

/** [GallerySaver] pelo `MediaStore` (Android 10+) ou pela pasta pública + `MediaScanner` (9 e anteriores). */
class AndroidGallerySaver(private val contextProvider: () -> Context?) : GallerySaver {

    override suspend fun save(path: String, fileName: String, mimeType: String, album: String?): GallerySaveResult =
        withContext(Dispatchers.IO) {
            val tipo = galleryMediaKindOf(mimeType) ?: return@withContext GallerySaveResult.UnsupportedType
            val context = contextProvider() ?: run {
                AppLogger.w(TAG, "initKmpLibPlatform não foi chamado — nada salvo")
                return@withContext GallerySaveResult.Failed
            }
            val origem = File(path)
            if (!origem.isFile || origem.length() <= 0L) return@withContext GallerySaveResult.Failed
            val pasta = sanitizeGalleryAlbum(album)
            val nome = fileName.substringAfterLast('/').ifBlank { origem.name }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                salvarNoMediaStore(context, origem, nome, mimeType, tipo, pasta)
            } else {
                salvarNaPastaPublica(context, origem, nome, mimeType, tipo, pasta)
            }
        }

    private fun salvarNoMediaStore(
        context: Context,
        origem: File,
        nome: String,
        mime: String,
        tipo: GalleryMediaKind,
        pasta: String?,
    ): GallerySaveResult {
        val resolver = context.contentResolver
        val colecao = when (tipo) {
            GalleryMediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            GalleryMediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val raiz = if (tipo == GalleryMediaKind.IMAGE) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (pasta != null) "$raiz/$pasta" else raiz)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(colecao, valores) }.getOrNull() ?: run {
            AppLogger.w(TAG, "MediaStore recusou a entrada nova")
            return GallerySaveResult.Failed
        }
        return try {
            val saida = resolver.openOutputStream(uri) ?: error("sem saída")
            saida.use { out -> origem.inputStream().use { it.copyTo(out, COPY_BUFFER_BYTES) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            GallerySaveResult.Saved
        } catch (e: Exception) {
            // Nada pela metade na galeria: a entrada pendente sai.
            runCatching { resolver.delete(uri, null, null) }
            AppLogger.w(TAG, "arquivo não gravou na galeria: ${e::class.simpleName}")
            GallerySaveResult.Failed
        }
    }

    @Suppress("DEPRECATION")
    private fun salvarNaPastaPublica(
        context: Context,
        origem: File,
        nome: String,
        mime: String,
        tipo: GalleryMediaKind,
        pasta: String?,
    ): GallerySaveResult {
        val concedida = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (!concedida) return GallerySaveResult.PermissionDenied
        return runCatching {
            val raiz = Environment.getExternalStoragePublicDirectory(
                if (tipo == GalleryMediaKind.IMAGE) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES,
            )
            val dir = (if (pasta != null) File(raiz, pasta) else raiz).apply { mkdirs() }
            val destino = semColisao(dir, nome)
            try {
                origem.inputStream().use { input -> destino.outputStream().use { input.copyTo(it, COPY_BUFFER_BYTES) } }
            } catch (e: Exception) {
                destino.delete()
                throw e
            }
            MediaScannerConnection.scanFile(context, arrayOf(destino.absolutePath), arrayOf(mime), null)
            GallerySaveResult.Saved as GallerySaveResult
        }.getOrElse {
            AppLogger.w(TAG, "arquivo não gravou na galeria: ${it::class.simpleName}")
            GallerySaveResult.Failed
        }
    }

    /** `foto.jpg`, `foto (1).jpg`… — nunca sobrescreve o que já está na galeria. */
    private fun semColisao(dir: File, nome: String): File {
        var f = File(dir, nome)
        if (!f.exists()) return f
        val base = nome.substringBeforeLast('.', nome)
        val ext = nome.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (f.exists()) f = File(dir, "$base (${i++})$ext")
        return f
    }
}
