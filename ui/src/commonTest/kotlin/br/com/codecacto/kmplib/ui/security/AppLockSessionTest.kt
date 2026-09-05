package br.com.codecacto.kmplib.ui.security

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regras da trava. O estado é de processo (ver [AppLockSession]), então cada teste começa e termina
 * trancado — que é também o estado com que o app nasce.
 */
class AppLockSessionTest {

    private val grace = 60_000L

    @BeforeTest
    fun trancar() = AppLockSession.lock()

    @AfterTest
    fun destrancar() = AppLockSession.lock()

    @Test
    fun nasceTrancado() {
        assertTrue(AppLockSession.isLocked)
    }

    @Test
    fun voltarDentroDaFolgaNaoTranca() {
        AppLockSession.unlock()
        AppLockSession.onBackground(1_000L)

        AppLockSession.onForeground(1_000L + 59_999L, grace)

        assertFalse(AppLockSession.isLocked)
    }

    @Test
    fun voltarNaFolgaExataTranca() {
        AppLockSession.unlock()
        AppLockSession.onBackground(1_000L)

        AppLockSession.onForeground(1_000L + grace, grace)

        assertTrue(AppLockSession.isLocked)
    }

    @Test
    fun voltarDepoisDaFolgaTranca() {
        AppLockSession.unlock()
        AppLockSession.onBackground(1_000L)

        AppLockSession.onForeground(1_000L + 60_001L, grace)

        assertTrue(AppLockSession.isLocked)
    }

    @Test
    fun folgaZeroTrancaEmQualquerSaida() {
        AppLockSession.unlock()
        AppLockSession.onBackground(1_000L)

        AppLockSession.onForeground(1_000L, graceMillis = 0L)

        assertTrue(AppLockSession.isLocked)
    }

    /** Hora do aparelho puxada para trás não pode virar folga infinita. */
    @Test
    fun relogioAndandoParaTrasTranca() {
        AppLockSession.unlock()
        AppLockSession.onBackground(10_000L)

        AppLockSession.onForeground(5_000L, grace)

        assertTrue(AppLockSession.isLocked)
    }

    /**
     * Duas voltas seguidas sem ter saído no meio (o `ON_START` que chega por recriação de tela) não
     * podem trancar: sem zerar a marca da saída, a segunda contaria o tempo da primeira.
     */
    @Test
    fun segundaVoltaSemNovaSaidaNaoTranca() {
        AppLockSession.unlock()
        AppLockSession.onBackground(1_000L)
        AppLockSession.onForeground(2_000L, grace)

        AppLockSession.onForeground(1_000_000L, grace)

        assertFalse(AppLockSession.isLocked)
    }

    /** Saída registrada com o app já trancado não destranca nada nem apaga a trava. */
    @Test
    fun saidaComAppTrancadoNaoMudaNada() {
        AppLockSession.onBackground(1_000L)

        AppLockSession.onForeground(2_000L, grace)

        assertTrue(AppLockSession.isLocked)
    }

    @Test
    fun destrancarLimpaAMarcaDeSaida() {
        AppLockSession.unlock()
        AppLockSession.onBackground(1_000L)
        AppLockSession.unlock()

        AppLockSession.onForeground(1_000_000L, grace)

        assertFalse(AppLockSession.isLocked)
    }
}
