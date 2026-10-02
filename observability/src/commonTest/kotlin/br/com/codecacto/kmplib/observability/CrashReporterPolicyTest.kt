package br.com.codecacto.kmplib.observability

import io.sentry.kotlin.multiplatform.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A política que o `SentryCrashReporter` aplica nas opções do SDK antes do `Sentry.init`.
 *
 * Monta `SentryOptions` sem inicializar o SDK: o que se prova aqui é o que o app PEDE ao Sentry, e
 * é isso que decide, no iOS, se uma exceção de Kotlin chega ao GlitchTip com tipo/mensagem/pilha ou
 * como "C++ Exception: …ExceptionObjHolderImpl".
 */
class CrashReporterPolicyTest {

    private val config = CrashReporterConfig(
        dsn = "https://public@glitchtip.example/1",
        environment = "production",
        release = "meu-app@1.0.0+42",
    )

    private fun options(config: CrashReporterConfig = this.config) =
        SentryOptions().apply { applyCrashReporterPolicy(config) }

    @Test
    fun `o padrao do SDK liga o monitor de C++ - e a politica que desliga`() {
        // Se um dia o default do SDK virar `false`, este teste avisa que a linha da política sobrou.
        assertTrue(SentryOptions().enableUnhandledCppExceptionMonitoring)

        assertFalse(options().enableUnhandledCppExceptionMonitoring)
    }

    @Test
    fun `dsn ambiente e release vem da config do app`() {
        val o = options()

        assertEquals("https://public@glitchtip.example/1", o.dsn)
        assertEquals("production", o.environment)
        assertEquals("meu-app@1.0.0+42", o.release)
    }

    @Test
    fun `nada que o GlitchTip nao entende e nada de dado pessoal`() {
        val o = options()

        assertFalse(o.sendDefaultPii)
        assertEquals(0.0, o.tracesSampleRate)
        assertFalse(o.enableAutoSessionTracking)
        assertFalse(o.attachScreenshot)
        assertFalse(o.attachViewHierarchy)
    }

    @Test
    fun `sendDefaultPii e tracing so mudam se a config do app mandar`() {
        val o = options(config.copy(sendDefaultPii = true, tracesSampleRate = 0.25))

        assertTrue(o.sendDefaultPii)
        assertEquals(0.25, o.tracesSampleRate)
    }
}
