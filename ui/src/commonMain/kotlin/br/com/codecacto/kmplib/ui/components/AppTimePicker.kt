package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.ui.theme.AppTheme
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Seletor de hora (HH:mm) em modal, par do [AppDatePicker].
 *
 * **Desde a 2.329.0 é uma FACHADA do [AppTimePickerDialog]** — o mesmo `TimePickerDialog` oficial
 * do Material 3 (24h, alternância relógio ⇄ teclado, ids `dialogo-*`), com a MESMA assinatura de
 * antes: nenhum app precisa mudar nada. O que muda para quem já usa:
 * - os rótulos dos botões seguem o **idioma do aparelho** (recursos da lib, pt-BR/en/es/pt-PT) —
 *   até a 2.328.0 eram `"OK"`/`"Cancelar"` fixos, e app GLOBAL em aparelho em inglês mostrava
 *   "Cancelar". Quem passa `confirmLabel`/`dismissLabel` continua mandando no texto;
 * - sem [title], o título é o do próprio Material ("Selecionar horário", traduzido por ele);
 * - ganha o modo teclado (o ícone no canto do diálogo).
 *
 * Contrato preservado: confirmar chama **só** [onTimeSelected] (nunca [onDismiss]); tocar fora,
 * voltar ou Cancelar chamam **só** [onDismiss] — cujo default continua sendo
 * `onTimeSelected(hour, minute)` com a hora de entrada, para quem fecha o diálogo por ali.
 *
 * Para código novo, prefira `AppTimeField` / `AppTimePickerDialog`: trabalham em `LocalTime` e
 * aceitam limite mínimo/máximo.
 *
 * **Não serve para expediente/faixa que fecha à meia-noite:** o relógio do Material 3 é fixo em
 * 0..23h e **não expressa "24:00"**. Para isso use `ui/calendar/AppDayTimePicker` (papel
 * [DayTimeRole.End]) ou o `AppWeeklyScheduleEditor`, que sabem "24:00".
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
 * @param hour Hora inicial selecionada (0-23; fora da faixa é puxada para dentro).
 * @param minute Minuto inicial selecionado (0-59; idem).
 * @param onTimeSelected Callback com a hora e o minuto escolhidos ao confirmar.
 * @param modifier Modificador aplicado ao relógio/teclado dentro do diálogo.
 * @param title Título exibido no topo do dialog (vazio = o título traduzido do Material).
 * @param confirmLabel Texto do botão de confirmação (default: do idioma do aparelho).
 * @param dismissLabel Texto do botão de cancelamento (default: do idioma do aparelho).
 * @param onDismiss Callback ao dispensar o dialog (toque fora, voltar ou cancelar).
 *   Por padrão, reusa [onTimeSelected] mantendo a hora/minuto de entrada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePicker(
    hour: Int,
    minute: Int,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    confirmLabel: String = rememberAppTimeFieldTexts().confirm,
    dismissLabel: String = rememberAppTimeFieldTexts().dismiss,
    onDismiss: () -> Unit = { onTimeSelected(hour, minute) }
) {
    val baseTexts = rememberAppTimeFieldTexts()
    val texts = remember(baseTexts, confirmLabel, dismissLabel) {
        baseTexts.copy(confirm = confirmLabel, dismiss = dismissLabel)
    }
    val currentOnTimeSelected by rememberUpdatedState(onTimeSelected)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val callbacks = remember {
        LegacyTimePickerCallbacks(
            onTimeSelected = { h, m -> currentOnTimeSelected(h, m) },
            onDismiss = { currentOnDismiss() },
        )
    }

    TimePickerDialogCore(
        selectedTime = legacyInitialTime(hour, minute),
        onTimeSelected = callbacks::confirm,
        onDismiss = callbacks::dismiss,
        texts = texts,
        title = title.ifEmpty { null },
        pickerModifier = modifier,
    )
}

/** A hora de abertura do [AppTimePicker]: [hour]/[minute] puxados para 0..23 / 0..59 (como antes). */
internal fun legacyInitialTime(hour: Int, minute: Int): LocalTime =
    LocalTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

/**
 * A ponte entre o contrato antigo do [AppTimePicker] e o do [AppTimePickerDialog]. O novo chama
 * `onDismiss` **também depois de confirmar**; o antigo, não — e o `onDismiss` default do antigo
 * chama `onTimeSelected(hora de ENTRADA)`, que regravaria a hora velha por cima da escolhida.
 * Aqui: confirmar repassa a hora e silencia o fechamento que vem logo atrás; fechar sem confirmar
 * repassa ao `onDismiss` do chamador.
 */
internal class LegacyTimePickerCallbacks(
    private val onTimeSelected: (hour: Int, minute: Int) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var confirmed = false

    fun confirm(time: LocalTime) {
        confirmed = true
        onTimeSelected(time.hour, time.minute)
    }

    fun dismiss() {
        if (confirmed) {
            // O diálogo novo fecha logo após confirmar; o antigo não avisava esse fechamento.
            confirmed = false
            return
        }
        onDismiss()
    }
}

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
