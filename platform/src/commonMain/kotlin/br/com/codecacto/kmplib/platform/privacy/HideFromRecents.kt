package br.com.codecacto.kmplib.platform.privacy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * **Esconde esta parte do app do seletor de recentes enquanto ela estiver na tela.**
 *
 * ```kotlin
 * // a tela inteira do app (o caso comum do "modo discreto")
 * HideFromRecents(enabled = ajustes.modoDiscreto)
 *
 * // ou só a tela que mostra o dado sensível
 * @Composable fun ListaDeMembros() {
 *     HideFromRecents()
 *     ...
 * }
 * ```
 *
 * A proteção cai sozinha ao sair da composição, como em [br.com.codecacto.kmplib.platform.KeepScreenOn]
 * e pelo mesmo motivo: versão imperativa solta é como se esquece o app protegido — ou, pior,
 * **desprotegido** — para sempre.
 *
 * **Aninhar é seguro.** Duas telas pedindo ao mesmo tempo contam como duas: a proteção só cai
 * quando a última sair. Sem essa contagem, fechar a tela de detalhe desligaria o `FLAG_SECURE`
 * que a tela de lista, ainda viva atrás dela, tinha pedido.
 *
 * @param enabled `false` não protege nada e não desprotege o que outra tela pediu.
 */
@Composable
fun HideFromRecents(enabled: Boolean = true) {
    val screen = remember { getPrivacyScreen() }
    DisposableEffect(screen, enabled) {
        if (enabled) hideFromRecentsRequests.acquire(screen)
        onDispose { if (enabled) hideFromRecentsRequests.release(screen) }
    }
}

/**
 * Contador de pedidos simultâneos de [HideFromRecents]. Único no app, porque a janela é uma só.
 */
internal val hideFromRecentsRequests = HideFromRecentsRequests()

/**
 * Contagem por referência dos pedidos de ocultação.
 *
 * A regra é a mesma do `start()`/`stop()` do `ConnectivityObserver`: quem sai não desliga o que
 * outro ainda está usando. `release()` a mais é ignorado (nunca fica negativo) — devolver o estado
 * "descoberto" por engano é o único erro que importa aqui.
 */
internal class HideFromRecentsRequests {

    private var count: Int = 0

    /** Quantos pedidos estão de pé. Visível para o teste. */
    val activeRequests: Int get() = count

    fun acquire(screen: PrivacyScreen) {
        count += 1
        if (count == 1) screen.setHidden(true)
    }

    fun release(screen: PrivacyScreen) {
        if (count == 0) return
        count -= 1
        if (count == 0) screen.setHidden(false)
    }
}
