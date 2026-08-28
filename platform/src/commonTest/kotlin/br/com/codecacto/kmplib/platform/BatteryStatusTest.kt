package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatteryStatusTest {

    @Test
    fun android_usaAEscalaReportada_naoCemFixo() {
        // scale nem sempre é 100 — dividir por 100 fixo só quebra no aparelho errado.
        val status = BatteryStatus.fromLevelAndScale(level = 128, scale = 256, isCharging = false)
        assertEquals(0.5f, status.level)
        assertEquals(50, status.percent)
        assertTrue(status.isAvailable)
    }

    @Test
    fun android_leituraInvalida_ficaIndisponivel() {
        assertEquals(BatteryStatus.UNAVAILABLE, BatteryStatus.fromLevelAndScale(-1, 100, false))
        assertEquals(BatteryStatus.UNAVAILABLE, BatteryStatus.fromLevelAndScale(50, 0, false))
    }

    @Test
    fun ios_menosUm_significaDesconhecido_naoBateriaZerada() {
        val status = BatteryStatus.fromIosLevel(-1f, isCharging = false)
        assertFalse(status.isAvailable)
        assertEquals(-1, status.percent)
        assertFalse(status.isCritical(0.05f), "desconhecido nunca é crítico")
    }

    @Test
    fun ios_nivelValido_viraFracaoDisponivel() {
        val status = BatteryStatus.fromIosLevel(0.04f, isCharging = false)
        assertTrue(status.isAvailable)
        assertEquals(4, status.percent)
    }

    @Test
    fun bateriaCritica_quandoAbaixoDoLimiarESemCarregador() {
        val status = BatteryStatus.fromLevelAndScale(4, 100, isCharging = false)
        assertTrue(status.isCritical(BatteryStatus.DEFAULT_CRITICAL_THRESHOLD))
    }

    @Test
    fun noCarregador_nuncaEhCritica() {
        val status = BatteryStatus.fromLevelAndScale(2, 100, isCharging = true)
        assertFalse(status.isCritical(0.05f), "2% no carregador não é emergência")
    }

    @Test
    fun exatamenteNoLimiar_jaConta() {
        val status = BatteryStatus.fromLevelAndScale(5, 100, isCharging = false)
        assertTrue(status.isCritical(0.05f))
    }

    @Test
    fun acimaDoLimiar_naoCorta() {
        val status = BatteryStatus.fromLevelAndScale(6, 100, isCharging = false)
        assertFalse(status.isCritical(0.05f))
    }

    @Test
    fun monitorInerte_reportaIndisponivel() {
        assertEquals(BatteryStatus.UNAVAILABLE, UnavailableBatteryMonitor.status.value)
        assertFalse(UnavailableBatteryMonitor.status.value.isCritical(0.5f))
    }
}
