package br.com.codecacto.kmplib.pdf.viewer

import br.com.codecacto.kmplib.core.storage.isValidBlobId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PdfCacheIdTest {

    @Test
    fun `a query fica FORA da chave`() {
        // O caso do produto: material de curso por URL assinada. Com o token na chave, o cache
        // erraria em toda abertura e o aluno rebaixaria a apostila inteira — inclusive sem sinal,
        // onde ela simplesmente não abriria.
        val primeira = pdfCacheIdFor("https://cdn.exemplo/aulas/apostila.pdf?token=aaa&expires=1")
        val segunda = pdfCacheIdFor("https://cdn.exemplo/aulas/apostila.pdf?token=bbb&expires=2")
        assertEquals(primeira, segunda)
    }

    @Test
    fun `caminhos diferentes dao chaves diferentes`() {
        assertNotEquals(
            pdfCacheIdFor("https://cdn.exemplo/a/apostila.pdf"),
            pdfCacheIdFor("https://cdn.exemplo/b/apostila.pdf"),
        )
    }

    @Test
    fun `mesmo nome de arquivo em pastas diferentes nao colide`() {
        val um = pdfCacheIdFor("https://cdn/curso-1/material.pdf")
        val dois = pdfCacheIdFor("https://cdn/curso-2/material.pdf")
        assertNotEquals(um, dois)
    }

    @Test
    fun `a chave e sempre um id valido de BlobStore`() {
        val urls = listOf(
            "https://cdn/aula 1/apostila final (v2).pdf",
            "https://cdn/aulas/açaí-e-saque.pdf",
            "https://cdn/",
            "https://cdn/um-nome-absurdamente-longo-que-passa-de-quarenta-caracteres-sem-nenhuma-duvida.pdf",
        )
        urls.forEach { url ->
            val id = pdfCacheIdFor(url)
            assertTrue(isValidBlobId(id), "id inválido para $url: $id")
        }
    }

    @Test
    fun `a chave e estavel entre chamadas`() {
        val url = "https://cdn/aulas/apostila.pdf"
        assertEquals(pdfCacheIdFor(url), pdfCacheIdFor(url))
    }

    @Test
    fun `so o PDF de verdade passa na assinatura`() {
        assertTrue(looksLikePdf("%PDF-1.7\n…".encodeToByteArray()))
        // O que chega quando o CDN devolve 200 com uma página de erro, ou o portal cativo do wi-fi
        // de hotel responde no lugar do arquivo. Guardar isso no cache faria o documento não abrir
        // NUNCA mais, nem com rede boa.
        assertFalse(looksLikePdf("<!DOCTYPE html><html>403</html>".encodeToByteArray()))
        assertFalse(looksLikePdf("""{"erro":"token expirado"}""".encodeToByteArray()))
        assertFalse(looksLikePdf(ByteArray(0)))
        assertFalse(looksLikePdf("%PD".encodeToByteArray()))
    }

    @Test
    fun `cada falha tem frase propria`() {
        val textos = PdfViewerTexts()
        val frases = PdfViewerError.entries.map { textos.messageFor(it) }
        assertEquals(frases.size, frases.toSet().size, "duas falhas diferentes com a mesma frase")
    }

    @Test
    fun `o indicador de pagina conta a partir de um`() {
        assertEquals("3 de 12", PdfViewerTexts().pageIndicator(3, 12))
    }

    @Test
    fun `bytes iguais por id nao recarregam o documento`() {
        // `ByteArray` compara por identidade: sem o equals por id, um `LaunchedEffect(source)`
        // reabriria o PDF a cada recomposição que recriasse a data class.
        val a = PdfSource.Bytes("um".encodeToByteArray(), id = "material-7")
        val b = PdfSource.Bytes("um".encodeToByteArray(), id = "material-7")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, PdfSource.Bytes("um".encodeToByteArray(), id = "material-8"))
    }
}
