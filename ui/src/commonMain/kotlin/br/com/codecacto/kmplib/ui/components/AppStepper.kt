package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_stepper_decrease
import br.com.codecacto.kmplib.generated.resources.kmplib_stepper_increase
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Próximo valor do stepper: [value] + [delta], preso em [[min], [max]] e sem estourar `Int`.
 * Função pura (testada) — é ela que decide, e o botão só a chama.
 */
fun stepperNextValue(value: Int, delta: Int, min: Int, max: Int): Int {
    require(min <= max) { "stepper: min ($min) > max ($max)." }
    val next = value.toLong() + delta.toLong()
    return next.coerceIn(min.toLong(), max.toLong()).toInt()
}

/**
 * **Stepper de quantidade** (2.220.0 — GAP-ER-08): `[−]  3  [+]`, para quantidade pequena que se
 * ajusta de um em um (extintores no local, cópias, pessoas). Para número livre e grande, use
 * [NumberField]; para grandeza contínua, [AppSlider].
 *
 * Botões com alvo de **48 dp** e descrição ("Diminuir"/"Aumentar", nos 4 idiomas da lib); cada um
 * desliga no limite. O valor é região viva — o leitor de tela anuncia o número novo a cada toque.
 *
 * @param label rótulo opcional acima (já traduzido).
 * @param valueText como o número aparece (ex.: `{ "$it un." }`); default o próprio número.
 */
@Composable
fun AppStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    step: Int = 1,
    label: String? = null,
    enabled: Boolean = true,
    valueText: (Int) -> String = { it.toString() },
) {
    require(step > 0) { "AppStepper: step deve ser > 0 (foi $step)." }
    val current = value.coerceIn(min, max)
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onValueChange(stepperNextValue(current, -step, min, max)) },
                enabled = enabled && current > min,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = kmpStringResource(Res.string.kmplib_stepper_decrease))
            }
            Text(
                text = valueText(current),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            FilledTonalIconButton(
                onClick = { onValueChange(stepperNextValue(current, step, min, max)) },
                enabled = enabled && current < max,
            ) {
                Icon(Icons.Filled.Add, contentDescription = kmpStringResource(Res.string.kmplib_stepper_increase))
            }
        }
    }
}
