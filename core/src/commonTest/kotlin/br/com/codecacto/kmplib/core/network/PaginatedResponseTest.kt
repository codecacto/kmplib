package br.com.codecacto.kmplib.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaginatedResponseTest {

    @Test
    fun hasNextPageTrueQuandoNaoEhUltima() {
        val response = PaginatedResponse(
            data = listOf(1, 2, 3),
            page = 1,
            pageSize = 3,
            total = 9,
            totalPages = 3
        )
        assertTrue(response.hasNextPage)
    }

    @Test
    fun hasNextPageFalseNaUltimaPagina() {
        val response = PaginatedResponse(
            data = listOf(7, 8, 9),
            page = 3,
            pageSize = 3,
            total = 9,
            totalPages = 3
        )
        assertFalse(response.hasNextPage)
    }

    @Test
    fun hasNextPageFalseQuandoPaginaUnica() {
        val response = PaginatedResponse(
            data = listOf(1),
            page = 1,
            pageSize = 10,
            total = 1,
            totalPages = 1
        )
        assertFalse(response.hasNextPage)
    }

    @Test
    fun preservaCampos() {
        val response = PaginatedResponse(
            data = listOf("a", "b"),
            page = 2,
            pageSize = 2,
            total = 5,
            totalPages = 3
        )
        assertEquals(listOf("a", "b"), response.data)
        assertEquals(2, response.page)
        assertEquals(2, response.pageSize)
        assertEquals(5, response.total)
        assertEquals(3, response.totalPages)
    }
}
