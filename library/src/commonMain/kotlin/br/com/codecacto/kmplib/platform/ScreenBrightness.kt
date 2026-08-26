package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * **Faixa e sentinelas do brilho da tela**, na única unidade que o app manipula: fração `0f..1f`.
 *
 * É a unidade das duas plataformas (`WindowManager.LayoutParams.screenBrightness` no Android,
 * `UIScreen.brightness` no iOS), então nada é convertido duas vezes.
 */
object ScreenBrightnessLevel {

    /** Brilho mínimo. **Não é "tela apagada"**, é o mínimo que o aparelho consegue exibir. */
    const val MIN: Float = 0f

    /** Brilho máximo — o valor do modo "tela como luz". */
    const val MAX: Float = 1f

    /**
     * **"O app não força nada; quem manda é o sistema."**
     *
     * Espelha o `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE` do Android (que também é
     * `-1f`) e é o valor com que [ScreenBrightnessState.overrideLevel] nasce.
     */
    const val SYSTEM: Float = -1f

    /** Leitura indisponível (a plataforma não informou o brilho). Também `-1f`. */
    const val UNKNOWN: Float = -1f

    /**
     * Prende [level] na faixa aplicável.
     *
     * Qualquer valor negativo — e `NaN` — vira [SYSTEM]: se o número veio de um cálculo que deu
     * errado, devolver o controle ao aparelho é o único desfecho seguro (forçar `0f` apagaria a
     * tela; forçar `1f` queimaria bateria).
     */
    fun clamp(level: Float): Float = when {
        level.isNaN() -> SYSTEM
        level < MIN -> SYSTEM
        level > MAX -> MAX
        else -> level
    }

    /** `true` quando [level] é um brilho de verdade (e não [SYSTEM]/[UNKNOWN]). */
    fun isOverride(level: Float): Boolean = !level.isNaN() && level >= MIN

    /** Fração → porcentagem inteira, para exibir. Devolve `-1` quando não há valor. */
    fun percent(level: Float): Int = if (!isOverride(level)) -1 else (level * 100).roundToInt()
}

/**
 * Brilho **da janela do app** agora: o que o app está forçando e o que o aparelho tem configurado.
 */
data class ScreenBrightnessState(
    /** Brilho forçado pelo app, ou [ScreenBrightnessLevel.SYSTEM] quando ele não força nada. */
    val overrideLevel: Float = ScreenBrightnessLevel.SYSTEM,
    /**
     * Brilho do aparelho lido do sistema, ou [ScreenBrightnessLevel.UNKNOWN].
     *
     * É **referência**, não garantia: com brilho automático ligado, o valor efetivo da tela pode
     * estar acima ou abaixo deste (nenhum dos dois SOs expõe o valor pós-ajuste automático).
     */
    val systemLevel: Float = ScreenBrightnessLevel.UNKNOWN,
) {

    /** `true` enquanto o app estiver forçando um brilho. */
    val isOverridden: Boolean get() = ScreenBrightnessLevel.isOverride(overrideLevel)

    /** O brilho que vale agora: o do app quando há override, senão o do sistema. */
    val effective: Float get() = if (isOverridden) overrideLevel else systemLevel

    companion object {
        /** Nada forçado e nada lido ainda. */
        val UNKNOWN: ScreenBrightnessState = ScreenBrightnessState()
    }
}

/**
 * **Controle do brilho da tela ENQUANTO o app está em primeiro plano** — e só dele.
 *
 * O escopo é a **janela do aplicativo**: sair do app, trocar de tela ou fechar o modo devolve o
 * brilho de antes. **Nada aqui altera a configuração do aparelho** e nenhuma permissão nova é
 * exigida (no Android, `Settings.System.SCREEN_BRIGHTNESS` — que mudaria o aparelho inteiro e
 * pediria `WRITE_SETTINGS` — é deliberadamente evitado; ver o `actual` Android).
 *
 * **O ponto crítico é a restauração.** Um app que deixa o brilho no talo depois que a pessoa fechou
 * a tela é um defeito que se sente na bateria e que ninguém consegue atribuir a nenhum app —
 * portanto:
 * - **Android:** restaurar é devolver a janela a `BRIGHTNESS_OVERRIDE_NONE`; o sistema reassume.
 * - **iOS:** o brilho é do **aparelho** e **não volta sozinho** — a lib guarda o valor lido antes do
 *   primeiro override e o repõe. Por isso [release] não é opcional no iOS.
 *
 * Prefira a forma declarativa ([ScreenBrightness]/[rememberScreenBrightnessController]), em que a
 * restauração é do Compose. Use [createScreenBrightnessController] só quando o dono do brilho não
 * for uma composição — e aí `release()` é responsabilidade de quem criou.
 *
 * ```kotlin
 * // "tela como luz": branco no máximo, tela acesa e brilho no talo
 * KeepScreenOn()
 * ScreenBrightness(level = ScreenBrightnessLevel.MAX)
 * ```
 */
interface ScreenBrightnessController {

    /** Override do app + brilho do sistema, observável. */
    val state: StateFlow<ScreenBrightnessState>

    /**
     * Lê o brilho corrente agora: o override do app, se houver, senão o do sistema.
     * Devolve [ScreenBrightnessLevel.UNKNOWN] quando a plataforma não informa.
     */
    fun current(): Float

    /**
     * Força o brilho da janela do app.
     *
     * O valor de antes é guardado na **primeira** chamada e preservado pelas seguintes — mover um
     * slider cem vezes não pode fazer a lib "esquecer" para onde voltar.
     *
     * @param level fração `0f..1f`; fora da faixa, ver [ScreenBrightnessLevel.clamp].
     */
    fun setBrightness(level: Float)

    /** Devolve o brilho ao estado anterior. Sem override ativo, não faz nada. */
    fun restore()

    /** [restore] + solta os recursos. Idempotente. */
    fun release()
}

/**
 * Cria o controlador de brilho da plataforma atual.
 *
 * **Quem cria, libera:** chame [ScreenBrightnessController.release] ao descartar o dono, ou o iOS
 * fica com o brilho forçado. Em composição, use [rememberScreenBrightnessController].
 */
expect fun createScreenBrightnessController(): ScreenBrightnessController

/**
 * Controlador de brilho com ciclo de vida atrelado à composição: restaurado e liberado no
 * `onDispose`.
 *
 * No Android ele se apoia na Activity da própria composição, então **funciona sem
 * `KmpLib.setActivity`** — ao contrário de [createScreenBrightnessController].
 */
@Composable
expect fun rememberScreenBrightnessController(): ScreenBrightnessController

/**
 * **Brilho da tela enquanto esta parte da interface estiver na composição** — o par do
 * [KeepScreenOn] (que só impede a tela de apagar; manter acesa não é aumentar a luz).
 *
 * Ao sair da composição — navegou, fechou o modo, o app foi para trás — o brilho de antes volta,
 * garantido pelo `onDispose`. É de propósito que a forma recomendada seja esta, e não um par
 * `set()`/`restore()` solto: brilho esquecido no máximo é o defeito clássico dessas telas.
 *
 * ```kotlin
 * ScreenBrightness(level = state.brightness, enabled = state.isLightOn)
 * ```
 *
 * @param level fração `0f..1f` (`1f` = máximo).
 * @param enabled `false` devolve o brilho ao normal sem tirar o componente da tela.
 */
@Composable
fun ScreenBrightness(
    level: Float = ScreenBrightnessLevel.MAX,
    enabled: Boolean = true,
) {
    val controller = rememberScreenBrightnessController()
    DisposableEffect(controller, level, enabled) {
        if (enabled) controller.setBrightness(level) else controller.restore()
        onDispose { controller.restore() }
    }
}
