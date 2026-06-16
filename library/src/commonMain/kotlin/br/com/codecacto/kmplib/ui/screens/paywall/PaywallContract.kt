package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.Plan
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot

/**
 * Estado da tela de paywall (MVI). Imutavel.
 *
 * @property plans Planos disponiveis para upgrade.
 * @property selectedPlanId Id do plano selecionado/destacado (default = primeiro com storeProductId
 *   ou primeiro da lista, resolvido pelo app).
 * @property usage Snapshot de uso que motivou o paywall (opcional, alimenta o UsageMeter no topo).
 * @property isLoading Carregando planos.
 * @property isPurchasing Compra/restauracao em andamento (desabilita CTAs).
 * @property error Mensagem de erro a exibir (opcional).
 */
data class PaywallState(
    val plans: List<Plan> = emptyList(),
    val selectedPlanId: String? = null,
    val usage: UsageSnapshot? = null,
    val isLoading: Boolean = false,
    val isPurchasing: Boolean = false,
    val error: String? = null,
)

/** Acoes do paywall disparadas pela UI stateless para o ViewModel do app. */
sealed interface PaywallAction {
    /** Usuario tocou no CTA de um plano (dispara a compra). */
    data class SelectPlan(val plan: Plan) : PaywallAction

    /** Usuario pediu para restaurar compras. */
    data object Restore : PaywallAction

    /** Usuario fechou o paywall ("Agora nao"). */
    data object Dismiss : PaywallAction
}

/**
 * Textos do paywall (i18n / customizacao por app). Defaults em pt-BR.
 */
data class PaywallTexts(
    val title: String = "Desbloqueie tudo",
    val subtitle: String = "Voce atingiu o limite do plano gratuito. Faca upgrade para continuar.",
    val usageLabel: String = "Seu uso",
    val ctaPrefix: String = "Assinar",
    val restore: String = "Restaurar compras",
    val dismiss: String = "Agora nao",
    val emptyPlans: String = "Nenhum plano disponivel no momento.",
    val freeLabel: String = "Gratis",
)
