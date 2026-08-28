package br.com.codecacto.kmplib.ui.mvi

import br.com.codecacto.kmplib.core.network.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiResourceTest {

    @Test
    fun successExpoeDadoEFlags() {
        val resource: UiResource<Int> = UiResource.Success(42)
        assertTrue(resource.isSuccess)
        assertFalse(resource.isLoading)
        assertFalse(resource.isError)
        assertEquals(42, resource.getOrNull())
        assertNull(resource.errorOrNull())
    }

    @Test
    fun errorExpoeMensagem() {
        val resource: UiResource<Int> = UiResource.Error("falhou")
        assertTrue(resource.isError)
        assertNull(resource.getOrNull())
        assertEquals("falhou", resource.errorOrNull())
    }

    @Test
    fun loadingEIdleFlags() {
        assertTrue(UiResource.Loading.isLoading)
        assertFalse(UiResource.Idle.isLoading)
        assertFalse(UiResource.Idle.isSuccess)
        assertFalse(UiResource.Idle.isError)
    }

    @Test
    fun toUiResourceMapeiaSuccess() {
        val result: ApiResult<String> = ApiResult.Success("ok")
        val resource = result.toUiResource()
        assertTrue(resource is UiResource.Success)
        assertEquals("ok", resource.getOrNull())
    }

    @Test
    fun toUiResourceMapeiaError() {
        val result: ApiResult<String> = ApiResult.Error(message = "erro de rede")
        val resource = result.toUiResource()
        assertTrue(resource is UiResource.Error)
        assertEquals("erro de rede", resource.errorOrNull())
    }

    @Test
    fun toUiResourceMapeiaLoading() {
        val result: ApiResult<String> = ApiResult.Loading
        assertEquals(UiResource.Loading, result.toUiResource())
    }
}
