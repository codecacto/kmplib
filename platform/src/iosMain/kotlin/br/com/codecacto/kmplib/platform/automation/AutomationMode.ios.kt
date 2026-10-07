package br.com.codecacto.kmplib.platform.automation

import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// O iOS não tem um "modo de teste" do aparelho: este sinal vem do dublê da loja, do `activate` ou
// da marca do runner de QA no simulador (`isQaRunnerMarked`).
internal actual fun isDeviceInTestHarness(): Boolean = false

/**
 * O binário é de SIMULADOR? Decidido na COMPILAÇÃO, por alvo (`iosSimulatorArm64`/`iosX64` = true,
 * `iosArm64` = false) — o equivalente Kotlin do `#if targetEnvironment(simulator)` do Swift. Não há
 * heurística de execução: o binário de aparelho simplesmente não tem o caminho que lê a marca.
 */
internal expect val isAppleSimulatorBinary: Boolean

/**
 * Binário Kotlin de depuração? `Platform.isDebugBinary` é fixado na ligação do framework: a
 * configuração *Debug* do Xcode liga o framework debug; Archive/TestFlight/App Store ligam release.
 */
@OptIn(ExperimentalNativeApi::class)
internal fun isKotlinDebugBinary(): Boolean = Platform.isDebugBinary

/**
 * `NSUserDefaults.standardUserDefaults` procura também no domínio global (`NSGlobalDomain`), onde o
 * runner grava (`simctl spawn <udid> defaults write -g CodecactoAutomacao -bool YES`) — por isso vale
 * para app recém-instalado. `boolForKey` aceita `YES`/`1`/`true`.
 */
internal fun readIosQaRunnerDefault(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(QaRunnerSignal.IOS_DEFAULTS_KEY)

/**
 * O processo é um APP (`.app`)? A marca existe para o app aberto pelo Maestro; o executável de teste
 * da própria lib (`test.kexe`, rodado no MESMO simulador que o runner marca) não é alvo dela.
 */
internal fun isRunningAsAppBundle(): Boolean = NSBundle.mainBundle.bundlePath.endsWith(".app")

private val qaRunnerProbe = QaRunnerProbe(
    eligible = { isAppleSimulatorBinary && isKotlinDebugBinary() && isRunningAsAppBundle() },
    readMark = ::readIosQaRunnerDefault,
)

internal actual fun isQaRunnerMarked(): Boolean = qaRunnerProbe.isOn()
