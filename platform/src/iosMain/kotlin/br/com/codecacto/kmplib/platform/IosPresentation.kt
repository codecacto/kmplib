package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIViewControllerTransitionCoordinatorContextProtocol
import platform.UIKit.transitionCoordinator
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

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

/**
 * O mesmo [topViewController], para os outros módulos da lib (2.332.0 — o `kmplib-ui` apresenta a
 * câmera da folha de anexo daqui, em vez de do `rootViewController`, que não abre nada quando já há
 * algo por cima). Não é contrato para app.
 */
@KmpLibPlatformInternalApi
fun kmplibTopViewController(): UIViewController? = topViewController()

/**
 * Apresenta [controller] por cima de tudo — **esperando sair de cena** o que estiver fechando (2.343.0).
 *
 * O caso que originou (LocAki, iPhone, 04/nov/2026): na folha de anexo, "Galeria" fechava a folha e, no mesmo
 * toque, apresentava o `PHPickerViewController` a partir do `rootViewController`. Com a folha ainda por cima (ou
 * saindo), o UIKit recusa a apresentação e só loga "already presenting" — **o toque não fazia nada**. O mesmo
 * vale para a ação de uma `UIAlertController` (o handler roda com o alerta ainda fechando).
 *
 * Regra: apresenta do controlador no TOPO ([topViewController]); se há um apresentado SAINDO de cena, espera a
 * transição dele terminar (`transitionCoordinator`, ou 300 ms quando não há coordenador) e tenta de novo, até
 * [TENTATIVAS] vezes. Sem janela, chama [onUnavailable].
 */
@KmpLibPlatformInternalApi
fun kmplibPresent(controller: UIViewController, onUnavailable: () -> Unit = {}) {
    apresentarQuandoPuder(controller, onUnavailable, tentativa = 0)
}

/** O mesmo [kmplibPresent], para os seletores deste módulo. */
internal fun apresentarQuandoPuder(controller: UIViewController, onUnavailable: () -> Unit = {}) {
    apresentarQuandoPuder(controller, onUnavailable, tentativa = 0)
}

private const val TENTATIVAS = 4
private const val ESPERA_NS = 300_000_000L
private const val TAG_APRESENTACAO = "KmpLibPresent"

private fun apresentarQuandoPuder(controller: UIViewController, onUnavailable: () -> Unit, tentativa: Int) {
    val topo = topViewController()
    if (topo == null) {
        AppLogger.w(TAG_APRESENTACAO, "Nenhuma janela ativa: nada apresentado.")
        onUnavailable()
        return
    }
    val saindo = topo.presentedViewController?.takeIf { it.isBeingDismissed() }
    if (saindo != null && tentativa < TENTATIVAS) {
        val coordenador = saindo.transitionCoordinator
        if (coordenador != null) {
            coordenador.animateAlongsideTransition(null) { _: UIViewControllerTransitionCoordinatorContextProtocol? ->
                apresentarQuandoPuder(controller, onUnavailable, tentativa + 1)
            }
        } else {
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, ESPERA_NS), dispatch_get_main_queue()) {
                apresentarQuandoPuder(controller, onUnavailable, tentativa + 1)
            }
        }
        return
    }
    topo.presentViewController(controller, animated = true, completion = null)
}
