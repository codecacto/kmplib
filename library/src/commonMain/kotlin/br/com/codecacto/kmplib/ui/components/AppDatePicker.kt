package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Campo de data com seletor em modal ([DatePickerDialog] do Material3).
 *
 * O campo é somente leitura; ao tocar abre o calendário. A data selecionada é
 * propagada via [onDateSelected].
 *
 * @param selectedDate Data atualmente selecionada, ou null
 * @param onDateSelected Callback com a nova data escolhida
 * @param label Rótulo do campo
 * @param modifier Modificador do campo
 * @param isEnabled Se o campo está habilitado
 * @param confirmText Texto do botão de confirmação
 * @param dismissText Texto do botão de cancelamento
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePicker(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    confirmText: String = "OK",
    dismissText: String = "Cancelar"
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = selectedDate?.toString() ?: "",
        onValueChange = {},
        readOnly = true,
        enabled = isEnabled,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = label
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isEnabled) { showDialog = true }
    )

    if (showDialog) {
        val initialMillis = selectedDate
            ?.atStartOfDayIn(TimeZone.UTC)
            ?.toEpochMilliseconds()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date
                            onDateSelected(date)
                        }
                        showDialog = false
                    }
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(dismissText)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
