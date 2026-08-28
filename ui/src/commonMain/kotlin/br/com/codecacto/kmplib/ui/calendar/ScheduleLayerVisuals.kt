package br.com.codecacto.kmplib.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppColors

/* ─────────────────── desenho da camada de destinação (fundo + amostra da legenda) ───────────────────
 * O padrão é desenhado por UMA função ([drawLayerPattern]), usada tanto pela faixa na grade quanto pela
 * amostra na legenda. Com duas implementações, a legenda mostraria uma textura e a grade outra — e a
 * legenda existe justamente para traduzir a grade.
 */

/**
 * Tinta de cada [LayerTone], resolvida pelo tema.
 *
 * Os cinco tons semânticos vêm dos tokens **semânticos** (`AppColors.success/warning/info`, `error`,
 * `onSurface`), **não** da cor de marca: se "Aluguel" saísse de `primary`, uma arena de marca vermelha
 * teria "Aluguel" e "Bloqueado" indistinguíveis. [LayerTone.Primary] e [LayerTone.Accent] são as duas
 * saídas para quem **quer** a cor do produto naquela destinação. Par web: `LAYER_TONE_INK` do
 * `layers.ts` (lá `neutral` é `var(--foreground)` e `accent` é `var(--accent)`; no Material 3 os
 * equivalentes são `onSurface` e `tertiary`).
 */
@Composable
fun layerToneColor(tone: LayerTone): Color = when (tone) {
    LayerTone.Neutral -> MaterialTheme.colorScheme.onSurface
    LayerTone.Info -> AppColors.current.info
    LayerTone.Success -> AppColors.current.success
    LayerTone.Warning -> AppColors.current.warning
    LayerTone.Danger -> MaterialTheme.colorScheme.error
    LayerTone.Primary -> MaterialTheme.colorScheme.primary
    LayerTone.Accent -> MaterialTheme.colorScheme.tertiary
}

/**
 * Opacidade do preenchimento de uma faixa de destinação (par do `fill: 12` do `layerSurfaceStyle` da
 * weblib).
 *
 * Baixa de propósito: o fundo tem de dizer o que a faixa é **sem competir** com a ocupação desenhada
 * por cima, que é o que a pessoa foi ler. Nesta opacidade as linhas-guia de horário continuam visíveis
 * através da faixa.
 */
const val LAYER_FILL_ALPHA: Float = 0.12f

/**
 * Opacidade da textura (par do `ink: 26` da weblib). Mais forte que o preenchimento — é ela que carrega
 * a distinção quando a cor não basta (WCAG 1.4.1).
 */
const val LAYER_PATTERN_ALPHA: Float = 0.26f

/** Preenchimento da amostra da legenda (par do `fill: 22` do `layerSwatchStyle` da weblib). */
const val LAYER_SWATCH_FILL_ALPHA: Float = 0.22f

/** Tinta da amostra da legenda (par do `ink: 62` da weblib). */
const val LAYER_SWATCH_PATTERN_ALPHA: Float = 0.62f

/** Altura mínima para caber o rótulo dentro da faixa sem espremer. Abaixo disso, só a legenda nomeia. */
private val LAYER_LABEL_MIN_HEIGHT = 22.dp

/**
 * Desenha a textura de [pattern] em [color]. Fonte **única** do desenho (grade e legenda).
 *
 * As medidas escalam com a densidade da tela, então a trama tem a mesma aparência num telefone e num
 * tablet — trama em pixels fixos "fecha" numa tela densa e vira mancha sólida, que é o oposto do que
 * ela existe para fazer.
 */
internal fun DrawScope.drawLayerPattern(pattern: LayerPattern, color: Color) {
    when (pattern) {
        LayerPattern.Solid -> Unit

        LayerPattern.Dots -> {
            val step = 9f * density
            val radius = 1.3f * density
            var y = step / 2f
            var row = 0
            while (y < size.height) {
                // Linhas ímpares deslocadas: uma grade quadrada lê como listra vertical.
                val offsetX = if (row % 2 == 0) 0f else step / 2f
                var x = step / 2f + offsetX
                while (x < size.width) {
                    drawCircle(color = color, radius = radius, center = Offset(x, y))
                    x += step
                }
                y += step
                row++
            }
        }

        LayerPattern.Stripes -> drawDiagonals(color = color, gap = 11f * density, ascending = true)

        LayerPattern.Hatch -> {
            val gap = 13f * density
            drawDiagonals(color = color, gap = gap, ascending = true)
            drawDiagonals(color = color, gap = gap, ascending = false)
        }
    }
}

private fun DrawScope.drawDiagonals(color: Color, gap: Float, ascending: Boolean) {
    val stroke = 1.5f * density
    var x = -size.height
    while (x < size.width) {
        if (ascending) {
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = stroke,
            )
        } else {
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = stroke,
            )
        }
        x += gap
    }
}

/**
 * Uma faixa de destinação desenhada na coluna: preenchimento + textura + rótulo.
 *
 * O `clip` não é detalhe: as diagonais são traçadas propositalmente **fora** dos limites (é assim que
 * elas cobrem os cantos), e sem recorte elas vazariam sobre a faixa vizinha.
 */
@Composable
internal fun LayerBand(
    top: Dp,
    height: Dp,
    color: Color,
    pattern: LayerPattern,
    label: String?,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .offset(y = top)
            .fillMaxWidth()
            .height(height)
            .clip(RectangleShape)
            .background(color.copy(alpha = LAYER_FILL_ALPHA))
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        if (pattern != LayerPattern.Solid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLayerPattern(pattern, color.copy(alpha = LAYER_PATTERN_ALPHA))
            }
        }
        if (label != null && height >= LAYER_LABEL_MIN_HEIGHT) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Amostra quadrada de uma destinação (o "quadradinho" da legenda), com a MESMA textura da grade.
 *
 * Opacidade **maior** que a da faixa, de propósito (e igual à do `layerSwatchStyle` da weblib): a faixa
 * é grande e não pode competir com a ocupação; a amostra tem 16dp e, na opacidade da faixa, seria um
 * quadrado quase branco — a legenda deixaria de traduzir a única coisa que ela existe para traduzir, e
 * é justamente ali que a pessoa aprende a associar textura ↔ destinação.
 */
@Composable
internal fun LayerSwatch(
    color: Color,
    pattern: LayerPattern,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = LAYER_SWATCH_FILL_ALPHA))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(3.dp),
            ),
    ) {
        if (pattern != LayerPattern.Solid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLayerPattern(pattern, color.copy(alpha = LAYER_SWATCH_PATTERN_ALPHA))
            }
        }
    }
}
