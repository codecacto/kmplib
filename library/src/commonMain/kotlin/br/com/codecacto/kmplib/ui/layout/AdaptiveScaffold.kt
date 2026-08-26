package br.com.codecacto.kmplib.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass

/**
 * Um destino de navegação do [AdaptiveScaffold].
 *
 * `selectedIcon`/`unselectedIcon` separados porque **estado nunca é só cor** (WCAG 1.4.1): o ativo é
 * preenchido, o inativo é vazado.
 */
data class AdaptiveDestination(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * Chassi que **troca a navegação de lugar** conforme a classe de janela — não a estica.
 *
 * | Classe | Navegação | Por quê |
 * |---|---|---|
 * | COMPACTA | barra inferior | alvo no alcance do polegar |
 * | MÉDIA | navigation rail (ícone + rótulo curto) | barra inferior num tablet põe o alvo a 25 cm do polegar |
 * | EXPANDIDA | rail **largo**, com rótulo ao lado do ícone | há largura sobrando; esconder o rótulo é desperdício |
 *
 * ## Por que isto não é "responsivo"
 *
 * Responsivo é a mesma árvore com outro `padding`. Aqui a barra inferior **deixa de existir** e o
 * conteúdo passa a dividir a tela na horizontal — é outra composição, com outra ordem de foco e
 * outro alvo de toque. Foi exatamente essa diferença que o fundador pediu em 16/ago/2026:
 * *"não só expandir e deixar responsivo… eu quero um layout próprio pra tablet e pra celulares"*.
 *
 * ## O que este componente NÃO faz
 *
 * Ele não decide o conteúdo. Duas colunas de conteúdo são [ListDetailScaffold] — separado de
 * propósito, porque nem toda tela de tablet é mestre-detalhe (uma leitura longa, por exemplo, quer
 * uma coluna centralizada e larga, não duas).
 */
@Composable
fun AdaptiveScaffold(
    destinations: List<AdaptiveDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    windowSizeClass: WindowSizeClass = LocalWindowSizeClass.current,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (!windowSizeClass.temNavegacaoLateral) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = {
                AdaptiveBottomBar(destinations, selectedId, onSelect)
            },
            content = content,
        )
        return
    }

    // Tablet: a navegação vira coluna à esquerda e o conteúdo ocupa o resto. O `Row` externo é o que
    // faz o rail e o conteúdo dividirem a ALTURA — num `Scaffold` com `bottomBar` eles dividiriam a
    // vertical, que é o layout de telefone esticado.
    Row(modifier = modifier.fillMaxSize()) {
        AdaptiveRail(
            destinations = destinations,
            selectedId = selectedId,
            onSelect = onSelect,
            largo = windowSizeClass == WindowSizeClass.EXPANDIDA,
        )
        Scaffold(
            modifier = Modifier.fillMaxHeight().weight(1f),
            topBar = topBar,
            content = content,
        )
    }
}

@Composable
private fun AdaptiveBottomBar(
    destinations: List<AdaptiveDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        destinations.forEach { destino ->
            val ativo = destino.id == selectedId
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = ativo,
                        role = Role.Tab,
                        onClick = { onSelect(destino.id) },
                    )
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = if (ativo) destino.selectedIcon else destino.unselectedIcon,
                    contentDescription = null,
                    tint = corDoItem(ativo),
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = destino.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = corDoItem(ativo),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A coluna de navegação do tablet.
 *
 * `largo = true` (EXPANDIDA) põe o rótulo **ao lado** do ícone; em MÉDIA ele fica embaixo, com a
 * coluna estreita. A diferença não é estética: numa tela de 1280dp, uma coluna de 80dp com rótulo
 * espremido desperdiça a largura que existe justamente para isso.
 */
@Composable
private fun AdaptiveRail(
    destinations: List<AdaptiveDestination>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    largo: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = if (largo) 220.dp else 88.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            // Rolagem própria: com muitos destinos num tablet em retrato, a coluna precisa rolar
            // DENTRO de si mesma — sem isso o último item fica inalcançável.
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = if (largo) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        destinations.forEach { destino ->
            val ativo = destino.id == selectedId
            val fundo = if (ativo) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }

            if (largo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(fundo)
                        .selectable(
                            selected = ativo,
                            role = Role.Tab,
                            onClick = { onSelect(destino.id) },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (ativo) destino.selectedIcon else destino.unselectedIcon,
                        contentDescription = null,
                        tint = corDoItem(ativo),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = destino.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = corDoItem(ativo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(fundo)
                        .selectable(
                            selected = ativo,
                            role = Role.Tab,
                            onClick = { onSelect(destino.id) },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (ativo) destino.selectedIcon else destino.unselectedIcon,
                        contentDescription = null,
                        tint = corDoItem(ativo),
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = destino.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = corDoItem(ativo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun corDoItem(ativo: Boolean) = if (ativo) {
    MaterialTheme.colorScheme.onSecondaryContainer
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * O que um painel do [ListDetailScaffold] sabe sobre **onde** está sendo desenhado.
 *
 * Escopo de receptor (`ColumnScope`, `LazyItemScope`, `ThreePaneScaffoldScope` do próprio AndroidX)
 * é a forma que o Compose usa para um container informar seus slots — e aqui ela é a **única**
 * forma que não quebra ninguém: um parâmetro `(Boolean) -> Unit` numa sobrecarga nova deixaria toda
 * chamada existente (`lista = { … }`, sem parâmetro declarado) **ambígua**, e ~20 apps parariam de
 * compilar. O escopo entra sem tocar em nenhuma chamada.
 */
@Immutable
interface ListDetailPaneScope {

    /**
     * `true` quando este painel é a **tela inteira** — em [WindowSizeClass.COMPACTA] e em
     * [WindowSizeClass.MEDIA].
     *
     * É aí, e só aí, que o detalhe precisa oferecer o caminho de volta.
     */
    val emPainelUnico: Boolean
}

private object PainelUnicoScope : ListDetailPaneScope {
    override val emPainelUnico: Boolean = true
}

private object DoisPaineisScope : ListDetailPaneScope {
    override val emPainelUnico: Boolean = false
}

/**
 * Mestre-detalhe adaptativo — **dois painéis** quando cabe, uma pilha de navegação quando não cabe.
 *
 * ## O ponto que faz isto funcionar
 *
 * Em COMPACTA e MÉDIA, o detalhe **substitui** a lista; em EXPANDIDA os dois convivem. Mas o
 * `selecionado` é o MESMO estado nos dois casos — quem chama guarda um id, não "estou na tela de
 * detalhe". É isso que faz a rotação retrato↔paisagem preservar a seleção em vez de voltar para a
 * lista, que é o defeito clássico de mestre-detalhe feito com duas rotas.
 *
 * ## Por que os slots ganharam [ListDetailPaneScope] (GAP-NCX-T-04, 2.151.0)
 *
 * O componente **sabe** se está em painel único; até a 2.150.x ele não contava. Quem consome
 * precisava recalcular a regra por fora só para decidir se o detalhe leva seta de voltar — e a
 * regra tem duas formas parecidas, uma certa e uma errada:
 *
 * - `!classe.temDoisPaineis` ✅ (COMPACTA **e** MÉDIA são painel único)
 * - `classe == WindowSizeClass.COMPACTA` ❌ (esquece o tablet em RETRATO)
 *
 * Com a segunda, o detalhe de um tablet em retrato nasce **sem seta de voltar** e a pessoa fica
 * presa nele ao girar o aparelho — foi o que aconteceu no NeuroCoreX. Todo app com mestre-detalhe
 * reescreveria essa linha, com uma chance em duas de errar igual; agora o valor chega pronto, em
 * [ListDetailPaneScope.emPainelUnico].
 *
 * **Nada muda para quem já consumia**: os slots continuam sendo lambdas sem parâmetro, e quem não
 * lê `emPainelUnico` não precisa saber que ele existe.
 *
 * ```kotlin
 * ListDetailScaffold(
 *     temSelecao = idSelecionado != null,
 *     lista = { ListaDeItens(onSelecionar = { idSelecionado = it }) },
 *     detalhe = {
 *         Detalhe(
 *             item = idSelecionado,
 *             // A seta de voltar só existe quando o detalhe SUBSTITUIU a lista.
 *             onVoltar = if (emPainelUnico) ({ idSelecionado = null }) else null,
 *         )
 *     },
 *     vazio = { EmptyState(...) },   // só aparece em dois painéis, quando nada foi escolhido
 * )
 * ```
 *
 * @param lista o painel mestre.
 * @param detalhe o painel de detalhe.
 * @param vazio o que mostrar no painel de detalhe antes de escolher algo. Só existe em dois
 *   painéis — logo o `emPainelUnico` dele é sempre `false`.
 * @param fracaoDaLista fração da largura que a lista ocupa em dois painéis.
 */
@Composable
fun ListDetailScaffold(
    temSelecao: Boolean,
    lista: @Composable ListDetailPaneScope.() -> Unit,
    detalhe: @Composable ListDetailPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    vazio: @Composable ListDetailPaneScope.() -> Unit = {},
    fracaoDaLista: Float = 0.38f,
    windowSizeClass: WindowSizeClass = LocalWindowSizeClass.current,
) {
    // Painel único é `!temDoisPaineis` — COMPACTA **e** MÉDIA. Escrever `== COMPACTA` aqui é o
    // defeito que este parâmetro existe para nunca mais ser reescrito por quem chama.
    if (!windowSizeClass.temDoisPaineis) {
        // O detalhe SUBSTITUI a lista. Quem controla a volta continua sendo o chamador (só ele sabe
        // o que "voltar" significa ali) — o que a lib faz é dizer QUANDO ela é necessária.
        Box(modifier = modifier.fillMaxSize()) {
            if (temSelecao) PainelUnicoScope.detalhe() else PainelUnicoScope.lista()
        }
        return
    }

    Row(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxHeight().weight(fracaoDaLista)) { DoisPaineisScope.lista() }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(modifier = Modifier.fillMaxHeight().weight(1f - fracaoDaLista)) {
            if (temSelecao) DoisPaineisScope.detalhe() else DoisPaineisScope.vazio()
        }
    }
}
