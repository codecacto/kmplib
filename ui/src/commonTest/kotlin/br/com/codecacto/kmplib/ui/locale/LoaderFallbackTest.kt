package br.com.codecacto.kmplib.ui.locale

import br.com.codecacto.kmplib.sync.rest.DomainApiTexts
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Os `load…Texts()` NUNCA lançam: quando os recursos não carregam — é o caso do teste de JVM do
 * Android, onde `Resources.getSystem()` é nulo e o `getString` estoura NPE — eles devolvem os
 * defaults pt-BR da classe. Mensagem de erro não pode virar a causa de outro erro.
 *
 * Em aparelho os recursos carregam; o conteúdo deles é travado pelo `LibStringResourcesParityTest`.
 */
class LoaderFallbackTest {

    @Test
    fun `leitor de textos do cliente de dominio sempre devolve um objeto utilizavel`() = runTest {
        val lido = loadDomainApiTexts()
        // Em JVM cai no default; em aparelho pt-BR é o mesmo texto (paridade do recurso pt-BR).
        if (lido == DomainApiTexts(serverError = lido.serverError)) {
            assertEquals(DomainApiTexts().serverError(418), lido.serverError(418))
        }
        assertEquals(true, lido.offline.isNotBlank())
    }
}
