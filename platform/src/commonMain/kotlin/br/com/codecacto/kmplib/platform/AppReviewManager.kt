package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.platform.automation.AutomationMode
import br.com.codecacto.kmplib.platform.audience.KmpLibAudience

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
 * @param triggerCount número de completions antes de mostrar o dialog.
 * @param store storage persistente. Default usa [PreferencesReviewStore].
 * @param suppressed quando `true`, nenhum pedido sai e nada é contado. Default: [AutomationMode] ou
 *   app infantil ([KmpLibAudience]).
 */
class AppReviewManager(
    private val triggerCount: Int = 3,
    private val store: ReviewStore = PreferencesReviewStore(),
    private val suppressed: () -> Boolean = {
        AutomationMode.suppressesAutomaticPrompts || KmpLibAudience.isKids
    },
) {
    /**
     * Incrementa contador de completions e retorna `true` se for hora de mostrar
     * o dialog. Retorna `false` se o usuário já avaliou ou ainda não atingiu o
     * trigger.
     */
    fun onCompletion(): Boolean {
        if (suppressed()) return false
        if (store.hasReviewed()) return false
        val count = store.incrementCompletionCount()
        return count >= triggerCount
    }

    /**
     * Verifica se deve mostrar (sem incrementar). Útil para checagens passivas.
     */
    fun shouldShow(): Boolean =
        !suppressed() && !store.hasReviewed() && store.getCompletionCount() >= triggerCount

    /** Marca como avaliado — não mostra mais o dialog. */
    fun markShown() {
        store.markReviewed()
    }

    /** Quantidade atual de completions registradas. */
    fun completionCount(): Int = store.getCompletionCount()

    /** Se o usuário já avaliou. */
    fun hasReviewed(): Boolean = store.hasReviewed()
}
