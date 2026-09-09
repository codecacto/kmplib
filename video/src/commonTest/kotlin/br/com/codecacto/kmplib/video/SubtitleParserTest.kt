package br.com.codecacto.kmplib.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubtitleParserTest {

    private val webvtt = """
        WEBVTT

        NOTE isto é um comentário e não vira fala

        1
        00:00:01.000 --> 00:00:03.500 align:start position:10%
        Bem-vindo à aula de <i>saque</i>.

        2
        00:00:04.000 --> 00:00:06.000
        Repare no <b>movimento</b> do punho:
        ele começa no ombro.
    """.trimIndent()

    private val srt = """
        1
        00:00:01,000 --> 00:00:02,500
        Primeira fala.

        2
        00:00:03,000 --> 00:00:04,000
        Segunda fala.
    """.trimIndent()

    @Test
    fun `webvtt vira falas sem marcacao e sem as cue settings`() {
        val cues = parseSubtitles(webvtt)
        assertEquals(2, cues.size)
        assertEquals(1_000, cues[0].startMillis)
        assertEquals(3_500, cues[0].endMillis)
        assertEquals("Bem-vindo à aula de saque.", cues[0].text)
        assertEquals("Repare no movimento do punho:\nele começa no ombro.", cues[1].text)
    }

    @Test
    fun `srt com virgula decimal e numeracao de bloco`() {
        val cues = parseSubtitles(srt)
        assertEquals(2, cues.size)
        assertEquals(1_000, cues[0].startMillis)
        assertEquals(2_500, cues[0].endMillis)
        assertEquals("Primeira fala.", cues[0].text)
    }

    @Test
    fun `quebra de linha do Windows nao quebra o carimbo`() {
        val comCrLf = srt.replace("\n", "\r\n")
        assertEquals(2, parseSubtitles(comCrLf).size)
    }

    @Test
    fun `bloco malformado e pulado, nao derruba o arquivo`() {
        val quebrado = """
            WEBVTT

            00:00:XX.000 --> 00:00:02.000
            fala impossível

            00:00:03.000 --> 00:00:04.000
            fala boa
        """.trimIndent()
        val cues = parseSubtitles(quebrado)
        assertEquals(1, cues.size)
        assertEquals("fala boa", cues[0].text)
    }

    @Test
    fun `fim antes do inicio e descartado`() {
        val invertido = "WEBVTT\n\n00:00:05.000 --> 00:00:02.000\nnada"
        assertTrue(parseSubtitles(invertido).isEmpty())
    }

    @Test
    fun `carimbo sem hora e aceito`() {
        assertEquals(65_500, parseSubtitleTimestamp("01:05.5"))
        assertEquals(65_050, parseSubtitleTimestamp("01:05.05"))
        assertEquals(65_000, parseSubtitleTimestamp("01:05"))
    }

    @Test
    fun `fracao curta e completada, nao truncada`() {
        // ".5" é meio segundo. Truncar em três dígitos sem completar daria 5 ms, e a fala piscaria.
        assertEquals(500, parseSubtitleTimestamp("00:00:00.5"))
        assertEquals(50, parseSubtitleTimestamp("00:00:00.05"))
    }

    @Test
    fun `carimbo invalido devolve nulo`() {
        assertNull(parseSubtitleTimestamp(""))
        assertNull(parseSubtitleTimestamp("abc"))
        assertNull(parseSubtitleTimestamp("00:75:00.000"))
        assertNull(parseSubtitleTimestamp("00:00:90.000"))
    }

    @Test
    fun `a fala em vigor sai pela posicao, e o silencio devolve nulo`() {
        val cues = parseSubtitles(webvtt)
        assertNull(cueAt(cues, 0))
        assertEquals(cues[0], cueAt(cues, 1_000))
        assertEquals(cues[0], cueAt(cues, 3_499))
        assertNull(cueAt(cues, 3_500), "o fim é exclusivo: 3,5 s já é o silêncio entre as falas")
        assertEquals(cues[1], cueAt(cues, 5_000))
        assertNull(cueAt(cues, 99_000))
    }

    @Test
    fun `busca binaria acha a fala certa numa aula longa`() {
        // Uma aula de 40 minutos passa de mil falas, e isto roda 4 vezes por segundo.
        val cues = (0 until 2_000).map {
            SubtitleCue(startMillis = it * 1_000L, endMillis = it * 1_000L + 900, text = "fala $it")
        }
        assertEquals("fala 0", cueAt(cues, 10)?.text)
        assertEquals("fala 1234", cueAt(cues, 1_234_500)?.text)
        assertEquals("fala 1999", cueAt(cues, 1_999_800)?.text)
        assertNull(cueAt(cues, 1_234_950), "o intervalo entre duas falas é silêncio")
    }

    @Test
    fun `falas fora de ordem no arquivo saem ordenadas`() {
        val bagunçado = """
            WEBVTT

            00:00:10.000 --> 00:00:11.000
            depois

            00:00:01.000 --> 00:00:02.000
            antes
        """.trimIndent()
        val cues = parseSubtitles(bagunçado)
        assertEquals(listOf("antes", "depois"), cues.map { it.text })
    }

    @Test
    fun `marcacoes e entidades saem do texto`() {
        assertEquals("Fulano: oi", stripSubtitleMarkup("<v Fulano>Fulano: oi</v>"))
        assertEquals("a < b & c", stripSubtitleMarkup("a &lt; b &amp; c"))
        assertEquals("texto", stripSubtitleMarkup("<c.amarelo>texto</c>"))
    }
}
