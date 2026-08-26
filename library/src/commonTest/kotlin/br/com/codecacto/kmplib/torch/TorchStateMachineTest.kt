package br.com.codecacto.kmplib.torch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TorchStateMachineTest {

    private fun discreteMachine(levels: Int = 5) = TorchStateMachine(
        androidTorchCapabilities(hasFlashUnit = true, sdkInt = 34, maxLevel = levels)
    )

    @Test
    fun estadoInicial_apagadoNoMaximo() {
        val m = discreteMachine()
        assertFalse(m.current.isOn)
        assertEquals(TorchLevel.MAX, m.current.level)
        assertNull(m.current.error)
    }

    @Test
    fun acender_publicaOEstadoAceso() {
        val m = discreteMachine()
        m.onTurnedOn(0.6f)
        assertTrue(m.current.isOn)
        assertEquals(0.6f, m.current.level)
    }

    @Test
    fun apagar_preservaAIntensidadeEscolhida() {
        val m = discreteMachine()
        m.onTurnedOn(0.4f)
        m.onTurnedOff()
        assertFalse(m.current.isOn)
        assertEquals(0.4f, m.current.level, "a próxima vez acende no nível que a pessoa deixou")
    }

    @Test
    fun soDesligouSozinho_ohardwareMandaNoEstado() {
        val m = discreteMachine()
        m.onTurnedOn(1f)
        m.onHardwareTorchChanged(false) // outro app pegou a câmera / aparelho esquentou
        assertFalse(m.current.isOn, "o botão não pode ficar preso em 'aceso' com o LED apagado")
        assertEquals(1f, m.current.level)
    }

    @Test
    fun hardwareAcendeuPorFora_tambemRefleteNoEstado() {
        val m = discreteMachine()
        m.onHardwareTorchChanged(true)
        assertTrue(m.current.isOn)
    }

    @Test
    fun hardwareRepetindoOMesmoEstado_naoEmiteDeNovo() {
        val m = discreteMachine()
        val before = m.current
        m.onHardwareTorchChanged(false)
        assertTrue(before === m.current, "estado idêntico não deve virar nova emissão")
    }

    @Test
    fun resolveLevel_nuloMantemONivelAtual() {
        val m = discreteMachine()
        m.onLevelChanged(0.6f)
        assertEquals(0.6f, m.resolveLevel(null))
    }

    @Test
    fun resolveLevel_alinhaAoDegrauDoHardware() {
        val m = discreteMachine(levels = 5)
        assertEquals(0.4f, m.resolveLevel(0.37f))
    }

    @Test
    fun resolveLevel_semIntensidade_sempreMaximo() {
        val m = TorchStateMachine(androidTorchCapabilities(true, sdkInt = 30, maxLevel = 5))
        assertEquals(TorchLevel.MAX, m.resolveLevel(0.2f))
    }

    @Test
    fun onCapabilities_realinhaONivelGuardado() {
        val m = TorchStateMachine(iosTorchCapabilities(hasTorch = true))
        m.onLevelChanged(0.37f)
        m.onCapabilities(androidTorchCapabilities(true, sdkInt = 34, maxLevel = 5))
        assertEquals(0.4f, m.current.level)
    }

    @Test
    fun erroQueImpedeAcender_deixaOEstadoApagado() {
        val m = discreteMachine()
        m.onTurnedOn(1f)
        m.onError(TorchError.InUse)
        assertFalse(m.current.isOn)
        assertEquals(TorchError.InUse, m.current.error)
    }

    @Test
    fun sucessoLimpaOErroAnterior() {
        val m = discreteMachine()
        m.onError(TorchError.Unavailable)
        m.onTurnedOn(1f)
        assertNull(m.current.error)
    }

    @Test
    fun clearError_removeSoOAviso() {
        val m = discreteMachine()
        m.onTurnedOn(1f)
        m.onError(TorchError.Unknown("boom"))
        m.clearError()
        assertNull(m.current.error)
    }

    @Test
    fun canAdjustIntensity_espelhaACapacidade() {
        assertTrue(discreteMachine().current.canAdjustIntensity)
        assertFalse(TorchStateMachine(TorchCapabilities.NONE).current.canAdjustIntensity)
    }
}
