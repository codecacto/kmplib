package br.com.codecacto.kmplib.ui.screens.paywall

import br.com.codecacto.kmplib.generated.resources.Res
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
import org.jetbrains.compose.resources.getString

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
) {
    /** "Economize 33%". O `%` vai no argumento: `%%` na string não desescapa igual no iOS. */
    fun savingsLabel(percent: Int): String = savingsTemplate.replace("%1\$s", "$percent%")
}

/** [PaywallMessages] no idioma da tela (recursos da lib, 4 idiomas). Falha de leitura → pt-BR. */
suspend fun loadPaywallMessages(): PaywallMessages {
    val own = try {
        PaywallMessages(
            nothingToRestore = getString(Res.string.kmplib_paywall_nothing_to_restore),
            unavailable = getString(Res.string.kmplib_paywall_unavailable),
            savingsTemplate = getString(Res.string.kmplib_paywall_savings),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        PaywallMessages()
    }
    return own.copy(planLabels = loadPaywallPlanLabels(), purchaseErrors = loadPurchaseErrorTexts())
}
