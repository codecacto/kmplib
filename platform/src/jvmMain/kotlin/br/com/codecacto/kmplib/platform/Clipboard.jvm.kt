package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Desktop (2.353.0): área de transferência do SO (`Toolkit.systemClipboard`).
 *
 * `sensitive = true`: o texto é APAGADO depois de [SENSITIVE_CLIP_EXPIRATION_SECONDS] se ainda for
 * o mesmo (a mesma janela do iOS). A JVM não tem como marcar o item fora do histórico da área de
 * transferência do Windows (Win+V) — ver lacunas do catálogo.
 */
internal class DesktopClipboard : Clipboard {

    private fun clipboard(): java.awt.datatransfer.Clipboard? =
        if (GraphicsEnvironment.isHeadless()) null else runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull()

    override fun copy(text: String, label: String, sensitive: Boolean) {
        val cb = clipboard() ?: return AppLogger.w(TAG, "área de transferência indisponível")
        runCatching { cb.setContents(StringSelection(text), null) }
            .onFailure { AppLogger.w(TAG, "falha ao copiar: ${it::class.simpleName}") }
            .onSuccess {
                if (sensitive) {
                    expirer.schedule({
                        runCatching { if (readText() == text) cb.setContents(StringSelection(""), null) }
                    }, SENSITIVE_CLIP_EXPIRATION_SECONDS, TimeUnit.SECONDS)
                }
            }
    }

    override fun hasText(): Boolean =
        runCatching { clipboard()?.isDataFlavorAvailable(DataFlavor.stringFlavor) == true }.getOrDefault(false)

    override fun readText(): String? =
        runCatching { clipboard()?.getData(DataFlavor.stringFlavor) as? String }.getOrNull()

    private companion object {
        const val TAG = "Clipboard"
        val expirer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "kmplib-clipboard").apply { isDaemon = true } }
    }
}

actual fun getClipboard(): Clipboard = DesktopClipboard()
