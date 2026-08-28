package br.com.codecacto.kmplib.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Android: `ClipboardManager` do sistema.
 *
 * Reusa o contexto do [AndroidAppContext] de propósito — é o mesmo `Application` context, e um
 * segundo holder seria mais um passo de inicialização para o app esquecer (e descobrir em produção).
 */
class AndroidClipboard(private val context: Context) : Clipboard {

    override fun copy(text: String, label: String) {
        try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (manager == null) {
                AppLogger.w(TAG, "ClipboardManager indisponível — o texto não foi copiado", null)
                return
            }
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao copiar para a área de transferência", e)
        }
    }

    private companion object {
        const val TAG = "Clipboard"
    }
}

actual fun getClipboard(): Clipboard {
    val context = AndroidAppContext.get()
        ?: throw IllegalStateException(
            "kmplib não foi inicializada. Chame initKmpLib(context) no Application.onCreate()",
        )
    return AndroidClipboard(context)
}
