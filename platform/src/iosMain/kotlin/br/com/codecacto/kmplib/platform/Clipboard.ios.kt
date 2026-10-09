package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly
import platform.UniformTypeIdentifiers.UTTypeUTF8PlainText

/**
 * iOS: `UIPasteboard.generalPasteboard`.
 *
 * O `label` do Android não tem equivalente aqui — o iOS mostra a própria prévia do sistema desde o
 * iOS 16 e não aceita rótulo do app. Ignorá-lo é o comportamento correto, não uma lacuna.
 *
 * Conteúdo sensível vai por `setItems(_:options:)`, a API que a Apple documenta para isso: o item
 * em `public.utf8-plain-text` com `UIPasteboardOptionLocalOnly` (fora do Handoff/Área de
 * Transferência Universal) e `UIPasteboardOptionExpirationDate` (o sistema apaga sozinho depois de
 * [SENSITIVE_CLIP_EXPIRATION_SECONDS]).
 */
class IosClipboard : Clipboard {

    override fun copy(text: String, label: String, sensitive: Boolean) {
        val pasteboard = UIPasteboard.generalPasteboard
        if (!sensitive) {
            pasteboard.string = text
            return
        }
        val localOnly = UIPasteboardOptionLocalOnly
        val expiration = UIPasteboardOptionExpirationDate
        if (localOnly == null || expiration == null) {
            // As duas constantes existem desde o iOS 10; nulas, só num runtime quebrado. Copiar sem
            // a proteção pediria um dado sensível na nuvem — não copia, e o log diz por quê.
            AppLogger.w(TAG, "Opções de área de transferência indisponíveis — texto sensível não copiado", null)
            return
        }
        pasteboard.setItems(
            items = listOf(mapOf(UTTypeUTF8PlainText.identifier to text)),
            options = mapOf(
                localOnly to true,
                expiration to NSDate.dateWithTimeIntervalSinceNow(SENSITIVE_CLIP_EXPIRATION_SECONDS.toDouble()),
            ),
        )
    }

    // `hasStrings` não dispara o aviso "App quer colar" do iOS 16+; ler `string` dispara.
    override fun hasText(): Boolean = UIPasteboard.generalPasteboard.hasStrings

    override fun readText(): String? = UIPasteboard.generalPasteboard.string?.takeIf { it.isNotEmpty() }

    private companion object {
        const val TAG = "Clipboard"
    }
}

actual fun getClipboard(): Clipboard = IosClipboard()
