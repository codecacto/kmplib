package br.com.codecacto.kmplib.firebase.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeletePrefixResultTest {

    @Test
    fun `sucesso completo quando nenhuma falha`() {
        val r = DeletePrefixResult(scannedCount = 3, deletedCount = 3, failedCount = 0)
        assertTrue(r.isCompleteSuccess)
    }

    @Test
    fun `sucesso completo quando prefixo vazio (nada varrido)`() {
        val r = DeletePrefixResult(scannedCount = 0, deletedCount = 0, failedCount = 0)
        assertTrue(r.isCompleteSuccess)
    }

    @Test
    fun `nao e sucesso completo quando ha falha`() {
        val r = DeletePrefixResult(scannedCount = 5, deletedCount = 4, failedCount = 1)
        assertFalse(r.isCompleteSuccess)
    }

    @Test
    fun `PartialDeletion carrega resultado parcial e causas`() {
        val partial = DeletePrefixResult(scannedCount = 2, deletedCount = 1, failedCount = 1)
        val causes = listOf<StorageException>(StorageException.Unauthorized("sem permissao"))
        val ex = StorageException.PartialDeletion("falha parcial", partial, causes)

        assertEquals("falha parcial", ex.message)
        assertEquals(1, ex.result.failedCount)
        assertEquals(1, ex.causes.size)
        assertTrue(ex.causes.first() is StorageException.Unauthorized)
    }
}
