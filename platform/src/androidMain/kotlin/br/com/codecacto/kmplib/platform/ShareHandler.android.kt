package br.com.codecacto.kmplib.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * Holder para o contexto do Android.
 * Deve ser inicializado no Application.onCreate() ou MainActivity.
 */
object ShareHandlerHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

class AndroidShareHandler(private val context: Context) : ShareHandler {

    companion object {
        private const val TAG = "ShareHandler"
    }

    override fun shareText(text: String, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, title.ifEmpty { "Compartilhar" }).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Loga E RELANÇA: engolir a exceção fazia o chamador (ex.: ExportService)
            // registrar "sucesso" sem nada ter sido compartilhado.
            AppLogger.e(TAG, "Erro ao compartilhar texto", e)
            throw e
        }
    }

    override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) {
        shareFile(imageBytes, fileName, getMimeType(fileName), title)
    }

    override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) {
        try {
            // Purga ANTES de gravar: o arquivo deste share é o mais novo do diretório, então nunca é
            // vítima da própria limpeza — e o resíduo dos shares anteriores não fica para sempre.
            clearSharedFiles()

            val cacheDir = sharedFilesDir()
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Nome sanitizado: vem do chamador (às vezes de dado do usuário) e um separador
            // escreveria fora do diretório de compartilhamento.
            val file = File(cacheDir, sanitizeSharedFileName(fileName))
            FileOutputStream(file).use { it.write(fileBytes) }

            // Obtém URI via FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, title.ifEmpty { "Compartilhar" }).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Loga E RELANÇA. Antes, o catch engolia a IllegalArgumentException do
            // FileProvider.getUriForFile (provider não declarado) e o chamador recebia
            // "sucesso" sem nada compartilhado — quebrava todo compartilhamento de
            // arquivo no Android. Agora a falha propaga para o caller refletir erro.
            AppLogger.e(TAG, "Erro ao compartilhar arquivo: $fileName", e)
            throw e
        }
    }

    /**
     * Apaga o resíduo dos compartilhamentos anteriores.
     *
     * **Não** existe no Android callback de "o app receptor terminou de ler a URI" — o `ACTION_SEND`
     * é assíncrono e o receptor lê depois, às vezes com o nosso processo já morto. Por isso a
     * limpeza é por **idade** (e nunca "logo depois de disparar o chooser", que quebraria o share).
     */
    override fun clearSharedFiles(olderThanMillis: Long): Int {
        val dir = sharedFilesDir()
        val arquivos = dir.listFiles() ?: return 0
        val agora = System.currentTimeMillis()
        var apagados = 0
        arquivos.forEach { file ->
            if (!file.isFile) return@forEach
            if (!shouldPurgeSharedFile(file.lastModified(), agora, olderThanMillis)) return@forEach
            if (file.delete()) {
                apagados++
            } else {
                AppLogger.w(TAG, "não foi possível apagar o arquivo compartilhado ${file.name}")
            }
        }
        return apagados
    }

    private fun sharedFilesDir(): File = File(context.cacheDir, SHARED_FILES_DIRECTORY)

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

actual fun getShareHandler(): ShareHandler {
    val context = ShareHandlerHolder.getContext()
        ?: throw IllegalStateException("ShareHandlerHolder não foi inicializado. Chame ShareHandlerHolder.init(context) no Application.onCreate()")
    return AndroidShareHandler(context)
}
