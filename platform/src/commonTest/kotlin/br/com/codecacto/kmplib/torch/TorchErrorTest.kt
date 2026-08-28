package br.com.codecacto.kmplib.torch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TorchErrorTest {

    @Test
    fun cameraEmUso_viraErroDeRecursoOcupado() {
        assertEquals(
            TorchError.InUse,
            TorchAccessDenialReason.fromAndroidReason(TorchAccessDenialReason.ANDROID_CAMERA_IN_USE).asTorchError,
        )
        assertEquals(
            TorchError.InUse,
            TorchAccessDenialReason.fromAndroidReason(TorchAccessDenialReason.ANDROID_MAX_CAMERAS_IN_USE).asTorchError,
        )
    }

    @Test
    fun cameraDesabilitadaPorPolitica_viraPermissaoNegada() {
        assertEquals(
            TorchError.PermissionDenied,
            TorchAccessDenialReason.fromAndroidReason(TorchAccessDenialReason.ANDROID_CAMERA_DISABLED).asTorchError,
        )
    }

    @Test
    fun desconectadaOuComFalha_viraIndisponivel() {
        assertEquals(
            TorchError.Unavailable,
            TorchAccessDenialReason.fromAndroidReason(TorchAccessDenialReason.ANDROID_CAMERA_DISCONNECTED).asTorchError,
        )
        assertEquals(
            TorchError.Unavailable,
            TorchAccessDenialReason.fromAndroidReason(TorchAccessDenialReason.ANDROID_CAMERA_ERROR).asTorchError,
        )
    }

    @Test
    fun codigoDesconhecido_degradaParaIndisponivel_naoParaPermissao() {
        val reason = TorchAccessDenialReason.fromAndroidReason(99)
        assertEquals(TorchAccessDenialReason.DeviceError, reason)
        assertEquals(TorchError.Unavailable, reason.asTorchError)
    }

    @Test
    fun outcome_exponheSucessoEErro() {
        assertTrue(TorchOutcome.Success.isSuccess)
        assertNull(TorchOutcome.Success.errorOrNull)

        val failure = TorchOutcome.Failure(TorchError.NoTorch)
        assertFalse(failure.isSuccess)
        assertEquals(TorchError.NoTorch, failure.errorOrNull)
    }

    @Test
    fun controladorInerte_reportaErroTipadoEmVezDeQuebrar() {
        val controller = UnavailableTorchController()
        assertEquals(TorchOutcome.Failure(TorchError.NoTorch), controller.turnOn())
        assertEquals(TorchError.NoTorch, controller.state.value.error)
        assertFalse(controller.state.value.isOn)
        assertFalse(controller.capabilities.hasTorch)

        controller.clearError()
        assertNull(controller.state.value.error)
        assertTrue(controller.turnOff().isSuccess, "apagar o que já está apagado é sucesso")
    }
}
