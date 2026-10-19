package br.com.codecacto.kmplib.ui.tour

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences

/**
 * "Já viu o tour?" — gravado nas [AppPreferences] da lib (SharedPreferences no Android,
 * NSUserDefaults no iOS): memória do APARELHO, não da conta. Reinstalar o app mostra de novo.
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
) {
    init {
        require(key.isNotBlank()) { "A chave do first-run do tour não pode ser vazia." }
    }

    /** Chave efetiva nas preferências — prefixada para não colidir com as do app. */
    val storageKey: String = STORAGE_PREFIX + key.trim()

    // Marca em memória: a gravação é assíncrona, e uma releitura logo depois não pode reabrir o tour.
    private var seenInMemory = false

    /** `true` se o tour desta chave já foi concluído ou pulado neste aparelho. */
    suspend fun hasBeenSeen(): Boolean =
        seenInMemory || runCatching { preferences.getBoolean(storageKey, false) }.getOrDefault(false)

    /** Marca como visto (o host chama ao concluir ou pular). */
    suspend fun markSeen() {
        seenInMemory = true
        runCatching { preferences.setBoolean(storageKey, true) }
    }

    /** Esquece — o tour volta a abrir sozinho na próxima vez. */
    suspend fun reset() {
        seenInMemory = false
        runCatching { preferences.remove(storageKey) }
    }

    companion object {
        /** Prefixo das chaves gravadas. Estável: renomear reabriria o tour para todo mundo. */
        const val STORAGE_PREFIX: String = "kmplib.tour.seen."
    }
}
