package br.com.codecacto.kmplib.monetization

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * **Premium com resolução explícita** (2.249.0) — a resposta a "esta pessoa é assinante?" que sabe
 * dizer **"ainda não sei"**.
 *
 * ## O defeito que isto corrige
 *
 * [MonetizationManager.isPremium] é `StateFlow<Boolean>` e nasce `false`: antes do primeiro
 * `CustomerInfo` da loja, "não sei ainda" e "não é assinante" chegam idênticos. Um app que decide um
 * gate premium logo na abertura trata o assinante como grátis por um instante — no Super 8, o
 * assinante que abria o Chaveamento via o modal "recurso premium" e, tocando "Agora não", era
 * expulso da tela que tinha pago para usar.
 *
 * Com [PremiumStatus] o gate espera a resposta ([Unknown]) antes de bloquear. A espera tem **teto**
 * ([DEFAULT_TIMEOUT]): sem resposta da loja, vira [Free] com [FreeReason.TIMEOUT] — nunca prende a
 * UI. E é **fail-closed**: só a loja transforma alguém em [Premium]; falha e timeout resolvem como
 * [Free].
 *
 * ## Transições
 *
 * - [Unknown] → [Premium] / [Free] — no primeiro estado de assinatura lido da loja (cache do SDK ou
 *   rede), na leitura que falhou ([FreeReason.STORE_FAILURE]), no teto ([FreeReason.TIMEOUT]), ou na
 *   hora em modo sem assinatura ([FreeReason.NOT_SOLD]).
 * - [Free] ↔ [Premium] — a vida normal (compra, restauração, expiração, troca de conta). Um [Free]
 *   presumido (timeout/falha) **é corrigido** pela leitura que chegar depois: gate que observa o
 *   estado reabre sozinho.
 * - **Nunca volta a [Unknown]** depois de resolvido (só `MonetizationManager.reset()`, de teste).
 */
sealed interface PremiumStatus {

    /** `true` quando já há resposta ([Premium] ou [Free]) — o gate pode decidir. */
    val isResolved: Boolean

    /** `true` só em [Premium]. [Unknown] responde `false` (fail-closed), mas não decida por ele. */
    val isPremium: Boolean

    /** A loja ainda não respondeu. **Não bloqueie nem libere** — mostre carregando, ou espere. */
    data object Unknown : PremiumStatus {
        override val isResolved: Boolean get() = false
        override val isPremium: Boolean get() = false
    }

    /** Assinatura ativa, dita pela loja. */
    data object Premium : PremiumStatus {
        override val isResolved: Boolean get() = true
        override val isPremium: Boolean get() = true
    }

    /**
     * Sem assinatura — e [reason] diz **de onde veio** a resposta: a loja confirmou
     * ([FreeReason.STORE]), o modo não vende ([FreeReason.NOT_SOLD]) ou foi presumido
     * ([FreeReason.STORE_FAILURE]/[FreeReason.TIMEOUT]).
     */
    data class Free(val reason: FreeReason = FreeReason.STORE) : PremiumStatus {
        override val isResolved: Boolean get() = true
        override val isPremium: Boolean get() = false

        /**
         * `true` quando a loja NÃO confirmou (falha/timeout). O app trata como grátis — e, se estiver
         * observando [MonetizationManager.premiumStatus], é corrigido quando a leitura chegar.
         */
        val isAssumed: Boolean
            get() = reason == FreeReason.STORE_FAILURE || reason == FreeReason.TIMEOUT
    }

    /** Por que o estado resolveu como [Free]. */
    enum class FreeReason {
        /** A loja respondeu: não há assinatura ativa. */
        STORE,

        /** O app não vende assinatura (`AdsOnly`) ou não monetiza (`declareNotMonetized`). */
        NOT_SOLD,

        /** A leitura da loja falhou (rede, chave, loja fora) — presumido grátis. */
        STORE_FAILURE,

        /** A loja não respondeu dentro do teto de espera — presumido grátis. */
        TIMEOUT,
    }

    companion object {
        /**
         * Teto default de espera pela primeira resposta da loja. Com o `CustomerInfo` em cache (toda
         * abertura depois da primeira) a resposta vem em milissegundos; o teto só pesa no primeiro
         * uso sem rede — e aí 4 s de "carregando" antes de cair em [Free] é o limite do tolerável.
         */
        val DEFAULT_TIMEOUT: Duration = 4.seconds
    }
}
