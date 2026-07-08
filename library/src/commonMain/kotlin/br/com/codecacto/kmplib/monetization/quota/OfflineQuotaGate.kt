package br.com.codecacto.kmplib.monetization.quota

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.monetization.entitlement.AssertResult
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementProvider
import br.com.codecacto.kmplib.monetization.entitlement.EntitlementRepository
import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import br.com.codecacto.kmplib.monetization.entitlement.UsageSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "OfflineQuotaGate"

/** Marcador de "sem teto" em limites e em [UsageSnapshot.limite]. */
const val QUOTA_UNLIMITED: Int = -1

/**
 * Fonte do sinal **premium** para o gate de cota. O gate **nunca** decide isto — ele apenas lê.
 *
 * Implementação canônica: [EntitlementPremiumSource] (RevenueCat via [EntitlementProvider]).
 */
fun interface PremiumSource {
    /** `true` se o usuário tem assinatura ativa (validada pela loja). */
    suspend fun isPremium(): Boolean
}

/**
 * [PremiumSource] sobre o [EntitlementProvider] (RevenueCat) — **resolve a corrida do premium**.
 *
 * ## O bug que isto corrige (real, MundoBandeiras `core/di/AppModule.kt:90`)
 * O gate lia `state.value` (um **snapshot** do `StateFlow`) *antes* do primeiro `refresh()` da loja.
 * No primeiro acesso após um cold start, o RevenueCat ainda não tinha publicado o `customerInfo`, o
 * flow valia `false` — e o app **tratava um assinante Pro como Free, consumindo a cota dele**.
 *
 * A correção: na **primeira** consulta, aguarda um `refresh()` do provider (uma única vez por
 * processo, serializado por mutex — chamadas concorrentes esperam o mesmo priming) e só então lê o
 * `isPremium`. Depois disso o `StateFlow` já está quente e a leitura é imediata.
 *
 * Se o `refresh()` falhar (offline), o valor lido é o **cache local do RevenueCat** (que persiste o
 * `customerInfo` em disco) — Pro continua Pro offline, e um Free **jamais** vira Pro por falha de
 * rede: `isPremium` só é `true` quando a loja disse que é.
 */
class EntitlementPremiumSource(
    private val provider: EntitlementProvider,
) : PremiumSource {

    private val primeMutex = Mutex()
    private var primed = false

    override suspend fun isPremium(): Boolean {
        if (!primed) {
            primeMutex.withLock {
                if (!primed) {
                    runCatching { provider.refresh() }
                        .onFailure { AppLogger.w(TAG, "Priming do premium falhou: ${it.message}") }
                    primed = true
                }
            }
        }
        return provider.isPremium.value
    }
}

/** Atalho: `entitlementProvider.asPremiumSource()`. */
fun EntitlementProvider.asPremiumSource(): PremiumSource = EntitlementPremiumSource(this)

/**
 * Regras puras de gating freemium (sem I/O, sem estado) — servem os limites **estruturais** dos apps
 * offline (ChamadaFacil: 2 turmas ativas; Esquecido: 3 checklists).
 */
object QuotaRules {
    /**
     * Pode executar a ação?
     *
     * @param isPremium sinal da loja. `true` ⇒ sempre permite.
     * @param currentCount contagem atual (turmas ativas, checklists, gerações de hoje...).
     * @param freeLimit teto do plano Free; [QUOTA_UNLIMITED] (`-1`) ⇒ sem teto.
     */
    fun allows(isPremium: Boolean, currentCount: Int, freeLimit: Int): Boolean =
        isPremium || freeLimit == QUOTA_UNLIMITED || currentCount < freeLimit

    /** Quantos usos restam. Premium/ilimitado ⇒ `null`. */
    fun remaining(isPremium: Boolean, currentCount: Int, freeLimit: Int): Int? =
        if (isPremium || freeLimit == QUOTA_UNLIMITED) null
        else (freeLimit - currentCount).coerceAtLeast(0)
}

/** Resultado de tentar consumir/executar uma ação sob cota. */
sealed interface QuotaOutcome {
    /** Liberado. [remaining] = usos restantes hoje; `null` = ilimitado (premium). */
    data class Allowed(val remaining: Int?) : QuotaOutcome

    /** Bloqueado — a UI abre o Paywall com [quota] (alimenta o `UsageMeter`). */
    data class Blocked(val quota: QuotaExceeded) : QuotaOutcome
}

/**
 * **Gate de cota offline-tolerante** — o denominador comum das 4 cópias da onda de onboarding
 * (`Esquecido/QuotaGate`, `ChamadaFacil/QuotaGate`, `MundoBandeiras/QuotaGate`,
 * `NumerosDaSorte/CotaDiariaLocal`). Implementa a **ADR-001** ponto a ponto.
 *
 * ## Contrato de segurança (errar aqui = dar premium de graça)
 * 1. **Premium curto-circuita** — assinante nunca consome cota. O sinal vem da [PremiumSource]
 *    (RevenueCat), **nunca** de estado local; use [EntitlementPremiumSource] para evitar a corrida
 *    do cold start (ver KDoc dela).
 * 2. **Servidor é a verdade** quando existe ([repository] != `null`): cada consumo faz `assertUsage`;
 *    `402` bloqueia e o saldo do servidor **sobrepõe** o espelho local.
 * 3. **Fail-open LIMITADO** — falha de rede/verificação (`AssertResult.Failed`) libera **apenas
 *    enquanto a contagem local < [freeLimit]**. Ao atingir o teto Free offline, **bloqueia**. Falha de
 *    rede NUNCA autopromove nem libera consumo infinito.
 * 4. **Reconcilia ao reconectar** ([reconcile]): reenvia a contagem local (que inclui o consumo
 *    offline) e deixa o servidor decidir o saldo final.
 * 5. **Sem servidor** ([repository] = `null`, app 100% offline): o espelho local é o gate — mesma
 *    regra de teto, sem `assert` nem reconciliação. Burlável por construção (documentado na ADR-001);
 *    a receita real está no RevenueCat.
 *
 * ## Dois formatos de limite
 * - **Consumível diário** (partidas, gerações): [tryConsume] + [DailyQuotaStore] (reset na virada do dia).
 * - **Estrutural/lifetime** (turmas ativas, checklists): [assertStructural] — a contagem é do domínio
 *   (o app passa), não do store; arquivar/excluir libera vaga.
 *
 * @param premium sinal premium (loja). Ver [EntitlementPremiumSource].
 * @param freeLimit teto do plano Free ([QUOTA_UNLIMITED] = sem teto).
 * @param feature nome da feature no contrato de quota (ex.: `"partidas_diarias"`).
 * @param store espelho local dia-aware; obrigatório para [tryConsume]/[reconcile], dispensável em
 *   gate puramente estrutural ([assertStructural]).
 * @param repository fonte de verdade (admin-api); `null` em apps 100% offline.
 */
class OfflineQuotaGate(
    private val premium: PremiumSource,
    private val freeLimit: Int,
    private val feature: String,
    private val store: DailyQuotaStore? = null,
    private val repository: EntitlementRepository? = null,
) {

    /**
     * Tenta consumir 1 uso da cota diária.
     *
     * Ordem: premium ⇒ [QuotaOutcome.Allowed]`(null)` sem tocar no store. Free com [repository]:
     * `assertUsage` ⇒ `Allowed` incrementa limpo · `Denied` sobrepõe com o saldo do servidor e bloqueia
     * · `Failed` cai no **fail-open limitado**. Free sem repository: gate puramente local.
     */
    suspend fun tryConsume(): QuotaOutcome {
        if (premium.isPremium()) return QuotaOutcome.Allowed(remaining = null)
        if (freeLimit == QUOTA_UNLIMITED) return QuotaOutcome.Allowed(remaining = null)

        val mirror = requireStore()
        val localCount = mirror.count()
        val repo = repository ?: return consumeLocally(mirror, localCount, offline = true)

        return when (val res = repo.assertUsage(feature, currentCount = localCount, amount = 1)) {
            AssertResult.Allowed -> {
                val next = mirror.increment(offline = false)
                QuotaOutcome.Allowed(remaining = (freeLimit - next).coerceAtLeast(0))
            }

            is AssertResult.Denied -> {
                mirror.overrideWithServer(serverCount = res.quota.contagem)
                QuotaOutcome.Blocked(res.quota)
            }

            // Rede/servidor indisponível: fail-open LIMITADO ao teto Free local.
            is AssertResult.Failed -> {
                AppLogger.w(TAG, "assertUsage falhou (${res.code}); fail-open limitado ao teto Free.")
                consumeLocally(mirror, localCount, offline = true)
            }
        }
    }

    /** Consumo decidido só pelo espelho local (offline ou app sem backend). */
    private suspend fun consumeLocally(
        mirror: DailyQuotaStore,
        localCount: Int,
        offline: Boolean,
    ): QuotaOutcome =
        if (localCount < freeLimit) {
            val next = mirror.increment(offline = offline)
            QuotaOutcome.Allowed(remaining = (freeLimit - next).coerceAtLeast(0))
        } else {
            QuotaOutcome.Blocked(blocked(localCount))
        }

    private fun requireStore(): DailyQuotaStore = requireNotNull(store) {
        "OfflineQuotaGate: cota consumível exige um DailyQuotaStore (use assertStructural para " +
            "limites estruturais)."
    }

    /**
     * Gate de **limite estrutural** (não-consumível): "pode criar mais um?" dada a [currentCount] de
     * itens ativos do domínio. Premium ⇒ sempre permitido. Não toca no [store].
     *
     * Substitui `ChamadaFacil.QuotaGate.podeCriarTurma` e `Esquecido.QuotaGate.podeCriarChecklist`,
     * já com o premium lido corretamente (sem a corrida do snapshot).
     */
    suspend fun assertStructural(currentCount: Int): QuotaOutcome {
        val isPremium = premium.isPremium()
        return if (QuotaRules.allows(isPremium, currentCount, freeLimit)) {
            QuotaOutcome.Allowed(QuotaRules.remaining(isPremium, currentCount, freeLimit))
        } else {
            QuotaOutcome.Blocked(blocked(currentCount))
        }
    }

    /**
     * Reconciliação ao reconectar (ADR-001 §4). No-op para premium, sem [repository] ou sem consumo
     * offline pendente. Reenvia a contagem local corrente (`amount = 0`, só verificação) e o servidor
     * decide: `Allowed` limpa a fila · `Denied` sobrepõe o saldo · `Failed` mantém a fila para depois.
     */
    suspend fun reconcile() {
        val mirror = store ?: return
        if (premium.isPremium()) {
            mirror.clearPending()
            return
        }
        val repo = repository ?: return
        if (mirror.pendingOffline() <= 0) return

        when (val res = repo.assertUsage(feature, currentCount = mirror.count(), amount = 0)) {
            AssertResult.Allowed -> mirror.clearPending()
            is AssertResult.Denied -> mirror.overrideWithServer(serverCount = res.quota.contagem)
            is AssertResult.Failed -> Unit // ainda sem rede; tenta na próxima reconexão.
        }
    }

    /**
     * Snapshot "X de Y" para `UsageMeter`/`UsageBadge`. Premium ⇒ ilimitado. **Só exibe; não é gate.**
     *
     * @param currentCount para limite estrutural, passe a contagem do domínio; `null` usa o store diário.
     */
    suspend fun usageSnapshot(currentCount: Int? = null): UsageSnapshot {
        if (premium.isPremium()) {
            return UsageSnapshot(feature = feature, contagem = 0, limite = QUOTA_UNLIMITED)
        }
        val count = currentCount ?: requireStore().count()
        return UsageSnapshot(feature = feature, contagem = count, limite = freeLimit)
    }

    private fun blocked(count: Int) =
        QuotaExceeded(feature = feature, limite = freeLimit, contagem = count)
}
