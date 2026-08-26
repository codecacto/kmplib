package br.com.codecacto.kmplib.ui.layout

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O `emPainelUnico` do [ListDetailScaffold] (GAP-NCX-T-04).
 *
 * Duas coisas se provam aqui, e a segunda **é o teste**, mesmo sem `assert`:
 *
 * 1. A regra que o componente aplica — painel único é `!temDoisPaineis`, e **não**
 *    `== COMPACTA`. É a linha que todo consumidor recalculava por fora; o NeuroCoreX a escreveu na
 *    forma errada e a pessoa ficava presa no detalhe ao girar o tablet para retrato.
 * 2. **Compatibilidade de fonte**: [chamadaAntiga] usa a forma de antes da 2.151.0, com lambdas sem
 *    parâmetro. Se este arquivo deixar de compilar, ~20 apps deixam junto.
 *
 * A segunda é o motivo de o `emPainelUnico` chegar por **escopo de receptor** e não por parâmetro
 * de lambda: a versão com sobrecarga `(Boolean) -> Unit` foi escrita, compilada e **reprovada pelo
 * compilador** — `Overload resolution ambiguity`, porque `lista = { … }` casa com as duas
 * assinaturas. Registrado aqui para não ser tentado de novo.
 */
class ListDetailScaffoldApiTest {

    @Test
    fun painel_unico_e_tudo_que_nao_e_dois_paineis() {
        // Tablet em RETRATO (MEDIA) é painel único — o erro clássico é esquecer justo este.
        assertTrue(!WindowSizeClass.COMPACTA.temDoisPaineis)
        assertTrue(!WindowSizeClass.MEDIA.temDoisPaineis)
        assertFalse(!WindowSizeClass.EXPANDIDA.temDoisPaineis)
    }

    @Test
    fun a_forma_errada_diverge_no_tablet_em_retrato() {
        // Prova de que as duas formas NÃO são equivalentes — é por isso que o valor passou a vir
        // pronto do componente em vez de ser recalculado por quem chama.
        val certo = !WindowSizeClass.MEDIA.temDoisPaineis
        val errado = WindowSizeClass.MEDIA == WindowSizeClass.COMPACTA
        assertTrue(certo)
        assertFalse(errado)
    }
}

/** Compila = a sobrecarga sem parâmetro continua resolvendo. Nunca é executada. */
@Suppress("unused")
@Composable
private fun chamadaAntiga(temSelecao: Boolean) {
    ListDetailScaffold(
        temSelecao = temSelecao,
        lista = { },
        detalhe = { },
        vazio = { },
    )
}

/** Compila = os três slots enxergam o `emPainelUnico` do escopo. Nunca é executada. */
@Suppress("unused")
@Composable
private fun chamadaNova(temSelecao: Boolean) {
    ListDetailScaffold(
        temSelecao = temSelecao,
        lista = { require(emPainelUnico || !emPainelUnico) },
        detalhe = { require(emPainelUnico || !emPainelUnico) },
        vazio = { require(!emPainelUnico) },
    )
}
