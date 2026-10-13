package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A escolha de tema do usuário — **padrão da fábrica para app (fundador, 13/out/2026)**.
 *
 * O app **segue o sistema** ([SYSTEM], o default) e a troca mora em **Configurações**, com as três
 * opções — nunca num botão fixo de alternar claro/escuro na barra do topo. Botão de lua/sol é
 * convenção de SITE (o navegador não é o sistema); no celular o tema é preferência do aparelho, e o
 * app que a ignora com um botão fixo contraria a Apple (HIG, *Dark Mode*) e o Material (*Dark theme*:
 * "deixe o usuário escolher em Configurações, com 'Padrão do sistema'").
 *
 * Como plugar (três linhas): [ThemeModeController] como `single` no Koin, o modo dele em
 * `AppTheme(darkTheme = mode.isDarkTheme())` na raiz, e [ThemeModeSelector] na tela de Configurações.
 *
 * @property storageValue o texto gravado nas preferências — estável, nunca renomear.
 */
enum class ThemeMode(val storageValue: String) {
    /** Segue o claro/escuro do aparelho, inclusive quando ele muda com o app aberto. Default. */
    SYSTEM("system"),

    /** Sempre claro. */
    LIGHT("light"),

    /** Sempre escuro. */
    DARK("dark");

    /** Se este modo desenha escuro, dado o tema atual do aparelho. Regra pura (testável sem tela). */
    fun resolveDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /**
         * Lê o valor gravado. Vazio, desconhecido ou corrompido = [SYSTEM] — nunca lança: uma
         * preferência ilegível não pode impedir o app de abrir.
         */
        fun fromStorage(value: String?): ThemeMode {
            val v = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageValue == v } ?: SYSTEM
        }
    }
}

/**
 * O `darkTheme` do `AppTheme` para este modo: `AppTheme(darkTheme = mode.isDarkTheme())`.
 * Em [ThemeMode.SYSTEM] lê `isSystemInDarkTheme()`, que recompõe quando o aparelho troca.
 */
@Composable
fun ThemeMode.isDarkTheme(): Boolean = resolveDark(isSystemInDarkTheme())

/**
 * Guarda e publica o [ThemeMode] escolhido em Configurações, persistido nas [AppPreferences] da lib
 * (SharedPreferences no Android, NSUserDefaults no iOS — memória do APARELHO, não da conta).
 *
 * Registre **um** por app (`single { ThemeModeController() }` no Koin): a raiz do app lê [mode] para
 * o `AppTheme`, e a tela de Configurações chama [setMode]. Como os dois olham o MESMO objeto, a troca
 * aparece na hora, sem reiniciar.
 *
 * [mode] nasce em [ThemeMode.SYSTEM] e passa ao valor gravado assim que a leitura termina ([isLoaded]);
 * quem escolheu Claro/Escuro e quer evitar um quadro no tema do sistema na abertura segura a primeira
 * tela até [isLoaded] (a Splash já é esse lugar). Uma escolha feita ANTES de a leitura terminar vence
 * a leitura — nunca é desfeita por ela.
 *
 * No Android exige `initKmpLibCore(context)` (ou `KmpLib.init`) antes, como toda preferência da lib.
 *
 * @param preferences onde gravar; teste usa `InMemoryAppPreferences`.
 * @param scope onde a leitura e as gravações rodam (vive o processo inteiro).
 * @param key chave nas preferências.
 */
class ThemeModeController(
    private val preferences: AppPreferences = appPreferences(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val key: String = DEFAULT_KEY,
) {
    /** Modo vigente + se veio de uma escolha do usuário (essa nunca é desfeita pela leitura). */
    private data class Selection(val mode: ThemeMode, val chosenByUser: Boolean)

    private val selection = MutableStateFlow(Selection(ThemeMode.SYSTEM, chosenByUser = false))
    private val writeMutex = Mutex()

    /** O modo vigente. Default [ThemeMode.SYSTEM]. */
    val mode: StateFlow<ThemeMode> = selection
        .map { it.mode }
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val _isLoaded = MutableStateFlow(false)

    /** `true` depois que o valor gravado foi lido (ou a leitura falhou e ficou o default). */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch {
            val stored = runCatching { ThemeMode.fromStorage(preferences.getString(key, "")) }
                .getOrDefault(ThemeMode.SYSTEM)
            // `update` é CAS: se o usuário escolheu enquanto a leitura corria, a escolha fica.
            selection.update { current -> if (current.chosenByUser) current else Selection(stored, false) }
            _isLoaded.value = true
        }
    }

    /** O modo vigente, lido na hora (sem esperar o [mode] propagar). */
    val currentMode: ThemeMode get() = selection.value.mode

    /** Troca o tema já (a tela recompõe no próximo quadro) e grava em segundo plano. */
    fun setMode(mode: ThemeMode) {
        selection.value = Selection(mode, chosenByUser = true)
        scope.launch {
            writeMutex.withLock {
                // Grava o VIGENTE: dois toques rápidos persistem o último, não o que pegou o lock por último.
                runCatching { preferences.setString(key, selection.value.mode.storageValue) }
            }
        }
    }

    companion object {
        /** Chave padrão nas preferências. */
        const val DEFAULT_KEY: String = "kmplib.theme_mode"
    }
}
