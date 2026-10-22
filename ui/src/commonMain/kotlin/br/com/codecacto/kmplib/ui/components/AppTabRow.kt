package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **Abas de largura igual com indicador fino em toda a aba** (2.318.0) — a faixa "Entrada · Saída ·
 * Lado a lado" e "Meus modelos · Modelos prontos" do Laudo Kit.
 *
 * Construída sobre o `SecondaryTabRow` do Material 3 (o indicador do M3 que ocupa a largura da aba
 * inteira, 2 dp), e não desenhada à mão: semântica de aba (`Role.Tab`, `selected`), foco por teclado
 * e animação do indicador vêm do componente oficial. O que a lib acrescenta é a forma do protótipo:
 * altura própria, ativa na cor da marca em peso 600, inativa no texto secundário em peso 400, uma
 * divisória de 1 dp embaixo e **um id por aba** (`<testTag>-<chave>`, como o `SegmentedControl`).
 *
 * Quando usar: 2 a 4 destinos irmãos de uma mesma tela, rótulos curtos. Para escolher um VALOR
 * (filtro, modo) use [SegmentedControl]; para navegação principal, a barra inferior.
 *
 * @param tabs os rótulos, na ordem.
 * @param selectedIndex a aba ativa (grampeada em `0..tabs.lastIndex`).
 * @param onSelect toque numa aba (inclusive a ativa — quem chama decide se ignora).
 * @param height altura da faixa. Default 44 dp (o kit usa 44 e 46).
 * @param indicatorHeight espessura do indicador. Default 2 dp.
 * @param selectedColor cor do rótulo e do indicador da aba ativa. Default: `primary` do tema.
 * @param unselectedColor cor das demais. Default: `onSurfaceVariant`.
 * @param dividerColor a linha de 1 dp sob a faixa; `Color.Unspecified` = sem divisória.
 * @param textStyle tipografia dos rótulos (o peso é trocado para 600 na ativa).
 * @param testTag prefixo dos ids; cada aba vira `<testTag>-<chave>` (ver [OptionTestTags]).
 * @param optionTestKeys chaves estáveis por aba (sem elas, o índice).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = AppTabRowDefaults.Height,
    indicatorHeight: Dp = AppTabRowDefaults.IndicatorHeight,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = Color.Transparent,
    dividerColor: Color = MaterialTheme.colorScheme.outline,
    textStyle: TextStyle = TextStyle(fontSize = 15.sp),
    testTag: String = AppTabRowDefaults.TEST_TAG,
    optionTestKeys: List<String>? = null,
) {
    if (tabs.isEmpty()) return
    val ativo = appTabRowSelectedIndex(selectedIndex, tabs.size)
    val ids = OptionTestTags.options(testTag, tabs.size, optionTestKeys)
    SecondaryTabRow(
        selectedTabIndex = ativo,
        modifier = modifier.testTag(testTag),
        containerColor = containerColor,
        contentColor = selectedColor,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(ativo, matchContentSize = false),
                height = indicatorHeight,
                color = selectedColor,
            )
        },
        divider = {
            if (dividerColor != Color.Unspecified) HorizontalDivider(thickness = 1.dp, color = dividerColor)
        },
    ) {
        tabs.forEachIndexed { i, rotulo ->
            val selecionada = i == ativo
            Tab(
                selected = selecionada,
                onClick = { onSelect(i) },
                modifier = Modifier.height(height).testTag(ids[i]),
                selectedContentColor = selectedColor,
                unselectedContentColor = unselectedColor,
                text = {
                    Text(
                        text = rotulo,
                        style = textStyle,
                        fontWeight = if (selecionada) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Medidas do [AppTabRow]. */
object AppTabRowDefaults {
    val Height: Dp = 44.dp
    val IndicatorHeight: Dp = 2.dp
    const val TEST_TAG: String = "abas"
}

/** A aba ativa válida para [count] abas (fora da faixa vira a mais próxima). Pura, para teste. */
fun appTabRowSelectedIndex(selectedIndex: Int, count: Int): Int =
    if (count <= 0) 0 else selectedIndex.coerceIn(0, count - 1)
