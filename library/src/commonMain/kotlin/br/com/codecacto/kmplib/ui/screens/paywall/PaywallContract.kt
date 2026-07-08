package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo

/**
 * Plano exibido no paywall canonico.
 *
 * O preco e SEMPRE a string ja formatada e localizada pela loja ([priceLabel]) — gold-standard:
 * a lib NUNCA calcula preco a partir de `Double`/centavos. O app obtem o preco do produto via
 * RevenueCat/StoreKit/Play Billing e injeta aqui.
 *
 * @property id Chave de selecao do plano = `storeProductId` (ou id interno do plano).
 * @property name Nome localizado do plano (o app provê).
 * @property description Descricao curta opcional (ex.: "Acesso completo por 6 meses").
 * @property priceLabel Preco formatado da loja, ja localizado (ex.: "R$ 29,90"). Fonte de verdade do preco.
 * @property pricePerMonthLabel Preco/mes formatado opcional (ex.: "R$ 4,98/mes"), para planos longos.
 * @property durationLabel Rotulo de duracao opcional (ex.: "6 meses").
 * @property badgeLabel Selo opcional (ex.: "Recomendado" / "Economize 30%").
 * @property highlights Beneficios/destaques listados no card.
 * @property isRecommended Destaca visualmente o plano (borda + cor de tema + selo). **Nunca preencha
 *   isto na mao:** derive com [withDerivedHighlight] — o selo e SEMPRE calculado (maior duracao
 *   elegivel), nunca configurado. Desligar o anual no admin migra o selo sozinho.
 * @property durationMonths Duracao do plano em meses. Canonicos: 1 (Mensal), 6 (Semestral), 12
 *   (Anual). Qualquer outra coisa (`3`, `1200` de um `lifetime` residual, `null` = desconhecido) e
 *   **nao-canonica**: o plano continua visivel/assinavel, mas ordena por ULTIMO e e **inelegivel ao
 *   selo**. Nunca substitua desconhecido por um numero grande para "mandar pro fim".
 * @property isFree Plano gratuito — exibivel, mas **jamais** recebe o selo (nem empatando em duracao
 *   com o mensal).
 */
data class PaywallPlan(
    val id: String,
    val name: String,
    val description: String? = null,
    val priceLabel: String,
    val pricePerMonthLabel: String? = null,
    val durationLabel: String? = null,
    val badgeLabel: String? = null,
    val highlights: List<String> = emptyList(),
    val isRecommended: Boolean = false,
    val durationMonths: Int? = null,
    val isFree: Boolean = false,
)

/**
 * Estado do paywall canonico (MVI). Imutavel; o estado real (compra/restauracao/planos) vive no app.
 *
 * @property plans Planos disponiveis para upgrade.
 * @property selectedPlanId Id do plano selecionado/destacado (resolvido pelo app).
 * @property usage Snapshot de uso opcional que motivou o paywall (alimenta o [UsageMeter] no topo).
 * @property isPremium Usuario ja e premium — mostra o bloco "assinatura ativa" em vez dos planos.
 * @property subscription Detalhes da assinatura ativa (data de expiracao/renovacao), quando premium.
 * @property isLoadingPlans Carregando os planos da loja.
 * @property isPurchasing Compra/restauracao em andamento (desabilita CTAs).
 * @property purchasingPlanId Id do plano cujo CTA esta em loading; `null` durante o restore.
 * @property error Mensagem de erro a exibir (opcional).
 */
data class PaywallState(
    val plans: List<PaywallPlan> = emptyList(),
    val selectedPlanId: String? = null,
    val usage: UsageSnapshot? = null,
    val isPremium: Boolean = false,
    val subscription: SubscriptionInfo? = null,
    val isLoadingPlans: Boolean = false,
    val isPurchasing: Boolean = false,
    val purchasingPlanId: String? = null,
    val error: String? = null,
)

/** Acoes do paywall disparadas pela UI stateless para o ViewModel do app. */
sealed interface PaywallAction {
    /** Usuario tocou no CTA de um plano (dispara a compra). */
    data class SelectPlan(val planId: String) : PaywallAction

    /** Usuario pediu para restaurar compras. */
    data object Restore : PaywallAction

    /** Abrir a Politica de Privacidade (disclosure legal). */
    data object Privacy : PaywallAction

    /** Abrir os Termos de Uso (disclosure legal). */
    data object Terms : PaywallAction

    /** "Precisa de ajuda?" → abrir a tela "Desenvolvido por CodeCacto" / contato. */
    data object OpenDeveloper : PaywallAction

    /** Gerenciar a assinatura ativa (loja). */
    data object ManageSubscription : PaywallAction

    /** Voltar (top bar). */
    data object Back : PaywallAction

    /** Dispensar o card de erro. */
    data object DismissError : PaywallAction
}

/**
 * Textos do paywall (i18n / customizacao por app). Defaults em pt-BR — apps nascem funcionando;
 * cada app sobrescreve via `stringResource(...)` (mecanismo oficial Compose Resources).
 */
data class PaywallTexts(
    // Topo / cabecalho
    val screenTitle: String = "Premium",
    val headerTitle: String = "Desbloqueie tudo",
    val headerSubtitle: String = "Aproveite todos os recursos sem limites.",
    val choosePlanLabel: String = "Escolha seu plano",
    val ctaSubscribe: String = "Assinar",
    val recommendedBadge: String = "Recomendado",
    val restore: String = "Restaurar compras",
    val restoring: String = "Restaurando...",
    val usageLabel: String = "Seu uso",
    val emptyPlans: String = "Nenhum plano disponivel no momento.",
    // Bloco de assinatura ativa
    val activeTitle: String = "Assinatura ativa",
    val activeDescription: String = "Voce tem acesso a todos os recursos premium.",
    val renewsAtLabel: String = "Renova em",
    val expiresAtLabel: String = "Expira em",
    val manageSubscription: String = "Gerenciar assinatura",
    // Disclosure legal (exigencia Apple/Google)
    val legalInfoTitle: String = "Informacoes legais",
    val autoRenewalNotice: String = "A assinatura renova automaticamente, salvo cancelamento ate 24h antes do fim do periodo.",
    val subscriptionDisclosure: String = "O pagamento sera cobrado na conta da loja na confirmacao. " +
        "A assinatura renova-se automaticamente pelo mesmo periodo e valor, a menos que cancelada. " +
        "Gerencie ou cancele a qualquer momento nas configuracoes da sua conta na loja.",
    val privacyPolicy: String = "Politica de Privacidade",
    val termsOfUse: String = "Termos de Uso",
    // Ajuda
    val needHelpTitle: String = "Precisa de ajuda?",
    val needHelpDescription: String = "Fale com o desenvolvedor para tirar duvidas sobre a assinatura.",
    val needHelpButton: String = "Falar com o desenvolvedor",
    // Acessibilidade / acoes
    val backContentDescription: String = "Voltar",
    val errorDismiss: String = "OK",
)
