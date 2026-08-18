package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp

/**
 * Desenha o **trilho vertical** (o "fio" que liga os marcadores) ATRÁS do item de uma linha do
 * tempo — usado por [StepTimeline] e [TimelineList].
 *
 * ### Por que desenhar, e não empilhar `Box`es
 * A forma ingênua (um `Box` de altura fixa acima e outro abaixo do marcador) **não acompanha a
 * altura real do item**: uma etapa com título de três linhas fica mais alta que o fio, e a linha do
 * tempo aparece com buracos entre os marcadores; um item curto, ao contrário, estoura. Aqui o fio é
 * pintado no `drawBehind` do **próprio item**, então usa `size.height` — a altura já medida pelo
 * layout — e acompanha qualquer conteúdo, incluindo texto que quebra.
 *
 * A linha passa **por trás** do marcador (que é opaco e é desenhado por cima), então não há nenhuma
 * conta de "onde o círculo começa": basta o centro.
 *
 * ### Continuidade
 * Cada item pinta de [centerY] até a sua base e do topo até [centerY]. Isso só forma um fio contínuo
 * se os itens forem **encostados** — use padding interno no item, nunca `Arrangement.spacedBy` entre
 * eles, senão sobra um vão sem fio entre um marcador e o seguinte.
 *
 * @param color cor do fio (token do tema, ex.: `outlineVariant`).
 * @param centerX distância da borda esquerda do item até o **centro** do marcador.
 * @param centerY distância do topo do item até o **centro** do marcador.
 * @param strokeWidth espessura do fio.
 * @param drawAbove pinta o segmento acima do marcador (`false` no primeiro item).
 * @param drawBelow pinta o segmento abaixo do marcador (`false` no último item).
 */
internal fun Modifier.timelineConnector(
    color: Color,
    centerX: Dp,
    centerY: Dp,
    strokeWidth: Dp,
    drawAbove: Boolean,
    drawBelow: Boolean,
): Modifier = drawBehind {
    val x = centerX.toPx()
    val y = centerY.toPx()
    val stroke = strokeWidth.toPx()
    if (drawAbove) {
        drawLine(color, Offset(x, 0f), Offset(x, y), stroke, StrokeCap.Butt)
    }
    if (drawBelow && size.height > y) {
        drawLine(color, Offset(x, y), Offset(x, size.height), stroke, StrokeCap.Butt)
    }
}

/**
 * `true` quando o item pinta o segmento **acima** do marcador — todos, menos o primeiro. Lógica pura
 * — testável (é a regra que, errada, faz a linha do tempo "começar antes" do primeiro marco).
 */
internal fun timelineDrawsSegmentAbove(index: Int): Boolean = index > 0

/**
 * `true` quando o item pinta o segmento **abaixo** do marcador — todos, menos o último (e nunca numa
 * lista vazia). Lógica pura — testável.
 */
internal fun timelineDrawsSegmentBelow(index: Int, count: Int): Boolean = index in 0 until count - 1
