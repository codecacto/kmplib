package br.com.codecacto.kmplib.platform.install

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.isCanonicalUuid
import br.com.codecacto.kmplib.platform.audience.KmpLibAudience
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **Identificador anônimo da INSTALAÇÃO** (2.335.0) — o que deixa a estatística contar *pessoas*
 * (instalações distintas) e não só eventos: quantas pessoas viram o comunicado, quantas o pedido de
 * avaliação, quantas o house ad.
 *
 * - UUID **v4 aleatório** ([Uuid.random], fonte criptográfica do sistema), gerado no primeiro uso e
 *   guardado nas [AppPreferences] do app (chave [KEY]). Reinstalar ou limpar os dados do app gera
 *   outro — é de propósito: não sobrevive ao app.
 * - **Sem ligação** com conta, aparelho, anúncio (nada de `ANDROID_ID`, IDFA, IDFV) ou servidor: não
 *   identifica ninguém e não é PII. Não use como chave de conta nem como segredo.
 * - **Telemetria usa [forTelemetry]**, que devolve `null` em processo de teste ([AutomationMode]) — a
 *   suíte não fabrica "pessoas" no painel — e em **app infantil** ([KmpLibAudience] `KIDS`, 2.335.1):
 *   os eventos continuam saindo, sem identificador persistente nenhum (Apple Kids / Play Famílias).
 *
 * Contrato do servidor: `docs/tarefas/2026-10-29-estatistica-dos-modais.md` §1 (fábrica).
 *
 * ```kotlin
 * val id: String? = InstallId.forTelemetry()   // null = sem id (teste, preferências indisponíveis)
 * ```
 */
object InstallId {

    /** Chave nas preferências do app. Estável — trocar faria toda instalação virar "pessoa nova". */
    const val KEY: String = "kmplib.install_id"

    private const val TAG = "InstallId"

    private val mutex = Mutex()

    @Volatile
    private var cached: String? = null

    @Volatile
    private var preferencesOverride: AppPreferences? = null

    @Volatile
    internal var isAutomation: () -> Boolean = { runCatching { AutomationMode.isActive }.getOrDefault(false) }

    @Volatile
    internal var isKidsAudience: () -> Boolean = { runCatching { KmpLibAudience.isKids }.getOrDefault(false) }

    /**
     * O id desta instalação, criando-o no primeiro uso. `null` só quando as preferências não estão
     * disponíveis (Android antes do `initKmpLibCore`) — nunca um id só de memória, que contaria a mesma
     * pessoa de novo a cada abertura.
     */
    suspend fun get(): String? {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            try {
                val prefs = preferencesOverride ?: appPreferences()
                val stored = prefs.getString(KEY, "").trim().lowercase()
                val id = if (isCanonicalUuid(stored)) {
                    stored
                } else {
                    newRandomUuidV4().also { prefs.setString(KEY, it) }
                }
                cached = id
                id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLogger.w(TAG, "Preferências indisponíveis — sem identificador de instalação (${e::class.simpleName}).")
                null
            }
        }
    }

    /**
     * O id para mandar em telemetria: `null` em processo de teste ([AutomationMode]), em app infantil
     * ([KmpLibAudience] `KIDS`, 2.335.1 — nem é criado) ou sem preferências.
     */
    suspend fun forTelemetry(): String? = if (isAutomation() || isKidsAudience()) null else get()

    /**
     * Troca o armazenamento (teste, ou app que guarda as preferências da lib num lugar próprio).
     * Esquece o id em memória — o próximo [get] lê de [preferences].
     */
    fun useStorage(preferences: AppPreferences?) {
        preferencesOverride = preferences
        cached = null
    }

    /** Apenas para testes. */
    internal fun resetForTesting() {
        preferencesOverride = null
        cached = null
        isAutomation = { runCatching { AutomationMode.isActive }.getOrDefault(false) }
        isKidsAudience = { runCatching { KmpLibAudience.isKids }.getOrDefault(false) }
    }
}

/** UUID v4 (RFC 9562 §5.4) em texto canônico minúsculo, da fonte aleatória segura do sistema. */
@OptIn(ExperimentalUuidApi::class)
fun newRandomUuidV4(): String = Uuid.random().toString()
