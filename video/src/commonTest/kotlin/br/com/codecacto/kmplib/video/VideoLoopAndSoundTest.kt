package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoLoopAndSoundTest {

    @Test
    fun configDefaultMantemOComportamentoDeAula() {
        val config = VideoPlayerConfig()
        assertFalse(config.loop)
        assertFalse(config.startMuted)
        assertFalse(config.soundControl)
        assertFalse(config.tapToTogglePlayback)
    }

    @Test
    fun nascerMudoLigaOBotaoDeSomPorDefault() {
        assertTrue(VideoPlayerConfig(startMuted = true).soundControl)
    }

    @Test
    fun botaoDeSomPodeSerDesligadoMesmoMudo() {
        assertFalse(VideoPlayerConfig(startMuted = true, soundControl = false).soundControl)
    }

    @Test
    fun botaoDeSomPodeSerLigadoComSom() {
        assertTrue(VideoPlayerConfig(soundControl = true).soundControl)
    }

    @Test
    fun toqueComControlesMostraEEscondeOsControles() {
        assertEquals(VideoTapAction.ToggleControls, videoTapActionOf(controls = true, tapToTogglePlayback = false))
    }

    @Test
    fun toqueParaPausarVenceOsControles() {
        assertEquals(VideoTapAction.TogglePlayback, videoTapActionOf(controls = true, tapToTogglePlayback = true))
        assertEquals(VideoTapAction.TogglePlayback, videoTapActionOf(controls = false, tapToTogglePlayback = true))
    }

    @Test
    fun semControlesESemToqueParaPausarOToqueNaoFazNada() {
        assertEquals(VideoTapAction.None, videoTapActionOf(controls = false, tapToTogglePlayback = false))
    }

    @Test
    fun semBotaoDeSomConfiguradoNaoApareceEmLugarNenhum() {
        for (controls in listOf(true, false)) {
            for (visiveis in listOf(true, false)) {
                assertEquals(
                    VideoSoundButtonPlacement.None,
                    videoSoundButtonPlacementOf(soundControl = false, controls = controls, controlsVisible = visiveis, isError = false),
                )
            }
        }
    }

    @Test
    fun semControlesOBotaoDeSomFicaNoCantoSempre() {
        assertEquals(
            VideoSoundButtonPlacement.Corner,
            videoSoundButtonPlacementOf(soundControl = true, controls = false, controlsVisible = false, isError = false),
        )
        assertEquals(
            VideoSoundButtonPlacement.Corner,
            videoSoundButtonPlacementOf(soundControl = true, controls = false, controlsVisible = true, isError = false),
        )
    }

    @Test
    fun comControlesOBotaoDeSomAcompanhaABarra() {
        assertEquals(
            VideoSoundButtonPlacement.ControlBar,
            videoSoundButtonPlacementOf(soundControl = true, controls = true, controlsVisible = true, isError = false),
        )
        assertEquals(
            VideoSoundButtonPlacement.None,
            videoSoundButtonPlacementOf(soundControl = true, controls = true, controlsVisible = false, isError = false),
        )
    }

    @Test
    fun emErroOBotaoDeSomSome() {
        assertEquals(
            VideoSoundButtonPlacement.None,
            videoSoundButtonPlacementOf(soundControl = true, controls = false, controlsVisible = true, isError = true),
        )
    }

    @Test
    fun textosDoBotaoDeSomTemDefault() {
        val textos = VideoPlayerTexts()
        assertEquals("Ativar som", textos.turnSoundOn)
        assertEquals("Desativar som", textos.turnSoundOff)
    }

    @Test
    fun idsDeAutomacaoEstaveis() {
        assertEquals("video-quadro", VideoPlayerTestTags.FRAME)
        assertEquals("video-btn-som", VideoPlayerTestTags.SOUND)
    }
}
