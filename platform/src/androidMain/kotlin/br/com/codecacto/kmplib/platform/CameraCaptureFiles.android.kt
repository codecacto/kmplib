package br.com.codecacto.kmplib.platform

import android.content.Context
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.File

actual fun clearCameraCaptureFiles(olderThanMillis: Long): Int {
    val context = AndroidAppContext.get() ?: return 0
    return clearCameraCaptureFiles(context, olderThanMillis)
}

/** A pasta onde a câmera grava o original (`cacheDir/photos`). */
fun cameraCaptureDirectory(context: Context): File = File(context.cacheDir, CAMERA_CAPTURE_DIRECTORY)

/** Variante com `Context` explícito — a que o seletor de imagem usa antes de cada captura. */
fun clearCameraCaptureFiles(context: Context, olderThanMillis: Long): Int =
    runCatching {
        val agora = System.currentTimeMillis()
        var apagados = 0
        cameraCaptureDirectory(context).listFiles()
            ?.filter { it.isFile && shouldPurgeCameraCapture(it.name, it.lastModified(), agora, olderThanMillis) }
            ?.forEach { if (it.delete()) apagados++ }
        apagados
    }.getOrElse {
        AppLogger.w("CameraCaptureFiles", "Varredura dos originais da câmera falhou", it)
        0
    }
