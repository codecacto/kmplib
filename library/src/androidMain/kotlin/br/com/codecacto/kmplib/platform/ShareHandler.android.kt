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
            // Salva arquivo no cache
            val cacheDir = File(context.cacheDir, "shared_files")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val file = File(cacheDir, fileName)
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
