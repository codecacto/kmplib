package br.com.codecacto.kmplib.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * O que a tela desenha **atrás** das barras do sistema — é disso que sai a cor dos ícones delas.
 *
 * @param statusBar cor de fundo sob a status bar (relógio, bateria, sinal).
 * @param navigationBar cor de fundo sob a barra de navegação (os três botões, a alça de gestos).
 */
data class SystemBarsBackground(
    val statusBar: Color,
    val navigationBar: Color = statusBar,
)

/**
 * `true` = os ícones da barra têm de ser **escuros** para aparecer sobre [background].
 *
 * A régua é a luminância do que está DE FATO atrás do ícone, e não o modo do aparelho: são duas
 * perguntas diferentes, e é a diferença entre elas que apaga o relógio (aparelho em modo escuro com
 * a tela de login clara → ícones claros sobre fundo claro; Meu Estacionamento, 02/out/2026).
 */
fun needsDarkSystemBarIcons(background: Color): Boolean = background.luminance() > 0.5f

/**
 * Mesma pergunta de [needsDarkSystemBarIcons], para uma barra que tem **cor própria**.
 *
 * Em edge-to-edge a barra é transparente e o que conta é o conteúdo. Fora dele (Android ≤ 14 sem
 * `enableEdgeToEdge()`), a barra é opaca na cor do tema da janela, e o ícone fica sobre ELA —
 * decidir pelo conteúdo ali escureceria o ícone em cima de uma barra preta. A película translúcida
 * (o *scrim* da navegação por três botões) é o caso do meio: vale a cor composta.
 */
fun needsDarkSystemBarIcons(barColor: Color, contentBackground: Color): Boolean =
    needsDarkSystemBarIcons(barColor.compositeOver(contentBackground.copy(alpha = 1f)))

/**
 * Pilha dos pedidos de aparência das barras — o último a ENTRAR vence, e sair devolve ao anterior.
 *
 * É o que permite o tema dizer "fundo claro" e uma tela por cima dele dizer "aqui é escuro" sem que
 * nenhum dos dois precise saber do outro: a tela sai da composição e o pedido do tema volta a
 * valer sozinho. Atualizar um pedido existente **não** muda a posição dele (o tema trocando de
 * claro para escuro com uma tela por cima não passa na frente dela).
 */
class SystemBarsRequests {
    private val entries = ArrayList<Pair<Any, SystemBarsBackground>>()

    /** O pedido que vale agora (`null` = ninguém pediu). Estado observável do Compose. */
    var current: SystemBarsBackground? by mutableStateOf(null)
        private set

    fun put(owner: Any, background: SystemBarsBackground) {
        val index = entries.indexOfFirst { it.first === owner }
        if (index >= 0) entries[index] = owner to background else entries.add(owner to background)
        current = entries.lastOrNull()?.second
    }

    fun remove(owner: Any) {
        entries.removeAll { it.first === owner }
        current = entries.lastOrNull()?.second
    }
}

/** Pilha do processo: os apps da fábrica têm uma Activity/uma janela de conteúdo. */
internal val ProcessSystemBarsRequests = SystemBarsRequests()

/**
 * Diz ao sistema o que esta parte da tela desenha atrás das barras, para os **ícones** delas
 * saírem legíveis (escuros sobre fundo claro, claros sobre fundo escuro).
 *
 * **O `AppTheme` já chama isto** com o fundo da paleta efetiva — app nenhum precisa lembrar. Chame
 * à mão só na tela que pinta um fundo DIFERENTE do tema atrás da barra: foto de capa, faixa de
 * marca no topo, tela com cores próprias. As telas de login e cadastro da lib já chamam. Enquanto a
 * tela está na composição o pedido dela vale; ao sair, volta o do tema.
 *
 * ```kotlin
 * // Tela com faixa verde-escura no topo e conteúdo claro embaixo:
 * SystemBarsAppearance(statusBarBackground = verdeEscuro, navigationBarBackground = fundoClaro)
 * ```
 *
 * App com tema próprio (sem `AppTheme`) chama uma vez na raiz do tema dele.
 *
 * ### Plataformas
 * - **Android:** `WindowInsetsControllerCompat.isAppearanceLightStatusBars`/`…NavigationBars` na
 *   janela da Activity — a API oficial. Vale com ou sem `enableEdgeToEdge()`: barra transparente
 *   decide pelo conteúdo, barra opaca decide pela cor dela.
 * - **iOS:** não faz nada, de propósito. A status bar do iOS segue o `userInterfaceStyle` da
 *   janela, e o único jeito de mudá-lo por código (`overrideUserInterfaceStyle`, o mesmo que o
 *   `.preferredColorScheme` do SwiftUI usa) muda também o que o `isSystemInDarkTheme()` do Compose
 *   devolve — um app que segue o sistema ficaria preso no modo forçado. No iOS a regra é a tela
 *   **seguir o tema** (é o que login e cadastro fazem por default desde a 2.241.0); app de tema fixo
 *   declara `UIUserInterfaceStyle` no `Info.plist`.
 */
@Composable
fun SystemBarsAppearance(
    statusBarBackground: Color,
    navigationBarBackground: Color = statusBarBackground,
) {
    val requests = ProcessSystemBarsRequests
    val owner = remember { Any() }
    val requested = SystemBarsBackground(statusBarBackground, navigationBarBackground)
    // `SideEffect` e não a composição: registra só o que foi de fato aplicado, na ordem em que as
    // telas entram (o tema, na raiz, entra primeiro e fica na base da pilha).
    SideEffect { requests.put(owner, requested) }
    DisposableEffect(owner) { onDispose { requests.remove(owner) } }
    // Todo chamador aplica o topo da pilha (idempotente): não depende de haver um `AppTheme` na raiz.
    ApplySystemBarsAppearance(requests.current ?: requested)
}

/** Aplica na plataforma. Android: ícones das barras; iOS: no-op (ver [SystemBarsAppearance]). */
@Composable
internal expect fun ApplySystemBarsAppearance(background: SystemBarsBackground)
