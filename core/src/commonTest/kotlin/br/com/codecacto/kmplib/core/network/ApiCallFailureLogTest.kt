package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiCallFailureLogTest {

    @Test
    fun `descreve a excecao e as causas com tipo e mensagem`() {
        val e = IllegalStateException("corpo interrompido", IOException("stream was reset: CANCEL"))
        assertEquals(
            "IllegalStateException: corpo interrompido ← causa: IOException: stream was reset: CANCEL",
            describeFailureChain(e),
        )
    }

    @Test
    fun `corta a query de URL — filtro pode carregar dado pessoal`() {
        val e = IOException("falhou em https://api.codecacto.com.br/locaki/v1/clientes?busca=joana&cpf=123 depois de 10ms")
        val d = describeFailureChain(e)
        assertTrue(d.contains("https://api.codecacto.com.br/locaki/v1/clientes?…"), d)
        assertFalse(d.contains("joana"), d)
        assertFalse(d.contains("cpf"), d)
    }

    @Test
    fun `no maximo 3 causas e sem laco infinito`() {
        var e: Throwable = IOException("raiz")
        repeat(6) { e = IOException("nivel $it", e) }
        assertEquals(3, describeFailureChain(e).split("← causa:").size - 1)
    }

    @Test
    fun `handleApiCall continua devolvendo Error e nao relanca`() = runTest {
        val r = handleApiCall<String> { throw IOException("Software caused connection abort") }
        assertIs<ApiResult.Error>(r)
        assertEquals(-1, r.code)
    }
}
