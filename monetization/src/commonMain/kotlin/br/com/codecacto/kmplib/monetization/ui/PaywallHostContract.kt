package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_identity_unconfirmed
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_nothing_to_restore
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_savings
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_unavailable
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementController
import br.com.codecacto.kmplib.monetization.entitlement.PlansResult
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot
import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorTexts
import br.com.codecacto.kmplib.monetization.purchase.loadPurchaseErrorTexts
import br.com.codecacto.kmplib.ui.mvi.UiAction
import br.com.codecacto.kmplib.ui.mvi.UiEffect
import br.com.codecacto.kmplib.ui.mvi.UiState
import kotlinx.coroutines.CancellationException
import br.com.codecacto.kmplib.ui.locale.kmpGetString

/**
 * Estado do [PaywallViewModel] (2.224.0). Embute o [PaywallState] que a [PaywallScreen] desenha e
 * acrescenta o que é da TELA, não do paywall: o indicador do puxar-para-atualizar.
 *
 * @property isRefreshing ligado **só pelo gesto** ([PaywallHostAction.Refresh]) — a releitura do
 *   `ON_RESUME` troca o conteúdo sem indicador, e puxar uma vitrine cheia não a troca por esqueleto.
 */
data class PaywallHostState(
    val paywall: PaywallState = PaywallState(isLoadingPlans = true),
    val isRefreshing: Boolean = false,
) : UiState

/** Ações do [PaywallViewModel]. */
sealed interface PaywallHostAction : UiAction {
    /**
     * (Re)lê a oferta: pacotes da loja, oferta central (se configurada) e o estado da assinatura.
     * O [PaywallHost] dispara a cada `ON_RESUME` — quem volta da gestão de assinaturas da loja
     * (cancelou, trocou de plano) vê o estado novo sem reabrir a tela. Ignorada durante uma compra:
     * fechar a folha da loja também é um `ON_RESUME`, e reler ali atropelaria o resultado.
     */
    data object Load : PaywallHostAction

    /** Puxar-para-atualizar: relê ignorando o cache da oferta central, com indicador. */
    data object Refresh : PaywallHostAction

    /** Mostra (ou tira, com `null`) o medidor de uso que motivou o paywall. */
    data class ShowUsage(val usage: UsageSnapshot?) : PaywallHostAction

    /** Ação da [PaywallScreen] repassada ao ViewModel. */
    data class Paywall(val action: PaywallAction) : PaywallHostAction
}

/** Efeitos do [PaywallViewModel] — o [PaywallHost] já executa todos. */
sealed interface PaywallHostEffect : UiEffect {
    /** Voltar (seta do topo) — ou fim da compra, com [PaywallAfterActivation.Close]. */
    data object Close : PaywallHostEffect

    /** Abrir um documento legal no navegador. */
    data class OpenUrl(val url: String) : PaywallHostEffect

    /** Abrir a gestão de assinaturas da loja DA PLATAFORMA (Play no Android, App Store no iOS). */
    data object OpenSubscriptionManagement : PaywallHostEffect

    /** "Precisa de ajuda?" → "Desenvolvido por CodeCacto" / contato. */
    data object OpenDeveloper : PaywallHostEffect

    /** Mensagem pontual (snackbar) — hoje, "nada para restaurar". */
    data class ShowMessage(val message: String) : PaywallHostEffect
}

/**
 * **De onde vem a oferta** que o paywall vende.
 *
 * Não é detalhe de implementação: é uma decisão por projeto, e as duas respostas têm motivo.
 */
sealed interface PaywallOfferSource {

    /**
     * **Só a loja** (Offerings/Packages do RevenueCat). É o caminho de todo app de **login próprio**
     * (own-auth) — a `casca-mobile` nasce assim. As rotas `/v1/projects/{slug}/me/…` do admin-api
     * autenticam por Firebase ID token; ligar a oferta central num app own-auth faria toda leitura
     * tomar 401, cair no fallback e disparar `OfertaCentralIndisponivel` a cada sessão — um alerta
     * falso permanente, que queima o verdadeiro.
     */
    data object Store : PaywallOfferSource

    /**
     * **Oferta central do admin-api ∩ pacotes da loja**, com **fallback pela loja** quando a leitura
     * central falha. O admin-api decide QUAIS planos estão ativos (desligar o anual no painel migra o
     * selo sozinho); a loja dá o preço.
     *
     * A distinção que importa: **oferta ILEGÍVEL ≠ oferta VAZIA**. [PlansResult.Unavailable] (401,
     * rede, 5xx) monta a vitrine só com os pacotes da loja e alerta
     * `OfertaCentralIndisponivel`; [PlansResult.Available] com lista vazia é o projeto sem plano
     * ativo — tela vazia, e alerta `PaywallSemPlano`. Foi a lista vazia nos dois casos que deixou o
     * paywall do LocAki morto para todo mundo, calado, com a loja funcionando (até 28/set/2026).
     *
     * @param readPlans lê a oferta; `forceReload = true` vem do puxar-para-atualizar.
     */
    class CentralWithStoreFallback(
        val readPlans: suspend (forceReload: Boolean) -> PlansResult,
    ) : PaywallOfferSource {
        /** O caso normal: o [EntitlementController] do app (cache com TTL incluso). */
        constructor(controller: EntitlementController) :
            this({ forceReload -> controller.plansResult(forceReload) })
    }
}

/** O que a tela faz quando a compra (ou a restauração) libera o premium. */
enum class PaywallAfterActivation {
    /** Fica e vira "assinatura ativa" + "Gerenciar assinatura" — a própria confirmação. Default. */
    ShowActive,

    /** Fecha a tela ([PaywallHostEffect.Close]) — o paywall foi aberto por um limite e a pessoa volta ao que fazia. */
    Close,
}

/**
 * **Economia dos planos longos** sobre o mensal — opt-in (`PaywallConfig.savings`).
 *
 * O percentual é calculado com os **micros da loja** (`PurchasePackage.priceAmountMicros`), em
 * `Long`, e só contra um mensal **da mesma moeda**; nunca a partir do catálogo central, cujo preço
 * não é o que a loja cobra naquela região. Ver [withStoreSavings].
 *
 * @param badge liga o selo "Economize N%" no card de destaque (ele substitui o "Recomendado").
 * @param badgeLabel texto do selo; `null` usa o da lib no idioma da tela ([PaywallMessages.savingsLabel]).
 * @param pricePerMonthLabel "R$ 4,98/mês" a partir de `(microsPorMês, moeda)`; `null` = não mostra.
 *   A lib **não formata preço** (quem formata é a loja); o app que quer o preço por mês decide como.
 */
class PaywallSavings(
    val badge: Boolean = true,
    val badgeLabel: ((percent: Int) -> String)? = null,
    val pricePerMonthLabel: ((perMonthMicros: Long, currencyCode: String) -> String?)? = null,
)

/**
 * **Uma conta = um trial** em produto com web E app (2.231.0, docs/43 §5).
 *
 * Sem configurar nada (o default), quem decide o trial é **só a loja**: o card promete "7 dias
 * grátis" exatamente quando a loja confirma a elegibilidade. Isso basta para app que só vende pela
 * loja. Produto com web + app informa aqui o que o **nosso** backend sabe — que a conta já usou o
 * trial na outra ponta:
 *
 * - **Android:** a compra vai pelo **plano base, sem a fase grátis**, e o card mostra "Assinar".
 * - **iOS:** a Apple aplica a oferta introdutória sozinha a quem é elegível naquele Apple ID; o app
 *   não consegue recusá-la no mesmo produto. A escolha é do fundador e é SÓ esta configuração:
 *   - [offeringWithoutTrial] `null` (**opção A**): aceita até 7 dias a mais no iPhone — e o card
 *     continua dizendo a verdade ("7 dias grátis", porque a Apple vai dar);
 *   - [offeringWithoutTrial] = id do offering da RevenueCat com as mesmas durações **sem** oferta
 *     introdutória (**opção B**): a tela vende esse offering a quem já usou o trial.
 *
 * @param alreadyUsed lê do backend se a conta já usou o trial — normalmente
 *   `{ controller.entitlement().trialUsed }` (campo `trialUsadoEm` do admin-api). Falha na leitura =
 *   `false`: a loja decide, e o card continua dizendo só o que a loja confirma.
 * @param offeringWithoutTrial ver acima. Só é lido quando [alreadyUsed] é `true` e algum pacote tem
 *   trial que a loja não deixa pular.
 *
 * ⚠️ **Exige o provider da lib** (`PurchaseManagerEntitlementProvider`/`RevenueCatEntitlementProvider`)
 * ou um `EntitlementProvider` próprio que implemente `purchasePackage(packageId, withoutFreeTrial)` e
 * `loadOfferings(offeringId)`. O default dessas sobrecargas compra o pacote como ele é — no Android,
 * COM a fase grátis que o card deixou de mostrar.
 */
class PaywallTrialPolicy(
    val alreadyUsed: suspend () -> Boolean = { false },
    val offeringWithoutTrial: String? = null,
)

/**
 * Configuração do [PaywallViewModel] — o que muda de um app para outro. Todo o resto (ordem Mensal →
 * Semestral → Anual, selo na maior duração, fallback, régua de alertas, compra, restauração) é da lib.
 *
 * @param termsUrl / privacyUrl documentos legais do app (os mesmos do site).
 * @param offerSource ver [PaywallOfferSource]. Default: só a loja.
 * @param recommendedDurationMonths força o selo numa duração; `null` = maior duração elegível (a regra).
 * @param storeHighlights benefícios do card quando ele nasce do pacote da loja (sem `Plan.destaques`).
 * @param savings economia sobre o mensal; `null` = desligado.
 * @param afterActivation ver [PaywallAfterActivation].
 * @param onPremiumActivated roda depois de compra/restauração que liberou o premium — ex.: invalidar o
 *   cache do `AdminApiEntitlementRepository` para a próxima leitura refletir o webhook. Falha aqui é
 *   logada e não desfaz a compra.
 * @param trialPolicy uma conta = um trial em produto web + app (2.231.0). Default: só a loja decide.
 */
class PaywallConfig(
    val termsUrl: String,
    val privacyUrl: String,
    val offerSource: PaywallOfferSource = PaywallOfferSource.Store,
    val recommendedDurationMonths: Int? = null,
    val storeHighlights: (durationMonths: Int) -> List<String> = { emptyList() },
    val savings: PaywallSavings? = null,
    val afterActivation: PaywallAfterActivation = PaywallAfterActivation.ShowActive,
    val onPremiumActivated: suspend () -> Unit = {},
    val trialPolicy: PaywallTrialPolicy = PaywallTrialPolicy(),
)

/**
 * Textos que o [PaywallViewModel] monta **fora da composição** (nomes de plano, erros da loja, avisos).
 * Existe como valor para o teste do ViewModel rodar sem recurso: ele recebe um `PaywallMessages()`
 * pronto. Os defaults são o pt-BR; [loadPaywallMessages] lê o idioma da tela.
 */
data class PaywallMessages(
    val planLabels: PaywallPlanLabels = PaywallPlanLabels(),
    val purchaseErrors: PurchaseErrorTexts = PurchaseErrorTexts(),
    val nothingToRestore: String = "Nenhuma compra encontrada para restaurar.",
    val unavailable: String = "Assinatura indisponível no momento.",
    /** Modelo com `%1$s` = o percentual JÁ com o sinal ("33%"). */
    val savingsTemplate: String = "Economize %1\$s",
    /** Compra/restauração recusada: a loja não ficou com a conta logada (2.233.0). */
    val identityUnconfirmed: String = "Não foi possível vincular a compra à sua conta. Tente de novo em instantes.",
) {
    /** "Economize 33%". O `%` vai no argumento: `%%` na string não desescapa igual no iOS. */
    fun savingsLabel(percent: Int): String = savingsTemplate.replace("%1\$s", "$percent%")
}

/** [PaywallMessages] no idioma da tela (recursos da lib, 4 idiomas). Falha de leitura → pt-BR. */
suspend fun loadPaywallMessages(): PaywallMessages {
    val own = try {
        PaywallMessages(
            nothingToRestore = kmpGetString(Res.string.kmplib_paywall_nothing_to_restore),
            unavailable = kmpGetString(Res.string.kmplib_paywall_unavailable),
            savingsTemplate = kmpGetString(Res.string.kmplib_paywall_savings),
            identityUnconfirmed = kmpGetString(Res.string.kmplib_paywall_identity_unconfirmed),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        PaywallMessages()
    }
    return own.copy(planLabels = loadPaywallPlanLabels(), purchaseErrors = loadPurchaseErrorTexts())
}
