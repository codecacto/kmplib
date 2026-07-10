package br.com.codecacto.kmplib.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.components.AppSwitch
import br.com.codecacto.kmplib.ui.theme.AppTheme

/**
 * **AppWeeklyScheduleEditor** — editor de **faixas por dia da semana**, par mobile do
 * `WeeklyScheduleEditor` da weblib (`@codecacto/weblib/calendar`). NOMES e semântica espelhados.
 *
 * Domínio-AGNÓSTICO (não é "horário de barbearia"): expediente do estabelecimento, jornada de um
 * profissional, disponibilidade de uma sala. Cada dia liga/desliga ([AppSwitch]) e, ligado, lista faixas
 * "HH:mm às HH:mm" (via [AppDayTimePicker]); duas ou mais faixas = o intervalo entre elas (almoço).
 *
 * **Sabe expressar "24:00"** (fim de dia / salão que fecha à meia-noite): o campo de fim usa
 * [DayTimeRole.End] (oferta "24:00"); o de início, [DayTimeRole.Start] (não oferta). Valida sobreposição
 * (fronteira **aberta**: 09–12 e 12–19 não colidem), fim ≤ início e faixa vazia via [validateDayRanges] —
 * mostra o problema, não silencia. "Copiar dia para os demais" com presets configuráveis
 * ([copyTargets]: dias úteis / todos / fim de semana).
 *
 * Controlado (o dono do estado passa [value] e recebe [onChange]); acessível (alvos ≥48dp,
 * `contentDescription`); tema 100% por tokens ([MaterialTheme.colorScheme]/[AppTheme]), sem hardcode.
 *
 * @param value Expediente atual (dias ausentes = fechado).
 * @param onChange Callback com o expediente atualizado.
 * @param modifier Modificador do container.
 * @param enabled Se o editor aceita interação.
 * @param weekdayLabels Rótulos dos 7 dias (índice 0=Dom … 6=Sáb). @default pt-BR
 * @param weekStartsOn Primeiro dia exibido: 0=Domingo (default) ou 1=Segunda. Só reordena a exibição.
 * @param stepMin Passo (min) das opções de horário. @default 30
 * @param defaultRange Faixa criada ao ligar um dia / adicionar intervalo. @default 09:00–18:00
 * @param copyTargets Alvos da ação "copiar dia". `[]` esconde a ação. @default todos / dias úteis / fim de semana
 * @param labels Textos (i18n). @default pt-BR
 */
@Composable
fun AppWeeklyScheduleEditor(
    value: List<WeekdaySchedule>,
    onChange: (List<WeekdaySchedule>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    weekdayLabels: List<String> = DEFAULT_SCHEDULE_WEEKDAY_LABELS,
    weekStartsOn: Int = 0,
    stepMin: Int = 30,
    defaultRange: TimeRange = DEFAULT_SCHEDULE_RANGE,
    copyTargets: List<WeekdayCopyTarget> = defaultScheduleCopyTargets(),
    labels: WeeklyScheduleEditorLabels = WeeklyScheduleEditorLabels(),
) {
    val byWeekday = remember(value) { value.associate { it.weekday to it.ranges } }
    val order = remember(weekStartsOn) {
        if (weekStartsOn == 1) listOf(1, 2, 3, 4, 5, 6, 0) else listOf(0, 1, 2, 3, 4, 5, 6)
    }

    fun setDay(weekday: Int, ranges: List<TimeRange>) {
        val next = (0..6).mapNotNull { wd ->
            val r = if (wd == weekday) ranges else byWeekday[wd]
            if (!r.isNullOrEmpty()) WeekdaySchedule(wd, r) else null
        }
        onChange(next)
    }

    fun copyDay(weekday: Int, ranges: List<TimeRange>, targets: List<Int>) {
        onChange(applyRangesToWeekdays(value, weekday, ranges, targets))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        order.forEach { weekday ->
            val label = weekdayLabels.getOrElse(weekday) { DEFAULT_SCHEDULE_WEEKDAY_LABELS[weekday] }
            val ranges = byWeekday[weekday] ?: emptyList()
            DayRow(
                label = label,
                ranges = ranges,
                enabled = enabled,
                stepMin = stepMin,
                copyTargets = copyTargets,
                labels = labels,
                onToggle = { on ->
                    setDay(weekday, if (on) listOf(defaultRange.copy()) else emptyList())
                },
                onChangeRange = { i, r -> setDay(weekday, ranges.mapIndexed { j, old -> if (j == i) r else old }) },
                onAddRange = { setDay(weekday, ranges + defaultRange.copy()) },
                onRemoveRange = { i -> setDay(weekday, ranges.filterIndexed { j, _ -> j != i }) },
                onCopy = { targets -> copyDay(weekday, ranges, targets) },
            )
        }
    }
}

@Composable
private fun DayRow(
    label: String,
    ranges: List<TimeRange>,
    enabled: Boolean,
    stepMin: Int,
    copyTargets: List<WeekdayCopyTarget>,
    labels: WeeklyScheduleEditorLabels,
    onToggle: (Boolean) -> Unit,
    onChangeRange: (Int, TimeRange) -> Unit,
    onAddRange: () -> Unit,
    onRemoveRange: (Int) -> Unit,
    onCopy: (List<Int>) -> Unit,
) {
    val open = ranges.isNotEmpty()
    val issues = remember(ranges) { if (open) validateDayRanges(ranges) else emptyList() }
    val issueByIndex = remember(issues) { issues.groupBy { it.index } }

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSwitch(checked = open, onCheckedChange = onToggle, enabled = enabled)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!open) {
                Text(
                    text = labels.closed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ranges.forEachIndexed { i, range ->
                    val rowIssues = issueByIndex[i].orEmpty()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppDayTimePicker(
                                value = range.start,
                                role = DayTimeRole.Start,
                                onValueChange = { onChangeRange(i, range.copy(start = it)) },
                                modifier = Modifier.weight(1f),
                                stepMin = stepMin,
                                enabled = enabled,
                                contentDescription = "$label — início da faixa ${i + 1}",
                            )
                            Text(
                                text = labels.separator,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AppDayTimePicker(
                                value = range.end,
                                role = DayTimeRole.End,
                                onValueChange = { onChangeRange(i, range.copy(end = it)) },
                                modifier = Modifier.weight(1f),
                                stepMin = stepMin,
                                enabled = enabled,
                                contentDescription = "$label — fim da faixa ${i + 1}",
                            )
                            IconButton(
                                onClick = { onRemoveRange(i) },
                                enabled = enabled,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "${labels.removeRange} ${i + 1} — $label",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (rowIssues.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = issueMessage(rowIssues.first().kind, labels),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onAddRange, enabled = enabled) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(labels.addRange)
                    }
                    if (copyTargets.isNotEmpty()) {
                        CopyDayMenu(enabled = enabled, label = labels.copyTo, targets = copyTargets, onCopy = onCopy)
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyDayMenu(
    enabled: Boolean,
    label: String,
    targets: List<WeekdayCopyTarget>,
    onCopy: (List<Int>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            targets.forEach { target ->
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = { Text(target.label) },
                    onClick = { onCopy(target.weekdays); expanded = false },
                )
            }
        }
    }
}

/** Textos (i18n) do [AppWeeklyScheduleEditor]. Defaults pt-BR. */
data class WeeklyScheduleEditorLabels(
    val closed: String = "Fechado",
    val addRange: String = "Adicionar intervalo",
    val copyTo: String = "Copiar para…",
    val removeRange: String = "Remover faixa",
    val separator: String = "às",
    val issueEmpty: String = "Preencha início e fim.",
    val issueInverted: String = "O fim precisa ser depois do início.",
    val issueOverlap: String = "Faixas do mesmo dia não podem se sobrepor.",
)

/** Rótulos pt-BR dos 7 dias (índice 0=Dom … 6=Sáb). */
val DEFAULT_SCHEDULE_WEEKDAY_LABELS: List<String> =
    listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")

/** Faixa padrão ao ligar um dia / adicionar intervalo. */
val DEFAULT_SCHEDULE_RANGE: TimeRange = TimeRange(start = "09:00", end = "18:00")

/** Presets padrão da ação "copiar dia" (todos / dias úteis / fim de semana). */
fun defaultScheduleCopyTargets(
    allLabel: String = "Todos os dias",
    businessLabel: String = "Dias úteis (seg–sex)",
    weekendLabel: String = "Fim de semana",
): List<WeekdayCopyTarget> = listOf(
    WeekdayCopyTarget(id = "all", label = allLabel, weekdays = ALL_WEEKDAYS),
    WeekdayCopyTarget(id = "business", label = businessLabel, weekdays = BUSINESS_WEEKDAYS),
    WeekdayCopyTarget(id = "weekend", label = weekendLabel, weekdays = WEEKEND_WEEKDAYS),
)

private fun issueMessage(kind: RangeIssueKind, labels: WeeklyScheduleEditorLabels): String = when (kind) {
    RangeIssueKind.Empty -> labels.issueEmpty
    RangeIssueKind.Inverted -> labels.issueInverted
    RangeIssueKind.Overlap -> labels.issueOverlap
}
