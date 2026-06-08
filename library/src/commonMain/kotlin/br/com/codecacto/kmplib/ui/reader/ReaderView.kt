package br.com.codecacto.kmplib.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.ui.tooling.preview.Preview
import br.com.codecacto.kmplib.ui.theme.AppTheme

/**
 * Tamanho de fonte do corpo de leitura, em passos de acessibilidade.
 *
 * Cada passo corresponde a um fator de escala ([scale]) aplicado **apenas** ao corpo de
 * leitura (não à UI ao redor). A faixa vai de [SMALL] (0.85×) a [EXTRA_LARGE] (1.6×),
 * cobrindo desde leitura compacta até baixa visão.
 *
 * Use [next]/[previous] para implementar controles A−/A+ e [scale] para persistir a
 * preferência (ex.: `AppPreferences`).
 */
enum class ReaderFontSize(val scale: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.3f),
    EXTRA_LARGE(1.6f);

    /** Próximo passo maior; satura em [EXTRA_LARGE]. */
    fun next(): ReaderFontSize = entries.getOrElse(ordinal + 1) { this }

    /** Passo anterior menor; satura em [SMALL]. */
    fun previous(): ReaderFontSize = entries.getOrElse(ordinal - 1) { this }

    companion object {
        /** Resolve um fator livre (0.85f–1.6f) para o passo mais próximo. */
        fun fromScale(scale: Float): ReaderFontSize =
            entries.minBy { kotlin.math.abs(it.scale - scale) }
    }
}

/**
 * Um bloco de texto exibido pelo [ReaderView].
 *
 * Genérico por construção (não acopla a "Salmo", "capítulo" etc.): um bloco é apenas um
 * trecho de corpo com numeração discreta opcional.
 *
 * @property text Conteúdo textual do bloco.
 * @property number Numeração discreta (ex.: nº do versículo/parágrafo). `null` oculta o número
 *   só neste bloco; a exibição global ainda é controlada por `showNumbers` no [ReaderView].
 */
data class ReaderBlock(
    val text: String,
    val number: Int? = null
)

/**
 * Converte uma lista de pares `(número, texto)` em [ReaderBlock]s — atalho para conteúdos
 * já modelados como versículos numerados.
 */
fun List<Pair<Int, String>>.toReaderBlocks(): List<ReaderBlock> =
    map { (number, text) -> ReaderBlock(text = text, number = number) }

/**
 * Leitor de texto longo devocional/editorial, reutilizável por qualquer app de leitura.
 *
 * Renderiza uma lista de [ReaderBlock] (ex.: versículos numerados) com tipografia de leitura
 * serena: corpo serifado opcional, altura de linha generosa e medida de linha limitada no
 * modo largo (tablet/landscape) para conforto (~60–72 caracteres). Suporta tema claro/escuro
 * automaticamente via `MaterialTheme.colorScheme` (qualquer paleta de flavor do `AppTheme`).
 *
 * Características:
 * - **Fonte ajustável:** [fontSize] escala apenas o corpo (diferencial de acessibilidade).
 * - **Seleção de trecho:** todo o conteúdo fica dentro de um [SelectionContainer], permitindo
 *   copiar versículos (alimenta, por exemplo, um gerador de card de compartilhamento).
 * - **Numeração discreta:** [showNumbers] liga/desliga o número do bloco, exibido como rótulo
 *   leading sobrescrito e esmaecido (cor `onSurfaceVariant`), sem competir com o texto.
 * - **Responsivo:** [maxContentWidth] limita a largura da coluna de leitura no modo largo;
 *   no compacto ocupa a largura total com margens de leitura.
 *
 * NÃO acopla a domínio: passe [ReaderBlock]s (ou use [toReaderBlocks] sobre `List<Pair<Int,String>>`).
 *
 * Exemplo (leitura de um salmo):
 * ```kotlin
 * ReaderView(
 *     blocks = salmo.versiculos.map { ReaderBlock(it.texto, it.numero) },
 *     title = "Salmo 23",
 *     subtitle = "Um salmo de Davi",
 *     footer = "Edição: Almeida ACF",
 *     fontSize = fontePreferida,        // de AppPreferences
 *     showNumbers = true,
 *     bodyFontFamily = serifFontFamily  // família serifada do flavor
 * )
 * ```
 *
 * @param blocks Blocos de corpo a renderizar, na ordem de leitura.
 * @param modifier Modificador externo aplicado ao container.
 * @param title Título opcional (ex.: número/nome). Tipografia de headline serifada.
 * @param subtitle Subtítulo/atribuição opcional (ex.: "Um salmo de Davi").
 * @param footer Rodapé opcional discreto (ex.: crédito da edição).
 * @param fontSize Passo de fonte do corpo; escala apenas o texto de leitura.
 * @param showNumbers Se `true`, exibe a numeração leading dos blocos que tiverem `number`.
 * @param bodyFontFamily Família do corpo de leitura (serifada recomendada); `null` usa a do tema.
 * @param titleFontFamily Família dos títulos; `null` usa [bodyFontFamily] (ou a do tema).
 * @param maxContentWidth Largura máxima da coluna de leitura no modo largo (conforto de medida).
 * @param contentPadding Padding interno da lista de leitura.
 * @param listState Estado da [LazyColumn], para controlar/observar scroll (ex.: voltar ao topo
 *   ao trocar de texto).
 */
@Composable
fun ReaderView(
    blocks: List<ReaderBlock>,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    footer: String? = null,
    fontSize: ReaderFontSize = ReaderFontSize.MEDIUM,
    showNumbers: Boolean = true,
    bodyFontFamily: FontFamily? = null,
    titleFontFamily: FontFamily? = bodyFontFamily,
    maxContentWidth: Int = 640,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    listState: LazyListState = rememberLazyListState()
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    // Estilo do corpo: parte do bodyLarge do tema, aplica escala de fonte e altura de linha
    // generosa (~1.6) para leitura confortável e prolongada.
    val bodyBase = typography.bodyLarge
    val bodyStyle: TextStyle = bodyBase.copy(
        fontFamily = bodyFontFamily ?: bodyBase.fontFamily,
        fontSize = bodyBase.fontSize * fontSize.scale,
        lineHeight = (bodyBase.fontSize.value * fontSize.scale * 1.6f).sp,
        color = colors.onBackground,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None
        )
    )

    // Número do versículo: discreto, esmaecido, levemente menor que o corpo (superíndice/leading).
    val numberStyle: TextStyle = bodyStyle.copy(
        fontSize = bodyStyle.fontSize * 0.7f,
        fontWeight = FontWeight.Medium,
        color = colors.onSurfaceVariant,
        lineHeight = bodyStyle.lineHeight
    )

    val titleStyle: TextStyle = typography.headlineSmall.copy(
        fontFamily = titleFontFamily ?: bodyFontFamily ?: typography.headlineSmall.fontFamily,
        color = colors.onBackground
    )
    val subtitleStyle: TextStyle = typography.titleMedium.copy(
        fontFamily = titleFontFamily ?: bodyFontFamily ?: typography.titleMedium.fontFamily,
        color = colors.onSurfaceVariant
    )

    // A coluna de leitura é centralizada e limitada em largura no modo largo (medida confortável).
    val contentModifier = Modifier
        .fillMaxWidth()
        .widthIn(max = maxContentWidth.dp)

    SelectionContainer(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title != null || subtitle != null) {
                item(key = "reader-header") {
                    Column(modifier = contentModifier.padding(bottom = 16.dp)) {
                        if (title != null) {
                            Text(text = title, style = titleStyle)
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = subtitleStyle,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            items(
                count = blocks.size,
                key = { index -> blocks[index].number ?: "block-$index" }
            ) { index ->
                ReaderBlockRow(
                    block = blocks[index],
                    bodyStyle = bodyStyle,
                    numberStyle = numberStyle,
                    showNumbers = showNumbers,
                    modifier = contentModifier.padding(vertical = 6.dp)
                )
            }

            if (footer != null) {
                item(key = "reader-footer") {
                    Text(
                        text = footer,
                        style = typography.bodySmall.copy(color = colors.onSurfaceVariant),
                        modifier = contentModifier.padding(top = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBlockRow(
    block: ReaderBlock,
    bodyStyle: TextStyle,
    numberStyle: TextStyle,
    showNumbers: Boolean,
    modifier: Modifier = Modifier
) {
    val number = block.number
    if (showNumbers && number != null) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Numeração leading discreta, com largura mínima para alinhar o corpo.
            Text(
                text = number.toString(),
                style = numberStyle,
                modifier = Modifier.widthIn(min = 20.dp)
            )
            Text(
                text = block.text,
                style = bodyStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Box(modifier = modifier) {
            Text(text = block.text, style = bodyStyle)
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ReaderViewPreview() {
    AppTheme {
        ReaderView(
            blocks = listOf(
                ReaderBlock("O Senhor é o meu pastor; nada me faltará.", 1),
                ReaderBlock("Deitar-me faz em verdes pastos, guia-me mansamente a águas tranquilas.", 2),
                ReaderBlock("Refrigera a minha alma; guia-me pelas veredas da justiça, por amor do seu nome.", 3)
            ),
            title = "Salmo 23",
            subtitle = "Um salmo de Davi",
            footer = "Edição: Almeida ACF (domínio público)",
            fontSize = ReaderFontSize.MEDIUM
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ReaderViewLargeFontNoNumbersPreview() {
    AppTheme(darkTheme = true) {
        ReaderView(
            blocks = listOf(
                Pair(1, "Bem-aventurado o homem que não anda segundo o conselho dos ímpios."),
                Pair(2, "Antes tem o seu prazer na lei do Senhor, e na sua lei medita de dia e de noite.")
            ).toReaderBlocks(),
            title = "Salmo 1",
            fontSize = ReaderFontSize.EXTRA_LARGE,
            showNumbers = false
        )
    }
}
