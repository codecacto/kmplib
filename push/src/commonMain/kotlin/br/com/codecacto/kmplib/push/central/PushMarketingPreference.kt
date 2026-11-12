package br.com.codecacto.kmplib.push.central

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * O interruptor **"Notificações de publicidade"** (2.360.0) — padrão LIGADO, gravado no aparelho
 * (as [AppPreferences] da lib: SharedPreferences / NSUserDefaults).
 *
 * É a fonte do campo `publicidade` do cadastro central do aparelho: campanha do Nexus é publicidade e
 * só vai a quem deixou isto ligado. O registro ([PushRegistrationClient.bind]) observa [enabled] e
 * re-registra sozinho quando muda — a tela só chama [setEnabled].
 *
 * Um por processo: [PushRegistration.marketing]. Construa outro só em teste.
 *
 * @param preferences onde gravar (default: as da lib, lidas sob demanda — no Android exigem
 *   `initKmpLibCore`/`KmpLib.init` antes).
 * @param loadScope onde a primeira leitura roda (troque em teste).
 */
class PushMarketingPreference(
    preferences: AppPreferences? = null,
    loadScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val preferencesOverride = preferences
    private val mutex = Mutex()
    private val _enabled = MutableStateFlow(DEFAULT_ENABLED)
    private val _isLoaded = MutableStateFlow(false)

    /** Ligado = aceita notificação de publicidade. Nasce `true` e passa ao gravado ao carregar. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** `true` depois da primeira leitura (com sucesso ou não). */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        loadScope.launch { load() }
    }

    private fun prefs(): AppPreferences = preferencesOverride ?: appPreferences()

    private suspend fun load() = mutex.withLock {
        if (_isLoaded.value) return@withLock
        val stored = runCatching { prefs().getBoolean(KEY, DEFAULT_ENABLED) }
            .onFailure { AppLogger.w(TAG, "Preferência de publicidade ilegível — fica ligada.") }
            .getOrDefault(DEFAULT_ENABLED)
        _enabled.value = stored
        _isLoaded.value = true
    }

    /** Suspende até a primeira leitura terminar. */
    suspend fun awaitLoaded() {
        if (!_isLoaded.value) load()
        _isLoaded.first { it }
    }

    /** Liga/desliga e grava. Falha ao gravar não desfaz a escolha na tela (log e segue). */
    suspend fun setEnabled(value: Boolean) {
        awaitLoaded()
        mutex.withLock {
            _enabled.value = value
            runCatching { prefs().setBoolean(KEY, value) }
                .onFailure { AppLogger.w(TAG, "Não foi possível gravar a preferência de publicidade.") }
        }
    }

    companion object {
        /** Chave nas preferências — estável, nunca renomear. */
        const val KEY: String = "kmplib.push.publicidade"
        const val DEFAULT_ENABLED: Boolean = true
        private const val TAG = "PushRegistration"
    }
}
