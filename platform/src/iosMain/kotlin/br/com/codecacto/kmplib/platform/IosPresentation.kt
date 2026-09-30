package br.com.codecacto.kmplib.platform

import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * O controlador **no topo** da janela-chave da cena ativa — de onde se apresenta uma folha do
 * sistema (compartilhar, imprimir, salvar em Arquivos).
 *
 * - Janela pelas `connectedScenes` (cena em primeiro plano, `keyWindow` dela): `UIApplication.windows`
 *   está obsoleto desde o iOS 15, e o primeiro da lista nem sempre é a janela que o usuário vê.
 * - Segue `presentedViewController`: apresentar do `rootViewController` quando já há algo por cima
 *   (uma sheet, um diálogo) não abre nada — o UIKit só loga "already presenting".
 *
 * Extraído do `IosShareHandler` (2.195.0) na 2.225.0, para a impressão e o salvar usarem o mesmo.
 */
internal fun topViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val scene = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        ?: scenes.firstOrNull()
    val window = scene?.keyWindow
        ?: scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
        ?: scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull()
    var top = window?.rootViewController ?: return null
    while (true) {
        val next = top.presentedViewController ?: break
        // Não empilha em cima de um controlador que está saindo de cena.
        if (next.isBeingDismissed()) break
        top = next
    }
    return top
}
