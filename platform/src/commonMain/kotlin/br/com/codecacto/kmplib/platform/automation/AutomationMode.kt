package br.com.codecacto.kmplib.platform.automation

import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

    /**
     * O **runner de QA da fábrica** (`Ferramentas/qa-runner`) marcou o aparelho de teste (2.262.0).
     * Nenhuma linha no app: o runner liga a marca no aparelho antes de cada execução, e a lib a lê
     * a cada consulta (com folga de [QaRunnerSignal.CACHE_TTL]).
     *
     * | Plataforma | Como o runner liga | Como a lib lê | Só vale em |
     * |---|---|---|---|
     * | Android | `adb shell setprop debug.codecacto.automacao 1` | `getprop` (processo do sistema) | app **depurável** (`ApplicationInfo.FLAG_DEBUGGABLE`) |
     * | iOS | `xcrun simctl spawn <udid> defaults write -g CodecactoAutomacao -bool YES` | `NSUserDefaults.standardUserDefaults` | binário Kotlin **debug** (`Platform.isDebugBinary`), alvo de **simulador** e processo `.app` |
     *
     * **Por que nunca vale em release:** a marca só é lida quando o binário é de depuração —
     * Android: o `FLAG_DEBUGGABLE` vem do manifesto mesclado do build, e a Play recusa APK/AAB
     * depurável; iOS: `Platform.isDebugBinary` é fixado na LIGAÇÃO do framework (Xcode *Debug*;
     * Archive/TestFlight/App Store ligam *release*), e a checagem de simulador é de COMPILAÇÃO — o
     * binário de `iosArm64` (aparelho) nem contém o caminho que lê a marca. Em release a lib não
     * executa `getprop` nem lê `NSUserDefaults` para isso.
     *
     * **Por que estes mecanismos (padrão-ouro):**
     * - Android: `debug.*` é o espaço de propriedades que o sistema reserva à depuração — o `shell`
     *   do `adb` pode gravar, apps podem ler, e a marca **some no reboot** (não é `persist.*`).
     *   A leitura é pelo binário `getprop` do sistema, API pública (`ProcessBuilder`), e não por
     *   reflexão em `android.os.SystemProperties` (interface fora do SDK — lista "unsupported",
     *   sujeita a bloqueio). `Settings.Global` foi descartado: do Android 12 em diante app comum
     *   não lê chave que não seja pública (`SecurityException`). Não há detecção OFICIAL de
     *   emulador no Android (as heurísticas de `Build.FINGERPRINT` são frágeis), então a trava é a
     *   depuração + a marca que só o `adb` grava.
     * - iOS: o **domínio global** (`-g`, `NSGlobalDomain`) entra na lista de busca do
     *   `standardUserDefaults` (é de onde vêm `AppleLanguages`/`AppleLocale`). Gravado nele, vale
     *   para app **recém-instalado ou reinstalado** — o domínio do próprio app só existe depois de
     *   instalado e some ao desinstalar. Variável `SIMCTL_CHILD_*` não serve: o Maestro abre o app
     *   pelo XCTest, não por `simctl launch`.
     *
     * Efeito: o MESMO de todo sinal — só [AutomationMode.suppressesAutomaticPrompts] (avaliação e
     * atualização opcional). Anúncio, paywall, cobrança e atualização obrigatória não mudam.
     */
    QA_RUNNER,
}

/**
 * A marca do runner de QA ([AutomationSignal.QA_RUNNER]): nomes que o runner grava e a lib lê.
 * São contrato com `Ferramentas/qa-runner` (`src/sinal-automacao.ts`) — mudar aqui exige mudar lá.
 */
object QaRunnerSignal {
    /** Propriedade de sistema do Android (`adb shell setprop debug.codecacto.automacao 1`). */
    const val ANDROID_PROPERTY: String = "debug.codecacto.automacao"

    /** Chave do `NSUserDefaults` no simulador iOS (domínio global: `defaults write -g … -bool YES`). */
    const val IOS_DEFAULTS_KEY: String = "CodecactoAutomacao"

    /** Quanto tempo uma leitura vale antes de consultar de novo (o diálogo lê na composição). */
    val CACHE_TTL: Duration = 5.seconds

    /** `1`/`true`/`yes`/`on` (sem caixa, sem espaço) liga; qualquer outra coisa — e `null` — não. */
    fun isOnValue(raw: String?): Boolean =
        raw?.trim()?.lowercase() in setOf("1", "true", "yes", "on")
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
 * - **O runner de QA da fábrica** ([AutomationSignal.QA_RUNNER], 2.262.0) — marca no aparelho
 *   (`setprop` no emulador, `defaults write -g` no simulador), lida a cada consulta e só em binário
 *   de depuração. É o que cobre o app que NÃO vende pela loja (sem dublê) sem nenhuma linha nele.
 *
 * Build de release não tem nenhum dos quatro: a `kmplib-testing` não é dependência dele, aparelho de
 * usuário não está em Test Harness Mode (ligar o modo apaga os dados do aparelho) e a marca do runner
 * só é lida em binário depurável.
 */
object AutomationMode {

    @Volatile
    private var declared: AutomationSignal? = null

    /** O sinal que ligou o modo, ou `null` se o processo não é de automação. */
    val signal: AutomationSignal?
        get() = declared
            ?: if (isDeviceInTestHarness()) AutomationSignal.DEVICE_TEST_HARNESS
            else if ((qaRunnerMarkOverride ?: ::isQaRunnerMarked)()) AutomationSignal.QA_RUNNER
            else null

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
     * processo; o Test Harness e a marca do runner continuam valendo, porque não são declaração.
     */
    @AutomationModeApi
    fun reset() {
        declared = null
    }
}

/** Test Harness Mode / Monkey no Android; `false` no iOS (sem equivalente na plataforma). */
internal expect fun isDeviceInTestHarness(): Boolean

/**
 * A marca do runner de QA está ligada E o binário é elegível (depurável; no iOS, simulador)?
 * Ver [AutomationSignal.QA_RUNNER].
 */
internal expect fun isQaRunnerMarked(): Boolean

/**
 * Só para teste da lib: substitui a leitura da marca do runner. Existe porque a suíte iOS roda num
 * simulador — o mesmo que o runner marca —, e "processo comum não é automação" precisa ser
 * verificável lá também.
 */
@Volatile
internal var qaRunnerMarkOverride: (() -> Boolean)? = null

/**
 * Leitura da marca do runner com folga ([ttl]): o `AppReviewDialog` consulta na composição, e no
 * Android ler é abrir um processo. [eligible] vem ANTES de tudo — binário não elegível (release)
 * nunca chega a ler a marca.
 */
internal class QaRunnerProbe(
    private val eligible: () -> Boolean,
    private val readMark: () -> Boolean,
    private val ttl: Duration = QaRunnerSignal.CACHE_TTL,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val isEligible: Boolean by lazy { eligible() }

    @Volatile
    private var cached: Pair<TimeMark, Boolean>? = null

    fun isOn(): Boolean {
        if (!isEligible) return false
        val atual = cached
        if (atual != null && atual.first.elapsedNow() < ttl) return atual.second
        val lido = try {
            readMark()
        } catch (_: Exception) {
            false
        }
        cached = timeSource.markNow() to lido
        return lido
    }
}
