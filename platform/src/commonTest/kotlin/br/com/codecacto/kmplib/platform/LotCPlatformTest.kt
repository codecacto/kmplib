package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.platform.display.OrientationStack
import br.com.codecacto.kmplib.platform.display.ScreenOrientation
import br.com.codecacto.kmplib.platform.tts.splitForSpeech
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LotCPlatformTest {

    @Test
    fun `pilha de orientacao vale a mais recente e volta ao sair`() {
        val s = OrientationStack()
        val app = Any(); val telao = Any()
        assertEquals(ScreenOrientation.UNSPECIFIED, s.current)
        s.push(app, ScreenOrientation.PORTRAIT)
        s.push(telao, ScreenOrientation.LANDSCAPE)
        assertEquals(ScreenOrientation.LANDSCAPE, s.current)
        s.pop(telao)
        assertEquals(ScreenOrientation.PORTRAIT, s.current)
        s.push(app, ScreenOrientation.LANDSCAPE) // mesmo dono substitui
        s.pop(app)
        assertEquals(ScreenOrientation.UNSPECIFIED, s.current)
    }

    @Test
    fun `texto vira trechos por paragrafo e frases longas sao divididas`() {
        assertEquals(listOf("Primeiro.", "Segundo parágrafo."), splitForSpeech("Primeiro.\n\n  Segundo parágrafo.\n"))
        val longo = (1..50).joinToString(" ") { "Frase número $it." }
        val partes = splitForSpeech(longo, maxChars = 100)
        assertTrue(partes.size > 1)
        assertTrue(partes.all { it.length <= 100 })
        assertEquals(longo.replace(" ", ""), partes.joinToString("").replace(" ", ""))
    }
}
