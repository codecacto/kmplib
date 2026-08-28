package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Seletor de hora (HH:mm) em modal, par do [AppDatePicker].
 *
 * Envolve o [TimePicker] do Material 3 num [AlertDialog] estilizado pelas cores
 * de [MaterialTheme.colorScheme] (qualquer paleta do [AppTheme]). A hora/minuto
 * inicial vêm de [hour]/[minute]; ao confirmar, propaga a seleção via
 * [onTimeSelected]. O dialog é controlado pelo chamador — exiba-o
 * condicionalmente (ex.: `if (showDialog) AppTimePicker(...)`).
 *
 * Uso típico: escolher o horário do lembrete diário — um **instante** 00:00..23:59.
 *
 * **Não serve para expediente/faixa que fecha à meia-noite:** este componente (relógio do Material 3)
 * é fixo em 0..23h e **não expressa "24:00"** (fim do dia). Para editar faixas de horário onde o fim
 * pode ser a meia-noite, use `ui/calendar/AppDayTimePicker` (papel [DayTimeRole.End]) ou o
 * `AppWeeklyScheduleEditor`, que trabalham em minuto-do-dia 0..1440 e sabem "24:00".
 *
 * ```kotlin
 * if (showTimePicker) {
 *     AppTimePicker(
 *         hour = horaLembrete,
 *         minute = minutoLembrete,
 *         onTimeSelected = { h, m ->
 *             horaLembrete = h
 *             minutoLembrete = m
 *             showTimePicker = false
 *         }
 *     )
 * }
 * ```
 *
 * @param hour Hora inicial selecionada (0-23).
 * @param minute Minuto inicial selecionado (0-59).
 * @param onTimeSelected Callback com a hora e o minuto escolhidos ao confirmar.
 * @param modifier Modificador aplicado ao container do [TimePicker].
 * @param title Título exibido no topo do dialog (oculto quando vazio).
 * @param confirmLabel Texto do botão de confirmação.
 * @param dismissLabel Texto do botão de cancelamento.
 * @param onDismiss Callback ao dispensar o dialog (toque fora ou cancelar).
 *   Por padrão, reusa [onTimeSelected] mantendo a hora/minuto atuais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePicker(
    hour: Int,
    minute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    confirmLabel: String = "OK",
    dismissLabel: String = "Cancelar",
    onDismiss: () -> Unit = { onTimeSelected(hour, minute) }
) {
    val timePickerState = rememberTimePickerState(
        initialHour = hour.coerceIn(0, 23),
        initialMinute = minute.coerceIn(0, 59),
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = if (title.isNotEmpty()) {
            { Text(text = title, style = MaterialTheme.typography.titleLarge) }
        } else {
            null
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = timePickerState,
                    modifier = modifier
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppTimePickerPreview() {
    AppTheme {
        AppTimePicker(
            hour = 8,
            minute = 30,
            onTimeSelected = { _, _ -> },
            title = "Horário do lembrete"
        )
    }
}
