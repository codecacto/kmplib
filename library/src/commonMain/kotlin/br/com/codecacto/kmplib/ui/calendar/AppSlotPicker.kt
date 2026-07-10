package br.com.codecacto.kmplib.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/* ─────────────────────────── AppSlotPicker (G-02b-M) ───────────────────────────
 * Par mobile do `SlotPicker` da weblib: grade de chips de horário para o cliente/recepção escolher.
 * MOBILE-FIRST (alvos ≥48dp via `defaultMinSize`, `FlowRow` que quebra sozinho), acessível
 * (`contentDescription` com motivo do indisponível), tema 100% por tokens (a primária = accent do
 * salão na página pública). NÃO decide disponibilidade — só renderiza os `TimeSlot` que recebe (do
 * motor §8 OU de `generateTimeSlots`).
 */

/** Textos i18n do slot picker (defaults pt-BR). */
data class AppSlotPickerTexts(
    val groupLabel: String = "Horários disponíveis",
    val empty: String = "Sem horários disponíveis.",
    val unavailable: String = "indisponível",
)

/**
 * @param slots Slots a exibir (saída do motor de disponibilidade OU de `generateTimeSlots`).
 * @param value Slot selecionado, por `startMin` (min-do-dia). `null` = nenhum.
 * @param onSelect Disparado ao escolher um slot LIVRE.
 * @param showUnavailable Também exibir indisponíveis (desabilitados). Default `false` (só livres).
 * @param formatSlot Rótulo de um slot. Default `slot.label` ou "HH:mm" de `startMin`.
 * @param emptyState Nó custom p/ o estado vazio (sobrepõe `texts.empty`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSlotPicker(
    slots: List<TimeSlot>,
    modifier: Modifier = Modifier,
    value: Int? = null,
    onSelect: ((TimeSlot) -> Unit)? = null,
    showUnavailable: Boolean = false,
    formatSlot: (TimeSlot) -> String = { it.label ?: formatTimeOfDay(it.startMin) },
    texts: AppSlotPickerTexts = AppSlotPickerTexts(),
    emptyState: (@Composable () -> Unit)? = null,
) {
    val visible = if (showUnavailable) slots else slots.filter { it.available }

    if (visible.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (emptyState != null) {
                emptyState()
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = texts.empty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                }
            }
        }
        return
    }

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = texts.groupLabel },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { slot ->
            SlotChip(
                slot = slot,
                selected = value != null && slot.startMin == value && slot.available,
                label = formatSlot(slot),
                unavailableWord = texts.unavailable,
                onClick = { if (slot.available) onSelect?.invoke(slot) },
            )
        }
    }
}

@Composable
private fun SlotChip(
    slot: TimeSlot,
    selected: Boolean,
    label: String,
    unavailableWord: String,
    onClick: () -> Unit,
) {
    val container = when {
        !slot.available -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        !slot.available -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val a11y = if (slot.available) label else "$label — ${slot.reason ?: unavailableWord}"

    Surface(
        onClick = onClick,
        enabled = slot.available,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.semantics { contentDescription = a11y },
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 72.dp, minHeight = 48.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (slot.available) null else TextDecoration.LineThrough,
            )
        }
    }
}
