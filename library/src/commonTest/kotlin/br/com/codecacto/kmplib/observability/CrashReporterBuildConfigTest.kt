package br.com.codecacto.kmplib.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes do helper que elimina o boilerplate de bootstrap do [CrashReporter]: derivação pura de
 * ambiente/release/enabled ([crashReporterConfigFromBuildConfig]) e a extensão [initFromBuildConfig].
 */
class CrashReporterBuildConfigTest {

    @Test
    fun release_canonica_no_formato_slug_arroba_versao_mais_code() {
        assertEquals("meu-barbeiro@1.4.0+42", crashReporterRelease("meu-barbeiro", "1.4.0", 42))
        // trim de espaços acidentais.
        assertEquals("app@2.0.0+7", crashReporterRelease("  app ", " 2.0.0 ", 7))
    }

    @Test
    fun debug_deriva_environment_debug() {
        val c = crashReporterConfigFromBuildConfig(
            dsn = "https://k@glitchtip.example/1",
            appSlug = "meu-app",
            versionName = "1.0.0",
            versionCode = 10,
            isDebug = true,
        )
        assertEquals(CrashEnvironment.DEBUG, c.environment)
        assertEquals("meu-app@1.0.0+10", c.release)
        assertTrue(c.enabled)
        // LGPD/performance: defaults seguros preservados.
        assertFalse(c.sendDefaultPii)
        assertEquals(0.0, c.tracesSampleRate)
    }

    @Test
    fun release_deriva_environment_production() {
        val c = crashReporterConfigFromBuildConfig(
            dsn = "https://k@glitchtip.example/1",
            appSlug = "meu-app",
            versionName = "1.0.0",
            versionCode = 10,
            isDebug = false,
        )
        assertEquals(CrashEnvironment.PRODUCTION, c.environment)
    }

    @Test
    fun dsn_em_branco_desliga_o_reporter() {
        val c = crashReporterConfigFromBuildConfig(
            dsn = "   ",
            appSlug = "meu-app",
            versionName = "1.0.0",
            versionCode = 1,
            isDebug = false,
        )
        assertFalse(c.enabled)
        assertEquals("", c.dsn)
    }

    @Test
    fun initFromBuildConfig_monta_e_inicializa_o_reporter() {
        val reporter = FakeCrashReporter()
        reporter.initFromBuildConfig(
            dsn = "https://k@glitchtip.example/1",
            appSlug = "meu-barbeiro",
            versionName = "1.4.0",
            versionCode = 42,
            isDebug = false,
        )
        assertTrue(reporter.active)
        assertEquals("meu-barbeiro@1.4.0+42", reporter.initializedConfig?.release)
        assertEquals(CrashEnvironment.PRODUCTION, reporter.initializedConfig?.environment)
    }

    @Test
    fun initFromBuildConfig_com_dsn_vazio_vira_no_op() {
        val reporter = FakeCrashReporter()
        reporter.initFromBuildConfig(
            dsn = "",
            appSlug = "meu-app",
            versionName = "1.0.0",
            versionCode = 1,
            isDebug = true,
        )
        assertFalse(reporter.active)
    }
}
