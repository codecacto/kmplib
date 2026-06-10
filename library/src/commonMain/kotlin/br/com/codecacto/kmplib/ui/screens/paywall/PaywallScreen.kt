package br.com.codecacto.kmplib.ui.screens.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.UsageMeter

/**
 * Paywall stateless (padrao telas 2.0 da kmplib). Mostra planos pagos + CTA de upgrade.
 *
 * - Tema 100% via [MaterialTheme] (AppTheme) — sem cores hardcoded.
 * - Compra disparada por [PaywallAction.SelectPlan] (o ViewModel chama RevenueCat via
 *   `EntitlementController.purchase`). A tela NAO conhece billing.
 * - Quando vem de um 402, exibe o contexto da cota (UsageMeter "X de Y") no topo.
 *
 * @param state estado renderizado.
 * @param onAction callback unico de acoes.
 * @param texts textos (i18n).
 */
@Composable
fun PaywallScreen(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    modifier: Modifier = Modifier,
    texts: PaywallTexts = PaywallTexts(),
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val limitReached = state.quota != null
            Text(
                text = if (limitReached) texts.limitReachedTitle() else texts.title(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = texts.subtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Contexto da cota (vindo do 402): "X de Y usados"
            state.usage?.let { usage ->
                UsageMeter(usage = usage, modifier = Modifier.padding(vertical = 4.dp))
            }

            // Mensagem de erro (ex.: compra falhou)
            state.errorMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        color = colors.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Cards de planos
            state.plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    texts = texts,
                    isPurchasing = state.purchasingPlan == plan.plano,
                    enabled = !state.isPurchasing && !state.isRestoring,
                    onSelect = { onAction(PaywallAction.SelectPlan(plan)) }
                )
            }

            // Restaurar compras
            TextButton(
                onClick = { onAction(PaywallAction.Restore) },
                enabled = !state.isPurchasing && !state.isRestoring
            ) {
                Text(text = texts.restore(), color = colors.primary)
            }

            // Fechar
            TextButton(
                onClick = { onAction(PaywallAction.Dismiss) },
                enabled = !state.isPurchasing && !state.isRestoring
            ) {
                Text(text = texts.close(), color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    texts: PaywallTexts,
    isPurchasing: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interval = when (plan.intervalo.lowercase()) {
        "monthly" -> texts.perMonth()
        "yearly" -> texts.perYear()
        "lifetime" -> texts.lifetime()
        else -> ""
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = plan.nome,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )

            // Preco + intervalo
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = priceLabel(plan),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                if (interval.isNotEmpty()) {
                    Text(
                        text = " $interval",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Destaques (beneficios)
            plan.destaques.forEach { beneficio ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = beneficio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface
                    )
                }
            }

            AppButton(
                text = texts.upgradeCta(),
                onClick = onSelect,
                isLoading = isPurchasing,
                enabled = enabled,
                primaryColor = colors.primary,
                contentColor = colors.onPrimary
            )
        }
    }
}

private fun priceLabel(plan: Plan): String {
    val preco = plan.preco ?: return "Gratis"
    // preco e decimal canonico "9.90" -> apresentacao BRL simples
    val normalized = preco.replace('.', ',')
    return if (plan.moeda.equals("BRL", ignoreCase = true)) "R$ $normalized" else "${plan.moeda} $normalized"
}
