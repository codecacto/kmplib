package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppColors
import kotlin.math.roundToInt

/**
 * Tom semantico da [AppProgressBar]. Cada tom mapeia para um token de cor da camada de tema
 * ([MaterialTheme.colorScheme] / [AppColors]) — nunca cores hardcoded. Permite que o app expresse
 * intencao (ex.: progresso de etapa = [Primary]; etapa concluida = [Success]; atrasada = [Warning])
 * sem conhecer a paleta concreta.
 */
enum class ProgressTone {
    /** Cor primaria do tema. Default. */
    Primary,

    /** Cor de sucesso (verde do tema) — ex.: progresso concluido. */
    Success,

    /** Cor de aviso (ambar do tema) — ex.: progresso em atencao/atraso. */
    Warning,

    /** Cor de erro (vermelho do tema) — ex.: bloqueado/critico. */
    Error,

    /** Cor informativa (azul do tema) — ex.: progresso neutro/secundario. */
    Info,
}

/**
 * Barra de progresso LINEAR tematica da kmplib.
 *
 * Exibe o avanco de uma tarefa (etapa de obra, progresso geral, upload, etc.) como uma barra
 * horizontal preenchivel. As cores vem SEMPRE da camada de tema (tokens de [AppTheme]/
 * [MaterialTheme.colorScheme] + semanticas [AppColors]) — nunca `Color(...)` hardcoded — e por isso
 * acompanha automaticamente a paleta e o dark mode do app consumidor.
 *
 * ### API de progresso
 * O progresso e um [Float] no intervalo **`0f..1f`** (fracao), coerente com `LinearProgressIndicator`
 * do Material 3 e com `UsageSnapshot.fraction` ja usado na kmplib. Valores fora do intervalo sao
 * "clampados" defensivamente. Para uma escala 0..100, divida por 100 antes de passar
 * (ou use o overload [AppProgressBar] que recebe `Int` percentual).
 *
 * ### Acessibilidade
 * Anuncia o progresso a leitores de tela via [ProgressBarRangeInfo]. Quando [showLabel] = true,
 * exibe o percentual textual (ex.: "75%") alinhado ao fim da barra.
 *
 * @param progress fracao concluida no intervalo `0f..1f` (clampada). Ex.: `0.75f` = 75%.
 * @param modifier modificador externo (a barra preenche a largura por padrao).
 * @param tone tom semantico da cor de preenchimento (default [ProgressTone.Primary]).
 * @param height altura/espessura da barra (default `8.dp`).
 * @param showLabel se `true`, mostra o percentual textual acima/ao lado da barra.
 * @param label rotulo opcional exibido a esquerda (ex.: "Fundacao"). Requer espaco; aparece junto
 *   ao percentual quando [showLabel] tambem e `true`.
 * @param color sobrescreve a cor de preenchimento (use tokens do tema; default derivado de [tone]).
 * @param trackColor sobrescreve a cor da trilha de fundo (default `surfaceVariant` do tema).
 */
@Composable
fun AppProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    tone: ProgressTone = ProgressTone.Primary,
    height: Dp = 8.dp,
    showLabel: Boolean = false,
    label: String? = null,
    color: Color? = null,
    trackColor: Color? = null,
) {
    val fraction = normalizeProgress(progress)
    val barColor = color ?: tone.toColor()
    val track = trackColor ?: MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showLabel || label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (showLabel) {
                    Text(
                        text = "${(fraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(track)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = fraction,
                        range = 0f..1f,
                    )
                },
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(height / 2))
                        .background(barColor),
                )
            }
        }
    }
}

/**
 * Overload de conveniencia que recebe o progresso como percentual inteiro `0..100`.
 * Internamente converte para a fracao `0f..1f`. Veja [AppProgressBar] (Float) para detalhes.
 *
 * @param percent percentual concluido `0..100` (clampado).
 */
@Composable
fun AppProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
    tone: ProgressTone = ProgressTone.Primary,
    height: Dp = 8.dp,
    showLabel: Boolean = false,
    label: String? = null,
    color: Color? = null,
    trackColor: Color? = null,
) {
    AppProgressBar(
        progress = percentToFraction(percent),
        modifier = modifier,
        tone = tone,
        height = height,
        showLabel = showLabel,
        label = label,
        color = color,
        trackColor = trackColor,
    )
}

/**
 * Normaliza uma fracao de progresso para o intervalo valido `0f..1f`. Trata valores fora do
 * intervalo e `NaN` (retorna `0f`). Logica pura, exposta internamente para teste.
 */
internal fun normalizeProgress(progress: Float): Float =
    if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/**
 * Converte um percentual inteiro `0..100` para a fracao `0f..1f` (clampada). Logica pura,
 * exposta internamente para teste.
 */
internal fun percentToFraction(percent: Int): Float =
    percent.coerceIn(0, 100) / 100f

/** Resolve o tom semantico para um token de cor da camada de tema. */
@Composable
private fun ProgressTone.toColor(): Color = when (this) {
    ProgressTone.Primary -> MaterialTheme.colorScheme.primary
    ProgressTone.Success -> AppColors.current.success
    ProgressTone.Warning -> AppColors.current.warning
    ProgressTone.Error -> MaterialTheme.colorScheme.error
    ProgressTone.Info -> AppColors.current.info
}
