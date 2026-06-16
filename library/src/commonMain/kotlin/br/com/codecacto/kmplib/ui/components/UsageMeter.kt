package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot
import br.com.codecacto.kmplib.ui.theme.AppColors

/**
 * Medidor de uso "X de Y" com barra de progresso para uma quota consumivel.
 *
 * So EXIBE o snapshot reportado pelo servidor — nao decide nem incrementa nada. A barra muda de cor
 * conforme se aproxima do limite (warning) ou esgota (error); features ilimitadas exibem "Ilimitado"
 * sem barra. Cores derivam de [AppColors]/[MaterialTheme] — nenhuma cor hardcoded.
 *
 * @param usage Snapshot de uso vindo da fonte de verdade.
 * @param modifier Modificador externo.
 * @param label Rotulo opcional acima da barra (ex.: "Recibos este mes").
 * @param warnThreshold Fracao a partir da qual a barra fica com cor de aviso (default 0.8).
 */
@Composable
fun UsageMeter(
    usage: UsageSnapshot,
    modifier: Modifier = Modifier,
    label: String? = null,
    warnThreshold: Float = 0.8f,
) {
    val palette = AppColors.current

    val barColor: Color = when {
        usage.isUnlimited -> MaterialTheme.colorScheme.primary
        usage.isExhausted -> palette.error
        usage.fraction >= warnThreshold -> palette.warning
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = usageText(usage),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (usage.isExhausted) palette.error else MaterialTheme.colorScheme.onSurface,
            )
        }

        if (!usage.isUnlimited) {
            LinearProgressIndicator(
                progress = { usage.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

/**
 * Versao compacta em pill (reusa [AppBadge]) — para barras de topo/cards. Cor de fundo reflete o
 * estado da quota (normal/aviso/esgotado). Features ilimitadas exibem "Ilimitado".
 *
 * @param usage Snapshot de uso.
 * @param modifier Modificador externo.
 * @param warnThreshold Fracao a partir da qual usa cor de aviso (default 0.8).
 */
@Composable
fun UsageBadge(
    usage: UsageSnapshot,
    modifier: Modifier = Modifier,
    warnThreshold: Float = 0.8f,
) {
    val palette = AppColors.current
    val background: Color = when {
        usage.isUnlimited -> MaterialTheme.colorScheme.secondaryContainer
        usage.isExhausted -> palette.error
        usage.fraction >= warnThreshold -> palette.warning
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor: Color = when {
        usage.isUnlimited -> MaterialTheme.colorScheme.onSecondaryContainer
        usage.isExhausted || usage.fraction >= warnThreshold -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    AppBadge(
        modifier = modifier,
        text = usageText(usage),
        style = BadgeStyle.PILL,
        backgroundColor = background,
        textColor = textColor,
    )
}

private fun usageText(usage: UsageSnapshot): String =
    if (usage.isUnlimited) "Ilimitado" else "${usage.contagem} de ${usage.limite}"
