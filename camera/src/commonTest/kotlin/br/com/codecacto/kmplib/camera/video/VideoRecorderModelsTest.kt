package br.com.codecacto.kmplib.camera.video

import br.com.codecacto.kmplib.camera.guided.CameraLens
import br.com.codecacto.kmplib.camera.guided.GuidedCameraStatus
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoRecorderModelsTest {

    private val asDuas = setOf(CameraLens.BACK, CameraLens.FRONT)
    private val pronta = GuidedCameraStatus.Ready(false, asDuas, CameraLens.BACK)
    private val G = PermissionStatus.GRANTED
    private val N = PermissionStatus.NOT_REQUESTED
    private val D = PermissionStatus.DENIED
    private val P = PermissionStatus.PERMANENTLY_DENIED

    @Test
    fun cameraNaoPedidaPedeSemErro() {
        val e = videoRecorderStateOf(N, N, recordAudio = false, camera = null, allowLensSwitch = true)
        assertEquals(VideoRecorderState.PermissionRequired(VideoRecorderPermission.CAMERA), e)
        assertNull(videoRecorderErrorOf(N, N, e))
    }

    @Test
    fun cameraNegadaRelataNegada() {
        val e = videoRecorderStateOf(D, G, recordAudio = true, camera = pronta, allowLensSwitch = true)
        assertEquals(VideoRecorderState.PermissionRequired(VideoRecorderPermission.CAMERA), e)
        assertEquals(VideoRecorderError.PERMISSION_DENIED, videoRecorderErrorOf(D, G, e))
    }

    @Test
    fun cameraEmDefinitivoVenceOMicrofone() {
        val e = videoRecorderStateOf(P, P, recordAudio = true, camera = pronta, allowLensSwitch = true)
        assertEquals(VideoRecorderState.PermissionPermanentlyDenied(VideoRecorderPermission.CAMERA), e)
        assertEquals(VideoRecorderError.PERMISSION_PERMANENTLY_DENIED, videoRecorderErrorOf(P, P, e))
    }

    @Test
    fun microfoneSoContaComAudio() {
        val semAudio = videoRecorderStateOf(G, D, recordAudio = false, camera = pronta, allowLensSwitch = true)
        assertEquals(VideoRecorderState.Live(canSwitchLens = true), semAudio)

        val comAudio = videoRecorderStateOf(G, D, recordAudio = true, camera = pronta, allowLensSwitch = true)
        assertEquals(VideoRecorderState.PermissionRequired(VideoRecorderPermission.MICROPHONE), comAudio)
        assertEquals(VideoRecorderError.MICROPHONE_DENIED, videoRecorderErrorOf(G, D, comAudio))

        val naoPedido = videoRecorderStateOf(G, N, recordAudio = true, camera = pronta, allowLensSwitch = true)
        assertNull(videoRecorderErrorOf(G, N, naoPedido))

        val definitivo = videoRecorderStateOf(G, P, recordAudio = true, camera = pronta, allowLensSwitch = true)
        assertEquals(VideoRecorderState.PermissionPermanentlyDenied(VideoRecorderPermission.MICROPHONE), definitivo)
        assertEquals(VideoRecorderError.MICROPHONE_PERMANENTLY_DENIED, videoRecorderErrorOf(G, P, definitivo))
    }

    @Test
    fun estadosDaSessao() {
        assertEquals(VideoRecorderState.Starting, videoRecorderStateOf(G, G, true, null, true))
        assertEquals(
            VideoRecorderState.CameraUnavailable,
            videoRecorderStateOf(G, G, false, GuidedCameraStatus.Unavailable, true),
        )
        val falha = videoRecorderStateOf(G, G, false, GuidedCameraStatus.Failed("x"), true)
        assertEquals(VideoRecorderState.InitializationFailed("x"), falha)
        assertEquals(VideoRecorderError.INITIALIZATION_FAILED, videoRecorderErrorOf(G, G, falha))
        assertEquals(
            VideoRecorderError.CAMERA_UNAVAILABLE,
            videoRecorderErrorOf(G, G, VideoRecorderState.CameraUnavailable),
        )
        assertNull(videoRecorderErrorOf(G, G, VideoRecorderState.Live(true)))
    }

    @Test
    fun trocaDeCameraSoComAsDuasEPermitida() {
        val so1 = GuidedCameraStatus.Ready(false, setOf(CameraLens.BACK), CameraLens.BACK)
        assertEquals(VideoRecorderState.Live(false), videoRecorderStateOf(G, G, false, so1, true))
        assertEquals(VideoRecorderState.Live(false), videoRecorderStateOf(G, G, false, pronta, false))
    }

    @Test
    fun relogioDaGravacao() {
        val inicio = recordingClockOf(0, 60_000, 1_000)
        assertEquals(0f, inicio.progress)
        assertFalse(inicio.canStop)
        assertFalse(inicio.isFinalStretch)

        val meio = recordingClockOf(30_000, 60_000, 1_000)
        assertEquals(0.5f, meio.progress)
        assertEquals(30_000L, meio.remainingMillis)
        assertTrue(meio.canStop)

        val fim = recordingClockOf(52_000, 60_000, 1_000)
        assertTrue(fim.isFinalStretch)

        val passou = recordingClockOf(61_500, 60_000, 1_000)
        assertEquals(60_000L, passou.elapsedMillis)
        assertEquals(0L, passou.remainingMillis)
        assertEquals(1f, passou.progress)
    }

    @Test
    fun tetoCurtoNaoComecaNaRetaFinal() {
        // Teto de 5 s: tudo está "nos últimos 10 s", mas o aviso não pode acender no zero.
        assertFalse(recordingClockOf(0, 5_000, 0).isFinalStretch)
        assertTrue(recordingClockOf(100, 5_000, 0).isFinalStretch)
    }

    @Test
    fun tempoDeGravacaoContaSegundosInteiros() {
        assertEquals("0:00", formatRecordingTime(0))
        assertEquals("0:00", formatRecordingTime(999))
        assertEquals("0:07", formatRecordingTime(7_400))
        assertEquals("0:59", formatRecordingTime(59_999))
        assertEquals("1:00", formatRecordingTime(60_000))
        assertEquals("10:00", formatRecordingTime(600_000))
        assertEquals("0:00", formatRecordingTime(-5))
    }

    @Test
    fun limitesSaoGrampeados() {
        assertEquals(1_000L, coerceMaxRecordingMillis(10))
        assertEquals(600_000L, coerceMaxRecordingMillis(3_600_000))
        assertEquals(60_000L, coerceMaxRecordingMillis(60_000))
        assertEquals(0L, coerceMinRecordingMillis(-1, 60_000))
        assertEquals(60_000L, coerceMinRecordingMillis(90_000, 60_000))
        assertEquals(0, coerceStartDelaySeconds(-3))
        assertEquals(10, coerceStartDelaySeconds(30))
        assertEquals(3, coerceStartDelaySeconds(3))
    }

    @Test
    fun previewTemAProporcaoDoVideo() {
        assertEquals(9f / 16f, videoRecorderPreviewAspectRatio(400f, 800f))
        assertEquals(16f / 9f, videoRecorderPreviewAspectRatio(800f, 400f))
    }

    @Test
    fun cameraPedidaOuAOutra() {
        assertEquals(CameraLens.FRONT, resolveRecorderLens(CameraLens.FRONT, asDuas))
        assertEquals(CameraLens.BACK, resolveRecorderLens(CameraLens.FRONT, setOf(CameraLens.BACK)))
        assertNull(resolveRecorderLens(CameraLens.BACK, emptySet()))
    }

    @Test
    fun videoGravadoNaoVazaCaminhoEArredondaSegundos() {
        val v = RecordedVideo(
            path = "/data/user/0/app/cache/kmplib_video_capture/maria-agachamento.mp4",
            uri = "file:///data/user/0/app/cache/kmplib_video_capture/maria-agachamento.mp4",
            durationMillis = 59_100, sizeBytes = 10, widthPx = 720, heightPx = 1280,
            mimeType = "video/mp4", lens = CameraLens.BACK, hasAudio = false, reachedMaxDuration = false,
        )
        assertFalse("maria" in v.toString())
        assertEquals(60, v.durationSeconds)
    }

    @Test
    fun ponteSemSessaoFalhaEmVezDeSilenciar() {
        val handle = VideoRecordHandle()
        var evento: VideoRecordEvent? = null
        handle.startRecording { evento = it }
        assertTrue(evento is VideoRecordEvent.Failed)
        handle.stopRecording() // sem sessão: não lança
    }
}
