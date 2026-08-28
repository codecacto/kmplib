package br.com.codecacto.kmplib.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O nome do banco vira **nome de diretório** no iOS (é o diretório que recebe a marcação de "fora do
 * backup do iCloud", para cobrir o `.db` e os `-wal`/`-shm` juntos). Nome vindo do app não pode virar
 * caminho: um `../` escreveria fora do container.
 */
class SyncDatabaseDirectoryTest {

    @Test
    fun `nome comum passa intacto`() {
        assertEquals("kmplib_sync.db", syncDatabaseDirectoryName("kmplib_sync.db"))
        assertEquals("cofre.db", syncDatabaseDirectoryName("cofre.db"))
    }

    @Test
    fun `separador de caminho nao escapa do diretorio`() {
        val nome = syncDatabaseDirectoryName("../../Documents/cofre.db")
        // Sem separador, o resultado é um nome de diretório único — não há travessia possível.
        // (`.._.._Documents_cofre.db` é feio e inofensivo; `../..` seria escrita fora do container.)
        assertFalse(nome.contains('/'), "não pode sobrar separador: $nome")
        assertTrue(nome != "." && nome != "..", "não pode virar componente de travessia: $nome")
    }

    @Test
    fun `separador do Windows e dois pontos tambem saem`() {
        val nome = syncDatabaseDirectoryName("pasta\\banco:1.db")
        assertFalse(nome.contains('\\'))
        assertFalse(nome.contains(':'))
    }

    @Test
    fun `caractere de controle sai`() {
        val nome = syncDatabaseDirectoryName("banco\u0001\n.db")
        assertTrue(nome.none { it.code < 0x20 }, "sobrou caractere de controle: $nome")
    }

    @Test
    fun `nome que sobra vazio cai no default`() {
        assertEquals(DEFAULT_SYNC_DB_NAME, syncDatabaseDirectoryName("   "))
        assertEquals(DEFAULT_SYNC_DB_NAME, syncDatabaseDirectoryName(""))
    }

    @Test
    fun `nome que e so ponto ou ponto-ponto cai no default`() {
        assertEquals(DEFAULT_SYNC_DB_NAME, syncDatabaseDirectoryName("."))
        assertEquals(DEFAULT_SYNC_DB_NAME, syncDatabaseDirectoryName(".."))
    }

    @Test
    fun `bancos diferentes ficam em diretorios diferentes`() {
        // Duas bases no mesmo app precisam de diretórios distintos: a marcação de backup é por
        // diretório, e um app pode querer excluir uma e preservar a outra.
        assertTrue(syncDatabaseDirectoryName("a.db") != syncDatabaseDirectoryName("b.db"))
    }
}
