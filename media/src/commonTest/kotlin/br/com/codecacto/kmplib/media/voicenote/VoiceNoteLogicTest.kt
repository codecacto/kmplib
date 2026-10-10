package br.com.codecacto.kmplib.media.voicenote

import br.com.codecacto.kmplib.media.AudioRecorderConfig
import br.com.codecacto.kmplib.media.RecordedAudio
import br.com.codecacto.kmplib.media.audioLevelFromAmplitude
import br.com.codecacto.kmplib.media.audioLevelFromDecibels
import br.com.codecacto.kmplib.media.formatVoiceNoteDuration
import br.com.codecacto.kmplib.media.resampleWaveform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VoiceNoteLogicTest {

    private val cfg = VoiceNoteGestureConfig(tapMaxMillis = 300, cancelDistancePx = 100f, lockDistancePx = 80f)
    private fun passo(s: VoiceNoteGestureState, e: VoiceNoteGestureEvent) = reduceVoiceNoteGesture(s, e, cfg)

    @Test
    fun segurarGravaESoltarEnvia() {
        val p1 = passo(VoiceNoteGestureState.Idle, VoiceNoteGestureEvent.Press(1_000))
        assertEquals(VoiceNoteGestureEffect.START, p1.effect, "começa no toque, sem esperar o toque longo")
        val p2 = passo(p1.state, VoiceNoteGestureEvent.Release(2_500))
        assertEquals(VoiceNoteGestureState.Idle, p2.state)
        assertEquals(VoiceNoteGestureEffect.SEND, p2.effect)
    }

    @Test
    fun toqueCurtoTravaEmVezDeEnviarESegundoToqueEnvia() {
        val p1 = passo(VoiceNoteGestureState.Idle, VoiceNoteGestureEvent.Press(1_000))
        val p2 = passo(p1.state, VoiceNoteGestureEvent.Release(1_150))
        assertIs<VoiceNoteGestureState.Locked>(p2.state)
        assertEquals(VoiceNoteGestureEffect.NONE, p2.effect)
        val p3 = passo(p2.state, VoiceNoteGestureEvent.Press(4_000))
        assertEquals(VoiceNoteGestureState.Idle to VoiceNoteGestureEffect.SEND, p3.state to p3.effect)
    }

    @Test
    fun arrastarParaOLadoCancelaEParaCimaTrava() {
        val segurando = passo(VoiceNoteGestureState.Idle, VoiceNoteGestureEvent.Press(0)).state
        val meio = passo(segurando, VoiceNoteGestureEvent.Drag(-50f, 0f))
        assertEquals(0.5f, (meio.state as VoiceNoteGestureState.Holding).cancelProgress)
        val cancelou = passo(meio.state, VoiceNoteGestureEvent.Drag(-100f, 0f))
        assertEquals(VoiceNoteGestureState.Idle to VoiceNoteGestureEffect.CANCEL, cancelou.state to cancelou.effect)

        val travou = passo(segurando, VoiceNoteGestureEvent.Drag(0f, -80f))
        assertIs<VoiceNoteGestureState.Locked>(travou.state)
        // Travado: soltar não envia nem cancela.
        val soltou = passo(travou.state, VoiceNoteGestureEvent.Release(5_000))
        assertIs<VoiceNoteGestureState.Locked>(soltou.state)
        assertEquals(VoiceNoteGestureEffect.NONE, soltou.effect)
        assertEquals(VoiceNoteGestureEffect.CANCEL, passo(soltou.state, VoiceNoteGestureEvent.CancelTapped).effect)
        assertEquals(VoiceNoteGestureEffect.SEND, passo(soltou.state, VoiceNoteGestureEvent.SendTapped).effect)
    }

    @Test
    fun arrastoParaOOutroLadoNaoContaESoltarCedoDepoisDeArrastarEnvia() {
        val segurando = passo(VoiceNoteGestureState.Idle, VoiceNoteGestureEvent.Press(0)).state
        val direita = passo(segurando, VoiceNoteGestureEvent.Drag(200f, 200f))
        assertEquals(0f, (direita.state as VoiceNoteGestureState.Holding).cancelProgress)
        val arrastou = passo(segurando, VoiceNoteGestureEvent.Drag(-10f, 0f)).state
        val soltou = passo(arrastou, VoiceNoteGestureEvent.Release(100))
        assertEquals(VoiceNoteGestureEffect.SEND, soltou.effect, "arrastou: não é toque, mesmo curto")
    }

    @Test
    fun gestoPerdidoCancelaETetoEnvia() {
        val segurando = passo(VoiceNoteGestureState.Idle, VoiceNoteGestureEvent.Press(0)).state
        assertEquals(VoiceNoteGestureEffect.CANCEL, passo(segurando, VoiceNoteGestureEvent.GestureLost).effect)
        assertEquals(VoiceNoteGestureEffect.SEND, voiceNoteGestureOnLimitReached(segurando).effect)
        assertEquals(VoiceNoteGestureEffect.SEND, voiceNoteGestureOnLimitReached(VoiceNoteGestureState.Locked(0)).effect)
        assertEquals(VoiceNoteGestureEffect.NONE, voiceNoteGestureOnLimitReached(VoiceNoteGestureState.Idle).effect)
        assertEquals(VoiceNoteGestureEffect.NONE, passo(VoiceNoteGestureState.Idle, VoiceNoteGestureEvent.Release(1)).effect)
    }

    @Test
    fun nivelPerceptualPorDecibel() {
        assertEquals(1f, audioLevelFromDecibels(0f))
        assertEquals(0f, audioLevelFromDecibels(-60f))
        assertEquals(0.5f, audioLevelFromDecibels(-25f))
        assertEquals(0f, audioLevelFromDecibels(Float.NaN))
        assertEquals(1f, audioLevelFromAmplitude(32_767))
        assertEquals(0f, audioLevelFromAmplitude(0))
        assertTrue(audioLevelFromAmplitude(3_276) in 0.55f..0.65f) // −20 dBFS
    }

    @Test
    fun ondaReduzidaPeloPicoDeCadaTrecho() {
        assertEquals(listOf(0.9f, 0.5f), resampleWaveform(listOf(0.1f, 0.9f, 0.5f, 0.2f), 2))
        assertEquals(4, resampleWaveform(listOf(0.3f, 0.6f), 4).size)
        assertEquals(emptyList(), resampleWaveform(emptyList(), 10))
        assertEquals(listOf(1f), resampleWaveform(listOf(2f), 1))
    }

    @Test
    fun duracaoDaNota() {
        assertEquals("0:00", formatVoiceNoteDuration(0))
        assertEquals("0:07", formatVoiceNoteDuration(7_999))
        assertEquals("1:05", formatVoiceNoteDuration(65_000))
        assertEquals("12:00", formatVoiceNoteDuration(720_000))
        assertEquals("0:00", formatVoiceNoteDuration(-5))
    }

    @Test
    fun configuracaoEArquivoGravado() {
        assertFailsWith<IllegalArgumentException> { AudioRecorderConfig(maxDurationMillis = 500) }
        assertFailsWith<IllegalArgumentException> { AudioRecorderConfig(minDurationMillis = 200_000) }
        val audio = RecordedAudio("/data/user/0/app/cache/kmplib_audio_capture/voz-1.m4a", 7_001, 60_000, listOf(0.5f), false)
        assertEquals(8, audio.durationSeconds)
        assertEquals("audio/mp4", audio.mimeType)
        assertFalse(audio.toString().contains("kmplib_audio_capture"), "sem caminho no toString")
    }

    @Test
    fun nomeDeCacheEstavelSemOIdCru() {
        val a = voiceNoteCacheFileName("asset-123")
        assertEquals(a, voiceNoteCacheFileName("asset-123"))
        assertTrue(a != voiceNoteCacheFileName("asset-124"))
        assertTrue(a.matches(Regex("voz-[0-9a-f]{16}\\.m4a")), a)
        assertFalse(voiceNoteCacheFileName("../../etc").contains("/"))
        assertEquals("Url(https://cdn.example.com/a.m4a)", VoiceNoteSource.Url("https://cdn.example.com/a.m4a?X-Amz-Signature=s", "a").toString())
        assertEquals(VoiceNoteSource.Url("https://x/1?sig=a", "k"), VoiceNoteSource.Url("https://x/1?sig=b", "k"))
    }
}
