package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Testes dos helpers compartilhados do renderer de PDF do recibo (commonMain):
 * composição da frase do corpo com negrito inline (§5), achatamento em palavras e
 * conversão de baseline → topo. Cobrem a paridade com a weblib e a unificação iOS↔Android.
 */
class ReciboPdfLayoutTest {

    private fun sample(pj: Boolean = false) = ReciboPdfData(
        emitente = ReciboParte(nome = "Diana Souza", documento = "CPF: 123.***.***-09"),
        pagador = ReciboParte(nome = "João da Silva", documento = "CPF: 987.***.***-00"),
        valorFormatado = "R$ 120,00",
        valorPorExtenso = "cento e vinte reais",
        descricao = "aula de violão",
        localData = "São Paulo, 6 de junho de 2026.",
        numeroRecibo = "0001",
        dataHoraEmissao = "06/06/2026 14:32",
        emitentePessoaJuridica = pj,
    )

    @Test
    fun segments_compoemFraseComTrechosVariaveisEmBold() {
        val segs = reciboBodySegments(sample())
        // Os 4 trechos variáveis devem ser bold; o resto regular.
        val bold = segs.filter { it.bold }.map { it.text }
        assertEquals(listOf("João da Silva", "R$ 120,00", "cento e vinte reais", "aula de violão"), bold)
        // Trechos fixos (incluindo os parênteses) são regular — paridade com a weblib.
        val regularConcat = segs.filter { !it.bold }.joinToString("") { it.text }
        assertTrue(regularConcat.contains(" ("))
        assertTrue(regularConcat.contains(") referente a "))
        assertTrue(regularConcat.endsWith("."))
    }

    @Test
    fun segments_usamRecebiParaPessoaFisica() {
        assertTrue(reciboBodySegments(sample(pj = false)).first().text.startsWith("Recebi de "))
    }

    @Test
    fun segments_usamRecebemosParaPessoaJuridica() {
        assertTrue(reciboBodySegments(sample(pj = true)).first().text.startsWith("Recebemos de "))
    }

    @Test
    fun fraseReconstruida_baleEquivalenteAoTextoCorrido() {
        val segs = reciboBodySegments(sample())
        val frase = segs.joinToString("") { it.text }
        assertEquals(
            "Recebi de João da Silva a quantia de R$ 120,00 (cento e vinte reais) " +
                "referente a aula de violão.",
            frase,
        )
    }

    @Test
    fun words_preservamBoldPorTrechoEEspacos() {
        val words = reciboBodyWords(reciboBodySegments(sample()))
        // Primeira palavra sem espaço antes.
        assertFalse(words.first().spaceBefore)
        assertEquals("Recebi", words.first().text)
        // "João" deve ser bold (parte do nome do pagador) e ter espaço antes.
        val joao = words.first { it.text == "João" }
        assertTrue(joao.bold)
        assertTrue(joao.spaceBefore)
        // "de" (após Recebi) é regular.
        val de = words.first { it.text == "de" }
        assertFalse(de.bold)
        // "(" é regular e DEVE colar (sem espaço) na palavra bold "cento" — paridade visual.
        val abreParen = words.first { it.text == "(" }
        assertFalse(abreParen.bold)
        assertTrue(abreParen.spaceBefore) // antes do "(" havia espaço
        val cento = words.first { it.text == "cento" }
        assertTrue(cento.bold)
        assertFalse(cento.spaceBefore) // colado ao "("
        // ")" regular colado à palavra bold "reais".
        val fechaParen = words.first { it.text == ")" }
        assertFalse(fechaParen.bold)
        assertFalse(fechaParen.spaceBefore)
        // "." final colado a "violão".
        val ponto = words.last()
        assertEquals(".", ponto.text)
        assertFalse(ponto.spaceBefore)
    }

    @Test
    fun words_reconstroemFraseOriginalSemEspacoEspurio() {
        val words = reciboBodyWords(reciboBodySegments(sample()))
        val sb = StringBuilder()
        for (w in words) {
            if (w.spaceBefore) sb.append(' ')
            sb.append(w.text)
        }
        // Reconstrução = frase original (pontuação colada, espaços únicos).
        assertEquals(
            "Recebi de João da Silva a quantia de R$ 120,00 (cento e vinte reais) " +
                "referente a aula de violão.",
            sb.toString(),
        )
    }

    @Test
    fun mmToPt_conversaoExata() {
        assertEquals(2.83465, mmToPt(1.0), 1e-9)
        assertEquals(595.2765, mmToPt(210.0), 1e-3)
    }

    @Test
    fun textTopFromBaseline_subtraiAscent() {
        // topo = baseline - ascent
        assertEquals(70.0, textTopFromBaseline(baselineY = 100.0, ascent = 30.0), 1e-9)
    }
}
