package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UrlLogRedactionTest {

    @Test
    fun soEsquemaEHostSaemNoLog() {
        assertEquals("https://pay.exemplo.com", urlForLog("https://pay.exemplo.com/onboarding/abc?token=SEGREDO#x"))
        assertEquals("https://pay.exemplo.com:8443", urlForLog("HTTPS://Pay.Exemplo.com:8443/a"))
        assertEquals("http://h", urlForLog("http://h?t=1"))
    }

    @Test
    fun tokenNaQueryNoCaminhoENoFragmentoNaoSai() {
        val log = urlForLog("https://app.exemplo.com/reset/TOKEN123?code=QWERTY#frag=ZZZ")
        assertFalse("TOKEN123" in log)
        assertFalse("QWERTY" in log)
        assertFalse("ZZZ" in log)
    }

    @Test
    fun credencialEmbutidaNaoSai() {
        assertEquals("https://host.com", urlForLog("https://usuario:senha@host.com/x"))
    }

    @Test
    fun esquemaSemAutoridadeViraSoOEsquema() {
        assertEquals("tel:", urlForLog("tel:+5565999990000"))
        assertEquals("mailto:", urlForLog("mailto:joao@exemplo.com?subject=oi"))
        assertEquals("whatsapp:", urlForLog("whatsapp://"))
    }

    @Test
    fun textoQueNaoEUrl() {
        assertEquals(INVALID_URL_FOR_LOG, urlForLog("sem esquema nenhum"))
        assertEquals(INVALID_URL_FOR_LOG, urlForLog(""))
    }
}
