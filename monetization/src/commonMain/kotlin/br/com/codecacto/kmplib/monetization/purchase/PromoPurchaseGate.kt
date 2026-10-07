package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.platform.audience.ParentalGate

/**
 * **Compra promovida da App Store passa pelo portão de pais** (2.259.1).
 *
 * A compra promovida (`PurchasesDelegate.onPurchasePromoProduct`, só iOS — inclui o *purchase
 * intent*) chega por um callback **não suspenso**: quem decide se ela acontece é quem chama o
 * `startPurchase` que o SDK entrega. Esta função embrulha esse `startPurchase` no mesmo padrão
 * não suspenso que a lib já usa nas outras saídas ([ParentalGate.request], par do `guard`): no
 * modo infantil a compra só começa **depois** de o adulto passar; negado (errou, cancelou, não há
 * host na tela, ou já havia outro portão aberto) a compra é **descartada** — o `startPurchase`
 * nunca é chamado, que é como a RevenueCat documenta "não comprar agora", e [onDenied] avisa.
 *
 * Fora do modo infantil o portão não existe e o `startPurchase` roda na hora, na mesma thread —
 * o comportamento de antes.
 *
 * Genérico nos tipos dos callbacks para ser testável sem o SDK.
 */
internal fun <E, S> gatePromoPurchase(
    startPurchase: (onError: E, onSuccess: S) -> Unit,
    onDenied: () -> Unit = {},
): (onError: E, onSuccess: S) -> Unit = { onError, onSuccess ->
    ParentalGate.request { passed ->
        if (passed) startPurchase(onError, onSuccess) else onDenied()
    }
}
