package br.com.codecacto.kmplib.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiResultTest {

    @Test
    fun successExposesDataAndMapsValue() {
        val result = ApiResult.Success(2)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertEquals(ApiResult.Success("2"), result.map { it.toString() })
    }

    @Test
    fun errorExposesMessageAndSkipsMap() {
        val result = ApiResult.Error(code = 404, message = "Nao encontrado")

        assertTrue(result.isError)
        assertEquals("Nao encontrado", result.errorOrNull())
        assertNull(result.getOrNull())
        assertEquals(result, result.map { "ignored" })
    }

    @Test
    fun loadingFlagsAsLoading() {
        assertTrue(ApiResult.Loading.isLoading)
        assertFalse(ApiResult.Loading.isSuccess)
    }

    @Test
    fun mapsDefaultMessages() {
        assertEquals("Recurso não encontrado.", defaultHttpErrorMessage(404, null))
        assertEquals("Falha", defaultHttpErrorMessage(418, "Falha"))
        assertEquals("Sem conexão com a internet.", mapGenericNetworkMessage(RuntimeException("Unable to resolve host")))
    }
}
