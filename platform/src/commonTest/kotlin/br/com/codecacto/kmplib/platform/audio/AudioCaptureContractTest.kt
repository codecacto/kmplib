package br.com.codecacto.kmplib.platform.audio

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Contrato comum da captura: máquina de estados, configuração e o bloco de amostras cruas. */
class AudioCaptureContractTest {

    @Test
    fun capturaIndisponivel_naoIniciaEjaDizOporque() {
        val capture = UnavailableAudioCapture(AudioCaptureError.PermissionDenied)

        assertFalse(capture.isAvailable)
        assertFalse(capture.start(), "start() em aparelho indisponível devolve false")

        val estado = assertIs<AudioCaptureState.Failed>(capture.state.value)
        assertEquals(AudioCaptureError.PermissionDenied, estado.error)
    }

    @Test
    fun capturaIndisponivel_naoEmiteNada() = runTest {
        val capture = UnavailableAudioCapture()
        assertEquals(0, capture.levels.count())
        assertEquals(0, capture.frames.count())
    }

    @Test
    fun releaseEidempotenteEterminal() {
        val capture = UnavailableAudioCapture()
        capture.release()
        assertEquals(AudioCaptureState.Released, capture.state.value)
        capture.release()
        capture.stop()
        capture.updateProcessing(weighting = AudioWeighting.Z)
        assertEquals(AudioCaptureState.Released, capture.state.value)
    }

    @Test
    fun configPadraoEadeUmDecibelimetro() {
        val config = AudioCaptureConfig()
        assertEquals(AudioWeighting.A, config.weighting)
        assertEquals(AudioTimeWeighting.FAST, config.timeWeighting)
        assertEquals(44_100, config.preferredSampleRate)
        assertFalse(config.emitFrames, "copiar buffer é opt-in: só o afinador paga esse custo")
        assertTrue(config.emitIntervalMillis in 100..200, "NFR: atualização entre 100 e 200 ms")
    }

    @Test
    fun configForaDaFaixa_eRecusadaNaConstrucao() {
        assertFailsWith<IllegalArgumentException> { AudioCaptureConfig(preferredSampleRate = 4_000) }
        assertFailsWith<IllegalArgumentException> {
            AudioCaptureConfig(preferredSampleRate = 400_000)
        }
        assertFailsWith<IllegalArgumentException> { AudioCaptureConfig(emitIntervalMillis = 0) }
        assertFailsWith<IllegalArgumentException> { AudioCaptureConfig(emitIntervalMillis = 60_000) }
    }

    @Test
    fun constantesDaIntegracaoTemporalSaoAsDaNorma() {
        assertEquals(0.125, AudioTimeWeighting.FAST.tauSeconds, 1e-9)
        assertEquals(1.0, AudioTimeWeighting.SLOW.tauSeconds, 1e-9)
        assertEquals(0.0, AudioTimeWeighting.NONE.tauSeconds, 1e-9)
    }

    @Test
    fun blocoDeAmostrasDescreveAPropriaDuracao() {
        val frame = AudioFrame(
            samples = ShortArray(2_048),
            sampleRate = 44_100,
            channelCount = 1,
            timestampMillis = 10L,
        )
        assertEquals(2_048, frame.frameCount)
        assertEquals(2_048.0 / 44_100.0, frame.durationSeconds, 1e-9)
    }

    @Test
    fun blocoComTaxaInvalida_naoDivideporZero() {
        val frame = AudioFrame(ShortArray(10), sampleRate = 0, channelCount = 1, timestampMillis = 0L)
        assertEquals(0.0, frame.durationSeconds)
    }

    @Test
    fun pisoDeSilencioEoAcordadoComAsTelas() {
        assertEquals(-120.0, AudioLevel.SILENCE_DBFS, 1e-9)
    }
}
