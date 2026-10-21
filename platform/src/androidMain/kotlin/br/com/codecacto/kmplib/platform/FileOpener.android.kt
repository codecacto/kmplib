package br.com.codecacto.kmplib.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.audience.withParentalGate
import java.io.File
import java.io.FileOutputStream

/**
 * `ACTION_VIEW` sobre a URI do FileProvider da lib (`${applicationId}.fileprovider`, caminho
 * `cache/shared_files`), com `FLAG_GRANT_READ_URI_PERMISSION` — o caminho que a documentação do
 * Android indica para entregar um arquivo privado a outro app. O arquivo fica na pasta do
 * compartilhamento e sai pela mesma purga por idade ([ShareHandler.clearSharedFiles] /
 * [clearKmpLibTemporaryFiles]): o visualizador lê a URI depois que o `startActivity` volta, e o
 * Android não avisa quando ele termina.
 */
internal class AndroidFileOpener(private val context: Context) : FileOpener {

    override fun open(fileBytes: ByteArray, fileName: String, mimeType: String): FileOpenResult {
        var arquivo: File? = null
        return try {
            // Purga o resíduo antigo ANTES de gravar (o arquivo desta abertura é o mais novo).
            AndroidShareHandler(context).clearSharedFiles()
            val pasta = File(context.cacheDir, SHARED_FILES_DIRECTORY).apply { if (!exists()) mkdirs() }
            val file = File(pasta, sanitizeSharedFileName(fileName)).also { arquivo = it }
            FileOutputStream(file).use { it.write(fileBytes) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, normalizeOpenMimeType(mimeType))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val activity = ShareHandlerHolder.currentActivity()
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            FileOpenResult.Opened
        } catch (e: ActivityNotFoundException) {
            // Nenhum app abre este tipo: o arquivo não vai ser lido por ninguém — sai já.
            arquivo?.delete()
            FileOpenResult.NoAppAvailable
        } catch (e: Exception) {
            arquivo?.delete()
            // Só o tipo: o nome (e a mensagem do FileProvider, que traz o caminho) é dado pessoal.
            AppLogger.e(TAG, "Erro ao abrir arquivo: ${e::class.simpleName}")
            FileOpenResult.Failed
        }
    }

    private companion object {
        const val TAG = "FileOpener"
    }
}

actual fun getFileOpener(): FileOpener {
    // App de módulos granulares que chama só o `initKmpLibCore` (o LocAki) não preenche o
    // `ShareHandlerHolder`: o `Context` do core serve igual (a abertura só precisa do da aplicação).
    val context = ShareHandlerHolder.getContext() ?: AndroidAppContext.get()
        ?: throw IllegalStateException(
            "kmplib sem Context: chame initKmpLibCore(context) ou initKmpLibPlatform(context) no Application.onCreate()"
        )
    return AndroidFileOpener(context).withParentalGate()
}
