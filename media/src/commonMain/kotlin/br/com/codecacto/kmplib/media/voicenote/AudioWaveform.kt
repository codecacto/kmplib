package br.com.codecacto.kmplib.media.voicenote

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.media.resampleWaveform

/** Como a onda ocupa a largura. */
enum class AudioWaveformMode {
    /** A gravação inteira cabe na largura (player). */
    FIT,

    /** As amostras mais recentes, entrando pela ponta (gravação ao vivo). */
    TAIL,
}

/**
 * A onda de uma nota de voz: barras verticais com o pico de cada trecho. Decorativa para leitor de
 * tela (quem a usa põe a descrição no contêiner — duração, estado).
 *
 * @param levels amostras 0..1 (do [RecordedAudio.levels][br.com.codecacto.kmplib.media.RecordedAudio.levels] ou de [extractAudioWaveform]).
 * @param progress parte já tocada (0..1), pintada com [activeColor]; `null` = tudo em [activeColor].
 */
@Composable
fun AudioWaveform(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    mode: AudioWaveformMode = AudioWaveformMode.FIT,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    minBarHeight: Dp = 3.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier) {
        val passo = (barWidth + barGap).toPx()
        if (passo <= 0f || size.width <= 0f) return@Canvas
        val barras = (size.width / passo).toInt().coerceAtLeast(1)
        val amostras = when (mode) {
            AudioWaveformMode.FIT -> resampleWaveform(levels, barras)
            AudioWaveformMode.TAIL -> levels.takeLast(barras)
        }
        val largura = barWidth.toPx()
        val minimo = minBarHeight.toPx().coerceAtMost(size.height)
        val corte = progress?.coerceIn(0f, 1f)
        val deslocamento = if (mode == AudioWaveformMode.TAIL) (barras - amostras.size) * passo else 0f
        val total = if (mode == AudioWaveformMode.FIT) amostras.size else barras
        // Sem amostra nenhuma: uma linha de barras mínimas (o espaço da onda não "pula" ao chegar dado).
        val desenhar = if (amostras.isEmpty()) List(total) { 0f } else amostras
        desenhar.forEachIndexed { i, nivel ->
            val altura = (minimo + (size.height - minimo) * nivel.coerceIn(0f, 1f))
            val xBase = deslocamento + i * passo
            val x = if (rtl) size.width - xBase - largura else xBase
            val tocada = corte == null || (i + 0.5f) / total.coerceAtLeast(1) <= corte
            drawRoundRect(
                color = if (tocada) activeColor else inactiveColor,
                topLeft = Offset(x, (size.height - altura) / 2f),
                size = Size(largura, altura),
                cornerRadius = CornerRadius(largura / 2f, largura / 2f),
            )
        }
    }
}
