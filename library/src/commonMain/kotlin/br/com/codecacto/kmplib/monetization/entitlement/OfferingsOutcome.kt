package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.monetization.purchase.PurchaseErrorCode
import br.com.codecacto.kmplib.monetization.purchase.PurchaseException
import br.com.codecacto.kmplib.monetization.purchase.PurchasePackage
import br.com.codecacto.kmplib.monetization.purchase.isPaymentIncident

/**
 * **Como terminou a leitura do catálogo de planos** — o par de [PurchaseOutcome] para o caminho de
 * leitura (2.141.0).
 *
 * Existe porque `List<PurchasePackage>` **não consegue dizer o que aconteceu**: até a 2.140.0 o
 * [EntitlementProvider.offerings] devolvia `emptyList()` tanto para *"a loja respondeu e não há
 * plano nenhum"* quanto para *"não consegui falar com a loja"* — e o app, que só via a lista vazia,
 * tratava as duas como a mesma coisa. O estrago medido (Torneio de Pênalti, 23/ago/2026): abrir a
 * tela de assinatura **sem rede** disparava `PaymentAlertKind.PaywallSemPlano`, severidade `Fatal`,
 * "impossível vender". Como o `PaymentAlertReporter` envia **um alerta por tipo por sessão**, esse
 * falso positivo ainda **queimava o alerta verdadeiro** da sessão: no dia em que a loja realmente
 * devolvesse zero pacote, o canal já estaria mudo.
 *
 * **A régua de quem chama:**
 *
 * | Resultado | O que houve | Alerta de pagamento? |
 * |---|---|---|
 * | [Disponivel] | há plano para vender | não |
 * | [Vazio] | **a loja respondeu, e o catálogo está vazio** | **sim** — `LojaIndisponivel`/`PaywallSemPlano` |
 * | [Falha] | não deu para ler (rede, loja fora, config) | só se [PurchaseErrorCode.isPaymentIncident] |
 * | [Indisponivel] | este build não tem billing (stub, sem chave) | não — é defeito de build, não da loja |
 *
 * Quem só precisa **desenhar a lista** continua usando [pacotes] e ignora o resto.
 */
sealed interface OfferingsOutcome {

    /** Pacotes lidos — vazio em tudo que não seja [Disponivel]. Para a UI que só renderiza. */
    val pacotes: List<PurchasePackage>

    /**
     * **A loja respondeu e o catálogo está vazio de verdade.** É o único resultado que autoriza o
     * alerta de "paywall sem plano": houve resposta, e não há o que vender. Causa típica: offering
     * sem `Package`, produto não aprovado/liberado na loja, `offeringId` que não existe.
     *
     * `false` em [Falha] — não saber o que a loja tem **não é** saber que ela não tem nada.
     */
    val catalogoVazioConfirmado: Boolean get() = this is Vazio

    /** Catálogo lido, com ao menos um pacote vendável. */
    data class Disponivel(override val pacotes: List<PurchasePackage>) : OfferingsOutcome {
        init {
            // Invariante do tipo: "disponível com lista vazia" é exatamente a ambiguidade que esta
            // API existe para matar. Construa por [dePacotes] quando a lista puder vir vazia.
            require(pacotes.isNotEmpty()) {
                "Disponivel exige ao menos um pacote — use OfferingsOutcome.dePacotes(lista)"
            }
        }
    }

    /** A loja respondeu **sem nenhum pacote**. Vender é impossível e alguém daqui precisa agir. */
    data object Vazio : OfferingsOutcome {
        override val pacotes: List<PurchasePackage> get() = emptyList()
    }

    /**
     * **Não foi possível ler** o catálogo — a lista continua desconhecida.
     *
     * @param mensagem texto TÉCNICO (log/detalhe do alerta). **Não exiba**: use
     *   [br.com.codecacto.kmplib.monetization.purchase.userMessage] sobre [code].
     * @param code motivo tipado. `NETWORK_ERROR` (o caso do usuário sem rede) **não** é incidente;
     *   `CONFIGURATION_ERROR`/`STORE_ERROR`/`UNKNOWN` são — ver
     *   [br.com.codecacto.kmplib.monetization.purchase.isPaymentIncident].
     */
    data class Falha(
        val mensagem: String,
        val code: PurchaseErrorCode = PurchaseErrorCode.UNKNOWN,
    ) : OfferingsOutcome {
        override val pacotes: List<PurchasePackage> get() = emptyList()

        /** Atalho de [isPaymentIncident]: esta falha merece alerta de pagamento? */
        val incidente: Boolean get() = code.isPaymentIncident
    }

    /**
     * **Este build não tem billing** — [StubEntitlementProvider] (sem credencial RevenueCat) ou
     * `PurchaseManager` sem repositório. O paywall mostra "planos em breve".
     *
     * Não é [Vazio] de propósito: a loja não foi consultada, então alertar "loja sem pacotes" seria
     * mentira. Se o app **deveria** vender, o defeito é de configuração do build (chave ausente) e o
     * lugar de pegá-lo é o release, não o alerta de runtime.
     */
    data object Indisponivel : OfferingsOutcome {
        override val pacotes: List<PurchasePackage> get() = emptyList()
    }

    companion object {
        /** Lista lida com sucesso → [Disponivel] ou [Vazio] (nunca `Disponivel` vazio). */
        fun dePacotes(pacotes: List<PurchasePackage>): OfferingsOutcome =
            if (pacotes.isEmpty()) Vazio else Disponivel(pacotes)

        /**
         * Converte o `Result` cru de
         * [br.com.codecacto.kmplib.monetization.purchase.PurchaseRepository.getOfferings] no
         * resultado tipado. Use isto também nos apps que falam com o repositório direto, em vez de
         * `getOrDefault(emptyList())` — foi esse `getOrDefault` que apagava a falha.
         *
         * O motivo sai de [PurchaseException.code] quando o repositório o informa (o RevenueCat da
         * lib informa); qualquer outro `Throwable` vira [PurchaseErrorCode.UNKNOWN], que é
         * incidente — desconhecido no caminho do dinheiro se investiga, não se ignora.
         */
        fun deResultado(resultado: Result<List<PurchasePackage>>): OfferingsOutcome =
            resultado.fold(
                onSuccess = { dePacotes(it) },
                onFailure = { erro ->
                    Falha(
                        mensagem = erro.message ?: erro.toString(),
                        code = (erro as? PurchaseException)?.code ?: PurchaseErrorCode.UNKNOWN,
                    )
                },
            )
    }
}
