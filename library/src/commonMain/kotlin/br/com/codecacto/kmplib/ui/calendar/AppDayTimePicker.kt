package br.com.codecacto.kmplib.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme

/**
 * **AppDayTimePicker** — seletor de horário de **faixa** ("HH:mm") que sabe expressar o **fim do dia**
 * ("24:00"), irmão do [br.com.codecacto.kmplib.ui.components.AppTimePicker].
 *
 * Por que um irmão em vez do `AppTimePicker`: aquele usa o `TimePicker` (relógio) do Material 3, fixo em
 * 0..23h, e devolve `(hour, minute)` — perfeito para um **instante** (ex.: horário do lembrete diário),
 * mas **incapaz de "24:00"**. Expediente é outro conceito: a faixa de um salão noturno vai 18:00 → 24:00
 * (fecha à meia-noite). Aqui o valor trafega como **"HH:mm" String** (com "24:00" válido), sobre o
 * minuto-do-dia 0..1440 — NUNCA `LocalTime` (que não representa 24:00). Ver `DayBoundaryTime.kt`.
 *
 * "24:00" só é ofertado quando [role] == [DayTimeRole.End]. Como [DayTimeRole.Start] a lista para em
 * 23:xx — a impossibilidade de "24:00 como início" é da API, não do consumidor.
 *
 * UI: campo clicável + dropdown de opções por passo ([stepMin]); alvos ≥48dp; acessível
 * ([contentDescription]); tema 100% por tokens ([MaterialTheme.colorScheme]/[AppTheme]), sem hardcode.
 *
 * @param value Valor atual "HH:mm" (ou "24:00" no fim; em branco = não selecionado).
 * @param role Papel do campo — decide se "24:00" é ofertado ([DayTimeRole.End]) ou não ([DayTimeRole.Start]).
 * @param onValueChange Callback com a opção "HH:mm" escolhida.
 * @param modifier Modificador do container.
 * @param stepMin Passo (min) das opções (clampado 5..60). @default 30
 * @param enabled Se o campo aceita interação.
 * @param contentDescription Rótulo de acessibilidade (ex.: "Sexta — início da faixa 1").
 * @param placeholder Texto quando [value] está em branco.
 */
@Composable
fun AppDayTimePicker(
    value: String,
    role: DayTimeRole,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    stepMin: Int = 30,
    enabled: Boolean = true,
    contentDescription: String? = null,
    placeholder: String = "--:--",
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(role, stepMin, value) {
        val base = dayTimeOptions(role, stepMin)
        // Mostra também um valor válido fora do passo (ex.: "09:15" salvo com passo 30).
        if (value.isNotBlank() && parseDayMinute(value, role) != null && value !in base) {
            (base + value).sortedBy { parseDayMinute(it, role) ?: Int.MAX_VALUE }
        } else {
            base
        }
    }
    val semantics = contentDescription?.let { Modifier.semantics { this.contentDescription = it } } ?: Modifier

    Box(modifier = modifier.then(semantics)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { expanded = true }
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    leadingIcon = if (opt == value) {
                        { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    text = {
                        Text(
                            text = opt,
                            textAlign = TextAlign.Start,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (opt == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onValueChange(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}
