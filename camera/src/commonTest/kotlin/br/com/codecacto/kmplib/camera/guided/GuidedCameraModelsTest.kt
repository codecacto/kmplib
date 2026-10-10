package br.com.codecacto.kmplib.camera.guided

import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidedCameraStateTest {

    private val asDuas = setOf(CameraLens.BACK, CameraLens.FRONT)

    @Test
    fun permissaoAindaNaoPedidaPedeSemRelatarErro() {
        val estado = guidedCameraStateOf(PermissionStatus.NOT_REQUESTED, null, allowLensSwitch = true)
        assertEquals(GuidedCameraState.PermissionRequired, estado)
        assertNull(guidedCameraErrorOf(PermissionStatus.NOT_REQUESTED, estado))
    }

    @Test
    fun permissaoNegadaRelataNegada() {
        val estado = guidedCameraStateOf(PermissionStatus.DENIED, null, allowLensSwitch = true)
        assertEquals(GuidedCameraState.PermissionRequired, estado)
        assertEquals(GuidedCameraError.PERMISSION_DENIED, guidedCameraErrorOf(PermissionStatus.DENIED, estado))
    }

    @Test
    fun negadaEmDefinitivoVenceQualquerStatusDaCamera() {
        val estado = guidedCameraStateOf(
            PermissionStatus.PERMANENTLY_DENIED,
            GuidedCameraStatus.Ready(true, asDuas, CameraLens.BACK),
            allowLensSwitch = true,
        )
        assertEquals(GuidedCameraState.PermissionPermanentlyDenied, estado)
        assertEquals(
            GuidedCameraError.PERMISSION_PERMANENTLY_DENIED,
            guidedCameraErrorOf(PermissionStatus.PERMANENTLY_DENIED, estado),
        )
    }

    @Test
    fun concedidaSemRespostaDaSessaoEstaPreparando() {
        val estado = guidedCameraStateOf(PermissionStatus.GRANTED, null, allowLensSwitch = true)
        assertEquals(GuidedCameraState.Starting, estado)
        assertNull(guidedCameraErrorOf(PermissionStatus.GRANTED, estado))
    }

    @Test
    fun aoVivoComAsDuasCamerasPermiteTrocar() {
        val estado = guidedCameraStateOf(
            PermissionStatus.GRANTED,
            GuidedCameraStatus.Ready(flashAvailable = true, availableLenses = asDuas, activeLens = CameraLens.BACK),
            allowLensSwitch = true,
        )
        assertEquals(GuidedCameraState.Live(flashAvailable = true, canSwitchLens = true), estado)
    }

    @Test
    fun umaCameraSoNaoMostraTroca() {
        val estado = guidedCameraStateOf(
            PermissionStatus.GRANTED,
            GuidedCameraStatus.Ready(false, setOf(CameraLens.BACK), CameraLens.BACK),
            allowLensSwitch = true,
        )
        assertEquals(GuidedCameraState.Live(flashAvailable = false, canSwitchLens = false), estado)
    }

    @Test
    fun appQueDesligaATrocaNaoMostraTroca() {
        val estado = guidedCameraStateOf(
            PermissionStatus.GRANTED,
            GuidedCameraStatus.Ready(true, asDuas, CameraLens.BACK),
            allowLensSwitch = false,
        )
        assertEquals(GuidedCameraState.Live(flashAvailable = true, canSwitchLens = false), estado)
    }

    @Test
    fun semCameraRelataIndisponivel() {
        val estado = guidedCameraStateOf(PermissionStatus.GRANTED, GuidedCameraStatus.Unavailable, true)
        assertEquals(GuidedCameraState.CameraUnavailable, estado)
        assertEquals(GuidedCameraError.CAMERA_UNAVAILABLE, guidedCameraErrorOf(PermissionStatus.GRANTED, estado))
    }

    @Test
    fun falhaDaSessaoRelataInicializacaoComODiagnostico() {
        val estado = guidedCameraStateOf(PermissionStatus.GRANTED, GuidedCameraStatus.Failed("bind"), true)
        assertEquals(GuidedCameraState.InitializationFailed("bind"), estado)
        assertEquals(GuidedCameraError.INITIALIZATION_FAILED, guidedCameraErrorOf(PermissionStatus.GRANTED, estado))
    }
}

class GuidedCameraLensAndFlashTest {

    @Test
    fun lenteAlterna() {
        assertEquals(CameraLens.FRONT, CameraLens.BACK.toggled())
        assertEquals(CameraLens.BACK, CameraLens.FRONT.toggled())
    }

    @Test
    fun flashPercorreDesligadoAutomaticoLigado() {
        assertEquals(CameraFlashMode.AUTO, CameraFlashMode.OFF.next())
        assertEquals(CameraFlashMode.ON, CameraFlashMode.AUTO.next())
        assertEquals(CameraFlashMode.OFF, CameraFlashMode.ON.next())
    }

    @Test
    fun lentePedidaDisponivelEUsada() {
        assertEquals(CameraLens.FRONT, resolveCameraLens(CameraLens.FRONT, setOf(CameraLens.BACK, CameraLens.FRONT)))
    }

    @Test
    fun lentePedidaAusenteCaiNaOutra() {
        assertEquals(CameraLens.BACK, resolveCameraLens(CameraLens.FRONT, setOf(CameraLens.BACK)))
        assertEquals(CameraLens.FRONT, resolveCameraLens(CameraLens.BACK, setOf(CameraLens.FRONT)))
    }

    @Test
    fun semNenhumaLenteNaoHaCamera() {
        assertNull(resolveCameraLens(CameraLens.BACK, emptySet()))
    }
}

class GuidedCameraLayoutTest {

    @Test
    fun retratoUsaTresPorQuatro() {
        assertEquals(0.75f, guidedPreviewAspectRatio(360f, 800f))
    }

    @Test
    fun paisagemUsaQuatroPorTres() {
        assertTrue(kotlin.math.abs(guidedPreviewAspectRatio(800f, 360f) - 4f / 3f) < 0.0001f)
    }

    @Test
    fun fracaoDeMolduraFicaEntreCincoPorCentoECem() {
        assertEquals(1f, coerceGuideFraction(1.5f))
        assertEquals(0.05f, coerceGuideFraction(0f))
        assertEquals(0.05f, coerceGuideFraction(-1f))
        assertEquals(1f, coerceGuideFraction(Float.NaN))
        assertEquals(0.8f, coerceGuideFraction(0.8f))
    }

    @Test
    fun opacidadeFicaEntreZeroEUm() {
        assertEquals(1f, coerceGuideAlpha(2f))
        assertEquals(0f, coerceGuideAlpha(-0.5f))
        assertEquals(CameraGuide.DEFAULT_ALPHA, coerceGuideAlpha(Float.NaN))
    }

    @Test
    fun fabricaDeGuiaPrendeOsValores() {
        val guia = CameraGuide.drawing(widthFraction = 3f, heightFraction = 0f, alpha = 9f) { }
        assertEquals(1f, guia.widthFraction)
        assertEquals(0.05f, guia.heightFraction)
        assertEquals(1f, guia.alpha)
    }
}

class GuidedCaptureHandleTest {

    @Test
    fun disparoComSessaoForaDoArViraFalhaNaoSilencio() {
        var resultado: GuidedCaptureResult? = null
        GuidedCaptureHandle().trigger { resultado = it }
        assertTrue(resultado is GuidedCaptureResult.Failure)
    }

    @Test
    fun disparoComSessaoNoArChamaAPlataforma() {
        val handle = GuidedCaptureHandle()
        var chamou = false
        handle.capture = { chamou = true }
        handle.trigger { }
        assertTrue(chamou)
    }
}

class GuidedCameraTextsTest {

    @Test
    fun rotuloDoFlashDizOModo() {
        val textos = GuidedCameraTexts()
        assertEquals(textos.flashOff, textos.flashLabel(CameraFlashMode.OFF))
        assertEquals(textos.flashAuto, textos.flashLabel(CameraFlashMode.AUTO))
        assertEquals(textos.flashOn, textos.flashLabel(CameraFlashMode.ON))
    }

    @Test
    fun rotuloDaTrocaDizParaOndeVai() {
        val textos = GuidedCameraTexts()
        assertEquals(textos.useFrontLens, textos.switchLensLabel(CameraLens.BACK))
        assertEquals(textos.useBackLens, textos.switchLensLabel(CameraLens.FRONT))
    }
}
