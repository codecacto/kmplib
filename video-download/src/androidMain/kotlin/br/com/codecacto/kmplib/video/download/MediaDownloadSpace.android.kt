package br.com.codecacto.kmplib.video.download

import android.os.StatFs
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.VideoPlayerHolder

/**
 * `StatFs` sobre o **`filesDir`**, e não sobre `Environment.getExternalStorageDirectory()`.
 *
 * A diferença aparece em aparelho com cartão: o "espaço livre do celular" que o usuário vê pode ser
 * o do cartão, enquanto a aula é gravada no armazenamento interno privado. Perguntar pelo volume
 * errado faz a conferência aprovar um download que não cabe.
 */
actual fun availableStorageBytes(): Long {
    val context = VideoPlayerHolder.getContext() ?: return 0L
    return runCatching {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrElse {
        AppLogger.w(DOWNLOAD_TAG, "Não foi possível ler o espaço livre: ${it.message}")
        0L
    }
}
