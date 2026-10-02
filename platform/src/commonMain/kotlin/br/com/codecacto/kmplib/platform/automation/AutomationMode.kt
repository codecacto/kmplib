package br.com.codecacto.kmplib.platform.automation

import kotlin.concurrent.Volatile

/**
 * Marca as APIs que LIGAM o modo automação. Não é para código de produção: quem liga é o build de
 * teste (o dublê da loja da `kmplib-testing` já faz isso sozinho). Exige `@OptIn(AutomationModeApi::class)`.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Liga o modo automação (some com os pedidos automáticos da lib). Só para build de teste — " +
        "o dublê da loja (kmplib-testing) já o liga sozinho.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class AutomationModeApi

/** De onde veio a certeza de que o processo é de teste. */
enum class AutomationSignal {
    /** O dublê da loja (`kmplib-testing`, `PurchaseTestHooks.instalar`) foi instalado neste processo. */
    STORE_DOUBLE,

    /** O app (ou um teste) declarou o processo como automação por [AutomationMode.activate]. */
    TEST_BUILD,

    /**
     * O aparelho está em **Test Harness Mode** do Android (`adb shell cmd testharness enable`,
     * `ActivityManager.isRunningInUserTestHarness()`, API 29+) ou sob o Monkey
     * (`ActivityManager.isUserAMonkey()`). Sinal OFICIAL do sistema; no iOS não existe equivalente.
     */
    DEVICE_TEST_HARNESS,
}

/**
 * **Modo automação** (2.238.0) — "este processo é um teste automatizado; não interrompa a tela".
 *
 * ## O problema
 *
 * Pedido de avaliação por contagem (`AppReviewManager(triggerCount = 3)`) guarda o contador no
 * APARELHO. Rodada após rodada da suíte Maestro no mesmo emulador, o contador chega ao gatilho e o
 * `AppReviewDialog` abre no meio de um flow — por cima da tela que o passo seguinte procura. Caso
 * real: Chamada Fácil, teste do agente de 02/out/2026. Todo app que pede avaliação por contagem
 * quebraria a suíte do mesmo jeito, num passo diferente a cada vez.
 *
 * ## A regra
 *
 * **Build de teste nunca pede avaliação** (nem oferece atualização opcional). Com o modo ligado,
 * [suppressesAutomaticPrompts] é `true` e a lib cala os pedidos que ELA dispara sozinha:
 *
 * | Pedido | Com o modo ligado |
 * |---|---|
 * | `AppReviewManager.onCompletion()`/`shouldShow()` | `false`, e o contador NÃO anda |
 * | `AppReviewDialog` | não desenha (salvo `suppressInAutomation = false`) |
 * | `SoftUpdateDialog` do `AppServiceGate`/`AppUpdateGate` | não oferece (o Hard e a manutenção CONTINUAM bloqueando) |
 *
 * Fica **de fora, de propósito**: o intersticial (é testado como produto — `AdsTestTags`) e os
 * pedidos de permissão (o app pede quando quer, e o Maestro os responde com `permissions:` no
 * `launchApp`; calar o pedido mudaria o comportamento sob teste).
 *
 * ## Quem liga
 *
 * - **O dublê da loja**: `PurchaseTestHooks.instalar(...)` (artefato `kmplib-testing`, que só entra
 *   no binário com `-Pqa.paywallDemo=true`/`QA_PAYWALL_DEMO=1` e é barrado em variante publicável
 *   pelo plugin `br.com.codecacto.kmplib.store-double`). Nenhuma linha no app.
 * - **O sistema Android em Test Harness Mode** ([AutomationSignal.DEVICE_TEST_HARNESS]) — lido a cada
 *   consulta, sem nada para configurar.
 * - **O app**, explicitamente, por [activate] (opt-in [AutomationModeApi]) — para um build de teste
 *   que não usa o dublê.
 *
 * Build de release não tem nenhum dos três: a `kmplib-testing` não é dependência dele, e aparelho de
 * usuário não está em Test Harness Mode (ligar o modo apaga os dados do aparelho).
 */
object AutomationMode {

    @Volatile
    private var declared: AutomationSignal? = null

    /** O sinal que ligou o modo, ou `null` se o processo não é de automação. */
    val signal: AutomationSignal?
        get() = declared ?: if (isDeviceInTestHarness()) AutomationSignal.DEVICE_TEST_HARNESS else null

    /** O processo é um teste automatizado? */
    val isActive: Boolean
        get() = signal != null

    /** A lib deve calar os pedidos que dispara sozinha (avaliação, atualização opcional)? */
    val suppressesAutomaticPrompts: Boolean
        get() = isActive

    /** Declara o processo como automação. Vale até o fim do processo (ou até [reset]). */
    @AutomationModeApi
    fun activate(signal: AutomationSignal = AutomationSignal.TEST_BUILD) {
        if (declared == null) declared = signal
    }

    /**
     * Esquece a declaração ([activate]). Para a suíte de teste que precisa dos dois estados no mesmo
     * processo; o Test Harness do aparelho continua valendo, porque não é declaração.
     */
    @AutomationModeApi
    fun reset() {
        declared = null
    }
}

/** Test Harness Mode / Monkey no Android; `false` no iOS (sem equivalente na plataforma). */
internal expect fun isDeviceInTestHarness(): Boolean
