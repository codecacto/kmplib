package br.com.codecacto.kmplib.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.ui.components.LotteryBallRow
import br.com.codecacto.kmplib.ui.components.LotteryBallVariant
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Estilo visual do card de jogo de loteria ([GameShareCard] / [renderGameShareCardToPng]).
 *
 * **Sem cor hardcoded de domínio:** o acento (identidade do tema do jogo) é sempre passado pelo
 * chamador — deriva da paleta do `AppTheme` (uma das 6 paletas) ou de um token específico do tema
 * (ex.: dourado da Numerologia). A lib não conhece a cor concreta de nenhum tema.
 *
 * Use [GameShareCardStyle.fromColorScheme] para derivar fundo/textos do tema e informar só o acento,
 * ou monte à mão para controle total.
 *
 * @param background fundo do card (gradiente sutil ou chapado).
 * @param accentColor cor de acento/identidade do tema do jogo (esferas, rótulo, ícone). NÃO hardcode
 *   no app — derive do `AppTheme`/token do tema e passe aqui.
 * @param onAccentColor cor do número dentro da esfera (contraste sobre [accentColor]).
 * @param titleColor cor do rótulo/título do tema.
 * @param narrativeColor cor da micro-narrativa/legenda.
 * @param brandColor cor da marca do app no rodapé.
 * @param disclaimerColor cor do disclaimer no rodapé (discreto, mas legível).
 */
data class GameShareCardStyle(
    val background: ShareCardBackground,
    val accentColor: Color,
    val onAccentColor: Color,
    val titleColor: Color,
    val narrativeColor: Color,
    val brandColor: Color,
    val disclaimerColor: Color,
) {
    companion object {
        /**
         * Deriva fundo/textos a partir de um [androidx.compose.material3.ColorScheme] e de um
         * [accent] explícito (a identidade visual do tema do jogo).
         *
         * Use dentro de um `@Composable`, passando `MaterialTheme.colorScheme` + o acento do tema
         * (ex.: `MaterialTheme.colorScheme.secondary` para Horóscopo, ou o dourado da Numerologia
         * derivado do `AppTheme`). Assim o card herda a paleta do flavor sem cor hardcoded.
         *
         * Fundo: gradiente sutil `surface → background`. Número na esfera: `onPrimary`.
         * Título: `onSurface`. Narrativa: `onSurfaceVariant`. Marca/disclaimer: `onSurfaceVariant`.
         *
         * @param scheme paleta do tema (`MaterialTheme.colorScheme`).
         * @param accent cor de acento/identidade do tema do jogo (token, não hardcode).
         * @param onAccent cor do número sobre o acento (default `scheme.onPrimary`).
         */
        fun fromColorScheme(
            scheme: androidx.compose.material3.ColorScheme,
            accent: Color = scheme.primary,
            onAccent: Color = scheme.onPrimary,
        ): GameShareCardStyle = GameShareCardStyle(
            background = ShareCardBackground.gradient(scheme.surface, scheme.background),
            accentColor = accent,
            onAccentColor = onAccent,
            titleColor = scheme.onSurface,
            narrativeColor = scheme.onSurfaceVariant,
            brandColor = scheme.onSurfaceVariant,
            disclaimerColor = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Especificação imutável de um card de **jogo de loteria** compartilhável.
 *
 * Genérico para qualquer app de loteria do ecossistema (Mega-Sena, Quina, Lotofácil, temas de
 * geração lúdica): nenhum texto de domínio é hardcoded na lib — rótulo, narrativa, marca e
 * disclaimer vêm todos do chamador.
 *
 * **Compliance:** o [disclaimer] (ex.: "Apenas diversão — não garante prêmios. +18. Sem vínculo
 * com a Caixa.") é responsabilidade do app passar; a lib só o renderiza no rodapé.
 *
 * @param numbers dezenas do jogo (na ordem dada; o card não ordena). Exibidas como esferas.
 * @param columns colunas do grid de esferas (default 3 → 6 dezenas em 3×2, padrão Mega-Sena).
 * @param label rótulo/identidade do tema (ex.: "Touro · 14/06/2026", "Aleatório", "Numerologia").
 *   Opcional; vazio = oculto.
 * @param icon glifo/emoji opcional do tema exibido acima do rótulo (ex.: "♉", "🎲", "🍀"). Sem ícone
 *   de imagem para manter o render off-screen 100% commonMain; vazio = oculto.
 * @param narrative micro-narrativa/legenda lúdica opcional (ex.: "A energia de Touro pede
 *   estabilidade hoje 🌱"). Opcional; vazio = oculto.
 * @param brand marca/nome do app no rodapé (ex.: "Números da Sorte"). Opcional; vazio = oculto.
 * @param disclaimer texto de disclaimer do rodapé (entretenimento/+18/sem vínculo). Passado pelo
 *   chamador (NÃO hardcode de domínio na lib). Opcional, mas recomendado para compliance.
 * @param format proporção do card ([ShareCardFormat.STORIES] 9:16 ou [ShareCardFormat.SQUARE] 1:1).
 */
data class GameShareCardSpec(
    val numbers: List<Int>,
    val columns: Int = 3,
    val label: String = "",
    val icon: String = "",
    val narrative: String = "",
    val brand: String = "",
    val disclaimer: String = "",
    val format: ShareCardFormat = ShareCardFormat.SQUARE,
)

/**
 * Card visual de um jogo de loteria (preview do "card do jogo gerado" — tela Resultado do
 * Números da Sorte). É o **mesmo** layout produzido pelo render off-screen
 * ([renderGameShareCardToPng]), então o preview é fiel ao PNG exportado.
 *
 * Reusa [LotteryBallRow] (sem animação) para as esferas, garantindo a mesma estética do app.
 *
 * O [style] deve vir de [GameShareCardStyle.fromColorScheme] com o acento do tema, para herdar a
 * paleta do `AppTheme` sem cor hardcoded.
 *
 * ```kotlin
 * val style = GameShareCardStyle.fromColorScheme(
 *     MaterialTheme.colorScheme,
 *     accent = MaterialTheme.colorScheme.secondary, // identidade do tema "Horóscopo"
 * )
 * GameShareCard(
 *     spec = GameShareCardSpec(
 *         numbers = listOf(4, 11, 23, 37, 48, 55),
 *         label = "Touro · 14/06/2026",
 *         icon = "♉",
 *         narrative = "A energia de Touro pede estabilidade hoje 🌱",
 *         brand = "Números da Sorte",
 *         disclaimer = "Apenas diversão — não garante prêmios. +18.",
 *     ),
 *     style = style,
 * )
 * ```
 *
 * @param spec conteúdo e formato do card.
 * @param style estilo visual (acento do tema + textos derivados do tema).
 * @param modifier modificador do container (a proporção é fixada por [ShareCardFormat]).
 * @param textFontFamily família dos textos (rótulo/narrativa/marca/disclaimer). Default sans neutra.
 */
@Composable
fun GameShareCard(
    spec: GameShareCardSpec,
    style: GameShareCardStyle,
    modifier: Modifier = Modifier,
    textFontFamily: FontFamily = FontFamily.SansSerif,
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
            .clip(RoundedCornerShape(GameShareCardLayout.CORNER_RADIUS_DP.dp))
            .background(brush),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = GameShareCardLayout.PADDING_H_DP.dp,
                    vertical = GameShareCardLayout.PADDING_V_DP.dp,
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (spec.icon.isNotBlank()) {
                Text(
                    text = spec.icon,
                    color = style.accentColor,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = GameShareCardLayout.ICON_FONT_SP.sp),
                )
            }
            if (spec.label.isNotBlank()) {
                Text(
                    text = spec.label,
                    color = style.titleColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = TextStyle(
                        fontFamily = textFontFamily,
                        fontSize = GameShareCardLayout.LABEL_FONT_SP.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }

            LotteryBallRow(
                numbers = spec.numbers,
                columns = spec.columns,
                variant = LotteryBallVariant.Normal,
                accentColor = style.accentColor,
                numberColor = style.onAccentColor,
                animateReveal = false,
                modifier = Modifier.padding(top = 24.dp),
            )

            if (spec.narrative.isNotBlank()) {
                Text(
                    text = spec.narrative,
                    color = style.narrativeColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    style = TextStyle(
                        fontFamily = textFontFamily,
                        fontSize = GameShareCardLayout.NARRATIVE_FONT_SP.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = (GameShareCardLayout.NARRATIVE_FONT_SP * 1.4f).sp,
                    ),
                )
            }
        }

        // Rodapé: marca do app + disclaimer (compliance).
        if (spec.brand.isNotBlank() || spec.disclaimer.isNotBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        horizontal = GameShareCardLayout.PADDING_H_DP.dp,
                        vertical = GameShareCardLayout.FOOTER_BOTTOM_DP.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (spec.brand.isNotBlank()) {
                    Text(
                        text = spec.brand,
                        color = style.brandColor,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontFamily = textFontFamily,
                            fontSize = GameShareCardLayout.BRAND_FONT_SP.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                if (spec.disclaimer.isNotBlank()) {
                    Text(
                        text = spec.disclaimer,
                        color = style.disclaimerColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        style = TextStyle(
                            fontFamily = textFontFamily,
                            fontSize = GameShareCardLayout.DISCLAIMER_FONT_SP.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = (GameShareCardLayout.DISCLAIMER_FONT_SP * 1.35f).sp,
                        ),
                    )
                }
            }
        }
    }
}

// Constantes internas compartilhadas entre o preview e o render off-screen,
// para manter o PNG fiel ao composable.
internal object GameShareCardLayout {
    const val CORNER_RADIUS_DP = 20f
    const val PADDING_H_DP = 28f
    const val PADDING_V_DP = 36f
    const val ICON_FONT_SP = 40f
    const val LABEL_FONT_SP = 18f
    const val NARRATIVE_FONT_SP = 16f
    const val BRAND_FONT_SP = 14f
    const val DISCLAIMER_FONT_SP = 11f
    const val FOOTER_BOTTOM_DP = 20f

    // Esfera no render off-screen (em "dp" base de 360 — espelha LotteryBallVariant.Normal: 56dp).
    const val BALL_SIZE_DP = 56f
    const val BALL_FONT_SP = 22f
    const val BALL_SPACING_DP = 12f
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun GameShareCardSquarePreview() {
    AppTheme {
        GameShareCard(
            spec = GameShareCardSpec(
                numbers = listOf(4, 11, 23, 37, 48, 55),
                label = "Touro · 14/06/2026",
                icon = "♉",
                narrative = "A energia de Touro pede estabilidade hoje 🌱",
                brand = "Números da Sorte",
                disclaimer = "Apenas diversão — não garante prêmios. +18.",
                format = ShareCardFormat.SQUARE,
            ),
            style = GameShareCardStyle.fromColorScheme(
                MaterialTheme.colorScheme,
                accent = MaterialTheme.colorScheme.secondary,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun GameShareCardStoriesPreview() {
    AppTheme {
        GameShareCard(
            spec = GameShareCardSpec(
                numbers = listOf(2, 9, 17, 28, 44, 60),
                label = "Aleatório · 14/06/2026",
                icon = "🎲",
                brand = "Números da Sorte",
                disclaimer = "Apenas diversão · +18",
                format = ShareCardFormat.STORIES,
            ),
            style = GameShareCardStyle.fromColorScheme(MaterialTheme.colorScheme),
            modifier = Modifier.padding(16.dp),
        )
    }
}
