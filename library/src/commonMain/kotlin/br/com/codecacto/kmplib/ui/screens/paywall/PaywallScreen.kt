package br.com.codecacto.kmplib.ui.screens.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.money.Money
import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.UsageMeter

/**
 * Paywall **stateless**: recebe [state] + [onAction] + [texts]. Sem `koinViewModel()`, sem rede,
 * sem calculo de negocio — apenas renderiza. O ViewModel do app coleta planos/usage e aplica acoes.
 *
 * Estrutura: header (titulo/subtitulo) + [UsageMeter] (se `usage != null`) + cards de plano
 * (destaques + preco via [Money.formatBRL] quando moeda BRL) + CTA "Assinar" por plano +
 * "Restaurar compras" e "Agora nao". Responsivo via [BoxWithConstraints] (limita a largura do
 * conteudo em telas largas). Sem cores hardcoded.
 */
@Composable
fun PaywallScreen(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    modifier: Modifier = Modifier,
    texts: PaywallTexts = PaywallTexts(),
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        val contentModifier = if (wide) {
            Modifier.fillMaxWidth().widthIn(max = 520.dp)
        } else {
            Modifier.fillMaxWidth()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = contentModifier) {
                Text(
                    text = texts.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = texts.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.usage?.let { usage ->
                    Spacer(Modifier.height(16.dp))
                    UsageMeter(usage = usage, label = texts.usageLabel)
                }

                state.error?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(20.dp))

                when {
                    state.isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.plans.isEmpty() -> {
                        Text(
                            text = texts.emptyPlans,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }

                    else -> {
                        state.plans.forEach { plan ->
                            PlanCard(
                                plan = plan,
                                selected = plan.plano == state.selectedPlanId,
                                isPurchasing = state.isPurchasing,
                                texts = texts,
                                onSelect = { onAction(PaywallAction.SelectPlan(plan)) },
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { onAction(PaywallAction.Restore) },
                        enabled = !state.isPurchasing,
                    ) {
                        Text(texts.restore)
                    }
                    TextButton(
                        onClick = { onAction(PaywallAction.Dismiss) },
                        enabled = !state.isPurchasing,
                    ) {
                        Text(texts.dismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    selected: Boolean,
    isPurchasing: Boolean,
    texts: PaywallTexts,
    onSelect: () -> Unit,
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = plan.nome,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = priceLabel(plan, texts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (plan.destaques.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                plan.destaques.forEach { destaque ->
                    Text(
                        text = "• $destaque",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            AppButton(
                text = "${texts.ctaPrefix} ${plan.nome}",
                onClick = onSelect,
                enabled = !isPurchasing,
                isLoading = isPurchasing && selected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun priceLabel(plan: Plan, texts: PaywallTexts): String {
    val price = plan.preco
    if (price.isNullOrBlank()) return texts.freeLabel
    val formatted = if (plan.moeda.equals("BRL", ignoreCase = true)) {
        Money.formatBRL(price)
    } else {
        "${plan.moeda} $price"
    }
    val interval = plan.intervalo?.let { intervalSuffix(it) }.orEmpty()
    return formatted + interval
}

private fun intervalSuffix(interval: String): String = when (interval.lowercase()) {
    "month", "mensal", "monthly" -> "/mes"
    "year", "anual", "yearly", "annual" -> "/ano"
    "week", "semanal", "weekly" -> "/semana"
    "once", "unico", "lifetime" -> ""
    else -> ""
}
