package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot

/**
 * Medidor de uso "X de Y" para features com cota (freemium-com-limite).
 *
 * Exibe um rotulo ("X de Y usados") e uma barra de progresso. Cores vem do tema ([MaterialTheme])
 * — sem cores hardcoded. Quando perto/no limite, a barra usa a cor de erro do tema (sinaliza
 * upgrade). Cota ilimitada (limite < 0) mostra "Ilimitado" sem barra.
 *
 * A autoridade do limite e o servidor; este componente apenas REFLETE o [UsageSnapshot]
 * (vindo do admin-api ou do contador local de UX).
 *
 * @param usage medidor da feature (contagem/limite/restante).
 * @param label rotulo da feature (ex.: "Recibos este mes"). Opcional.
 * @param warnThreshold fracao a partir da qual a barra fica em cor de alerta (default 0.8).
 */
@Composable
fun UsageMeter(
    usage: UsageSnapshot,
    modifier: Modifier = Modifier,
    label: String? = null,
    warnThreshold: Float = 0.8f,
) {
    val colors = MaterialTheme.colorScheme
    val barColor: Color = when {
        usage.isExhausted -> colors.error
        usage.fraction >= warnThreshold -> colors.error.copy(alpha = 0.85f)
        else -> colors.primary
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface
                )
            }
            Text(
                text = usageText(usage),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (usage.isExhausted) colors.error else colors.onSurfaceVariant
            )
        }

        if (!usage.isUnlimited) {
            LinearProgressIndicator(
                progress = { usage.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = colors.surfaceVariant
            )
        }
    }
}

/** Texto "X de Y usados" ou "Ilimitado". Exposto para reuso (ex.: badge compacto). */
fun usageText(usage: UsageSnapshot): String =
    if (usage.isUnlimited) "Ilimitado"
    else "${usage.contagem} de ${usage.limite} usados"

/**
 * Variante compacta (badge "X/Y") para barras de topo/listas. Reusa [StatusBadge].
 */
@Composable
fun UsageBadge(
    usage: UsageSnapshot,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val text = if (usage.isUnlimited) "∞" else "${usage.contagem}/${usage.limite}"
    StatusBadge(
        text = text,
        textColor = if (usage.isExhausted) colors.onError else colors.onSecondaryContainer,
        backgroundColor = if (usage.isExhausted) colors.error else colors.secondaryContainer,
        modifier = modifier
    )
}
