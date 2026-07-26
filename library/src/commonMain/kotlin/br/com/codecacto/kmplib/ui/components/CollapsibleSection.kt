package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Seção **colapsável** com título, contador opcional e chevron que gira — par mobile do
 * `CollapsibleSection` da weblib (usado na vitrine e no wizard de agendamento da web).
 *
 * Serve para listas longas agrupadas (ex.: serviços por categoria) não nascerem gigantes: o usuário
 * abre só o grupo que interessa. O estado aberto/fechado sobrevive a rotação e recomposição
 * (`rememberSaveable`), mas pode ser controlado de fora passando [expanded] + [onExpandedChange].
 *
 * ```kotlin
 * CollapsibleSection(title = "Cabelo", count = 4, defaultOpen = true) {
 *     servicos.forEach { ServiceCard(it) }
 * }
 * ```
 *
 * @param title Título da seção.
 * @param modifier Modificador da seção inteira.
 * @param count Quantos itens a seção tem (aparece ao lado do título). `null` esconde.
 * @param defaultOpen Estado inicial quando NÃO controlado.
 * @param expanded Estado controlado. `null` (default) ⇒ a seção guarda o próprio estado.
 * @param onExpandedChange Chamado no toque do cabeçalho (obrigatório no modo controlado).
 * @param content Conteúdo revelado quando aberta.
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    defaultOpen: Boolean = false,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var internalExpanded by rememberSaveable(title) { mutableStateOf(defaultOpen) }
    val isOpen = expanded ?: internalExpanded
    val rotation by animateFloatAsState(if (isOpen) 180f else 0f, label = "chevron")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    val next = !isOpen
                    if (expanded == null) internalExpanded = next
                    onExpandedChange?.invoke(next)
                }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            count?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}
