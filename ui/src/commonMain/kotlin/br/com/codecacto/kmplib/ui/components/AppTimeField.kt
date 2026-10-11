package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.platform.automation.exposeTestTagsAsResourceId
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_time_confirm
import br.com.codecacto.kmplib.generated.resources.kmplib_time_dismiss
import br.com.codecacto.kmplib.generated.resources.kmplib_time_not_after
import br.com.codecacto.kmplib.generated.resources.kmplib_time_not_before
import br.com.codecacto.kmplib.generated.resources.kmplib_time_placeholder
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Textos do [AppTimeField] / [AppTimePickerDialog]. Na tela use [rememberAppTimeFieldTexts], que lê
 * os Compose Resources da lib no **idioma do aparelho**; o construtor existe para app que traz os
 * próprios textos (e para teste).
 *
 * [notAfter]/[notBefore] recebem a hora-limite já formatada (`"14:32"`) e devolvem a frase.
 */
data class AppTimeFieldTexts(
    val confirm: String,
    val dismiss: String,
    val placeholder: String,
    val notAfter: (time: String) -> String,
    val notBefore: (time: String) -> String,
)

/** [AppTimeFieldTexts] no idioma do aparelho (pt-BR, en, es, pt-PT). */
@Composable
fun rememberAppTimeFieldTexts(): AppTimeFieldTexts {
    val confirm = kmpStringResource(Res.string.kmplib_time_confirm)
    val dismiss = kmpStringResource(Res.string.kmplib_time_dismiss)
    val placeholder = kmpStringResource(Res.string.kmplib_time_placeholder)
    // Sem argumento, o stringResource devolve o modelo cru ("… até %1$s"); a hora entra no lambda,
    // porque ela só é conhecida quando o limite viola — não na composição dos textos.
    val notAfter = kmpStringResource(Res.string.kmplib_time_not_after)
    val notBefore = kmpStringResource(Res.string.kmplib_time_not_before)
    return remember(confirm, dismiss, placeholder, notAfter, notBefore) {
        AppTimeFieldTexts(
            confirm = confirm,
            dismiss = dismiss,
            placeholder = placeholder,
            notAfter = { fillTimeTemplate(notAfter, it) },
            notBefore = { fillTimeTemplate(notBefore, it) },
        )
    }
}

/** Qual limite a hora escolhida fura. */
enum class TimeLimitViolation { AfterMax, BeforeMin }

/**
 * A hora está fora de `[minTime, maxTime]`? `null` = dentro (ou sem limite). Os dois extremos são
 * **inclusivos**: com `maxTime = 14:32`, escolher 14:32 vale.
 */
fun timeLimitViolation(
    time: LocalTime,
    minTime: LocalTime? = null,
    maxTime: LocalTime? = null,
): TimeLimitViolation? = when {
    maxTime != null && time > maxTime -> TimeLimitViolation.AfterMax
    minTime != null && time < minTime -> TimeLimitViolation.BeforeMin
    else -> null
}

/**
 * O `maxTime` para "a hora **não pode ser depois de agora**" quando a hora vem junto de uma data
 * escolhida em outro campo — o caso da "hora da queimadura" (QueiMap AP10), do "horário do
 * ocorrido", do "início dos sintomas".
 *
 * - data **anterior** a hoje → `null` (qualquer hora daquele dia já passou);
 * - data **de hoje** → a hora de agora, **truncada no minuto** (o minuto corrente é válido);
 * - data **nula** → a mesma regra de hoje (sem data escolhida ainda, a leitura segura é "hoje");
 * - data **futura** → `00:00`, o mais restritivo possível. Data futura já é erro do campo de DATA;
 *   aqui só não se abre a porta para uma hora futura passar junto.
 *
 * [now] é parâmetro (e não `Clock` lido aqui dentro) para o ViewModel e o teste controlarem o
 * relógio: `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())`.
 */
fun maxTimeNotAfterNow(date: LocalDate?, now: LocalDateTime): LocalTime? {
    val today = now.date
    return when {
        date != null && date < today -> null
        date != null && date > today -> LocalTime(0, 0)
        else -> LocalTime(now.hour, now.minute)
    }
}

/** `LocalTime` → `"HH:mm"` em 24h (padrão BR da UI; segundos descartados). */
fun formatTimeHm(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/**
 * A hora com que o seletor ABRE: a escolhida; sem escolha, [fallback] (a hora de agora, na tela) —
 * e, nos dois casos, **puxada para dentro dos limites**. Abrir o relógio num valor que o botão OK
 * vai recusar obriga a pessoa a descobrir o limite sozinha.
 */
fun initialPickerTime(
    selected: LocalTime?,
    minTime: LocalTime?,
    maxTime: LocalTime?,
    fallback: LocalTime,
): LocalTime {
    val base = selected ?: fallback
    val start = LocalTime(base.hour, base.minute)
    return when (timeLimitViolation(start, minTime, maxTime)) {
        TimeLimitViolation.AfterMax -> maxTime!!
        TimeLimitViolation.BeforeMin -> minTime!!
        null -> start
    }
}

/** Troca `%1$s` do modelo de texto pela hora. */
internal fun fillTimeTemplate(template: String, time: String): String = template.replace("%1\$s", time)

/** A frase de erro para a hora fora dos limites, ou `null` se está dentro. */
fun timeLimitMessage(
    time: LocalTime,
    minTime: LocalTime?,
    maxTime: LocalTime?,
    texts: AppTimeFieldTexts,
): String? = when (timeLimitViolation(time, minTime, maxTime)) {
    TimeLimitViolation.AfterMax -> texts.notAfter(formatTimeHm(maxTime!!))
    TimeLimitViolation.BeforeMin -> texts.notBefore(formatTimeHm(minTime!!))
    null -> null
}

@OptIn(ExperimentalTime::class)
private fun nowLocalTime(): LocalTime {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return LocalTime(now.hour, now.minute)
}

/**
 * Campo de **hora do dia** (HH:mm, 24h) que abre o seletor oficial do Material 3 — par do
 * [AppDatePicker], com o mesmo desenho e o mesmo contrato de erro.
 *
 * **Tocar em qualquer ponto do campo abre o seletor** (hora não se digita no campo; digita-se, se a
 * pessoa quiser, no modo teclado do próprio seletor — o botão de alternância é do Material).
 *
 * ## Limites ([minTime]/[maxTime], inclusivos)
 *
 * O `TimePicker` do Material **não tem** faixa permitida. Aqui ela é aplicada do jeito que o M3
 * recomenda para validação: o seletor abre já dentro da faixa, e com uma hora fora dela o botão de
 * confirmar fica **desligado** com a frase do limite embaixo do relógio (anunciada pelo leitor de
 * tela). Hora futura com data de hoje: `maxTime = maxTimeNotAfterNow(dataEscolhida, agora)`.
 *
 * Se o valor **já escolhido** sair da faixa depois (a pessoa marcou 14:30 ontem e trocou a data para
 * hoje, às 10:00), o campo mostra sozinho o erro do limite — o valor na tela é inválido e dizer isso
 * não depende de envio. [errorMessage] do app vence esse texto.
 *
 * @param selectedTime a hora escolhida, ou `null`.
 * @param onTimeSelected a hora confirmada (sempre dentro dos limites).
 * @param label rótulo do campo.
 * @param helperText dica NEUTRA embaixo do campo (ex.: "a reposição conta a partir deste horário").
 * @param errorMessage erro do campo (vence o erro automático do limite e o [helperText]).
 * @param texts textos; default no idioma do aparelho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimeField(
    selectedTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    minTime: LocalTime? = null,
    maxTime: LocalTime? = null,
    helperText: String? = null,
    errorMessage: String? = null,
    texts: AppTimeFieldTexts = rememberAppTimeFieldTexts(),
) {
    var showDialog by remember { mutableStateOf(false) }

    // Mesmo caminho do AppDatePicker: o OutlinedTextField consome o toque, então o clique chega
    // pelo interactionSource (Android) e pelo overlay (iOS, onde o readOnly não gera PressInteraction).
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource, isEnabled) {
        interactionSource.interactions.collect { interaction ->
            if (isEnabled && interaction is PressInteraction.Release) showDialog = true
        }
    }

    val error = errorMessage
        ?: selectedTime?.let { timeLimitMessage(it, minTime, maxTime, texts) }
    val supporting = error ?: helperText

    Box(
        modifier = modifier.then(
            if (error != null) Modifier.semantics { error(error) } else Modifier,
        ),
    ) {
        OutlinedTextField(
            value = selectedTime?.let(::formatTimeHm) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = isEnabled,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(texts.placeholder) },
            isError = error != null,
            supportingText = supporting?.let { { Text(it) } },
            interactionSource = interactionSource,
            trailingIcon = {
                IconButton(onClick = { showDialog = true }, enabled = isEnabled) {
                    Icon(imageVector = Icons.Filled.Schedule, contentDescription = label)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = isEnabled) { showDialog = true },
        )
    }

    if (showDialog) {
        AppTimePickerDialog(
            selectedTime = selectedTime,
            onTimeSelected = onTimeSelected,
            onDismiss = { showDialog = false },
            minTime = minTime,
            maxTime = maxTime,
            texts = texts,
        )
    }
}

/**
 * **Só o modal** do seletor de hora — `TimePickerDialog` oficial do Material 3, 24h, com o título e
 * a alternância relógio ⇄ teclado do próprio Material (já traduzidos por ele). Para projeto com
 * PROTÓTIPO, que desenha o próprio campo (mesmo papel do [AppDatePickerDialog]).
 *
 * Diferença para o antigo [AppTimePicker] (que segue igual): trabalha em [LocalTime], respeita
 * [minTime]/[maxTime] e oferece o modo teclado. Não expressa "24:00" — para fim de expediente use
 * `ui/calendar/AppDayTimePicker`.
 *
 * @param onTimeSelected a hora confirmada; cancelar não dispara.
 * @param onDismiss fechar — roda também depois de confirmar.
 * @param initialDisplayMode relógio ([TimePickerDisplayMode.Picker]) ou teclado ([TimePickerDisplayMode.Input]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(
    selectedTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    minTime: LocalTime? = null,
    maxTime: LocalTime? = null,
    texts: AppTimeFieldTexts = rememberAppTimeFieldTexts(),
    initialDisplayMode: TimePickerDisplayMode = TimePickerDisplayMode.Picker,
) {
    val initial = remember { initialPickerTime(selectedTime, minTime, maxTime, nowLocalTime()) }
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    var displayMode by remember { mutableStateOf(initialDisplayMode) }

    val chosen = LocalTime(state.hour.coerceIn(0, 23), state.minute.coerceIn(0, 59))
    val limitMessage = timeLimitMessage(chosen, minTime, maxTime, texts)

    TimePickerDialog(
        onDismissRequest = onDismiss,
        // Outra janela: religa o `testTagsAsResourceId` da raiz (ver `DialogTestTags`).
        modifier = Modifier.exposeTestTagsAsResourceId().testTag(DialogTestTags.CONTAINER),
        title = { TimePickerDialogDefaults.Title(displayMode = displayMode) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) {
                        TimePickerDisplayMode.Input
                    } else {
                        TimePickerDisplayMode.Picker
                    }
                },
                displayMode = displayMode,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(chosen)
                    onDismiss()
                },
                enabled = limitMessage == null,
                modifier = Modifier.testTag(DialogTestTags.BTN_CONFIRMAR),
            ) {
                Text(texts.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(DialogTestTags.BTN_CANCELAR)) {
                Text(texts.dismiss)
            }
        },
    ) {
        if (displayMode == TimePickerDisplayMode.Picker) {
            TimePicker(state = state)
        } else {
            TimeInput(state = state)
        }
        if (limitMessage != null) {
            Text(
                text = limitMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
