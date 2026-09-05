package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Campo de data com seletor em modal ([DatePickerDialog] do Material3).
 *
 * **Tocar em QUALQUER PONTO do campo abre o calendário** — padrão do ecossistema: campo de data
 * nunca é digitado, é escolhido. Isso exige interceptar o toque pelo [MutableInteractionSource]:
 * um `Modifier.clickable` no `OutlinedTextField` habilitado **não funciona**, porque o próprio campo
 * de texto consome o gesto (era o bug até 2.79.0 — só o ícone parecia clicável, e nem ele era).
 *
 * O valor é exibido em **dd/MM/yyyy** (padrão BR da UI); o ISO fica na fronteira da API. Use
 * [formatDate] para mudar a apresentação sem tocar no valor de domínio.
 *
 * @param selectedDate Data atualmente selecionada, ou null
 * @param onDateSelected Callback com a nova data escolhida
 * @param label Rótulo do campo
 * @param modifier Modificador do campo
 * @param isEnabled Se o campo está habilitado
 * @param placeholder Texto exibido quando não há data escolhida
 * @param formatDate Como a data aparece no campo. Default dd/MM/yyyy
 * @param confirmText Texto do botão de confirmação
 * @param dismissText Texto do botão de cancelamento
 * @param errorMessage Mensagem de erro (null = sem erro). Ver a nota abaixo.
 *
 * ## Erro de campo fica NO campo (2.181.0)
 *
 * A regra é da casa (constituição, 19/ago/2026) e o [AppTextField], o `AppTextArea`, o `NumberField`
 * e o [AppPickerField] já a cumpriam — **só o campo de data ficava de fora**, e "a data de fim não
 * pode ser antes da de início" é erro de campo como qualquer outro. Sem o parâmetro, cada app
 * escrevia um `Text` vermelho solto embaixo do campo: sem borda, sem a frase ligada ao campo para o
 * leitor de tela, e sem o foco chegar nela depois de um envio recusado.
 *
 * Passando [errorMessage] o campo se comporta como os irmãos: **borda e rótulo em
 * `colorScheme.error`** (via `isError` do Material), **a frase embaixo** (o mesmo `supportingText`
 * dos demais) e **`error()` na semântica** do campo — é isso que faz o leitor de tela anunciar
 * "entrada inválida" com a frase, e o `focusFirstInvalidField` da tela ter onde parar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePicker(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    placeholder: String = "dd/mm/aaaa",
    formatDate: (LocalDate) -> String = ::formatDateBr,
    confirmText: String = "OK",
    dismissText: String = "Cancelar",
    errorMessage: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    // Interceptar o toque pelo interactionSource é o caminho oficial para um campo "readOnly que
    // abre um seletor": o OutlinedTextField consome o gesto, então um clickable por fora nunca dispara.
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource, isEnabled) {
        interactionSource.interactions.collect { interaction ->
            if (isEnabled && interaction is PressInteraction.Release) showDialog = true
        }
    }

    // ⚠️ **Box + overlay para iOS** (2.164.0). No Compose Multiplatform iOS, o TextField com
    // `readOnly = true` não produz `PressInteraction` (Issue #4087 no GitHub), então o
    // `interactionSource` acima não dispara. O overlay transparente captura o toque diretamente.
    // No Android funciona das duas formas (interactionSource E overlay), então não há conflito.
    Box(
        modifier = modifier.then(
            // A frase específica vai na semântica do campo. O Material já marca o campo como
            // inválido (mensagem genérica) quando `isError` é true; o que ele não tem como saber é
            // O QUE está errado — e é isso que o leitor de tela precisa dizer.
            if (errorMessage != null) Modifier.semantics { error(errorMessage) } else Modifier,
        ),
    ) {
        OutlinedTextField(
            value = selectedDate?.let(formatDate) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = isEnabled,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = errorMessage != null,
            supportingText = errorMessage?.let { { Text(it) } },
            interactionSource = interactionSource,
            trailingIcon = {
                IconButton(onClick = { showDialog = true }, enabled = isEnabled) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = label)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Overlay de toque para iOS — mesmo padrão do AppDropdownField.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = isEnabled) { showDialog = true },
        )
    }

    if (showDialog) {
        val initialMillis = selectedDate?.let(::dataParaMillisDoCalendario)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(millisDoCalendarioParaData(millis))
                        }
                        showDialog = false
                    },
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(dismissText)
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** `LocalDate` → `dd/MM/yyyy` (padrão BR da UI; o ISO fica na fronteira da API). */
fun formatDateBr(date: LocalDate): String {
    val d = date.dayOfMonth.toString().padStart(2, '0')
    val m = date.monthNumber.toString().padStart(2, '0')
    return "$d/$m/${date.year}"
}

/**
 * `LocalDate` → o epoch millis que o [DatePicker] do Material entende.
 *
 * **É UTC de propósito, nos dois sentidos.** O `DatePickerState` trabalha em UTC: convertendo no
 * fuso do aparelho, quem está a oeste de Greenwich abre o calendário no dia ANTERIOR ao escolhido —
 * o clássico "salvei dia 5 e o app mostra 4". A data aqui é uma data de calendário (sem hora), e
 * ida e volta por este par é idempotente.
 */
internal fun dataParaMillisDoCalendario(date: LocalDate): Long =
    date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** O caminho de volta de [dataParaMillisDoCalendario]: o millis do seletor vira a data escolhida. */
internal fun millisDoCalendarioParaData(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
