package br.com.codecacto.kmplib.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Android: `ClipboardManager` do sistema.
 *
 * Reusa o contexto do [AndroidAppContext] de propósito — é o mesmo `Application` context, e um
 * segundo holder seria mais um passo de inicialização para o app esquecer (e descobrir em produção).
 *
 * Conteúdo sensível segue a orientação oficial do Android 13 ("Copy and paste" → *Mark sensitive
 * content*): `ClipDescription.extras` com `EXTRA_IS_SENSITIVE = true`, e a prévia do sistema troca
 * o texto por pontos. Abaixo da API 33 a constante não existe no SDK, mas a chave é a mesma string
 * — a documentação manda usá-la literal, e é o que [sensitiveClipExtraKey] faz.
 */
class AndroidClipboard(private val context: Context) : Clipboard {

    override fun copy(text: String, label: String, sensitive: Boolean) {
        try {
            val manager = manager() ?: run {
                AppLogger.w(TAG, "ClipboardManager indisponível — o texto não foi copiado", null)
                return
            }
            val clip = ClipData.newPlainText(label, text)
            if (sensitive) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(sensitiveClipExtraKey(Build.VERSION.SDK_INT), true)
                }
            }
            manager.setPrimaryClip(clip)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao copiar para a área de transferência", e)
        }
    }

    override fun hasText(): Boolean = try {
        val description = manager()?.primaryClipDescription
        description != null && (
            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            )
    } catch (e: Exception) {
        AppLogger.w(TAG, "Não foi possível consultar a área de transferência", e)
        false
    }

    override fun readText(): String? = try {
        val clip = manager()?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            null
        } else {
            clip.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        // SecurityException quando o app não tem foco (Android 10+): é "sem leitura", não erro.
        AppLogger.w(TAG, "Leitura da área de transferência negada ou falhou", e)
        null
    }

    private fun manager(): ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private companion object {
        const val TAG = "Clipboard"
    }
}

/** A chave do extra de conteúdo sensível — a constante do SDK na API 33+, a mesma string abaixo. */
internal fun sensitiveClipExtraKey(sdkInt: Int): String =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else LEGACY_IS_SENSITIVE_KEY

internal const val LEGACY_IS_SENSITIVE_KEY: String = "android.content.extra.IS_SENSITIVE"

actual fun getClipboard(): Clipboard {
    val context = AndroidAppContext.get()
        ?: throw IllegalStateException(
            "kmplib não foi inicializada. Chame initKmpLib(context) no Application.onCreate()",
        )
    return AndroidClipboard(context)
}
