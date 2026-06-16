package br.com.codecacto.kmplib.ui.share

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.encodeBitmapToPng
import br.com.codecacto.kmplib.platform.getShareHandler
import kotlin.math.ceil

/**
 * Renderiza o card de **jogo de loteria** [spec]/[style] **off-screen para PNG** (ByteArray),
 * pronto para compartilhar em Stories/WhatsApp.
 *
 * Mesmo motor de captura do card devocional ([renderShareCardToPng]): commonMain puro, desenha o
 * layout num [ImageBitmap] via [CanvasDrawScope] + [TextMeasurer] e codifica com `encodeBitmapToPng`
 * (módulo `platform`, expect/actual já existente — Android `Bitmap.compress`, iOS Skia). **Não exige
 * host de UI ativo** — funciona em Android e iOS sem expect/actual adicional.
 *
 * As esferas das dezenas são desenhadas diretamente no canvas (círculos do acento + número),
 * espelhando a estética do composable [GameShareCard]/[br.com.codecacto.kmplib.ui.components.LotteryBallRow]
 * (sem a animação, que não se aplica a uma captura estática).
 *
 * O [textMeasurer] deve vir de `rememberTextMeasurer()` no chamador — é o único recurso que precisa
 * do ambiente de fontes da plataforma.
 *
 * ```kotlin
 * val measurer = rememberTextMeasurer()
 * val style = GameShareCardStyle.fromColorScheme(
 *     MaterialTheme.colorScheme,
 *     accent = MaterialTheme.colorScheme.secondary,
 * )
 * val png = renderGameShareCardToPng(spec, style, measurer)  // ByteArray PNG
 * getShareHandler().shareImage(png, "jogo.png", "Compartilhar jogo")
 * ```
 *
 * @param spec conteúdo e formato do card.
 * @param style estilo visual (acento do tema + textos derivados do tema).
 * @param textMeasurer medidor de texto (`rememberTextMeasurer()`).
 * @param targetWidthPx largura-alvo do PNG em px (altura segue a proporção do formato). Default 1080.
 * @param textFontFamily família dos textos (mesma do preview). Default sans neutra.
 * @return bytes do PNG renderizado.
 */
fun renderGameShareCardToPng(
    spec: GameShareCardSpec,
    style: GameShareCardStyle,
    textMeasurer: TextMeasurer,
    targetWidthPx: Int = 1080,
    textFontFamily: FontFamily = FontFamily.SansSerif,
): ByteArray {
    require(targetWidthPx > 0) { "targetWidthPx deve ser > 0" }

    val (wPx, hPx) = spec.format.pixelSize(targetWidthPx)
    val widthF = wPx.toFloat()
    val heightF = hPx.toFloat()

    // Mesma referência do card devocional: base de ~360dp → sp casam com o preview.
    val density = Density(density = targetWidthPx / 360f, fontScale = 1f)

    val bitmap = ImageBitmap(wPx, hPx)
    val canvas = ComposeCanvas(bitmap)

    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(widthF, heightF),
    ) {
        // Fundo (gradiente sutil ou chapado).
        val bg = style.background
        val brush = if (bg.isSolid) {
            Brush.linearGradient(listOf(bg.topColor, bg.topColor))
        } else {
            Brush.verticalGradient(
                colors = listOf(bg.topColor, bg.bottomColor),
                startY = 0f,
                endY = heightF,
            )
        }
        drawRect(brush = brush, size = Size(widthF, heightF))

        val padH = GameShareCardLayout.PADDING_H_DP.dp.toPx()
        val contentWidth = (widthF - 2 * padH).coerceAtLeast(1f)
        val contentWidthInt = contentWidth.toInt()

        // --- Medições do bloco central (ícone, rótulo, esferas, narrativa) ---
        val iconLayout: TextLayoutResult? = spec.icon.takeIf { it.isNotBlank() }?.let {
            textMeasurer.measure(
                text = it,
                style = TextStyle(
                    color = style.accentColor,
                    fontSize = GameShareCardLayout.ICON_FONT_SP.sp,
                    textAlign = TextAlign.Center,
                ),
                constraints = fixedWidthConstraints(contentWidthInt),
            )
        }

        val labelLayout: TextLayoutResult? = spec.label.takeIf { it.isNotBlank() }?.let {
            textMeasurer.measure(
                text = it,
                style = TextStyle(
                    color = style.titleColor,
                    fontFamily = textFontFamily,
                    fontSize = GameShareCardLayout.LABEL_FONT_SP.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                constraints = fixedWidthConstraints(contentWidthInt),
            )
        }

        val narrativeLayout: TextLayoutResult? = spec.narrative.takeIf { it.isNotBlank() }?.let {
            textMeasurer.measure(
                text = it,
                style = TextStyle(
                    color = style.narrativeColor,
                    fontFamily = textFontFamily,
                    fontSize = GameShareCardLayout.NARRATIVE_FONT_SP.sp,
                    lineHeight = (GameShareCardLayout.NARRATIVE_FONT_SP * 1.4f).sp,
                    textAlign = TextAlign.Center,
                ),
                constraints = fixedWidthConstraints(contentWidthInt),
            )
        }

        // Geometria das esferas (grid de `columns`).
        val ballSize = GameShareCardLayout.BALL_SIZE_DP.dp.toPx()
        val ballSpacing = GameShareCardLayout.BALL_SPACING_DP.dp.toPx()
        val cols = spec.columns.coerceAtLeast(1)
        val rowsCount = if (spec.numbers.isEmpty()) 0 else ceil(spec.numbers.size.toFloat() / cols).toInt()
        val ballsBlockHeight = if (rowsCount == 0) 0f else
            rowsCount * ballSize + (rowsCount - 1) * ballSpacing

        // Espaçamentos verticais entre seções (em dp base).
        val gapIconLabel = 8.dp.toPx()
        val gapBeforeBalls = 24.dp.toPx()
        val gapBeforeNarrative = 24.dp.toPx()

        // Altura total do bloco central para centralizar verticalmente.
        var blockHeight = 0f
        if (iconLayout != null) blockHeight += iconLayout.size.height
        if (labelLayout != null) {
            if (iconLayout != null) blockHeight += gapIconLabel
            blockHeight += labelLayout.size.height
        }
        if (ballsBlockHeight > 0f) {
            if (iconLayout != null || labelLayout != null) blockHeight += gapBeforeBalls
            blockHeight += ballsBlockHeight
        }
        if (narrativeLayout != null) {
            blockHeight += gapBeforeNarrative + narrativeLayout.size.height
        }

        var cursorY = (heightF - blockHeight) / 2f
        val centerX = widthF / 2f

        // --- Ícone ---
        if (iconLayout != null) {
            drawText(iconLayout, topLeft = Offset(padH, cursorY))
            cursorY += iconLayout.size.height
        }

        // --- Rótulo/identidade do tema ---
        if (labelLayout != null) {
            if (iconLayout != null) cursorY += gapIconLabel
            drawText(labelLayout, topLeft = Offset(padH, cursorY))
            cursorY += labelLayout.size.height
        }

        // --- Esferas das dezenas ---
        if (ballsBlockHeight > 0f) {
            if (iconLayout != null || labelLayout != null) cursorY += gapBeforeBalls
            val rows = spec.numbers.chunked(cols)
            rows.forEach { rowNumbers ->
                val rowWidth = rowNumbers.size * ballSize + (rowNumbers.size - 1) * ballSpacing
                var x = centerX - rowWidth / 2f
                rowNumbers.forEach { number ->
                    val cx = x + ballSize / 2f
                    val cy = cursorY + ballSize / 2f
                    drawCircle(
                        color = style.accentColor,
                        radius = ballSize / 2f,
                        center = Offset(cx, cy),
                    )
                    val numberLayout = textMeasurer.measure(
                        text = number.toString().padStart(2, '0'),
                        style = TextStyle(
                            color = style.onAccentColor,
                            fontFamily = textFontFamily,
                            fontSize = GameShareCardLayout.BALL_FONT_SP.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    drawText(
                        numberLayout,
                        topLeft = Offset(
                            cx - numberLayout.size.width / 2f,
                            cy - numberLayout.size.height / 2f,
                        ),
                    )
                    x += ballSize + ballSpacing
                }
                cursorY += ballSize + ballSpacing
            }
            cursorY -= ballSpacing // remove o espaçamento extra após a última linha
        }

        // --- Micro-narrativa/legenda ---
        if (narrativeLayout != null) {
            cursorY += gapBeforeNarrative
            drawText(narrativeLayout, topLeft = Offset(padH, cursorY))
        }

        // --- Rodapé: marca + disclaimer (compliance) ---
        val footerBottom = GameShareCardLayout.FOOTER_BOTTOM_DP.dp.toPx()
        var footerY = heightF - footerBottom

        val disclaimerLayout: TextLayoutResult? = spec.disclaimer.takeIf { it.isNotBlank() }?.let {
            textMeasurer.measure(
                text = it,
                style = TextStyle(
                    color = style.disclaimerColor,
                    fontFamily = textFontFamily,
                    fontSize = GameShareCardLayout.DISCLAIMER_FONT_SP.sp,
                    lineHeight = (GameShareCardLayout.DISCLAIMER_FONT_SP * 1.35f).sp,
                    textAlign = TextAlign.Center,
                ),
                constraints = fixedWidthConstraints(contentWidthInt),
            )
        }
        val brandLayout: TextLayoutResult? = spec.brand.takeIf { it.isNotBlank() }?.let {
            textMeasurer.measure(
                text = it,
                style = TextStyle(
                    color = style.brandColor,
                    fontFamily = textFontFamily,
                    fontSize = GameShareCardLayout.BRAND_FONT_SP.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                constraints = fixedWidthConstraints(contentWidthInt),
            )
        }

        // Desenha de baixo para cima: disclaimer (mais embaixo), depois a marca acima dele.
        if (disclaimerLayout != null) {
            footerY -= disclaimerLayout.size.height
            drawText(disclaimerLayout, topLeft = Offset(padH, footerY))
            footerY -= 4.dp.toPx()
        }
        if (brandLayout != null) {
            footerY -= brandLayout.size.height
            drawText(brandLayout, topLeft = Offset(padH, footerY))
        }
    }

    return encodeBitmapToPng(bitmap)
}

/** Constraints de largura fixa (altura livre) para medir textos centralizados. */
private fun fixedWidthConstraints(widthPx: Int): androidx.compose.ui.unit.Constraints =
    androidx.compose.ui.unit.Constraints(
        minWidth = widthPx.coerceAtLeast(0),
        maxWidth = widthPx.coerceAtLeast(0),
    )

/**
 * Atalho: renderiza o card de jogo e abre o share sheet de imagem via [ShareHandler].
 *
 * Combina [renderGameShareCardToPng] + `ShareHandler.shareImage(...)`. Reusa o `ShareHandler`
 * existente do módulo `platform` (não recria compartilhamento).
 *
 * ```kotlin
 * val measurer = rememberTextMeasurer()
 * val style = GameShareCardStyle.fromColorScheme(MaterialTheme.colorScheme, accent = temaAccent)
 * Button(onClick = {
 *     shareGameCardImage(spec, style, measurer, fileName = "meu-jogo.png", title = "Compartilhar jogo")
 * }) { Text("Compartilhar") }
 * ```
 *
 * @param fileName nome do PNG no share sheet.
 * @param title título do chooser.
 * @param shareHandler handler de compartilhamento (default = `getShareHandler()`).
 */
fun shareGameCardImage(
    spec: GameShareCardSpec,
    style: GameShareCardStyle,
    textMeasurer: TextMeasurer,
    fileName: String = "jogo.png",
    title: String = "Compartilhar",
    targetWidthPx: Int = 1080,
    textFontFamily: FontFamily = FontFamily.SansSerif,
    shareHandler: ShareHandler = getShareHandler(),
) {
    val png = renderGameShareCardToPng(
        spec = spec,
        style = style,
        textMeasurer = textMeasurer,
        targetWidthPx = targetWidthPx,
        textFontFamily = textFontFamily,
    )
    shareHandler.shareImage(png, fileName, title)
}

/**
 * Compartilha o jogo como **texto** (fallback / "Compartilhar texto").
 * Reusa `ShareHandler.shareText`. Monta "rótulo + dezenas + marca + disclaimer".
 */
fun shareGameCardText(
    spec: GameShareCardSpec,
    title: String = "Compartilhar",
    shareHandler: ShareHandler = getShareHandler(),
) {
    val lines = buildList {
        if (spec.label.isNotBlank()) add(spec.label)
        add(spec.numbers.joinToString("  ") { it.toString().padStart(2, '0') })
        if (spec.narrative.isNotBlank()) add(spec.narrative)
        if (spec.brand.isNotBlank()) add(spec.brand)
        if (spec.disclaimer.isNotBlank()) add(spec.disclaimer)
    }
    shareHandler.shareText(lines.joinToString("\n"), title)
}
