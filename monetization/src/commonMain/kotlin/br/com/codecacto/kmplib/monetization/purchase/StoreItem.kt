package br.com.codecacto.kmplib.monetization.purchase

/**
 * **Um item NÃO-CONSUMÍVEL do catálogo da loja** — compra única, acesso vitalício.
 *
 * É o produto que a App Store chama de *non-consumable* e o Google Play de *in-app product* (one
 * time, sem consumo): um curso avulso, um e-book, um pacote de conteúdo. Ele **não é assinatura** e
 * por isso não passa por [PurchasePackage]/`PaywallScreen`, que são a camada de Offerings do
 * RevenueCat e existem para plano recorrente (Mensal → Semestral → Anual).
 *
 * Ler item por **id de produto** é o caminho oficial do fornecedor para não-assinatura
 * (`Purchases.getProducts`), e aqui é o único que serve: num catálogo de cursos os produtos nascem
 * junto com o conteúdo, são dezenas e mudam toda semana — modelá-los como `Package` de um offering
 * obrigaria a editar o painel do RevenueCat a cada curso publicado.
 *
 * @param productId id do produto **na loja** (o mesmo nas duas lojas, por convenção da fábrica).
 *   É a chave que o nosso servidor usa para saber a qual item do catálogo o direito corresponde.
 * @param title título vindo da loja (já localizado por ela).
 * @param description descrição vinda da loja.
 * @param priceLabel preço **já formatado pela loja** ("R$ 149,90"). A lib NUNCA calcula nem formata
 *   preço: moeda, separador e posição do símbolo são responsabilidade da loja, e montar isso aqui é
 *   como se exibe "R$ 149.90" para quem está em Portugal.
 * @param priceAmountMicros preço em micros (1_000_000 = 1 unidade da moeda) — para ordenar e
 *   comparar, nunca para exibir.
 * @param currencyCode código ISO da moeda ("BRL").
 */
data class StoreItem(
    val productId: String,
    val title: String,
    val description: String,
    val priceLabel: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
)

/**
 * Loja de onde a compra saiu — o mesmo vocabulário que [ConsumablePurchaseResult.store] já usa em
 * texto ("play_store"/"app_store"), agora tipado.
 *
 * Existe tipado porque este valor viaja para o **nosso backend** e vira
 * `EntitlementSource.IAP_APPLE`/`IAP_GOOGLE` na concessão do acesso: errar a string faz o estorno
 * procurar o pedido na loja errada.
 */
enum class PurchaseStore(val wireValue: String) {
    /** Google Play. */
    PLAY_STORE("play_store"),

    /** App Store (iOS/macOS). */
    APP_STORE("app_store"),

    /** Plataforma sem loja conhecida (build de teste, dublê). */
    UNKNOWN("unknown");

    companion object {
        /** Lê o valor de fio ("play_store") de volta para o enum; desconhecido ⇒ [UNKNOWN]. */
        fun fromWire(value: String?): PurchaseStore =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * **Como terminou a leitura do catálogo de itens** — o par de [br.com.codecacto.kmplib.monetization.entitlement.OfferingsOutcome] para a venda
 * avulsa (2.192.0).
 *
 * `List<StoreItem>` sozinha não consegue dizer o que aconteceu, e aqui a ambiguidade é pior que no
 * paywall de assinatura: a loja **não erra** quando um id não existe no catálogo dela — ela
 * simplesmente **omite** o produto da resposta e devolve 200. O app pede 12 cursos, recebe 11, e a
 * tela fica plausível: um curso a menos não parece defeito de ninguém. É por isso que
 * [missingProductIds] é campo de primeira classe deste tipo, e não detalhe de log.
 *
 * | Resultado | O que houve | Alerta de pagamento? |
 * |---|---|---|
 * | [Available] | há item para vender | só se [missingProductIds] não estiver vazia |
 * | [Empty] | **a loja respondeu, e nenhum dos ids pedidos existe nela** | **sim** |
 * | [Failed] | não deu para ler (rede, loja fora, config) | só se [PurchaseErrorCode.isPaymentIncident] |
 * | [Unavailable] | este build não tem billing (sem chave, dublê) | não — é defeito de build |
 *
 * Quem só desenha a lista usa [items] e ignora o resto.
 */
sealed interface StoreItemsOutcome {

    /** Itens lidos — vazio em tudo que não seja [Available]. */
    val items: List<StoreItem>

    /**
     * Ids **pedidos que a loja não devolveu**: produto não criado, não aprovado, não liberado no
     * país, ou id digitado errado no nosso catálogo. Cada um é um item que **ninguém consegue
     * comprar**, e a tela não tem como perceber sozinha.
     *
     * Vazia em [Failed]/[Unavailable]: não saber o que a loja tem não é saber o que falta nela.
     */
    val missingProductIds: List<String>

    /**
     * Esta leitura merece alerta de pagamento (`PaymentAlertKind.LojaIndisponivel`)?
     *
     * `true` quando algum id pedido sumiu do catálogo ([Available] com falta, [Empty]) ou quando a
     * falha é incidente da fábrica. `false` para rede caída e para build sem billing.
     */
    val incident: Boolean

    /** Catálogo lido, com ao menos um item vendável. Pode vir com [missingProductIds]. */
    data class Available(
        override val items: List<StoreItem>,
        override val missingProductIds: List<String> = emptyList(),
    ) : StoreItemsOutcome {
        init {
            // Invariante do tipo: "disponível sem item" é a ambiguidade que este selado mata.
            require(items.isNotEmpty()) {
                "Available exige ao menos um item — use StoreItemsOutcome.from(pedidos, lidos)"
            }
        }

        override val incident: Boolean get() = missingProductIds.isNotEmpty()
    }

    /**
     * A loja respondeu e **nenhum** dos ids pedidos existe nela. Vender é impossível e alguém daqui
     * precisa agir — é o "paywall vazio" da venda avulsa.
     */
    data class Empty(override val missingProductIds: List<String>) : StoreItemsOutcome {
        override val items: List<StoreItem> get() = emptyList()
        override val incident: Boolean get() = true
    }

    /**
     * **Não foi possível ler** o catálogo — o que a loja tem continua desconhecido.
     *
     * @param message texto TÉCNICO (log/detalhe do alerta). Não exiba: a frase de tela sai de
     *   [userMessage] sobre [code].
     * @param code motivo tipado. `NETWORK_ERROR` (usuário sem rede) **não** é incidente.
     */
    data class Failed(
        val message: String,
        val code: PurchaseErrorCode = PurchaseErrorCode.UNKNOWN,
    ) : StoreItemsOutcome {
        override val items: List<StoreItem> get() = emptyList()
        override val missingProductIds: List<String> get() = emptyList()
        override val incident: Boolean get() = code.isPaymentIncident
    }

    /**
     * **Este build não tem billing** — sem credencial de loja, ou repositório que não implementa a
     * venda avulsa (dublê, stub). A tela mostra "em breve", não erro.
     *
     * Não é [Empty] de propósito: a loja não foi consultada, então dizer "a loja não tem nada" seria
     * mentira, e a mentira aqui queima o alerta verdadeiro.
     */
    data object Unavailable : StoreItemsOutcome {
        override val items: List<StoreItem> get() = emptyList()
        override val missingProductIds: List<String> get() = emptyList()
        override val incident: Boolean get() = false
    }

    companion object {
        /**
         * Monta o resultado a partir do que foi **pedido** e do que a loja **devolveu** — é aqui que
         * a omissão silenciosa da loja vira [missingProductIds].
         *
         * Preserva a ordem de [requestedProductIds] na lista de itens: a tela do app pede na ordem
         * do nosso catálogo (o curso em destaque primeiro), e a loja responde na ordem dela.
         */
        fun from(
            requestedProductIds: List<String>,
            readItems: List<StoreItem>,
        ): StoreItemsOutcome {
            val byId = readItems.associateBy { it.productId }
            val ordered = requestedProductIds.mapNotNull { byId[it] }
            // Item que a loja devolveu sem ter sido pedido não é descartado: some da ordenação, mas
            // continua vendável, e sumir com produto pago é pior que uma ordem inesperada.
            val extras = readItems.filter { it.productId !in requestedProductIds.toSet() }
            val missing = requestedProductIds.filter { it !in byId }
            val all = ordered + extras
            return if (all.isEmpty()) Empty(missing) else Available(all, missing)
        }
    }
}
