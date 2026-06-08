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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.encodeBitmapToPng
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Renderiza o card devocional [spec]/[style] **off-screen para PNG** (ByteArray), pronto
 * para compartilhar em Stories/WhatsApp.
 *
 * Implementação multiplataforma em commonMain puro: desenha o mesmo layout do composable
 * [ShareCard] num [ImageBitmap] via [CanvasDrawScope] + [TextMeasurer] (Compose MP 1.10),
 * e codifica com `encodeBitmapToPng` (módulo `platform`, expect/actual já existente —
 * Android `Bitmap.compress`, iOS Skia). **Não exige host de UI ativo nem `graphicsLayer`**,
 * portanto compila e funciona em Android e iOS sem expect/actual adicional.
 *
 * O [textMeasurer] deve vir de `rememberTextMeasurer()` (Compose) no chamador — é o único
 * recurso que precisa do ambiente de fontes da plataforma.
 *
 * ```kotlin
 * val measurer = rememberTextMeasurer()
 * val style = ShareCardStyle.fromColorScheme(MaterialTheme.colorScheme)
 * // ... ao tocar "Compartilhar imagem":
 * val png = renderShareCardToPng(spec, style, measurer)   // ByteArray PNG
 * shareCardImage(png)                                      // abre o share sheet
 * ```
 *
 * @param spec conteúdo e formato do card.
 * @param style estilo visual (cores derivadas do tema/flavor).
 * @param textMeasurer medidor de texto (`rememberTextMeasurer()`).
 * @param targetWidthPx largura-alvo do PNG em px (a altura segue a proporção do formato).
 *   Default 1080 (largura padrão de Stories/post).
 * @param quoteFontFamily família serifada do trecho (mesma do preview).
 * @param brandFontFamily família da marca/referência (mesma do preview).
 * @return bytes do PNG renderizado.
 */
fun renderShareCardToPng(
    spec: ShareCardSpec,
    style: ShareCardStyle,
    textMeasurer: TextMeasurer,
    targetWidthPx: Int = 1080,
    quoteFontFamily: FontFamily = FontFamily.Serif,
    brandFontFamily: FontFamily = FontFamily.SansSerif,
): ByteArray {
    require(targetWidthPx > 0) { "targetWidthPx deve ser > 0" }

    val (wPx, hPx) = spec.format.pixelSize(targetWidthPx)
    val widthF = wPx.toFloat()
    val heightF = hPx.toFloat()

    // Densidade do render: escalamos o desenho a partir de uma largura base (1080 = densidade 1x
    // sobre a referência de ~360dp do preview), para que tamanhos de fonte em sp casem com o preview.
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

        val padH = ShareCardLayout.PADDING_H_DP.dp.toPx()
        val contentWidth = (widthF - 2 * padH).coerceAtLeast(1f)

        // --- Trecho (herói, centralizado) ---
        val quoteLayout: TextLayoutResult = textMeasurer.measure(
            text = "“${spec.quote}”",
            style = TextStyle(
                color = style.textColor,
                fontFamily = quoteFontFamily,
                fontSize = ShareCardLayout.QUOTE_FONT_SP.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                lineHeight = ShareCardLayout.QUOTE_LINE_HEIGHT_SP.sp,
                textAlign = TextAlign.Center,
            ),
            constraints = fixedWidthConstraints(contentWidth.toInt()),
        )

        val referenceLayout: TextLayoutResult = textMeasurer.measure(
            text = "— ${spec.reference}",
            style = TextStyle(
                color = style.accentColor,
                fontFamily = brandFontFamily,
                fontSize = ShareCardLayout.REFERENCE_FONT_SP.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            constraints = fixedWidthConstraints(contentWidth.toInt()),
        )

        val refGap = ShareCardLayout.REFERENCE_TOP_SP.sp.toPx()
        val blockHeight = quoteLayout.size.height + refGap + referenceLayout.size.height
        val blockTop = (heightF - blockHeight) / 2f

        drawText(quoteLayout, topLeft = Offset(padH, blockTop))
        drawText(
            referenceLayout,
            topLeft = Offset(padH, blockTop + quoteLayout.size.height + refGap),
        )

        // --- Marca discreta no rodapé ---
        if (spec.brand.isNotBlank()) {
            val brandLayout = textMeasurer.measure(
                text = spec.brand,
                style = TextStyle(
                    color = style.watermarkColor,
                    fontFamily = brandFontFamily,
                    fontSize = ShareCardLayout.BRAND_FONT_SP.sp,
                    textAlign = TextAlign.Center,
                ),
                constraints = fixedWidthConstraints(contentWidth.toInt()),
            )
            val brandBottom = ShareCardLayout.BRAND_BOTTOM_DP.dp.toPx()
            drawText(
                brandLayout,
                topLeft = Offset(padH, heightF - brandBottom - brandLayout.size.height),
            )
        }
    }

    return encodeBitmapToPng(bitmap)
}

/** Constraints de largura fixa (altura livre) para medir parágrafos centralizados. */
private fun fixedWidthConstraints(widthPx: Int): androidx.compose.ui.unit.Constraints =
    androidx.compose.ui.unit.Constraints(
        minWidth = widthPx.coerceAtLeast(0),
        maxWidth = widthPx.coerceAtLeast(0),
    )

/**
 * Atalho: renderiza o card e abre o share sheet de imagem via [ShareHandler].
 *
 * Combina [renderShareCardToPng] + `ShareHandler.shareImage(...)`. Reusa o `ShareHandler`
 * existente do módulo `platform` (não recria compartilhamento).
 *
 * ```kotlin
 * val measurer = rememberTextMeasurer()
 * val style = ShareCardStyle.fromColorScheme(MaterialTheme.colorScheme)
 * Button(onClick = {
 *     shareCardImage(spec, style, measurer, fileName = "salmo-23.png", title = "Compartilhar")
 * }) { Text("Compartilhar imagem") }
 * ```
 *
 * @param fileName nome do PNG no share sheet.
 * @param title título do chooser.
 * @param shareHandler handler de compartilhamento (default = `getShareHandler()`).
 */
fun shareCardImage(
    spec: ShareCardSpec,
    style: ShareCardStyle,
    textMeasurer: TextMeasurer,
    fileName: String = "card.png",
    title: String = "Compartilhar",
    targetWidthPx: Int = 1080,
    quoteFontFamily: FontFamily = FontFamily.Serif,
    brandFontFamily: FontFamily = FontFamily.SansSerif,
    shareHandler: ShareHandler = getShareHandler(),
) {
    val png = renderShareCardToPng(
        spec = spec,
        style = style,
        textMeasurer = textMeasurer,
        targetWidthPx = targetWidthPx,
        quoteFontFamily = quoteFontFamily,
        brandFontFamily = brandFontFamily,
    )
    shareHandler.shareImage(png, fileName, title)
}

/**
 * Compartilha o card como **texto** (fallback / "Compartilhar texto" da tela #9).
 * Reusa `ShareHandler.shareText`.
 */
fun shareCardText(
    spec: ShareCardSpec,
    title: String = "Compartilhar",
    shareHandler: ShareHandler = getShareHandler(),
) {
    val brandSuffix = if (spec.brand.isNotBlank()) "\n\n${spec.brand}" else ""
    shareHandler.shareText("“${spec.quote}”\n— ${spec.reference}$brandSuffix", title)
}
