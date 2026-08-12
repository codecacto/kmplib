package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O arquivo que o `ShareHandler` cria para compartilhar **não pode ficar para sempre** (é a cópia em
 * texto claro do que o app exportou), mas também **não pode ser apagado cedo demais** — o app receptor
 * lê a URI depois do chooser. A regra de idade é o que equilibra os dois.
 */
class SharedFileCleanupTest {

    private val agora = 1_000_000_000L

    @Test
    fun `arquivo do share em curso NAO e apagado`() {
        // Acabou de ser gravado (idade 0): apagar aqui quebraria o compartilhamento.
        assertFalse(shouldPurgeSharedFile(agora, agora, DEFAULT_SHARED_FILE_TTL_MILLIS))
    }

    @Test
    fun `arquivo recente ainda dentro da janela e preservado`() {
        val cincoMinutos = 5 * 60 * 1000L
        assertFalse(
            shouldPurgeSharedFile(agora - cincoMinutos, agora, DEFAULT_SHARED_FILE_TTL_MILLIS),
        )
    }

    @Test
    fun `arquivo alem da janela e apagado`() {
        val duasHoras = 2 * 60 * 60 * 1000L
        assertTrue(shouldPurgeSharedFile(agora - duasHoras, agora, DEFAULT_SHARED_FILE_TTL_MILLIS))
    }

    @Test
    fun `idade exatamente igual a janela e apagada`() {
        assertTrue(
            shouldPurgeSharedFile(
                agora - DEFAULT_SHARED_FILE_TTL_MILLIS,
                agora,
                DEFAULT_SHARED_FILE_TTL_MILLIS,
            ),
        )
    }

    @Test
    fun `janela zero apaga tudo`() {
        // "Limpar dados": não sobra cópia nenhuma, nem a recém-gravada.
        assertTrue(shouldPurgeSharedFile(agora, agora, 0L))
        assertTrue(shouldPurgeSharedFile(agora, agora, -1L))
    }

    @Test
    fun `data desconhecida conta como residuo antigo`() {
        // Sistema que não informa a data de modificação devolve 0 (epoch): é lixo, não share em curso.
        assertTrue(shouldPurgeSharedFile(0L, agora, DEFAULT_SHARED_FILE_TTL_MILLIS))
    }

    @Test
    fun `data no futuro preserva o arquivo`() {
        // Relógio do aparelho alterado: manter lixo por um ciclo é melhor que matar um share ativo.
        assertFalse(
            shouldPurgeSharedFile(agora + 60_000L, agora, DEFAULT_SHARED_FILE_TTL_MILLIS),
        )
    }

    @Test
    fun `nome comum passa intacto`() {
        assertEquals("cofre-2026-08-11.json", sanitizeSharedFileName("cofre-2026-08-11.json"))
    }

    @Test
    fun `separador de caminho nao escapa do diretorio`() {
        val nome = sanitizeSharedFileName("../../Documents/cofre.json")
        assertFalse(nome.contains('/'), "sobrou separador: $nome")
        assertFalse(nome.startsWith(".."), "sobrou travessia: $nome")
    }

    @Test
    fun `nome vindo de dado do usuario e sanitizado`() {
        // "Cofre de João/Maria" é nome plausível montado a partir de dado do usuário.
        val nome = sanitizeSharedFileName("Cofre de João/Maria.json")
        assertEquals("Cofre de João_Maria.json", nome)
    }

    @Test
    fun `nome vazio cai no fallback`() {
        assertEquals(FALLBACK_SHARED_FILE_NAME, sanitizeSharedFileName("   "))
        assertEquals(FALLBACK_SHARED_FILE_NAME, sanitizeSharedFileName("///"))
        assertEquals(FALLBACK_SHARED_FILE_NAME, sanitizeSharedFileName(".."))
    }

    @Test
    fun `nome gigante e truncado preservando a extensao`() {
        val nome = sanitizeSharedFileName("a".repeat(400) + ".pdf")
        assertTrue(nome.length <= 128, "não truncou: ${nome.length}")
        // A extensão decide qual app abre o arquivo — perdê-la quebraria o share.
        assertTrue(nome.endsWith(".pdf"), "perdeu a extensão: $nome")
    }
}
