package br.com.codecacto.kmplib.monetization.purchase

/**
 * Se o [PurchaseRepository.subscriptionState] já é uma **leitura da loja** ou ainda o valor de
 * partida (2.249.0).
 *
 * Existe porque `subscriptionState` é um `Flow<SubscriptionInfo>` que precisa de um valor inicial, e
 * no adaptador da RevenueCat esse valor (`isActive = false`) é um **marcador**, não uma resposta.
 * Sem este sinal o `MonetizationManager` não tinha como separar "não é assinante" de "a loja ainda
 * não respondeu" — e tratava o assinante como grátis na abertura.
 *
 * Transições permitidas: [PENDING] → [READ] · [PENDING] → [FAILED] · [FAILED] → [READ]. Uma vez
 * [READ], continua [READ]: falha numa releitura posterior **preserva** o estado anterior.
 */
enum class SubscriptionReadState {
    /** Nada foi lido da loja ainda; o `subscriptionState` corrente é o valor de partida. */
    PENDING,

    /** O `subscriptionState` corrente veio da loja (cache do SDK ou rede). */
    READ,

    /** A primeira leitura falhou e nenhuma deu certo depois. */
    FAILED,
}
