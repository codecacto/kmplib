package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Plataforma falsa: o brilho "do aparelho" é uma variável e toda escrita fica registrada.
 *
 * A diferença entre os dois SOs está aqui: no iOS a escrita **gruda** no aparelho
 * ([BrightnessRestoreMode.RestorePrevious]); no Android ela vive na janela e o brilho do sistema
 * segue intocado ([BrightnessRestoreMode.ReleaseToSystem]).
 */
private class FakeScreen(
    initial: Float = 0.4f,
    private val mode: BrightnessRestoreMode = BrightnessRestoreMode.RestorePrevious,
) {
    var systemBrightness: Float = initial
    val writes = mutableListOf<Float>()

    val session = ScreenBrightnessSession(mode, ::read, ::write)

    private fun read(): Float = systemBrightness

    private fun write(level: Float) {
        writes += level
        if (mode == BrightnessRestoreMode.RestorePrevious && ScreenBrightnessLevel.isOverride(level)) {
            systemBrightness = level
        }
    }
}

private fun androidScreen(initial: Float = 0.4f) =
    FakeScreen(initial, BrightnessRestoreMode.ReleaseToSystem)

private fun iosScreen(initial: Float = 0.4f) =
    FakeScreen(initial, BrightnessRestoreMode.RestorePrevious)

class ScreenBrightnessSessionTest {

    // --- captura do valor anterior -------------------------------------------------------------

    /**
     * O bug que este teste existe para impedir: recapturar o valor anterior a cada `set` faria a
     * restauração devolver o brilho **já forçado** — e a tela ficaria no talo para sempre.
     */
    @Test
    fun valorAnterior_capturadoUmaUnicaVez_mesmoComOSliderSeMexendo() {
        val screen = iosScreen(initial = 0.3f)

        screen.session.set(0.6f)
        screen.session.set(0.8f)
        screen.session.set(1f)
        screen.session.restore()

        assertEquals(0.3f, screen.systemBrightness)
        assertEquals(listOf(0.6f, 0.8f, 1f, 0.3f), screen.writes)
    }

    @Test
    fun depoisDeRestaurar_novoOverrideCapturaOValorAnteriorDeNovo() {
        val screen = iosScreen(initial = 0.2f)

        screen.session.set(1f)
        screen.session.restore()
        assertEquals(0.2f, screen.systemBrightness)

        screen.systemBrightness = 0.55f
        screen.session.set(1f)
        screen.session.restore()
        assertEquals(0.55f, screen.systemBrightness)
    }

    // --- restauração ---------------------------------------------------------------------------

    @Test
    fun android_restaurar_devolveOControleAoSistema() {
        val screen = androidScreen()

        screen.session.set(1f)
        screen.session.restore()

        assertEquals(listOf(1f, ScreenBrightnessLevel.SYSTEM), screen.writes)
        assertFalse(screen.session.state.value.isOverridden)
    }

    @Test
    fun restaurarSemOverrideAtivo_naoEscreveNada() {
        val screen = iosScreen()

        screen.session.restore()
        screen.session.restore()

        assertTrue(screen.writes.isEmpty())
    }

    @Test
    fun semLeituraDoBrilhoAnterior_naoChutaUmNumeroNoAparelho() {
        val screen = iosScreen(initial = ScreenBrightnessLevel.UNKNOWN)

        screen.session.set(1f)
        screen.systemBrightness = ScreenBrightnessLevel.UNKNOWN
        screen.session.restore()

        assertEquals(listOf(1f, ScreenBrightnessLevel.SYSTEM), screen.writes)
        assertFalse(screen.session.state.value.isOverridden)
    }

    // --- faixa ---------------------------------------------------------------------------------

    @Test
    fun valorAcimaDoMaximo_eAplicadoComoMaximo() {
        val screen = androidScreen()

        screen.session.set(2.5f)

        assertEquals(listOf(1f), screen.writes)
        assertEquals(1f, screen.session.state.value.overrideLevel)
    }

    @Test
    fun setComValorInvalido_restauraEmVezDeForcarBrilho() {
        val screen = iosScreen(initial = 0.4f)

        screen.session.set(0.9f)
        screen.session.set(Float.NaN)

        assertEquals(listOf(0.9f, 0.4f), screen.writes)
        assertFalse(screen.session.state.value.isOverridden)
    }

    @Test
    fun brilhoZero_eUmOverrideValidoNaoUmPedidoDeRestauracao() {
        val screen = androidScreen()

        screen.session.set(0f)

        assertEquals(listOf(0f), screen.writes)
        assertTrue(screen.session.state.value.isOverridden)
    }

    // --- ciclo de vida -------------------------------------------------------------------------

    @Test
    fun release_restauraEEIdempotente() {
        val screen = iosScreen(initial = 0.35f)

        screen.session.set(1f)
        screen.session.release()
        screen.session.release()

        assertEquals(listOf(1f, 0.35f), screen.writes)
        assertEquals(0.35f, screen.systemBrightness)
    }

    @Test
    fun depoisDoRelease_nenhumComandoVoltaAMexerNoBrilho() {
        val screen = androidScreen(initial = 0.35f)

        screen.session.release()
        screen.session.set(1f)
        screen.session.refresh()

        assertTrue(screen.writes.isEmpty())
        assertFalse(screen.session.state.value.isOverridden)
    }

    // --- leitura -------------------------------------------------------------------------------

    @Test
    fun current_devolveOOverrideQuandoHaEALeituraDoSistemaQuandoNaoHa() {
        val screen = androidScreen(initial = 0.25f)

        assertEquals(0.25f, screen.session.current())

        screen.session.set(0.9f)
        assertEquals(0.9f, screen.session.current())

        screen.session.restore()
        assertEquals(0.25f, screen.session.current())
    }

    @Test
    fun leituraIndisponivel_viraUnknownENuncaZero() {
        val screen = androidScreen(initial = ScreenBrightnessLevel.UNKNOWN)

        screen.session.refresh()

        assertEquals(ScreenBrightnessLevel.UNKNOWN, screen.session.current())
        assertEquals(ScreenBrightnessLevel.UNKNOWN, screen.session.state.value.systemLevel)
    }

    @Test
    fun estado_guardaOBrilhoDoSistemaJuntoComOOverride() {
        val screen = iosScreen(initial = 0.45f)

        screen.session.set(1f)

        val state = screen.session.state.value
        assertEquals(1f, state.overrideLevel)
        assertEquals(0.45f, state.systemLevel)
        assertEquals(1f, state.effective)
    }
}
