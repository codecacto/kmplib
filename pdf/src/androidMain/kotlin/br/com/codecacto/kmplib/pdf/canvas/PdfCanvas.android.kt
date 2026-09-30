package br.com.codecacto.kmplib.pdf.canvas

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.pdf.PdfDocument
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Android: `android.graphics.pdf.PdfDocument` — a API oficial de geração de PDF da plataforma. O
 * `Canvas` da página é o Skia com backend PDF: texto vira texto (selecionável, com a fonte
 * **embutida** e subconjuntada), traço vira vetor, e `DashPathEffect` sai como tracejado de verdade.
 *
 * Uma unidade do `Canvas` da página = 1 pt, então as coordenadas gravadas (em pt) entram direto.
 */
internal actual fun platformPdfTextMeasurer(): PdfTextMeasurer = AndroidPdfTextMeasurer()

internal actual fun renderPdfPages(pages: List<RecordedPdfPage>): ByteArray {
    val document = PdfDocument()
    val paints = AndroidPdfPaints()
    try {
        pages.forEachIndexed { index, recorded ->
            // O PageInfo só aceita pt inteiros: 58 mm = 164,4 pt vira 164 pt (0,14 mm a menos),
            // abaixo de qualquer tolerância de corte de papel.
            val info = PdfDocument.PageInfo.Builder(
                recorded.widthPt.roundToInt().coerceAtLeast(1),
                recorded.heightPt.roundToInt().coerceAtLeast(1),
                index + 1,
            ).create()
            val page = document.startPage(info)
            try {
                recorded.ops.forEach { op -> draw(page.canvas, op, paints) }
            } finally {
                document.finishPage(page)
            }
        }
        return ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            out.toByteArray()
        }
    } finally {
        document.close()
    }
}

private fun draw(canvas: Canvas, op: PdfOp, paints: AndroidPdfPaints) {
    when (op) {
        is PdfOp.Text -> canvas.drawText(op.text, op.xPt.toFloat(), op.baselinePt.toFloat(), paints.text(op.style))
        is PdfOp.Line -> canvas.drawLine(
            op.x1.toFloat(), op.y1.toFloat(), op.x2.toFloat(), op.y2.toFloat(), paints.stroke(op.stroke),
        )
        is PdfOp.Rect -> {
            val rect = RectF(op.x.toFloat(), op.y.toFloat(), (op.x + op.width).toFloat(), (op.y + op.height).toFloat())
            val radius = op.radius.toFloat()
            op.fill?.let { fill ->
                val paint = paints.fill(fill)
                if (radius > 0f) canvas.drawRoundRect(rect, radius, radius, paint) else canvas.drawRect(rect, paint)
            }
            op.stroke?.let { stroke ->
                val paint = paints.stroke(stroke)
                if (radius > 0f) canvas.drawRoundRect(rect, radius, radius, paint) else canvas.drawRect(rect, paint)
            }
        }
        is PdfOp.Image -> drawImage(canvas, op)
    }
}

private fun drawImage(canvas: Canvas, op: PdfOp.Image) {
    // Imagem que não decodifica deixa a caixa em branco (contrato do `image()`).
    val bitmap = BitmapFactory.decodeByteArray(op.bytes, 0, op.bytes.size) ?: return
    try {
        val r = imageDrawRect(
            bitmap.width.toDouble(), bitmap.height.toDouble(), op.x, op.y, op.width, op.height, op.fit,
        )
        canvas.save()
        canvas.clipRect(op.x.toFloat(), op.y.toFloat(), (op.x + op.width).toFloat(), (op.y + op.height).toFloat())
        val dst = RectF(r[0].toFloat(), r[1].toFloat(), (r[0] + r[2]).toFloat(), (r[1] + r[3]).toFloat())
        canvas.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    } finally {
        bitmap.recycle()
    }
}

/** Paints por estilo, reaproveitados dentro de UM documento (Paint não é thread-safe). */
private class AndroidPdfPaints {
    private val textPaints = HashMap<PdfTextStyle, Paint>()

    fun text(style: PdfTextStyle): Paint = textPaints.getOrPut(style) { textPaintFor(style) }

    fun stroke(stroke: PdfStroke): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke.widthPt.toFloat()
        strokeCap = Paint.Cap.BUTT
        color = stroke.color.toArgb()
        stroke.dash?.let { dash ->
            pathEffect = DashPathEffect(
                floatArrayOf(dash.onMm.mmToPoints().toFloat(), dash.offMm.mmToPoints().toFloat()),
                dash.phaseMm.mmToPoints().toFloat(),
            )
        }
    }

    fun fill(color: PdfColor): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color.toArgb()
    }
}

private class AndroidPdfTextMeasurer : PdfTextMeasurer {
    private val paints = HashMap<PdfTextStyle, Paint>()
    private val metrics = HashMap<PdfTextStyle, PdfFontMetricsPt>()

    private fun paint(style: PdfTextStyle) = paints.getOrPut(style) { textPaintFor(style) }

    override fun widthPt(text: String, style: PdfTextStyle): Double =
        if (text.isEmpty()) 0.0 else paint(style).measureText(text).toDouble()

    override fun fontMetrics(style: PdfTextStyle): PdfFontMetricsPt = metrics.getOrPut(style) {
        val paint = paint(style)
        val fm = paint.fontMetrics
        val bounds = Rect()
        // Altura de versal medida no "H" — o FontMetrics do Android não expõe capHeight.
        paint.getTextBounds("H", 0, 1, bounds)
        val ascent = -fm.ascent.toDouble()
        PdfFontMetricsPt(
            ascentPt = ascent,
            descentPt = fm.descent.toDouble(),
            capHeightPt = (-bounds.top).toDouble().takeIf { it > 0.0 } ?: (ascent * 0.7),
        )
    }
}

private fun textPaintFor(style: PdfTextStyle): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        textSize = style.sizePt.toFloat()
        color = style.color.toArgb()
        val (face, syntheticBold) = typefaceFor(style.font, style.bold)
        typeface = face
        // Negrito sintético só quando a família não tem o arquivo de negrito.
        isFakeBoldText = syntheticBold
        if (style.tabularNumbers) fontFeatureSettings = "'tnum' 1"
    }

/** Typeface da família no peso pedido; `second` = precisa de negrito sintético. */
private fun typefaceFor(family: PdfFontFamily, bold: Boolean): Pair<Typeface, Boolean> {
    val weightStyle = if (bold) Typeface.BOLD else Typeface.NORMAL
    return when (family.kind) {
        PdfFontFamily.Kind.SansSerif -> Typeface.create(Typeface.SANS_SERIF, weightStyle) to false
        PdfFontFamily.Kind.Monospace -> Typeface.create(Typeface.MONOSPACE, weightStyle) to false
        PdfFontFamily.Kind.Custom -> {
            val loaded = loadedFaces(family)
            when {
                !bold -> loaded.regular to false
                loaded.bold != null -> loaded.bold to false
                else -> loaded.regular to true
            }
        }
    }
}

private class LoadedFaces(val regular: Typeface, val bold: Typeface?)

private fun loadedFaces(family: PdfFontFamily): LoadedFaces {
    (family.platformCache as? LoadedFaces)?.let { return it }
    val faces = LoadedFaces(
        regular = typefaceFromBytes(family.regular!!, family.name),
        bold = family.bold?.let { typefaceFromBytes(it, family.name + "-bold") },
    )
    family.platformCache = faces
    return faces
}

/**
 * Fonte a partir de bytes. Android 10+: `Font.Builder(ByteBuffer)` — a API oficial para fonte em
 * memória. Antes disso a plataforma só carrega de arquivo, então os bytes passam por um arquivo no
 * diretório temporário do app (`java.io.tmpdir` é o `cacheDir` no Android).
 */
private fun typefaceFromBytes(bytes: ByteArray, name: String): Typeface {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
        buffer.rewind()
        val font = Font.Builder(buffer).build()
        return Typeface.CustomFallbackBuilder(FontFamily.Builder(font).build()).build()
    }
    // Arquivo ESTÁVEL por conteúdo, e não temporário apagado em seguida: até o Android 9 o Skia pode
    // reabrir o arquivo para ler glifos depois de criado o Typeface. Fonte não é dado pessoal, e o
    // mesmo arquivo serve às próximas gerações (e o sistema o limpa com o resto do cache).
    val dir = File(System.getProperty("java.io.tmpdir") ?: ".", "kmplib-pdf-fonts").apply { mkdirs() }
    val file = File(dir, "font-${bytes.contentHashCode().toUInt()}-${bytes.size}.ttf")
    if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    return Typeface.createFromFile(file)
        ?: throw IllegalArgumentException("a fonte '$name' não pôde ser carregada")
}

private fun PdfColor.toArgb(): Int =
    ((alpha * 255f).roundToInt() shl 24) or
        ((red * 255f).roundToInt() shl 16) or
        ((green * 255f).roundToInt() shl 8) or
        (blue * 255f).roundToInt()
