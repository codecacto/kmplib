package br.com.codecacto.kmplib.signature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.platform.encodeBitmapToPng
import kotlin.math.roundToInt

/**
 * Estado/controlador do [SignaturePad]. Mantém os traços capturados e expõe ações
 * (`clear`, `undo`), o flag de vazio e a exportação para PNG transparente.
 *
 * Obtenha uma instância com [rememberSignaturePadState] e passe-a ao [SignaturePad].
 *
 * ```kotlin
 * val sign = rememberSignaturePadState()
 * SignaturePad(state = sign, modifier = Modifier.fillMaxWidth())
 * Button(onClick = { sign.clear() }) { Text("Limpar") }
 * Button(onClick = { sign.undo() }) { Text("Desfazer") }
 * Button(onClick = {
 *     val png: ByteArray? = sign.toPngBytes()   // null se vazio
 *     // upload do PNG transparente ...
 * }) { Text("Salvar") }
 * ```
 */
@Stable
class SignaturePadState internal constructor() {
    /** Lista de traços; cada traço é a lista de pontos do toque até soltar. */
    internal val strokes: SnapshotStateList<MutableList<Offset2D>> = mutableStateListOf()

    /** Tamanho atual da área de desenho (px), atualizado pelo composable no draw. */
    internal var canvasWidthPx: Int by mutableStateOf(0)
    internal var canvasHeightPx: Int by mutableStateOf(0)

    /** Densidade atual (para converter espessura em dp → px na exportação). */
    internal var densityPx: Float by mutableStateOf(1f)

    /** `true` enquanto nenhum traço foi desenhado. Reativo (recompõe a UI). */
    val isEmpty: Boolean
        get() = strokes.isEmpty()

    /** Remove todos os traços (limpa o pad). */
    fun clear() {
        strokes.clear()
    }

    /** Remove o último traço desenhado (desfazer). No-op se vazio. */
    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
    }

    /**
     * Exporta a assinatura como **PNG com fundo transparente**.
     *
     * @param strokeColor cor do traço no PNG exportado (default preto).
     * @param strokeWidth espessura do traço (default 3.dp) — convertida para px
     *   com a densidade atual da tela.
     * @return bytes do PNG, ou `null` se o pad estiver vazio (nada a exportar) ou
     *   se a área de desenho ainda não tiver sido medida.
     */
    fun toPngBytes(
        strokeColor: Color = Color.Black,
        strokeWidth: Dp = 3.dp,
    ): ByteArray? {
        if (isEmpty) return null
        val w = canvasWidthPx
        val h = canvasHeightPx
        if (w <= 0 || h <= 0) return null

        val widthPx = densityPx * strokeWidth.value
        val bitmap = ImageBitmap(w, h) // ARGB transparente por padrão
        val canvas = ComposeCanvas(bitmap)
        val paint = Paint().apply {
            color = strokeColor
            style = PaintingStyle.Stroke
            this.strokeWidth = widthPx
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            isAntiAlias = true
        }
        for (stroke in strokes) {
            val path = stroke.toPath()
            if (path != null) canvas.drawPath(path, paint)
        }
        return encodeBitmapToPng(bitmap)
    }
}

/** Ponto 2D leve (evita depender de tipos de gesture na API pública do estado). */
internal data class Offset2D(val x: Float, val y: Float)

/** Constrói um [Path] suave a partir dos pontos do traço (quadrático entre médios). */
private fun MutableList<Offset2D>.toPath(): Path? {
    if (isEmpty()) return null
    val path = Path()
    val first = this[0]
    if (size == 1) {
        // ponto único: desenha um traço mínimo para ficar visível
        path.moveTo(first.x, first.y)
        path.lineTo(first.x + 0.1f, first.y + 0.1f)
        return path
    }
    path.moveTo(first.x, first.y)
    for (i in 1 until size) {
        val prev = this[i - 1]
        val cur = this[i]
        val midX = (prev.x + cur.x) / 2f
        val midY = (prev.y + cur.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
    }
    val last = this[lastIndex]
    path.lineTo(last.x, last.y)
    return path
}

/** Cria e lembra um [SignaturePadState] através de recomposições. */
@Composable
fun rememberSignaturePadState(): SignaturePadState = remember { SignaturePadState() }

/**
 * Composable de captura de assinatura por canvas (toque/mouse).
 *
 * Bloqueador da Onda 2 do ReciboFacil (GAP-RF-M-02). Captura o traço com o dedo ou
 * mouse e expõe `clear`/`undo`/`isEmpty`/`toPngBytes()` via [SignaturePadState].
 * Reusa `encodeBitmapToPng` (módulo `platform`) para a exportação cross-platform.
 *
 * É **stateless quanto à UI de ações** (botões Limpar/Desfazer/Salvar ficam a cargo
 * do app) — este composable é apenas a superfície de desenho.
 *
 * @param state estado do pad (use [rememberSignaturePadState]).
 * @param modifier modificador (defina `fillMaxWidth()` + altura).
 * @param strokeColor cor do traço exibido na tela.
 * @param strokeWidth espessura do traço exibido.
 * @param backgroundColor cor de fundo da área (a exportação é sempre transparente).
 * @param height altura padrão quando o modifier não define uma.
 */
@Composable
fun SignaturePad(
    state: SignaturePadState,
    modifier: Modifier = Modifier,
    strokeColor: Color = Color.Black,
    strokeWidth: Dp = 3.dp,
    backgroundColor: Color = Color.Transparent,
    height: Dp = 180.dp,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        state.strokes.add(mutableListOf(Offset2D(offset.x, offset.y)))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val current = state.strokes.lastOrNull() ?: return@detectDragGestures
                        current.add(Offset2D(change.position.x, change.position.y))
                    },
                )
            },
    ) {
        // Atualiza medidas para a exportação (px reais da superfície).
        state.canvasWidthPx = size.width.roundToInt()
        state.canvasHeightPx = size.height.roundToInt()
        state.densityPx = density

        if (backgroundColor != Color.Transparent) {
            drawRect(color = backgroundColor)
        }

        val px = strokeWidth.toPx()
        for (stroke in state.strokes) {
            val path = stroke.toPath()
            if (path != null) {
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = px, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
