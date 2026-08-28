package br.com.codecacto.kmplib.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes de contrato do módulo de observabilidade.
 *
 * Não inicializa o Sentry real (o `SentryCrashReporter` toca o SDK nativo, indisponível em
 * unit test) — valida o mapeamento de config, o no-op quando desabilitado e a captura via
 * [RecordingCrashReporter] (mesma semântica da impl real), além do mapeamento [CrashLevel] -> `SentryLevel`.
 */
class CrashReporterTest {

    private fun config(
        dsn: String = "https://public@glitchtip.example/1",
        enabled: Boolean = true,
    ) = CrashReporterConfig(
        dsn = dsn,
        environment = "production",
        release = "meu-app@1.0.0+42",
        enabled = enabled,
    )

    @Test
    fun config_defaults_travam_privacidade_e_performance() {
        val c = config()
        // LGPD: PII sempre desligado por padrão.
        assertFalse(c.sendDefaultPii)
        // Tracing OFF por padrão (GlitchTip).
        assertEquals(0.0, c.tracesSampleRate)
        assertTrue(c.enabled)
    }

    @Test
    fun init_com_enabled_false_vira_no_op() {
        val reporter = RecordingCrashReporter()
        reporter.init(config(enabled = false))

        assertFalse(reporter.active)
        reporter.captureException(RuntimeException("boom"))
        reporter.captureMessage("hello")
        reporter.setUser("user-1")
        reporter.setTag("k", "v")

        assertTrue(reporter.exceptions.isEmpty())
        assertTrue(reporter.messages.isEmpty())
        assertNull(reporter.currentUserId)
        assertTrue(reporter.tags.isEmpty())
    }

    @Test
    fun init_com_dsn_em_branco_vira_no_op() {
        val reporter = RecordingCrashReporter()
        reporter.init(config(dsn = "   "))

        assertFalse(reporter.active)
        reporter.captureException(IllegalStateException("x"))
        assertTrue(reporter.exceptions.isEmpty())
    }

    @Test
    fun captura_excecao_com_tags_quando_ativo() {
        val reporter = RecordingCrashReporter()
        reporter.init(config())
        assertTrue(reporter.active)

        val error = RuntimeException("falhou")
        reporter.captureException(error, mapOf("feature" to "checkout"))

        assertEquals(1, reporter.exceptions.size)
        val (captured, tags) = reporter.exceptions.first()
        assertEquals(error, captured)
        assertEquals("checkout", tags["feature"])
    }

    @Test
    fun captura_mensagem_com_nivel_e_tags() {
        val reporter = RecordingCrashReporter()
        reporter.init(config())

        reporter.captureMessage("degradado", CrashLevel.Warning, mapOf("area" to "pagamento"))

        assertEquals(1, reporter.messages.size)
        val msg = reporter.messages.first()
        assertEquals("degradado", msg.message)
        assertEquals(CrashLevel.Warning, msg.level)
        assertEquals("pagamento", msg.tags["area"])
    }

    @Test
    fun isActive_reflete_o_estado_do_reporter() {
        val reporter = RecordingCrashReporter()
        // Antes do init: morto — nenhum evento sai daqui.
        assertFalse(reporter.isActive)

        reporter.init(config(dsn = ""))
        assertFalse(reporter.isActive)

        reporter.init(config())
        assertTrue(reporter.isActive)
    }

    @Test
    fun breadcrumb_e_tag_registrados_quando_ativo() {
        val reporter = RecordingCrashReporter()
        reporter.init(config())

        reporter.addBreadcrumb("abriu tela", category = "navigation")
        reporter.setTag("build", "release")

        assertEquals(1, reporter.breadcrumbs.size)
        val crumb = reporter.breadcrumbs.first()
        assertEquals("abriu tela", crumb.message)
        assertEquals("navigation", crumb.category)
        assertEquals(CrashLevel.Info, crumb.level)
        assertEquals("release", reporter.tags["build"])
    }

    @Test
    fun setUser_guarda_id_opaco_e_clear_remove() {
        val reporter = RecordingCrashReporter()
        reporter.init(config())

        reporter.setUser("opaque-123")
        assertEquals("opaque-123", reporter.currentUserId)

        // id em branco equivale a limpar.
        reporter.setUser("  ")
        assertNull(reporter.currentUserId)

        reporter.setUser("opaque-456")
        reporter.clearUser()
        assertNull(reporter.currentUserId)
    }

    @Test
    fun crashLevel_mapeia_para_sentryLevel() {
        assertEquals(io.sentry.kotlin.multiplatform.SentryLevel.DEBUG, CrashLevel.Debug.toSentryLevel())
        assertEquals(io.sentry.kotlin.multiplatform.SentryLevel.INFO, CrashLevel.Info.toSentryLevel())
        assertEquals(io.sentry.kotlin.multiplatform.SentryLevel.WARNING, CrashLevel.Warning.toSentryLevel())
        assertEquals(io.sentry.kotlin.multiplatform.SentryLevel.ERROR, CrashLevel.Error.toSentryLevel())
        assertEquals(io.sentry.kotlin.multiplatform.SentryLevel.FATAL, CrashLevel.Fatal.toSentryLevel())
    }
}
