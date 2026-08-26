package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import kotlin.math.roundToInt
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Slider estilizado pelas cores do tema — par do [AppSwitch] e do [AppCheckbox].
 *
 * Wrapper fino sobre o [Slider] do Material 3, com as cores vindas do [AppTheme] (nenhum
 * `Color(0x…)`) e com o cabeçalho **rótulo à esquerda / valor à direita** que toda tela de ajuste
 * acabava reescrevendo à mão. Serve qualquer grandeza contínua: intensidade de lanterna, frequência
 * de piscada, brilho, sensibilidade.
 *
 * ```kotlin
 * AppSlider(
 *     value = intensidade,
 *     onValueChange = { intensidade = it },
 *     label = "Intensidade",
 *     valueText = "${(intensidade * 100).roundToInt()}%",
 * )
 * ```
 *
 * Para uma grandeza com incremento fixo (0,5 Hz, 5 em 5 minutos), calcule os degraus com
 * [AppSliderDefaults.stepsFor] em vez de contar na mão — `steps` no Material 3 é o número de pontos
 * **intermediários**, não o de posições.
 *
 * @param value valor atual, dentro de [valueRange].
 * @param onValueChange chamado a cada arrasto.
 * @param label rótulo acima do slider. `null` = sem cabeçalho.
 * @param valueText valor formatado, à direita do rótulo (`"60%"`, `"4,5 Hz"`). `null` = sem valor.
 * @param leadingIcon ícone à esquerda do controle (ex.: sol pequeno/grande em brilho).
 * @param trailingIcon ícone à direita do controle.
 * @param steps pontos intermediários entre o mínimo e o máximo; `0` = faixa contínua.
 * @param onValueChangeFinished chamado ao soltar — use para persistir a preferência, e não a cada
 *   quadro do arrasto.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    label: String? = null,
    valueText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledThumbColor = MaterialTheme.colorScheme.outline,
        disabledActiveTrackColor = MaterialTheme.colorScheme.outline,
        disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null || valueText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (valueText != null) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                valueRange = valueRange,
                steps = steps,
                onValueChangeFinished = onValueChangeFinished,
                colors = colors,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.size(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Cálculos auxiliares do [AppSlider] — puros, para não serem refeitos (errado) em cada tela. */
object AppSliderDefaults {

    /**
     * Quantos **pontos intermediários** um slider precisa para andar de [increment] em [increment]
     * dentro de [valueRange].
     *
     * O `steps` do Material 3 conta os pontos ENTRE os extremos: uma faixa de 0 a 10 andando de 1
     * em 1 tem 11 posições e `steps = 9`. Errar esse `-1` é o bug clássico — o slider passa a
     * parar em valores que não existem na grandeza.
     *
     * Incremento inválido (`<= 0`) ou que não cabe na faixa devolve `0` (faixa contínua), que é o
     * comportamento seguro.
     */
    fun stepsFor(valueRange: ClosedFloatingPointRange<Float>, increment: Float): Int {
        if (increment <= 0f) return 0
        val span = valueRange.endInclusive - valueRange.start
        if (span <= 0f) return 0
        val positions = (span / increment).roundToInt()
        return (positions - 1).coerceAtLeast(0)
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppSliderPreview() {
    AppTheme {
        AppSlider(value = 0.6f, onValueChange = {}, label = "Intensidade", valueText = "60%")
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppSliderSteppedPreview() {
    AppTheme {
        AppSlider(
            value = 4f,
            onValueChange = {},
            valueRange = 1f..10f,
            steps = AppSliderDefaults.stepsFor(1f..10f, increment = 1f),
            label = "Frequência",
            valueText = "4 Hz",
        )
    }
}
