package br.com.codecacto.kmplib.ui.tour

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences
import kotlinx.coroutines.CancellationException

/**
 * Onde o "já viu" mora na CONTA (2.346.0): vale em qualquer aparelho e sobrevive a reinstalar. O app
 * implementa sobre a própria API, no contrato do `backlib-product-tour`:
 * `GET …/me/tours/seen` → `{seen: []}` · `PUT …/me/tours/seen/{key}`.
 */
interface ProductTourAccountStore {
    suspend fun isSeen(key: String): Boolean
    suspend fun markSeen(key: String)
}

/**
 * "Já viu o tour?" — gravado nas [AppPreferences] da lib (SharedPreferences no Android,
 * NSUserDefaults no iOS) e, com [accountStore], também na conta. Sem [accountStore], a memória é só
 * do APARELHO: reinstalar o app mostra de novo.
 *
 * O [ProductTourHost] usa isto sozinho quando recebe `firstRunKey`; o app só toca aqui para
 * casos especiais (ex.: [reset] num menu de depuração).
 *
 * **Versione a chave** (`"tour.inicio.v1"`): quando o tour mudar de verdade (menu novo), trocar para
 * `v2` mostra o novo a quem já viu o antigo.
 *
 * No Android exige `initKmpLibCore(context)` (ou `KmpLib.init`) antes, como toda preferência da lib.
 */
class ProductTourFirstRun(
    key: String,
    private val preferences: AppPreferences = appPreferences(),
    private val accountStore: ProductTourAccountStore? = null,
) {
    init {
        require(key.isNotBlank()) { "A chave do first-run do tour não pode ser vazia." }
    }

    /** Chave efetiva nas preferências — prefixada para não colidir com as do app. */
    val storageKey: String = STORAGE_PREFIX + key.trim()

    private val storageKeyForAccount: String = key.trim()
    private val syncedKey: String = "$storageKey.account"

    // Marca em memória: a gravação é assíncrona, e uma releitura logo depois não pode reabrir o tour.
    private var seenInMemory = false

    /** `true` se o tour desta chave já foi concluído ou pulado neste aparelho. */
    suspend fun hasBeenSeen(): Boolean =
        seenInMemory || runCatching { preferences.getBoolean(storageKey, false) }.getOrDefault(false)

    /**
     * O tour deve abrir sozinho? Com a conta, é ela que decide. Sem resposta dela o tour NÃO abre: a
     * próxima abertura pergunta de novo, porque mostrar a quem já viu é o defeito a evitar.
     */
    suspend fun shouldOpen(): Boolean {
        if (hasBeenSeen()) {
            syncToAccount()
            return false
        }
        val store = accountStore ?: return true
        val seen = try {
            store.isSeen(storageKeyForAccount)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        if (!seen) return true
        markLocally()
        markSynced()
        return false
    }

    /** Marca só no aparelho: o host chama ao ABRIR, para fechar o app no meio não mostrar de novo. */
    suspend fun markLocally() {
        seenInMemory = true
        runCatching { preferences.setBoolean(storageKey, true) }
    }

    /** Marca como visto, no aparelho e na conta (o host chama ao concluir ou pular). */
    suspend fun markSeen() {
        markLocally()
        syncToAccount()
    }

    /** Sobe para a conta, uma vez só. Falhou? A próxima abertura tenta de novo. */
    private suspend fun syncToAccount() {
        val store = accountStore ?: return
        if (runCatching { preferences.getBoolean(syncedKey, false) }.getOrDefault(false)) return
        try {
            store.markSeen(storageKeyForAccount)
            markSynced()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fica para a próxima abertura.
        }
    }

    private suspend fun markSynced() {
        runCatching { preferences.setBoolean(syncedKey, true) }
    }

    /** Esquece — o tour volta a abrir sozinho na próxima vez. */
    suspend fun reset() {
        seenInMemory = false
        runCatching { preferences.remove(storageKey) }
        runCatching { preferences.remove(syncedKey) }
    }

    companion object {
        /** Prefixo das chaves gravadas. Estável: renomear reabriria o tour para todo mundo. */
        const val STORAGE_PREFIX: String = "kmplib.tour.seen."
    }
}
