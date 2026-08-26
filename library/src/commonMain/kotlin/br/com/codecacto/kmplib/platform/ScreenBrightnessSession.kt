package br.com.codecacto.kmplib.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Como cada plataforma **desfaz** um override de brilho.
 *
 * A diferença não é estilo: no Android o brilho forçado vive na janela e existe um valor que
 * significa "não force nada" ([ScreenBrightnessLevel.SYSTEM] = `BRIGHTNESS_OVERRIDE_NONE`); no iOS
 * o brilho é do **aparelho**, não há sentinela, e quem subiu precisa repor o número lido antes.
 */
internal enum class BrightnessRestoreMode {
    /** Android: escrever [ScreenBrightnessLevel.SYSTEM] devolve o controle ao sistema. */
    ReleaseToSystem,

    /** iOS: repor explicitamente o valor lido antes do primeiro override. */
    RestorePrevious,
}

/**
 * **A regra de restauração do brilho, em código comum e testável.**
 *
 * Os dois `actual` só sabem ler e escrever o brilho da plataforma; *quando* escrever, *o que*
 * guardar e *para onde* voltar é decidido aqui — o mesmo desenho do
 * [TorchStateMachine][br.com.codecacto.kmplib.torch.TorchStateMachine].
 *
 * As três invariantes que ela protege (e que são justamente as que se perdem quando cada plataforma
 * improvisa):
 * 1. o valor anterior é capturado **uma vez**, na primeira aplicação — do contrário o segundo
 *    `setBrightness` guardaria o brilho **já forçado** e "restaurar" viraria "manter no talo";
 * 2. sem override ativo, [restore] **não escreve nada** — mexer no brilho de quem nunca pediu é o
 *    mesmo defeito ao contrário;
 * 3. [release] é idempotente e restaura no máximo uma vez.
 *
 * @param readPlatform brilho efetivo da plataforma agora, ou [ScreenBrightnessLevel.UNKNOWN].
 * @param writePlatform aplica o brilho ([ScreenBrightnessLevel.SYSTEM] = devolver ao sistema).
 */
internal class ScreenBrightnessSession(
    private val restoreMode: BrightnessRestoreMode,
    private val readPlatform: () -> Float,
    private val writePlatform: (Float) -> Unit,
) {

    private val _state = MutableStateFlow(ScreenBrightnessState.UNKNOWN)
    val state: StateFlow<ScreenBrightnessState> = _state.asStateFlow()

    /** Brilho lido antes do primeiro override — o alvo da restauração no iOS. */
    private var previous: Float = ScreenBrightnessLevel.UNKNOWN

    private var released = false

    /** Relê o brilho do sistema (ex.: ao voltar do segundo plano) sem tocar no override. */
    fun refresh() {
        if (released) return
        _state.value = _state.value.copy(systemLevel = normalizeRead(readPlatform()))
    }

    /** Brilho corrente: o override do app quando há, senão a leitura da plataforma. */
    fun current(): Float {
        val override = _state.value.overrideLevel
        if (ScreenBrightnessLevel.isOverride(override)) return override
        return normalizeRead(readPlatform())
    }

    /** Aplica o brilho, guardando o valor anterior na primeira vez. */
    fun set(level: Float) {
        if (released) return
        val target = ScreenBrightnessLevel.clamp(level)
        if (!ScreenBrightnessLevel.isOverride(target)) {
            restore()
            return
        }
        if (!ScreenBrightnessLevel.isOverride(previous)) {
            previous = normalizeRead(readPlatform())
        }
        writePlatform(target)
        _state.value = _state.value.copy(
            overrideLevel = target,
            systemLevel = if (ScreenBrightnessLevel.isOverride(previous)) previous else _state.value.systemLevel,
        )
    }

    /** Devolve o brilho ao estado anterior. Sem override ativo, é no-op. */
    fun restore() {
        if (!_state.value.isOverridden) return
        when (restoreMode) {
            BrightnessRestoreMode.ReleaseToSystem -> writePlatform(ScreenBrightnessLevel.SYSTEM)
            BrightnessRestoreMode.RestorePrevious ->
                if (ScreenBrightnessLevel.isOverride(previous)) {
                    writePlatform(previous)
                } else {
                    // Sem valor anterior legível, forçar um número seria chutar o brilho do
                    // aparelho. Deixa como está e some do caminho.
                    writePlatform(ScreenBrightnessLevel.SYSTEM)
                }
        }
        _state.value = _state.value.copy(overrideLevel = ScreenBrightnessLevel.SYSTEM)
        previous = ScreenBrightnessLevel.UNKNOWN
    }

    /** [restore] + encerra a sessão. Idempotente. */
    fun release() {
        if (released) return
        restore()
        released = true
    }

    private fun normalizeRead(value: Float): Float =
        if (ScreenBrightnessLevel.isOverride(value)) value.coerceIn(ScreenBrightnessLevel.MIN, ScreenBrightnessLevel.MAX)
        else ScreenBrightnessLevel.UNKNOWN
}
