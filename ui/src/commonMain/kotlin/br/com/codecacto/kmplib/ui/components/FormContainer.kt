package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass

/**
 * Medidas de formulário — regra pura, fora do `@Composable` para ser testável sem árvore de
 * composição (mesmo motivo de `windowSizeClassFor`/`gridColumnsFor`).
 */
object FormDefaults {

    /**
     * Largura máxima de um formulário fora do telefone.
     *
     * **480dp não é a largura de leitura** (`leituraMaxWidth`, 640/720dp): campo de formulário é
     * mais estreito que texto corrido de propósito. Um `AppTextField` de 700dp para digitar um
     * e-mail obriga o olho a ir do rótulo, à esquerda, até o cursor lá na direita — e nenhum app
     * grande faz isso. É a mesma medida que a weblib usa no cartão de login.
     */
    val MaxContentWidth: Dp = 480.dp

    /**
     * Fração da largura que o painel de marca ocupa no login/cadastro em janela EXPANDIDA.
     *
     * 37% deixa o formulário com ~63% — em 1280dp são 806dp, folgado para os 480dp de campo mais o
     * respiro. Abaixo de um terço o painel vira uma tarja; acima da metade o formulário aperta.
     */
    const val BrandPanelFraction: Float = 0.37f

    /**
     * Teto de largura do conteúdo de um [FormContainer], por classe de janela.
     *
     * [Dp.Unspecified] em [WindowSizeClass.COMPACTA]: no telefone o teto nunca morde (a coluna útil
     * é ~354dp) e deixar sem restrição evita um `widthIn` inútil na hierarquia de todo formulário
     * da fábrica.
     */
    fun maxContentWidth(classe: WindowSizeClass): Dp = when (classe) {
        WindowSizeClass.COMPACTA -> Dp.Unspecified
        WindowSizeClass.MEDIA, WindowSizeClass.EXPANDIDA -> MaxContentWidth
    }
}

/**
 * Container for form screens that handles keyboard behavior properly:
 * - Scrolls content when keyboard appears (imePadding)
 * - Allows scrolling through form fields
 * - Dismisses keyboard when tapping outside input fields
 *
 * ## O teto de largura (GAP-NCX-T-01)
 *
 * O container **continua** `fillMaxSize` — quem pinta o fundo é a tela, e ele tem de ir de borda a
 * borda. Quem ganha teto é só a `Column` interna, centrada. Fazer o contrário (limitar o container)
 * é o que um app tentaria de fora, passando `Modifier.widthIn(...)`: encolheria o fundo junto e a
 * tela ficaria com uma faixa de 480dp de cor no meio de um fundo de outra cor.
 *
 * Até a 2.150.x não havia teto nenhum: num tablet em paisagem (1280dp) o campo de e-mail nascia com
 * 1184dp de largura, **em todo app da fábrica**.
 *
 * @param modifier Modifier to apply to the container
 * @param horizontalPadding Horizontal padding for the content (default: 24.dp)
 * @param verticalPadding Vertical padding for the content (default: 16.dp)
 * @param horizontalAlignment Horizontal alignment for the column items
 * @param verticalArrangement Vertical arrangement for the column items
 * @param maxContentWidth Teto de largura do conteúdo. [Dp.Unspecified] = sem teto.
 * @param content The form content
 */
@Composable
fun FormContainer(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
    verticalPadding: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    maxContentWidth: Dp = FormDefaults.maxContentWidth(LocalWindowSizeClass.current),
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
            .verticalScroll(scrollState),
        // Centraliza a coluna de conteúdo quando ela tem teto. Sem isto o formulário limitado
        // ficaria colado na borda esquerda de um tablet.
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            // `widthIn` ANTES de `fillMaxWidth`: invertido, o `fillMaxWidth` fixa a largura do pai
            // e o teto passa a não ter efeito nenhum — o defeito compila e some na revisão.
            modifier = Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement
        ) {
            content()
        }
    }
}
