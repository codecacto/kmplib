package br.com.codecacto.kmplib.platform

import platform.UIKit.UIPasteboard

/**
 * iOS: `UIPasteboard.generalPasteboard`.
 *
 * O `label` do Android não tem equivalente aqui — o iOS mostra a própria prévia do sistema desde o
 * iOS 16 e não aceita rótulo do app. Ignorá-lo é o comportamento correto, não uma lacuna.
 */
class IosClipboard : Clipboard {
    override fun copy(text: String, label: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}

actual fun getClipboard(): Clipboard = IosClipboard()
