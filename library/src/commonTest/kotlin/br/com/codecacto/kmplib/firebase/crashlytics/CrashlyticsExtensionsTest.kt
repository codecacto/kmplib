package br.com.codecacto.kmplib.firebase.crashlytics

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrashlyticsExtensionsTest {

    // ====== runCatchingAndReport (sync) ======

    @Test
    fun `runCatchingAndReport retorna Success quando bloco nao lanca`() {
        val crashlytics = FakeCrashlyticsService()
        val result = crashlytics.runCatchingAndReport { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
        assertTrue(crashlytics.recordedExceptions.isEmpty())
    }

    @Test
    fun `runCatchingAndReport retorna Failure e reporta quando bloco lanca`() {
        val crashlytics = FakeCrashlyticsService()
        val boom = RuntimeException("boom")

        val result = crashlytics.runCatchingAndReport<Int> { throw boom }

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
        assertEquals(1, crashlytics.recordedExceptions.size)
        assertEquals(boom, crashlytics.recordedExceptions.first())
    }

    @Test
    fun `runCatchingAndReport seta customKeys antes de reportar`() {
        val crashlytics = FakeCrashlyticsService()
        val keys = mapOf("user_action" to "save", "screen" to "profile")

        crashlytics.runCatchingAndReport<Int>(customKeys = keys) {
            throw RuntimeException()
        }

        assertEquals("save", crashlytics.customKeys["user_action"])
        assertEquals("profile", crashlytics.customKeys["screen"])
    }

    @Test
    fun `runCatchingAndReport NAO seta customKeys em caso de sucesso`() {
        val crashlytics = FakeCrashlyticsService()
        crashlytics.runCatchingAndReport(customKeys = mapOf("k" to "v")) { 1 }
        assertTrue(crashlytics.customKeys.isEmpty())
    }

    @Test
    fun `runCatchingAndReport re-lanca CancellationException sem reportar`() {
        val crashlytics = FakeCrashlyticsService()

        assertFailsWith<CancellationException> {
            crashlytics.runCatchingAndReport<Unit> {
                throw CancellationException("job canceled")
            }
        }
        assertTrue(crashlytics.recordedExceptions.isEmpty())
    }

    // ====== runCatchingAndReportSuspend ======

    @Test
    fun `runCatchingAndReportSuspend retorna Success`() = runTest {
        val crashlytics = FakeCrashlyticsService()
        val result = crashlytics.runCatchingAndReportSuspend { "ok" }
        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun `runCatchingAndReportSuspend reporta e retorna Failure`() = runTest {
        val crashlytics = FakeCrashlyticsService()
        val boom = IllegalStateException("nope")

        val result = crashlytics.runCatchingAndReportSuspend<String> { throw boom }

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(1, crashlytics.recordedExceptions.size)
    }

    @Test
    fun `runCatchingAndReportSuspend re-lanca CancellationException`() = runTest {
        val crashlytics = FakeCrashlyticsService()

        assertFailsWith<CancellationException> {
            crashlytics.runCatchingAndReportSuspend<Unit> {
                throw CancellationException("canceled in suspend")
            }
        }
        assertTrue(crashlytics.recordedExceptions.isEmpty())
    }

    // ====== reportAndRethrow ======

    @Test
    fun `reportAndRethrow propaga a exception apos registrar`() {
        val crashlytics = FakeCrashlyticsService()
        val boom = RuntimeException("propagate me")

        val thrown = assertFailsWith<RuntimeException> {
            crashlytics.reportAndRethrow(boom)
        }
        assertEquals(boom, thrown)
        assertEquals(1, crashlytics.recordedExceptions.size)
    }

    @Test
    fun `reportAndRethrow aceita customKeys variadicos`() {
        val crashlytics = FakeCrashlyticsService()
        try {
            crashlytics.reportAndRethrow(
                RuntimeException(),
                "stage" to "upload",
                "size" to "1024"
            )
        } catch (_: RuntimeException) {}
        assertEquals("upload", crashlytics.customKeys["stage"])
        assertEquals("1024", crashlytics.customKeys["size"])
    }

    @Test
    fun `reportAndRethrow nao registra CancellationException`() {
        val crashlytics = FakeCrashlyticsService()
        val cancel = CancellationException("nope")

        assertFailsWith<CancellationException> {
            crashlytics.reportAndRethrow(cancel)
        }
        assertTrue(crashlytics.recordedExceptions.isEmpty())
    }

    // ====== reportSilently ======

    @Test
    fun `reportSilently registra e nao propaga`() {
        val crashlytics = FakeCrashlyticsService()
        crashlytics.reportSilently(RuntimeException("silently"))

        assertEquals(1, crashlytics.recordedExceptions.size)
    }

    @Test
    fun `reportSilently aceita customKeys e ignora CancellationException`() {
        val crashlytics = FakeCrashlyticsService()
        crashlytics.reportSilently(
            CancellationException("ignored"),
            "k" to "v"
        )
        assertTrue(crashlytics.recordedExceptions.isEmpty())
        // customKeys também não setadas porque retornou cedo
        assertTrue(crashlytics.customKeys.isEmpty())
    }

    @Test
    fun `reportSilently com customKeys aplica chaves antes de reportar`() {
        val crashlytics = FakeCrashlyticsService()
        crashlytics.reportSilently(
            RuntimeException("boom"),
            "context" to "background",
            "retry_count" to "3"
        )
        assertEquals("background", crashlytics.customKeys["context"])
        assertEquals("3", crashlytics.customKeys["retry_count"])
    }
}
