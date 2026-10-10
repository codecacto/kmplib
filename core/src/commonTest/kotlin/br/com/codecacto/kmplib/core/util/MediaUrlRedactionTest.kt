@file:OptIn(KmpLibCoreInternalApi::class)

package br.com.codecacto.kmplib.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaUrlRedactionTest {

    @Test
    fun urlPreAssinadaPerdeQueryEFragmento() {
        assertEquals(
            "https://cdn.exemplo.com/aulas/1/master.m3u8",
            redactMediaUrl("https://cdn.exemplo.com/aulas/1/master.m3u8?X-Amz-Signature=abc&Expires=99#t=10"),
        )
    }

    @Test
    fun soFragmentoTambemSai() {
        assertEquals("https://cdn.x.com/v.mp4", redactMediaUrl("https://cdn.x.com/v.mp4#token=segredo"))
    }

    @Test
    fun portaFicaCredencialDaAutoridadeSai() {
        assertEquals(
            "https://cdn.x.com:8443/v.mp4",
            redactMediaUrl("https://user:senha@cdn.x.com:8443/v.mp4?sig=1"),
        )
    }

    @Test
    fun semCaminhoFicaSoEsquemaEHost() {
        assertEquals("https://cdn.x.com", redactMediaUrl("https://cdn.x.com?sig=1"))
    }

    @Test
    fun urlComEspacoQueONsUrlRecusaAindaReduz() {
        assertEquals("https://cdn.x.com/aula 1.mp4", redactMediaUrl("https://cdn.x.com/aula 1.mp4?sig=abc"))
    }

    @Test
    fun arquivoLocalEContentUri() {
        assertEquals("file:///var/mobile/x.mp4", redactMediaUrl("file:///var/mobile/x.mp4"))
        assertEquals(
            "content://media/external/video/media/12",
            redactMediaUrl("content://media/external/video/media/12?x=1"),
        )
    }

    @Test
    fun semEsquemaNaoVaiParaOLog() {
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl("cdn.x.com/v.mp4?sig=abc"))
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl("lixo sem esquema?token=1"))
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl("?sig=abc"))
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl(""))
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl("   "))
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl(null))
    }

    @Test
    fun esquemaSemNadaDepoisNaoVaza() {
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl("https://"))
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl("https://?sig=1"))
        assertEquals(REDACTED_MEDIA_URL, redactMediaUrl("token:abc"))
    }

    @Test
    fun textoLivreTemCadaUrlReduzida() {
        val msg = "Client request(GET https://cdn.x.com/l.vtt?X-Amz-Signature=abc) invalid: 403; " +
            "retry https://b.x.com/v.mp4?token=zz"
        val red = redactMediaUrlsIn(msg)
        assertEquals("Client request(GET https://cdn.x.com/l.vtt) invalid: 403; retry https://b.x.com/v.mp4", red)
        assertFalse("abc" in red)
        assertFalse("zz" in red)
    }

    @Test
    fun textoSemUrlPassaIgualENuloViraVazio() {
        assertEquals("Source error", redactMediaUrlsIn("Source error"))
        assertEquals("", redactMediaUrlsIn(null))
        assertTrue(redactMediaUrlsIn("a ? b").contains("?"))
    }
}
