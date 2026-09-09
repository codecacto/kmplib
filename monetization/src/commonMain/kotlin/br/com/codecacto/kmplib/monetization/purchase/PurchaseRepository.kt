package br.com.codecacto.kmplib.monetization.purchase

import kotlinx.coroutines.flow.Flow

/**
 * Interface para operacoes de compra e assinatura.
 */
interface PurchaseRepository {

    /** Flow que emite o estado atual da assinatura. */
    val subscriptionState: Flow<SubscriptionInfo>

    /** Verifica se o usuario tem assinatura premium ativa. */
    suspend fun isPremium(): Boolean

    /**
     * Retorna os pacotes de assinatura da camada uniforme do RevenueCat (Offering -> Packages),
     * gold-standard. Le o offering [PurchaseConfig.offeringId] (fallback: offering `current`) e
     * mapeia cada `Package` disponivel para [PurchasePackage] (preco JA formatado pela loja). O app
     * compra por [purchasePackage] — nunca pelo ID cru de produto. Falha de rede/loja -> [Result.failure].
     */
    suspend fun getOfferings(): Result<List<PurchasePackage>>

    /**
     * Compra um pacote de assinatura pelo [PurchasePackage.packageId] (camada Offering/Package do
     * RevenueCat). Atualiza [subscriptionState] a partir do `customerInfo` retornado. Chame
     * [getOfferings] antes (para popular o cache de pacotes); se o pacote nao estiver em cache, o
     * repositorio tenta recarregar os offerings automaticamente.
     */
    suspend fun purchasePackage(packageId: String): PurchaseResult

    /**
     * Retorna os produtos disponiveis para compra.
     *
     * @deprecated Assinaturas agora usam [getOfferings] (Offerings/Packages do RevenueCat). Permanece
     *   funcional apenas para consumiveis/pay-per-action ([purchaseConsumable]).
     */
    @Deprecated(
        "Assinaturas usam getOfferings() (Offerings/Packages). getProducts() so p/ consumiveis.",
        ReplaceWith("getOfferings()")
    )
    suspend fun getProducts(): Result<List<PurchaseProduct>>

    /**
     * Compra um produto pelo ID cru.
     *
     * @deprecated Assinaturas agora usam [purchasePackage] (Offerings/Packages do RevenueCat).
     *   Permanece funcional apenas para fluxos consumiveis legados.
     */
    // Sem ReplaceWith: productId (id cru da loja) != packageId (identifier do Package);
    // um quick-fix automatico geraria chamada incorreta. Migrar manualmente para getOfferings()+purchasePackage.
    @Deprecated(
        "Assinaturas usam purchasePackage(packageId) via getOfferings() (Offerings/Packages)."
    )
    suspend fun purchase(productId: String): PurchaseResult

    /**
     * Compra um produto CONSUMIVEL (one-time / pay-per-action). Diferente de [purchase], nao depende
     * de entitlement: devolve a transacao da loja (transactionId/productId) para o app enviar a
     * admin-api, que valida e libera a acao. NAO altera [subscriptionState].
     */
    suspend fun purchaseConsumable(productId: String): ConsumablePurchaseResult

    /** Restaura compras anteriores. */
    suspend fun restorePurchases(): RestoreResult

    /**
     * **Identifica quem assina** na loja — `Purchases.logIn` no RevenueCat, a forma OFICIAL do
     * fornecedor de amarrar a compra a um sujeito conhecido **depois** do `configure`.
     *
     * Existe porque o `appUserId` do [PurchaseInitializer] só pode ser informado no **bootstrap**, e
     * o sujeito real da assinatura muitas vezes só é conhecido **depois do login** (ex.: produto
     * multi-tenant em que quem assina é a ORGANIZAÇÃO, resolvida por `GET /me`). Sem este passo o
     * webhook chega à central com o id anônimo/do usuário e o entitlement vai para o tenant errado —
     * a organização paga e continua bloqueada. Reconfigurar o SDK não é alternativa suportada.
     *
     * **Nome:** `identify`/[resetIdentity], não `logIn`/`logOut`. A API pública da lib é neutra ao
     * fornecedor (como `CrashReporter` é a Sentry) e, principalmente, `logOut()` numa fachada de
     * monetização colidiria com o `signOut()` do módulo de autenticação — dois "logout" no mesmo app,
     * um deles derrubando a sessão e o outro não. A documentação do próprio RevenueCat chama o tema
     * de *Identifying Users*.
     *
     * **Contrato:**
     * - o id passa por [PurchaseIdentity.check] antes do SDK (branco/reservado/anônimo ⇒ falha
     *   [PurchaseIdentityError.INVALID_APP_USER_ID], sem ida à rede);
     * - idempotente: chamar com o id já corrente é no-op do lado do SDK;
     * - **atualiza [subscriptionState]** com o `customerInfo` do novo sujeito — o entitlement em tela
     *   passa a ser o de quem foi identificado, nunca o do sujeito anterior;
     * - **invalida qualquer catálogo em cache** (offerings/packages), porque a oferta pode ser
     *   personalizada por app user (Targeting/Experiments) e o `Package` carrega o contexto de
     *   offering usado para atribuir a compra;
     * - nunca lança: falha vem em `Result.failure` com [PurchaseIdentityException].
     *
     * Tem implementação default (falha explícita [PurchaseIdentityError.UNSUPPORTED]) para não
     * quebrar fakes/impls existentes — a implementação real é a do RevenueCat.
     *
     * @param appUserId identificador estável e opaco do sujeito da assinatura (nunca um id anônimo,
     *   rotativo ou dado pessoal — ver [PurchaseIdentity.looksLikePersonalData]).
     */
    suspend fun identify(appUserId: String): Result<Unit> =
        Result.failure(
            PurchaseIdentityException(
                PurchaseIdentityError.UNSUPPORTED,
                "identify nao suportado por este repository"
            )
        )

    /**
     * Volta a loja para um app user **anônimo** — `Purchases.logOut`. Chamar no logout, para o
     * próximo usuário do mesmo aparelho não herdar o entitlement de quem saiu.
     *
     * Com o app user **já anônimo** a operação é **sucesso no-op**: o SDK devolveria erro
     * (`LogOutWithAnonymousUserError`) para algo que, do ponto de vista do app, é o estado desejado —
     * e esse falso-erro viraria alerta de pagamento sem incidente nenhum por trás. Também invalida o
     * catálogo em cache e reflete o `customerInfo` anônimo em [subscriptionState].
     */
    suspend fun resetIdentity(): Result<Unit> =
        Result.failure(
            PurchaseIdentityException(
                PurchaseIdentityError.UNSUPPORTED,
                "resetIdentity nao suportado por este repository"
            )
        )

    /**
     * App user id corrente na loja (diagnóstico/log — "identifiquei quem?"). `null` quando o SDK não
     * está configurado; com usuário anônimo devolve o id anônimo do próprio SDK
     * ([PurchaseIdentity.ANONYMOUS_ID_PREFIX]) — a resposta honesta, e distinguível por
     * [PurchaseIdentity.isAnonymous].
     */
    fun currentAppUserId(): String? = null


    // ---------------------------------------------------------------------------------------------
    // Venda AVULSA — item não-consumível (compra única, acesso vitalício). Aditivo desde a 2.192.0.
    //
    // Convive com a assinatura sem tocá-la: são catálogos diferentes (produto cru × Offering),
    // resultados diferentes ([ItemPurchaseResult] × [PurchaseResult]) e, principalmente, perguntas
    // diferentes — "quais itens esta pessoa possui" não cabe no "tem assinatura: sim/não" de
    // [restorePurchases]. Todos têm implementação default para não quebrar repositório existente
    // (dublês dos apps, stubs de build sem billing): o default é "esta loja não vende avulso".
    // ---------------------------------------------------------------------------------------------

    /**
     * Lê do catálogo da loja os itens **não-consumíveis** de [productIds].
     *
     * Caminho oficial do fornecedor para não-assinatura (`Purchases.getProducts` por id de produto),
     * e não a camada de Offerings — que existe para plano recorrente e obrigaria a editar o painel a
     * cada curso publicado.
     *
     * **Produto de assinatura passado aqui é descartado**, não vendido: comprá-lo por este caminho
     * criaria uma cobrança recorrente enquanto o app acha que vendeu acesso vitalício. Ele aparece
     * em [StoreItemsOutcome.missingProductIds], que é onde a fábrica vê que o catálogo está errado.
     *
     * Ids que a loja **não conhece** também caem em `missingProductIds` — a loja os omite e responde
     * com sucesso, então sem esse campo o app mostraria um curso a menos sem nada falhar.
     */
    suspend fun getStoreItems(productIds: List<String>): StoreItemsOutcome =
        StoreItemsOutcome.Unavailable

    /**
     * Compra o item não-consumível [productId] (compra única, acesso vitalício).
     *
     * **Não concede nada.** Devolve o recibo em [ItemPurchaseResult.Success] para o app mandar ao
     * servidor conciliar — ver [StorePurchaseClaim] §"quem decide o acesso". Não mexe em
     * [subscriptionState]: item avulso não é assinatura, e um produto pode vender os dois.
     *
     * Chame [getStoreItems] antes (é o que popula o catálogo); sem cache, a implementação recarrega
     * sozinha.
     *
     * **Identifique o comprador antes** ([identify]). Compra feita com app user anônimo não volta:
     * a documentação do fornecedor é explícita de que compra única e não-renovável só se restaura
     * com App User ID próprio, e no Google Play, do Billing Client 8 em diante, o que foi consumido
     * não é mais consultável.
     */
    suspend fun purchaseItem(productId: String): ItemPurchaseResult =
        ItemPurchaseResult.Failed(
            PurchaseErrorCode.CONFIGURATION_ERROR,
            "venda avulsa nao suportada por este repository",
        )

    /**
     * **Restaura as compras avulsas — devolvendo a LISTA de itens**, não um booleano.
     *
     * A mesma leitura traz o estado da assinatura ([ItemRestoreResult.Restored.subscription]) e
     * atualiza [subscriptionState]: a loja abre um diálogo do sistema a cada restauração, e chamar
     * este método e [restorePurchases] em sequência pede a senha da Apple duas vezes.
     *
     * ⚠️ **Só a partir de um toque do usuário** ("Restaurar compras"). Para conciliar sozinho na
     * abertura do app existe [ownedItems], que lê sem prompt.
     */
    suspend fun restoreItems(): ItemRestoreResult =
        ItemRestoreResult.Failed(
            PurchaseErrorCode.CONFIGURATION_ERROR,
            "venda avulsa nao suportada por este repository",
        )

    /**
     * O que a loja **já sabe** que a pessoa possui, **sem interação nenhuma** — leitura do
     * `customerInfo` (cache do SDK, com busca só quando ele julga necessário).
     *
     * Dois usos, e nenhum deles é liberar tela: pintar "Comprado" no catálogo, e conciliar com o
     * servidor na abertura do app. Diferente de [restoreItems], não abre diálogo do sistema — por
     * isso é este o método que pode rodar sozinho.
     *
     * Falha vem em `Result.failure` com [PurchaseException] (código tipado em [PurchaseException.code]).
     */
    suspend fun ownedItems(): Result<StorePurchaseClaim> =
        Result.failure(
            PurchaseException(
                PurchaseErrorCode.CONFIGURATION_ERROR,
                "venda avulsa nao suportada por este repository",
            )
        )

    /** Retorna a info atual da assinatura. */
    suspend fun getSubscriptionInfo(): SubscriptionInfo

    /** Sincroniza o estado com o backend. Chamar no app launch e ao voltar do background. */
    suspend fun syncSubscriptionState()
}
