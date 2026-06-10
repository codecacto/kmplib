package br.com.codecacto.kmplib.ui.screens.paywall

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot

/**
 * Estado stateless do Paywall (padrao telas 2.0 da kmplib). O ViewModel monta este estado a partir
 * do `EntitlementState`; a tela so renderiza e dispara `PaywallAction`.
 */
data class PaywallState(
    /** Planos pagos a exibir (geralmente filtrando o free). */
    val plans: List<Plan> = emptyList(),
    /** Contexto da cota estourada (mapeado do 402), para mostrar "voce usou X de Y". Opcional. */
    val quota: QuotaExceeded? = null,
    /** Medidor a exibir no topo (ex.: derivado de [quota] ou do uso atual). Opcional. */
    val usage: UsageSnapshot? = null,
    /** Plano em processo de compra (mostra loading no CTA). */
    val purchasingPlan: String? = null,
    /** Restauracao de compras em andamento. */
    val isRestoring: Boolean = false,
    /** Mensagem de erro a exibir (ex.: compra falhou). */
    val errorMessage: String? = null,
) {
    val isPurchasing: Boolean get() = purchasingPlan != null
}

/** Acoes do Paywall. */
sealed interface PaywallAction {
    /** Usuario tocou o CTA de upgrade de um plano -> dispara compra via RevenueCat. */
    data class SelectPlan(val plan: Plan) : PaywallAction
    /** Restaurar compras anteriores. */
    data object Restore : PaywallAction
    /** Fechar o paywall. */
    data object Dismiss : PaywallAction
}

/** Textos configuraveis (i18n) do Paywall. */
data class PaywallTexts(
    val title: @Composable () -> String = { "Desbloqueie tudo" },
    val subtitle: @Composable () -> String = { "Escolha um plano e continue sem limites." },
    val limitReachedTitle: @Composable () -> String = { "Voce atingiu o limite" },
    val perMonth: @Composable () -> String = { "/mes" },
    val perYear: @Composable () -> String = { "/ano" },
    val lifetime: @Composable () -> String = { "pagamento unico" },
    val upgradeCta: @Composable () -> String = { "Assinar" },
    val restore: @Composable () -> String = { "Restaurar compras" },
    val close: @Composable () -> String = { "Agora nao" },
)
