package br.com.codecacto.kmplib.media

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmbientSoundPlayerTest {

    private class FakeEngine(var loadResult: AmbientSoundError? = null) : AmbientAudioEngine {
        override var onSystemPause: (() -> Unit)? = null
        override var onSystemResumeAllowed: (() -> Unit)? = null
        val calls = mutableListOf<String>()
        var appliedGain = -1f
        var playing = false
        var released = false

        override suspend fun load(bytes: ByteArray): AmbientSoundError? {
            calls += "load"
            return loadResult
        }
        override fun start() { calls += "start"; playing = true }
        override fun pause() { calls += "pause"; playing = false }
        override fun rewind() { calls += "rewind" }
        override fun setGain(gain: Float) { appliedGain = gain }
        override fun release() { released = true }
    }

    private fun TestScope.novo(engine: FakeEngine = FakeEngine()) =
        engine to DefaultAmbientSoundPlayer(engine, StandardTestDispatcher(testScheduler))

    private val som = byteArrayOf(1, 2, 3)

    @Test
    fun carregarDeixaProntoEBytesVaziosSaoInvalidos() = runTest {
        val (engine, player) = novo()
        assertEquals(AmbientSoundError.InvalidAudio, player.load(ByteArray(0)).errorOrNull)
        assertTrue(engine.calls.isEmpty())
        assertTrue(player.load(som).isSuccess)
        assertEquals(AmbientSoundStatus.READY, player.state.value.status)
    }

    @Test
    fun falhaDoDecodificadorVoltaParaVazio() = runTest {
        val (_, player) = novo(FakeEngine(loadResult = AmbientSoundError.InvalidAudio))
        assertEquals(AmbientSoundError.InvalidAudio, player.load(som).errorOrNull)
        assertEquals(AmbientSoundStatus.EMPTY, player.state.value.status)
        assertEquals(AmbientSoundError.NotLoaded, player.play().errorOrNull)
    }

    @Test
    fun fadeInSobeDeZeroAteOVolume() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.setVolume(0.8f)
        player.play(fadeInMillis = 1_000)
        assertTrue(engine.playing)
        assertEquals(0f, engine.appliedGain)
        assertEquals(AmbientSoundStatus.PLAYING, player.state.value.status)
        advanceTimeBy(500); runCurrent()
        assertTrue(engine.appliedGain in 0.3f..0.5f, "meio do fade: ${engine.appliedGain}")
        advanceUntilIdle()
        assertEquals(0.8f, engine.appliedGain)
    }

    @Test
    fun semFadeEntraNoVolumeCheio() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.play(fadeInMillis = 0)
        assertEquals(1f, engine.appliedGain)
        advanceUntilIdle()
        assertEquals(1f, engine.appliedGain)
    }

    @Test
    fun pausaDesceEParaSoNoFim() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.play(0)
        player.pause(fadeOutMillis = 800)
        assertEquals(AmbientSoundStatus.PAUSED, player.state.value.status)
        assertTrue(engine.playing, "ainda tocando durante o fade-out")
        advanceUntilIdle()
        assertFalse(engine.playing)
        assertEquals(0f, engine.appliedGain)
        assertEquals(1f, player.state.value.volume, "o volume escolhido não muda com o fade")
    }

    @Test
    fun tocarNoMeioDoFadeOutRevertSemReiniciar() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.play(0)
        player.pause(1_000)
        advanceTimeBy(500); runCurrent()
        val noMeio = engine.appliedGain
        assertTrue(noMeio in 0.4f..0.6f)
        player.play(1_000)
        assertEquals(1, engine.calls.count { it == "start" }, "não reinicia o player")
        assertFalse("pause" in engine.calls)
        advanceUntilIdle()
        assertEquals(1f, engine.appliedGain)
        assertTrue(engine.playing)
    }

    @Test
    fun stopRebobinaEVoltaAPronto() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.play(0)
        player.stop(200)
        advanceUntilIdle()
        assertEquals(listOf("load", "start", "pause", "rewind"), engine.calls)
        assertEquals(AmbientSoundStatus.READY, player.state.value.status)
    }

    @Test
    fun volumeTocandoFazRampaEParadoValeParaOProximoPlay() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.setVolume(0.3f)
        assertEquals(-1f, engine.appliedGain, "parado: não toca no player")
        player.play(0)
        assertEquals(0.3f, engine.appliedGain)
        player.setVolume(0.9f, fadeMillis = 400)
        advanceUntilIdle()
        assertEquals(0.9f, engine.appliedGain)
        player.setVolume(7f)
        advanceUntilIdle()
        assertEquals(1f, player.state.value.volume, "grampeado em 0..1")
    }

    @Test
    fun pausaDoSistemaERetomadaPermitida() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.play(0)
        engine.playing = false
        engine.onSystemPause?.invoke()
        assertEquals(AmbientSoundStatus.PAUSED, player.state.value.status)
        engine.onSystemResumeAllowed?.invoke()
        assertEquals(AmbientSoundStatus.PLAYING, player.state.value.status)
        assertEquals(2, engine.calls.count { it == "start" })
    }

    @Test
    fun retomadaDoSistemaNaoReligaOQueOAppPausou() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.play(0)
        player.pause(0)
        advanceUntilIdle()
        engine.onSystemResumeAllowed?.invoke()
        assertEquals(AmbientSoundStatus.PAUSED, player.state.value.status)
    }

    @Test
    fun releaseEIdempotenteEDesligaTudo() = runTest {
        val (engine, player) = novo()
        player.load(som)
        player.play(1_000)
        player.release()
        player.release()
        assertTrue(engine.released)
        assertEquals(AmbientSoundStatus.RELEASED, player.state.value.status)
        assertEquals(AmbientSoundError.Released, player.play().errorOrNull)
        assertEquals(AmbientSoundError.Released, player.load(som).errorOrNull)
    }

    @Test
    fun rampaPura() {
        assertEquals(1, ambientFadeSteps(0))
        assertEquals(50, ambientFadeSteps(1_000))
        assertEquals(1, ambientFadeSteps(5))
        assertEquals(0.5f, ambientFadeGainAt(0f, 1f, 25, 50))
        assertEquals(0.2f, ambientFadeGainAt(0.9f, 0.2f, 50, 50))
    }
}
