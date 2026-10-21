package br.com.codecacto.kmplib.monetization.purchase

/**
 * **Troca de plano de quem já assina** (2.316.0) — subir ou descer de degrau, ou trocar o ciclo, pela
 * própria loja, sem criar uma segunda assinatura.
 *
 * O fluxo é o OFICIAL de cada plataforma, e é diferente nas duas:
 *
 * - **Google Play:** produtos de assinatura diferentes não se substituem sozinhos — comprar o novo sem
 *   dizer qual sai deixa a pessoa com DUAS assinaturas, cobrada duas vezes. A troca é a compra do novo
 *   produto **com o produto antigo e o modo de substituição** (`oldProductId` + `replacementMode` na
 *   RevenueCat, `SubscriptionUpdateParams` no Play Billing). É o que [oldProductId] e [replacementMode]
 *   carregam.
 * - **App Store:** os produtos de uma escada moram no MESMO grupo de assinatura (o provisionador cria
 *   assim, com `groupLevel` por degrau — nível 1 = mais alto). Comprar outro produto do grupo É a troca:
 *   subir de nível vale na hora (a Apple devolve o proporcional), descer vale na próxima renovação, e
 *   trocar só o período (mesmo nível) também vale na próxima renovação. [oldProductId] e
 *   [replacementMode] são ignorados lá — a regra é da Apple, não do app.
 *
 * @param oldProductId o produto que está ativo na loja **desta** plataforma (`SubscriptionInfo.productId`).
 *   No Play, o id da ASSINATURA, sem o plano base (`premium_v3_mensal_rondaki`, não `…:mensal`) — a
 *   lib corta o `:<plano base>` se ele vier.
 * @param replacementMode como o Play cobra a troca. Ver [StoreReplacementMode].
 */
data class SubscriptionChange(
    val oldProductId: String,
    val replacementMode: StoreReplacementMode,
)

/**
 * **Modo de substituição do Google Play** (`ReplacementMode` do Play Billing / `GoogleReplacementMode`
 * da RevenueCat) — a lib não inventa nome: cada valor é o do fornecedor. Só vale no Android.
 *
 * Recomendação do Google (e o default da lib, em [br.com.codecacto.kmplib.ui.screens.paywall.PaywallTierChangeModes]):
 * subir de degrau → [CHARGE_PRORATED_PRICE] (na hora, mantém a data de cobrança e cobra a diferença
 * proporcional); descer ou trocar o ciclo → [DEFERRED] (vale na próxima renovação — o mesmo que a Apple
 * faz, então a tela diz uma frase só para as duas lojas).
 */
enum class StoreReplacementMode {
    /** Na hora; o tempo que sobrou do plano antigo vira crédito de tempo no novo. Default do Play. */
    WITH_TIME_PRORATION,

    /**
     * Na hora, mantendo a data de cobrança e cobrando a diferença proporcional. **Só existe para
     * upgrade em que o preço por tempo SOBE** — fora disso o Play recusa a compra; a lib cai para
     * [WITH_TIME_PRORATION] quando o preço por mês não sobe ([resolveUpgradeMode]).
     */
    CHARGE_PRORATED_PRICE,

    /** Na hora, cobrando o preço cheio do novo plano; o que sobrou do antigo vira crédito proporcional. */
    CHARGE_FULL_PRICE,

    /** Na hora, sem crédito do antigo; a cobrança do novo começa na próxima data de renovação. */
    WITHOUT_PRORATION,

    /** Só na próxima renovação: até lá a pessoa continua no plano antigo. */
    DEFERRED,
}

/** Sentido de uma troca de degrau, pela `ordem` dos degraus (maior = superior). */
enum class SubscriptionChangeDirection {
    /** Degrau mais alto. */
    UPGRADE,

    /** Degrau mais baixo. */
    DOWNGRADE,

    /** Mesmo degrau, outro ciclo (mensal ↔ semestral ↔ anual). */
    CROSSGRADE,
}

/**
 * O modo de upgrade que o Play aceita: [StoreReplacementMode.CHARGE_PRORATED_PRICE] exige que o preço
 * por tempo **suba**. Se não sobe (degrau acima no anual, vindo do mensal de baixo) ou se não dá para
 * comparar (moedas diferentes, duração desconhecida), cai para [StoreReplacementMode.WITH_TIME_PRORATION]
 * — também imediato, e válido em qualquer troca. Puro, para teste.
 */
fun resolveUpgradeMode(
    preferred: StoreReplacementMode,
    current: PurchasePackage?,
    target: PurchasePackage,
): StoreReplacementMode {
    if (preferred != StoreReplacementMode.CHARGE_PRORATED_PRICE) return preferred
    val atual = current?.microsPerMonth() ?: return StoreReplacementMode.WITH_TIME_PRORATION
    val novo = target.microsPerMonth() ?: return StoreReplacementMode.WITH_TIME_PRORATION
    if (current.currencyCode != target.currencyCode) return StoreReplacementMode.WITH_TIME_PRORATION
    return if (novo > atual) preferred else StoreReplacementMode.WITH_TIME_PRORATION
}

/** Preço por mês em micros (divisão inteira); `null` sem duração ou sem preço. */
private fun PurchasePackage.microsPerMonth(): Long? {
    val meses = durationMonths?.takeIf { it > 0 } ?: return null
    if (priceAmountMicros <= 0L) return null
    return priceAmountMicros / meses
}

/**
 * Id de produto comparável entre as duas formas da loja: no Play a RevenueCat escreve o produto do
 * pacote como `assinatura:plano-base` e o da assinatura ativa só como `assinatura`. Corta o `:…`.
 */
fun normalizeStoreProductId(productId: String): String = productId.substringBefore(':').trim()

/** A loja desta plataforma: [PurchaseStore.PLAY_STORE] no Android, [PurchaseStore.APP_STORE] no iOS. */
fun platformPurchaseStore(): PurchaseStore = PurchaseStore.fromWire(currentStore())
