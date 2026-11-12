package br.com.codecacto.kmplib.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
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
 * O **tamanho da letra do app** — os três degraus do "A− A+" de Configurações (2.354.0).
 *
 * Não é a escala de acessibilidade do sistema (essa o Compose já aplica sozinho, por cima de tudo):
 * é a preferência **do app**, para o público que pede letra maior sem mexer no aparelho inteiro. Os
 * fatores são os do sistema de design da família cristã (base 17 → 19 → 21): o app passa o
 * [factor] para `AppTheme(fontScale = …)`, e a tipografia inteira escala — inclusive as telas da lib.
 *
 * Ordem e [storageValue] são estáveis (gravados nas preferências): nunca renomear nem reordenar.
 *
 * @property factor multiplicador da tipografia (1,0 · 19/17 · 21/17).
 */
enum class TextScale(val storageValue: String, val factor: Float) {
    /** Base (17 no sistema da família). Default. */
    STANDARD("standard", 1f),

    /** Um degrau acima (19/17 ≈ 1,118). */
    LARGE("large", 19f / 17f),

    /** O maior (21/17 ≈ 1,235). */
    LARGEST("largest", 21f / 17f);

    /** O degrau seguinte; satura no maior. */
    fun larger(): TextScale = entries.getOrElse(ordinal + 1) { this }

    /** O degrau anterior; satura no menor. */
    fun smaller(): TextScale = entries.getOrElse(ordinal - 1) { this }

    /** `true` se ainda dá para aumentar. */
    val canIncrease: Boolean get() = ordinal < entries.lastIndex

    /** `true` se ainda dá para diminuir. */
    val canDecrease: Boolean get() = ordinal > 0

    /** Posição do degrau de 1 a [STEPS] — a barra "1/3, 2/3, 3/3" de Configurações. */
    val step: Int get() = ordinal + 1

    companion object {
        /** Quantos degraus existem. */
        val STEPS: Int get() = entries.size

        /**
         * Lê o valor gravado. Vazio, desconhecido ou corrompido = [STANDARD] — nunca lança: uma
         * preferência ilegível não pode impedir o app de abrir.
         */
        fun fromStorage(value: String?): TextScale {
            val v = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageValue == v } ?: STANDARD
        }
    }
}

/**
 * O [TextScale] em vigor na árvore. Quem desenha com tamanhos próprios (o kit da família, um leitor)
 * multiplica por `LocalTextScale.current.factor`. Default [TextScale.STANDARD]: quem não provê nada
 * não muda.
 */
val LocalTextScale = staticCompositionLocalOf { TextScale.STANDARD }

/**
 * Guarda e publica o [TextScale] escolhido em Configurações, persistido nas [AppPreferences] da lib
 * (memória do APARELHO, não da conta). Mesmo desenho do [ThemeModeController]: **um** por app
 * (`single { TextScaleController() }` no Koin), a raiz lê [scale] para o tema e a tela de
 * Configurações chama [increase]/[decrease]/[setScale] — a troca aparece na hora, sem reiniciar.
 *
 * [scale] nasce em [TextScale.STANDARD] e passa ao valor gravado quando a leitura termina
 * ([isLoaded]). Escolha feita antes de a leitura terminar vence a leitura.
 *
 * No Android exige `initKmpLibCore(context)` antes, como toda preferência da lib.
 */
class TextScaleController(
    private val preferences: AppPreferences = appPreferences(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val key: String = DEFAULT_KEY,
) {
    private data class Selection(val scale: TextScale, val chosenByUser: Boolean)

    private val selection = MutableStateFlow(Selection(TextScale.STANDARD, chosenByUser = false))
    private val writeMutex = Mutex()

    /** O degrau vigente. Default [TextScale.STANDARD]. */
    val scale: StateFlow<TextScale> = selection
        .map { it.scale }
        .stateIn(scope, SharingStarted.Eagerly, TextScale.STANDARD)

    private val _isLoaded = MutableStateFlow(false)

    /** `true` depois que o valor gravado foi lido (ou a leitura falhou e ficou o default). */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        scope.launch {
            val stored = runCatching { TextScale.fromStorage(preferences.getString(key, "")) }
                .getOrDefault(TextScale.STANDARD)
            selection.update { current -> if (current.chosenByUser) current else Selection(stored, false) }
            _isLoaded.value = true
        }
    }

    /** O degrau vigente, lido na hora. */
    val currentScale: TextScale get() = selection.value.scale

    /** Troca o degrau já e grava em segundo plano. */
    fun setScale(scale: TextScale) {
        selection.value = Selection(scale, chosenByUser = true)
        scope.launch {
            writeMutex.withLock {
                runCatching { preferences.setString(key, selection.value.scale.storageValue) }
            }
        }
    }

    /** "A+": sobe um degrau (satura no maior). */
    fun increase() = setScale(currentScale.larger())

    /** "A−": desce um degrau (satura no menor). */
    fun decrease() = setScale(currentScale.smaller())

    companion object {
        /** Chave padrão nas preferências. */
        const val DEFAULT_KEY: String = "kmplib.text_scale"
    }
}
