package br.com.codecacto.kmplib.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundEffectFormatTest {

    private fun header(vararg parts: String): ByteArray =
        parts.joinToString("").encodeToByteArray()

    private fun wav(): ByteArray = header("RIFF", "0000", "WAVEfmt ")

    @Test
    fun detecta_wav_pelo_par_riff_wave() {
        assertEquals(SoundEffectFormat.WAV, detectSoundEffectFormat(wav()))
    }

    @Test
    fun riff_sem_wave_nao_e_wav() {
        // RIFF e contêiner generico (AVI, WEBP). Sem o marcador WAVE nao e audio para o SoundPool.
        val riffAvi = header("RIFF", "0000", "AVI LIST")
        assertEquals(SoundEffectFormat.UNKNOWN, detectSoundEffectFormat(riffAvi))
    }

    @Test
    fun detecta_caf_aiff_e_ogg() {
        assertEquals(SoundEffectFormat.CAF, detectSoundEffectFormat(header("caff", "aaaaaaaa")))
        assertEquals(
            SoundEffectFormat.AIFF,
            detectSoundEffectFormat(header("FORM", "0000", "AIFFCOMM")),
        )
        assertEquals(
            SoundEffectFormat.AIFF,
            detectSoundEffectFormat(header("FORM", "0000", "AIFCFVER")),
        )
        assertEquals(SoundEffectFormat.OGG, detectSoundEffectFormat(header("OggS", "aaaaaaaa")))
    }

    @Test
    fun detecta_m4a_pelo_ftyp_no_offset_quatro() {
        assertEquals(
            SoundEffectFormat.M4A,
            detectSoundEffectFormat(header("0000", "ftyp", "M4A0")),
        )
    }

    @Test
    fun detecta_mp3_com_id3_e_com_frame_sync() {
        assertEquals(SoundEffectFormat.MP3, detectSoundEffectFormat(header("ID3", "aaaaaaaaa")))

        val frameSync = ByteArray(16).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xFB.toByte()
        }
        assertEquals(SoundEffectFormat.MP3, detectSoundEffectFormat(frameSync))
    }

    @Test
    fun conteudo_vazio_curto_ou_desconhecido_e_unknown() {
        assertEquals(SoundEffectFormat.UNKNOWN, detectSoundEffectFormat(ByteArray(0)))
        assertEquals(SoundEffectFormat.UNKNOWN, detectSoundEffectFormat("RIFF".encodeToByteArray()))
        // PNG trocado por audio no empacotamento: recusa cedo, sem chegar ao decodificador.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)
        assertEquals(SoundEffectFormat.UNKNOWN, detectSoundEffectFormat(png))
    }

    @Test
    fun so_o_wav_e_multiplataforma() {
        // A regra que evita o "carrega no Android e falha calado no iPhone".
        assertTrue(SoundEffectFormat.WAV.isCrossPlatform)
        SoundEffectFormat.entries
            .filter { it != SoundEffectFormat.WAV }
            .forEach { assertFalse(it.isCrossPlatform, "$it nao toca nas duas plataformas") }
    }

    @Test
    fun unknown_e_o_unico_nao_tocavel() {
        assertFalse(SoundEffectFormat.UNKNOWN.isPlayable)
        SoundEffectFormat.entries
            .filter { it != SoundEffectFormat.UNKNOWN }
            .forEach { assertTrue(it.isPlayable, "$it deveria ser tocavel") }
    }

    @Test
    fun cada_formato_tem_extensao_propria() {
        val extensoes = SoundEffectFormat.entries.map { it.fileExtension }
        assertEquals(
            extensoes.size,
            extensoes.toSet().size,
            "extensoes duplicadas colidiriam no cache",
        )
    }
}
