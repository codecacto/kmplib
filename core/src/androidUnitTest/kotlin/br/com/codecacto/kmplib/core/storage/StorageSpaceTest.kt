package br.com.codecacto.kmplib.core.storage

import kotlin.test.Test
import kotlin.test.assertNull

/**
 * `availableStorageBytes()` nunca lança: sem `Context` (ou com a plataforma sem resposta) devolve
 * `null`, e quem chama NÃO bloqueia a captura — não saber não é motivo para recusar (2.328.0).
 */
class StorageSpaceTest {
    @Test
    fun `sem resposta da plataforma devolve null em vez de lancar`() {
        assertNull(availableStorageBytes())
    }
}
