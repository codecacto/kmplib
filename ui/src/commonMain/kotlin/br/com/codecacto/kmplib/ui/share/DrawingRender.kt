package br.com.codecacto.kmplib.ui.share

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import br.com.codecacto.kmplib.platform.encodeBitmapToPng

/**
 * Largura de referência (dp) do desenho off-screen: a [Density] default faz `widthPx` equivaler a
 * **360 dp**, a largura de um telefone comum — assim `16.sp` no desenho tem o tamanho que teria na
 * tela, e o PNG de 1080 px sai com o texto na proporção do protótipo.
 */
const val DRAWING_REFERENCE_WIDTH_DP: Float = 360f

/**
 * **O motor** do [renderShareCardToPng]/[renderGameShareCardToPng], sem layout nenhum: desenha
 * [draw] num [ImageBitmap] de [widthPx]×[heightPx] **fora da tela**. Desde 2.228.0.
 *
 * Para card de story/post com layout PRÓPRIO — o do protótipo do projeto —, quando o `ShareCard` e
 * o `GameShareCard` (layouts fixos da lib) não desenham o que foi aprovado. É a API oficial do
 * Compose para isso ([CanvasDrawScope] sobre um [ComposeCanvas] de um [ImageBitmap]), em
 * `commonMain`, sem `graphicsLayer`, sem captura de tela e sem host de UI ativo.
 *
 * Texto: meça com um `TextMeasurer` de `rememberTextMeasurer()` capturado pelo lambda
 * (`drawText(measurer, …)`) — é o único recurso que depende do ambiente de fontes da plataforma.
 *
 * ```kotlin
 * val measurer = rememberTextMeasurer()
 * // … no clique de "Compartilhar":
 * val png = renderDrawingToPng(widthPx = 1080, heightPx = 1920) {
 *     drawRect(fundo)
 *     drawText(measurer, "Lua Cheia", topLeft = Offset(64.dp.toPx(), 200.dp.toPx()), style = titulo)
 * }
 * getShareHandler().shareImage(png, "lua-cheia.png")
 * ```
 *
 * @param density escala dp/sp → px. Default: [widthPx] equivale a [DRAWING_REFERENCE_WIDTH_DP].
 * @param layoutDirection direção de layout do [DrawScope] (afeta `drawText` com alinhamento
 *   relativo). Default LTR.
 */
fun renderDrawingToImageBitmap(
    widthPx: Int,
    heightPx: Int,
    density: Density = Density(density = widthPx / DRAWING_REFERENCE_WIDTH_DP, fontScale = 1f),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    draw: DrawScope.() -> Unit,
): ImageBitmap {
    require(widthPx > 0 && heightPx > 0) { "renderDrawing: largura e altura devem ser > 0 (veio ${widthPx}x$heightPx)." }
    val bitmap = ImageBitmap(widthPx, heightPx)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = ComposeCanvas(bitmap),
        size = Size(widthPx.toFloat(), heightPx.toFloat()),
        block = draw,
    )
    return bitmap
}

/**
 * [renderDrawingToImageBitmap] codificado em **PNG** (`encodeBitmapToPng`: Android
 * `Bitmap.compress`, iOS Skia). Os bytes vão direto para `ShareHandler.shareImage`/`shareCardImage`.
 */
fun renderDrawingToPng(
    widthPx: Int,
    heightPx: Int,
    density: Density = Density(density = widthPx / DRAWING_REFERENCE_WIDTH_DP, fontScale = 1f),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    draw: DrawScope.() -> Unit,
): ByteArray = encodeBitmapToPng(renderDrawingToImageBitmap(widthPx, heightPx, density, layoutDirection, draw))
