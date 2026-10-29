package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import br.com.codecacto.kmplib.platform.audience.KmpLibAudience
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Storage abstrato para o estado de avaliação. Permite injetar fakes em testes.
 *
 * Implementação padrão é [PreferencesReviewStore], que persiste via [ReviewPreferences].
 */
interface ReviewStore {
    fun hasReviewed(): Boolean
    fun markReviewed()
    fun getCompletionCount(): Int
    fun incrementCompletionCount(): Int

    /**
     * Exibições automáticas já registradas (2.335.0). O default devolve 0 — um `ReviewStore` próprio
     * que não implemente as quatro funções abaixo segue a regra antiga (sem teto nem lembrete).
     */
    fun getPromptCount(): Int = 0

    /** Instante (epoch ms) da última exibição automática; 0 = nunca (2.335.0). */
    fun getLastPromptAtMillis(): Long = 0L

    /** Registra uma exibição automática em [atMillis] (2.335.0). */
    fun recordPrompt(atMillis: Long) {}

    /** A última config remota guardada, ou `null` (2.335.0). */
    fun readCachedConfig(): CachedReviewConfig? = null

    /** Guarda a config remota lida em [fetchedAtMillis] (2.335.0). */
    fun writeCachedConfig(config: ReviewPromptConfig, fetchedAtMillis: Long) {}
}

/**
 * Adapter que delega para [ReviewPreferences] real.
 */
class PreferencesReviewStore(
    private val prefs: ReviewPreferences = ReviewPreferences()
) : ReviewStore {
    override fun hasReviewed(): Boolean = prefs.hasReviewed()
    override fun markReviewed() = prefs.markReviewed()
    override fun getCompletionCount(): Int = prefs.getCompletionCount()
    override fun incrementCompletionCount(): Int = prefs.incrementCompletionCount()
    override fun getPromptCount(): Int = prefs.getPromptCount()
    override fun getLastPromptAtMillis(): Long = prefs.getLastPromptAt()
    override fun recordPrompt(atMillis: Long) = prefs.recordPrompt(atMillis)

    override fun readCachedConfig(): CachedReviewConfig? {
        val json = prefs.getConfigJson() ?: return null
        val config = runCatching { reviewConfigJson.decodeFromString(ReviewPromptConfig.serializer(), json) }.getOrNull()
            ?: return null
        return CachedReviewConfig(config, prefs.getConfigFetchedAt())
    }

    override fun writeCachedConfig(config: ReviewPromptConfig, fetchedAtMillis: Long) {
        prefs.setConfig(reviewConfigJson.encodeToString(ReviewPromptConfig.serializer(), config), fetchedAtMillis)
    }
}

/** JSON da config da avaliação (cache local e resposta do servidor): tolerante a campo novo. */
val reviewConfigJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}

/**
 * Helper que decide quando mostrar o `AppReviewDialog` baseado em "completions".
 *
 * Mostra o dialog quando o usuário completou [triggerCount] ações relevantes e
 * ainda não avaliou. Persiste via [store] (default: [PreferencesReviewStore]).
 *
 * Uso típico:
 * ```
 * // No Application ou DI
 * val reviewManager = AppReviewManager(triggerCount = 3)
 *
 * // Ao final de uma ação relevante (ex.: salvar uma sessão, completar tarefa)
 * if (reviewManager.onCompletion()) {
 *     showReviewDialog = true
 * }
 *
 * // Quando o dialog for exibido (positivo ou negativo)
 * reviewManager.markShown()
 * ```
 *
 * Em testes, injete uma `ReviewStore` fake (in-memory):
 * ```
 * val store = FakeReviewStore()
 * val manager = AppReviewManager(triggerCount = 3, store = store)
 * ```
 *
 * ## Build de teste nunca pede avaliação (2.238.0)
 *
 * Com o [AutomationMode] ligado (dublê da loja instalado, Test Harness do Android ou
 * `AutomationMode.activate`), [onCompletion] e [shouldShow] devolvem `false` e o contador **não anda**.
 * Sem isso, o contador salvo no emulador chegava ao gatilho rodada após rodada e o diálogo abria no
 * meio da suíte Maestro, escondendo a tela (Chamada Fácil, 02/out/2026). O app não faz nada.
 *
 * ## App infantil nunca pede avaliação (2.259.0)
 *
 * Com [KmpLibAudience] em `KIDS`, o mesmo silêncio: quem está na tela é a criança, e o pedido de
 * avaliação é uma saída para a loja. O "Avaliar" do menu continua existindo — atrás do portão de pais.
 *
 * ## Regra remota (2.335.0)
 *
 * Com [configSource] (o `AppReviewService` da `kmplib-central` monta o do apps-api), a regra vem do
 * Nexus por projeto — [ReviewPromptConfig]: liga/desliga, gatilho, lembrete e teto. O pedido fechado
 * sem resposta VOLTA depois de `remindAfterDays` (até `maxPrompts`); "Sim"/"Não" concluem. Fluxo:
 * [refreshConfig] ao abrir → [onCompletion] no fim do fluxo → exibiu: [onPromptShown] → terminou:
 * [onPromptOutcome]. O `AppReviewPromptHost` faz tudo isso numa linha. Sem rede: última config em
 * cache; sem cache: defaults. **Compatível:** sem [configSource] e com [markShown], o comportamento é
 * o de antes.
 *
 * @param triggerCount número de completions antes de mostrar o dialog (sem config remota; também o
 *   gatilho do fallback).
 * @param store storage persistente. Default usa [PreferencesReviewStore].
 * @param suppressed quando `true`, nenhum pedido sai e nada é contado. Default: [AutomationMode] ou
 *   app infantil ([KmpLibAudience]).
 * @param configSource de onde vem a regra remota (2.335.0); `null` = só a regra local.
 * @param clock relógio em epoch ms (teste).
 * @param configMaxAgeMillis idade máxima da config antes de reler do servidor.
 */
class AppReviewManager(
    private val triggerCount: Int = 3,
    private val store: ReviewStore = PreferencesReviewStore(),
    private val configSource: ReviewConfigSource? = null,
    private val clock: () -> Long = { currentTimeMillis() },
    private val configMaxAgeMillis: Long = DEFAULT_CONFIG_MAX_AGE_MILLIS,
    private val suppressed: () -> Boolean = {
        AutomationMode.suppressesAutomaticPrompts || KmpLibAudience.isKids
    },
) {
    @Volatile
    private var memoryConfig: CachedReviewConfig? = null

    private val refreshMutex = Mutex()

    /** A regra sem servidor: os defaults do contrato com o [triggerCount] do construtor. */
    private val fallbackConfig: ReviewPromptConfig get() = ReviewPromptConfig.DEFAULT.copy(triggerCount = triggerCount)

    /**
     * A regra em vigor (2.335.0): a última lida do servidor (memória → cache do aparelho) ou, sem
     * nenhuma, os defaults `{true, triggerCount, 30, 3}`.
     */
    val config: ReviewPromptConfig
        get() {
            if (configSource == null) return fallbackConfig
            val cached = memoryConfig ?: runCatching { store.readCachedConfig() }.getOrNull()?.also { memoryConfig = it }
            return cached?.config ?: fallbackConfig
        }

    /**
     * Relê a regra do servidor (2.335.0) — chame ao abrir a tela/app (o `rememberAppReviewPrompt` da
     * `kmplib-central` já chama). Respeita o cache: só vai à rede se a última leitura tiver mais que
     * [configMaxAgeMillis] (ou com [force]). Falha de rede mantém a última conhecida. Nunca lança.
     */
    suspend fun refreshConfig(force: Boolean = false): ReviewPromptConfig {
        val source = configSource ?: return fallbackConfig
        refreshMutex.withLock {
            val now = clock()
            val known = memoryConfig ?: runCatching { store.readCachedConfig() }.getOrNull()
            val fresh = known != null && now - known.fetchedAtMillis in 0 until configMaxAgeMillis
            if (fresh && !force) {
                memoryConfig = known
                return known.config
            }
            val fetched = try {
                source.fetch()?.sanitized()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (fetched != null) {
                val entry = CachedReviewConfig(fetched, now)
                memoryConfig = entry
                runCatching { store.writeCachedConfig(fetched, now) }
            } else if (known != null) {
                memoryConfig = known
            }
        }
        return config
    }

    /**
     * Conta uma conclusão de fluxo relevante e diz se é hora de pedir a avaliação (regra
     * [shouldPromptReview] com a [config] em vigor). Com a regra suprimida ou o pedido concluído,
     * `false` e o contador não anda.
     */
    fun onCompletion(): Boolean {
        if (suppressed()) return false
        if (store.hasReviewed()) return false
        store.incrementCompletionCount()
        return evaluate()
    }

    /**
     * Verifica se deve mostrar (sem incrementar). Útil para checagens passivas.
     */
    fun shouldShow(): Boolean = !suppressed() && evaluate()

    private fun evaluate(): Boolean = shouldPromptReview(
        config = config,
        completions = store.getCompletionCount(),
        concluded = store.hasReviewed(),
        promptCount = store.getPromptCount(),
        lastPromptAtMillis = store.getLastPromptAtMillis(),
        nowMillis = clock(),
    )

    /**
     * O pedido AUTOMÁTICO apareceu na tela (2.335.0): soma uma exibição (teto `maxPrompts`) e marca o
     * instante (lembrete `remindAfterDays`). Não chame para o "Avaliar o app" do menu.
     */
    fun onPromptShown() {
        store.recordPrompt(clock())
    }

    /**
     * Como o pedido terminou (2.335.0): [ReviewPromptOutcome.POSITIVE]/[ReviewPromptOutcome.NEGATIVE]
     * concluem (nunca mais pede); [ReviewPromptOutcome.DISMISSED] não grava nada — a exibição já foi
     * contada em [onPromptShown] e o pedido volta depois de `remindAfterDays`.
     */
    fun onPromptOutcome(outcome: ReviewPromptOutcome) {
        when (outcome) {
            ReviewPromptOutcome.POSITIVE, ReviewPromptOutcome.NEGATIVE -> store.markReviewed()
            ReviewPromptOutcome.DISMISSED -> Unit
        }
    }

    /**
     * Marca como avaliado — não mostra mais o dialog. Forma ANTIGA, que conclui já na exibição; quem
     * usa a regra remota chama [onPromptShown] + [onPromptOutcome] (ou o `AppReviewPromptHost`).
     */
    fun markShown() {
        store.markReviewed()
    }

    /** Quantidade atual de completions registradas. */
    fun completionCount(): Int = store.getCompletionCount()

    /** Se o usuário já avaliou (respondeu "Sim" ou "Não", ou a forma antiga [markShown]). */
    fun hasReviewed(): Boolean = store.hasReviewed()

    /** Exibições automáticas registradas (2.335.0). */
    fun promptCount(): Int = store.getPromptCount()

    companion object {
        /** Idade máxima da config remota antes de reler (1 h). O servidor também responde com cache curto. */
        const val DEFAULT_CONFIG_MAX_AGE_MILLIS: Long = 60L * 60 * 1000
    }
}
