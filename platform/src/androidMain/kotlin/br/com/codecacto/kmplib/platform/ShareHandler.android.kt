package br.com.codecacto.kmplib.platform

import android.app.Activity
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
    private var activityRef: WeakReference<Activity>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    /**
     * A `Activity` em foco (via `kmpLibPlatformOnResume`, desde 2.195.0). Com ela o chooser abre
     * **na tarefa do app**, como a documentação do Android manda; sem ela o handler cai no
     * `applicationContext` + `FLAG_ACTIVITY_NEW_TASK`, que funciona mas abre a folha numa tarefa
     * separada (o "voltar" e a multitarefa se comportam como se fosse outro app).
     */
    internal fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    internal fun clearActivity() {
        activityRef = null
    }

    internal fun getContext(): Context? = contextRef?.get()

    internal fun currentActivity(): Activity? =
        activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
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
            }
            launchChooser(intent, title)
        } catch (e: Exception) {
            // Loga E RELANÇA: engolir a exceção fazia o chamador (ex.: ExportService)
            // registrar "sucesso" sem nada ter sido compartilhado.
            AppLogger.e(TAG, "Erro ao compartilhar texto", e)
            throw e
        }
    }

    /**
     * Como a documentação oficial ("Send simple data to other apps") recomenda: `ACTION_SEND`
     * `text/plain` com o texto em `EXTRA_TEXT`, e o título em `EXTRA_TITLE` — é ele que aparece
     * como prévia no topo da folha do Android 10+ (o título do chooser, sozinho, o sistema não
     * mostra mais). `EXTRA_SUBJECT` é o assunto quando o destino é um app de e-mail.
     */
    override fun shareLink(url: String, message: String, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, composeShareText(message, url))
                if (title.isNotBlank()) {
                    putExtra(Intent.EXTRA_TITLE, title)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }
            }
            launchChooser(intent, title)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao compartilhar link", e)
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
            }
            launchChooser(intent, title)
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

    /**
     * Abre o chooser do sistema a partir da `Activity` em foco, quando houver; senão, do
     * `applicationContext` com `FLAG_ACTIVITY_NEW_TASK` (obrigatório fora de uma Activity).
     */
    private fun launchChooser(intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title.ifEmpty { "Compartilhar" })
        val activity = ShareHandlerHolder.currentActivity()
        if (activity != null) {
            activity.startActivity(chooser)
        } else {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
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
