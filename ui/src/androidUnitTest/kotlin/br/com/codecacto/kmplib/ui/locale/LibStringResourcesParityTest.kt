package br.com.codecacto.kmplib.ui.locale

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Trava a paridade das traduções da lib (2.219.0): as quatro pastas têm **as mesmas chaves**, o
 * mesmo conjunto de **argumentos** (`%1$s`, `%1$d`…) por chave, e nenhum texto vazio.
 *
 * Chave faltando numa pasta não quebra o build: o `compose-resources` cai em `values` e a pessoa com
 * o aparelho em inglês vê aquela frase em português — exatamente o defeito que esta versão fecha.
 * Argumento divergente é pior: `%1$d` numa pasta e `%1$s` na outra estoura em runtime só naquele
 * idioma.
 */
class LibStringResourcesParityTest {

    private val pastas = listOf("values", "values-en", "values-es", "values-pt-rPT")

    private fun ler(pasta: String): Map<String, String> {
        val arquivo = File("src/commonMain/composeResources/$pasta/strings.xml")
        assertTrue(arquivo.exists(), "não achei ${arquivo.absolutePath}")
        val regex = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val mapa = LinkedHashMap<String, String>()
        regex.findAll(arquivo.readText()).forEach { m ->
            val chave = m.groupValues[1]
            if (chave in mapa) fail("chave duplicada em $pasta: $chave")
            mapa[chave] = m.groupValues[2]
        }
        return mapa
    }

    private fun argumentos(texto: String): List<String> =
        Regex("""%\d+\$[sd]""").findAll(texto).map { it.value }.sorted().toList()

    @Test
    fun `as quatro pastas tem as mesmas chaves`() {
        val base = ler("values")
        pastas.drop(1).forEach { pasta ->
            val outra = ler(pasta)
            assertEquals(base.keys, outra.keys, "chaves divergentes em $pasta: " +
                "faltam ${base.keys - outra.keys}, sobram ${outra.keys - base.keys}")
        }
    }

    @Test
    fun `mesmos argumentos em todos os idiomas e nenhum texto vazio`() {
        val base = ler("values")
        pastas.forEach { pasta ->
            val outra = ler(pasta)
            outra.forEach { (chave, texto) ->
                assertTrue(texto.isNotBlank(), "$pasta/$chave vazio")
                assertEquals(argumentos(base.getValue(chave)), argumentos(texto), "$pasta/$chave")
            }
        }
    }

    @Test
    fun `locale_tag de cada pasta e o idioma dela`() {
        val esperado = mapOf("values" to "pt-BR", "values-en" to "en", "values-es" to "es", "values-pt-rPT" to "pt-PT")
        esperado.forEach { (pasta, tag) -> assertEquals(tag, ler(pasta)["kmplib_locale_tag"], pasta) }
    }

    @Test
    fun `placeholder da lib nunca e dado real`() {
        // Regra da casa: placeholder é instrução ou formato. "Ex.:" é o sintoma clássico.
        pastas.forEach { pasta ->
            ler(pasta).filterKeys { it.endsWith("_placeholder") }.forEach { (chave, texto) ->
                assertTrue(!texto.contains("Ex.", ignoreCase = true) && !texto.contains("e.g.", ignoreCase = true), "$pasta/$chave")
            }
        }
    }
}
