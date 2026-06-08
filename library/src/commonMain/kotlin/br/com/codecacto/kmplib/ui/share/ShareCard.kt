package br.com.codecacto.kmplib.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Proporção do card devocional de compartilhamento.
 *
 * - [STORIES] 9:16 — Instagram/Facebook Stories, WhatsApp Status.
 * - [SQUARE] 1:1 — post de feed.
 */
enum class ShareCardFormat(val widthRatio: Float, val heightRatio: Float) {
    STORIES(9f, 16f),
    SQUARE(1f, 1f);

    /** Razão largura/altura (para `Modifier.aspectRatio`). */
    val aspectRatio: Float get() = widthRatio / heightRatio

    /** Tamanho em pixels para o render off-screen, dada a largura-alvo. */
    fun pixelSize(targetWidthPx: Int): Pair<Int, Int> {
        val w = targetWidthPx
        val h = (targetWidthPx * heightRatio / widthRatio).toInt()
        return w to h
    }
}

/**
 * Estilo de fundo do card. Tons serenos/dessaturados (direção visual do Salmos):
 * papel quente, ardósia suave, dourado discreto — gradientes muito sutis (2 tons próximos).
 *
 * Use [ShareCardBackground.solid] / [ShareCardBackground.gradient] para construir, ou
 * [ShareCardStyle.fromColorScheme] para derivar do tema/flavor automaticamente.
 */
data class ShareCardBackground(
    val topColor: Color,
    val bottomColor: Color,
) {
    /** `true` quando as duas cores são iguais (fundo chapado). */
    val isSolid: Boolean get() = topColor == bottomColor

    companion object {
        fun solid(color: Color): ShareCardBackground = ShareCardBackground(color, color)
        fun gradient(top: Color, bottom: Color): ShareCardBackground = ShareCardBackground(top, bottom)
    }
}

/**
 * Estilo visual completo do card: fundo, cor do texto, cor do acento (referência/marca).
 * Deriva da paleta do tema (serve aos 2 flavors) ou pode ser montado à mão.
 */
data class ShareCardStyle(
    val background: ShareCardBackground,
    val textColor: Color,
    val accentColor: Color,
    val watermarkColor: Color,
) {
    companion object {
        /**
         * Deriva um estilo sereno a partir de um [androidx.compose.material3.ColorScheme].
         * Use dentro de um `@Composable` com `MaterialTheme.colorScheme` — assim o card
         * herda a paleta do flavor (azul-ardósia/trigo vs violeta/âmbar) sem cor hardcoded.
         *
         * Fundo: gradiente sutil `surface → background`. Texto: `onSurface`.
         * Acento (referência/marca): `primary`. Marca d'água: `onSurfaceVariant`.
         */
        fun fromColorScheme(
            scheme: androidx.compose.material3.ColorScheme,
        ): ShareCardStyle = ShareCardStyle(
            background = ShareCardBackground.gradient(scheme.surface, scheme.background),
            textColor = scheme.onSurface,
            accentColor = scheme.primary,
            watermarkColor = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Especificação imutável do card devocional a renderizar.
 *
 * Genérico (não acoplado a "Salmo"): serve a qualquer app que gere cards de versículo/citação.
 *
 * @param quote trecho/versículo (herói do card), em serifada grande.
 * @param reference referência discreta (ex.: "Salmo 23" — numeração do flavor).
 * @param brand marca/nome discreto no rodapé (opcional; vazio = oculto).
 * @param format proporção do card ([ShareCardFormat.STORIES] ou [ShareCardFormat.SQUARE]).
 */
data class ShareCardSpec(
    val quote: String,
    val reference: String,
    val brand: String = "",
    val format: ShareCardFormat = ShareCardFormat.STORIES,
)

/**
 * Card devocional estilizado (preview da tela #9 do Salmos — "Gerador de card").
 *
 * Renderiza o [spec] na UI com estética serena (espaço em branco generoso, tipografia herói,
 * gradiente sutil). É o **mesmo** layout produzido pelo render off-screen
 * ([renderShareCardToPng]), então o preview é fiel ao PNG exportado.
 *
 * O [style] deve vir de [ShareCardStyle.fromColorScheme] (`MaterialTheme.colorScheme`) para
 * herdar a paleta do flavor — nada hardcoded.
 *
 * ```kotlin
 * val style = ShareCardStyle.fromColorScheme(MaterialTheme.colorScheme)
 * ShareCard(
 *     spec = ShareCardSpec("O Senhor é o meu pastor, nada me faltará.", "Salmo 23", "Refúgio"),
 *     style = style,
 *     modifier = Modifier.fillMaxWidth(),
 * )
 * ```
 *
 * @param spec conteúdo e formato do card.
 * @param style estilo visual (cores derivadas do tema).
 * @param modifier modificador do container (a proporção é fixada por [ShareCardFormat]).
 * @param quoteFontFamily família serifada do trecho (direção visual: "livro devocional"). Default = serifada do sistema.
 * @param brandFontFamily família da marca/referência (sans neutra). Default = [FontFamily.SansSerif].
 */
@Composable
fun ShareCard(
    spec: ShareCardSpec,
    style: ShareCardStyle,
    modifier: Modifier = Modifier,
    quoteFontFamily: FontFamily = FontFamily.Serif,
    brandFontFamily: FontFamily = FontFamily.SansSerif,
) {
    val bg = style.background
    val brush = if (bg.isSolid) {
        Brush.linearGradient(listOf(bg.topColor, bg.topColor))
    } else {
        Brush.verticalGradient(listOf(bg.topColor, bg.bottomColor))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(spec.format.aspectRatio)
            .clip(RoundedCornerShape(20.dp))
            .background(brush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "“${spec.quote}”",
                color = style.textColor,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = quoteFontFamily,
                    fontSize = 26.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 38.sp,
                ),
            )
            Text(
                text = "— ${spec.reference}",
                color = style.accentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                style = TextStyle(
                    fontFamily = brandFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }

        if (spec.brand.isNotBlank()) {
            Text(
                text = spec.brand,
                color = style.watermarkColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = brandFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
    }
}

// Constantes internas compartilhadas entre o preview e o render off-screen,
// para manter o PNG fiel ao composable.
internal object ShareCardLayout {
    const val CORNER_RADIUS_DP = 20f
    const val PADDING_H_DP = 28f
    const val PADDING_V_DP = 36f
    const val QUOTE_FONT_SP = 26f
    const val QUOTE_LINE_HEIGHT_SP = 38f
    const val REFERENCE_FONT_SP = 16f
    const val REFERENCE_TOP_SP = 20f
    const val BRAND_FONT_SP = 12f
    const val BRAND_BOTTOM_DP = 20f
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ShareCardStoriesPreview() {
    AppTheme {
        ShareCard(
            spec = ShareCardSpec(
                quote = "O Senhor é o meu pastor, nada me faltará.",
                reference = "Salmo 23",
                brand = "Refúgio",
                format = ShareCardFormat.STORIES,
            ),
            style = ShareCardStyle.fromColorScheme(MaterialTheme.colorScheme),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ShareCardSquarePreview() {
    AppTheme {
        ShareCard(
            spec = ShareCardSpec(
                quote = "Aquele que habita no esconderijo do Altíssimo.",
                reference = "Salmo 91",
                brand = "Antífona",
                format = ShareCardFormat.SQUARE,
            ),
            style = ShareCardStyle.fromColorScheme(MaterialTheme.colorScheme),
            modifier = Modifier.padding(16.dp),
        )
    }
}
