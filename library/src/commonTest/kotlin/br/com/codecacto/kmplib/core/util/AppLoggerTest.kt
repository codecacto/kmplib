package br.com.codecacto.kmplib.core.util

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes do [AppLogger]. Como é `expect object`, o teste é executado contra o
 * `actual` da plataforma (Android usa `android.util.Log`, iOS usa `NSLog`).
 *
 * Testes aqui cobrem apenas API segura para unit tests: [setMinLevel] /
 * [getMinLevel] e ordinais do [Level]. Os métodos `d/i/w/e` chamam APIs
 * de plataforma que requerem Robolectric (Android) ou simulator real (iOS),
 * fora do escopo destes unit tests.
 *
 * Importante: como `AppLogger` é singleton, restauramos `Level.DEBUG` em
 * `@AfterTest` para não contaminar outros testes.
 */
class AppLoggerTest {

    @BeforeTest
    fun setup() {
        AppLogger.setMinLevel(Level.DEBUG)
    }

    @AfterTest
    fun tearDown() {
        AppLogger.setMinLevel(Level.DEBUG)
    }

    // ====== Level enum ======

    @Test
    fun `Level tem 4 valores em ordem crescente de severidade`() {
        assertEquals(0, Level.DEBUG.ordinal)
        assertEquals(1, Level.INFO.ordinal)
        assertEquals(2, Level.WARN.ordinal)
        assertEquals(3, Level.ERROR.ordinal)
    }

    @Test
    fun `Level entries contem todos os valores na ordem`() {
        assertEquals(
            listOf(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR),
            Level.entries
        )
    }

    @Test
    fun `Level e comparavel por ordinal`() {
        assertTrue(Level.DEBUG.ordinal < Level.ERROR.ordinal)
        assertTrue(Level.WARN.ordinal > Level.INFO.ordinal)
    }

    // ====== setMinLevel / getMinLevel ======

    @Test
    fun `getMinLevel padrao e DEBUG apos setup`() {
        assertEquals(Level.DEBUG, AppLogger.getMinLevel())
    }

    @Test
    fun `setMinLevel altera o nivel atual`() {
        AppLogger.setMinLevel(Level.WARN)
        assertEquals(Level.WARN, AppLogger.getMinLevel())
    }

    @Test
    fun `setMinLevel aceita todos os Level entries`() {
        for (level in Level.entries) {
            AppLogger.setMinLevel(level)
            assertEquals(level, AppLogger.getMinLevel())
        }
    }

    @Test
    fun `setMinLevel ERROR seguido de DEBUG restaura corretamente`() {
        AppLogger.setMinLevel(Level.ERROR)
        assertEquals(Level.ERROR, AppLogger.getMinLevel())

        AppLogger.setMinLevel(Level.DEBUG)
        assertEquals(Level.DEBUG, AppLogger.getMinLevel())
    }
}
