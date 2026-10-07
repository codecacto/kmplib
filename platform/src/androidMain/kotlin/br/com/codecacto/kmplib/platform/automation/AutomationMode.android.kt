package br.com.codecacto.kmplib.platform.automation

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import kotlin.concurrent.Volatile

internal actual fun isDeviceInTestHarness(): Boolean = try {
    val harness = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityManager.isRunningInUserTestHarness()
    harness || ActivityManager.isUserAMonkey()
} catch (_: RuntimeException) {
    // Teste JVM sem o framework Android (stub "Stub!"): não é aparelho em Test Harness.
    false
}

/**
 * O `Context` da aplicação para a marca do runner de QA ([AutomationSignal.QA_RUNNER]), registrado
 * por `initKmpLibPlatform` (o `KmpLib.init` do umbrella já chama). Sem ele a marca não vale —
 * fecha para o lado seguro: sem saber se o app é depurável, não se cala nada.
 */
internal object AutomationContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun contextOrNull(): Context? = appContext
}

/** O app é depurável? Vem do manifesto mesclado do build (`debuggable`), que a Play recusa em release. */
internal fun isDebuggableApp(context: Context?): Boolean =
    context != null && (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * Lê `debug.codecacto.automacao` pelo binário `getprop` do sistema — API pública (`ProcessBuilder`),
 * sem reflexão em `android.os.SystemProperties` (fora do SDK). Qualquer falha = desligada.
 */
internal fun readAndroidQaRunnerProperty(): Boolean {
    val processo = try {
        ProcessBuilder("getprop", QaRunnerSignal.ANDROID_PROPERTY).redirectErrorStream(true).start()
    } catch (_: Exception) {
        return false
    }
    return try {
        // `getprop <chave>` imprime uma linha e sai; a leitura termina no fim da saída.
        val saida = processo.inputStream.bufferedReader().use { it.readText() }
        processo.waitFor()
        QaRunnerSignal.isOnValue(saida)
    } catch (_: Exception) {
        false
    } finally {
        processo.destroy()
    }
}

private val qaRunnerProbe = QaRunnerProbe(
    eligible = { isDebuggableApp(AutomationContextHolder.contextOrNull()) },
    readMark = ::readAndroidQaRunnerProperty,
)

internal actual fun isQaRunnerMarked(): Boolean =
    // A elegibilidade é memorizada pelo probe, então só se consulta depois que há Context — antes
    // disso responde `false` sem fixar "não elegível" para o resto do processo.
    AutomationContextHolder.contextOrNull() != null && qaRunnerProbe.isOn()
