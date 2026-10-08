package br.com.codecacto.kmplib.signature

import kotlin.test.Test
import kotlin.test.assertEquals

/** Os textos de acessibilidade do quadro seguem a escolha de idioma do compose-resources. */
class SignaturePadTextsTest {

    @Test
    fun ptBr_eODefault() {
        val t = signaturePadTexts("pt-BR")
        assertEquals("Quadro de assinatura", t.contentDescription)
        assertEquals("Sem assinatura", t.stateEmpty)
        assertEquals("Assinado", t.stateSigned)
        assertEquals("Limpar assinatura", t.clearAction)
        assertEquals("Desfazer último traço", t.undoAction)
    }

    @Test
    fun ingles_espanhol_e_ptPt() {
        assertEquals("Signature pad", signaturePadTexts("en-US").contentDescription)
        assertEquals("Signed", signaturePadTexts("en-GB").stateSigned)
        assertEquals("Recuadro de firma", signaturePadTexts("es-MX").contentDescription)
        assertEquals("Sin firmar", signaturePadTexts("es").stateEmpty)
        assertEquals("Por assinar", signaturePadTexts("pt-PT").stateEmpty)
        assertEquals("Anular último traço", signaturePadTexts("pt-PT").undoAction)
    }

    @Test
    fun idiomaForaDaFabrica_caiEmPtBr_comoOsRecursos() {
        // pt-AO e fr-FR: o compose-resources mostra a pasta default (pt-BR); o quadro também.
        assertEquals(signaturePadTexts("pt-BR"), signaturePadTexts("pt-AO"))
        assertEquals(signaturePadTexts("pt-BR"), signaturePadTexts("fr-FR"))
        assertEquals(signaturePadTexts("pt-BR"), signaturePadTexts(""))
    }

    @Test
    fun quatroIdiomas_quatroConjuntosDistintosNoEstadoVazio() {
        val vazios = listOf("pt-BR", "en", "es", "pt-PT").map { signaturePadTexts(it).stateEmpty }
        assertEquals(4, vazios.toSet().size)
    }
}
