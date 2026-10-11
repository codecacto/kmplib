package br.com.codecacto.kmplib.observability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** GAP-IOS-UNCAUGHT-LOG-01 (2.290.0): o que a exceção não tratada escreve no log do aparelho. */
class UncaughtExceptionLoggerTest {

    private class ErroDeTela(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

    @Test
    fun `depuracao - tipo e mensagem de cada causa na primeira linha`() {
        val erro = ErroDeTela("falhou ao abrir", ClassCastException("asList cannot be cast to Int"))
        val linhas = uncaughtExceptionLogLines(erro, includeMessage = true)

        assertEquals(1, linhas.size)
        val primeira = linhas.single()
        assertTrue(primeira.startsWith(UNCAUGHT_PREFIX))
        assertTrue(primeira.contains("ErroDeTela: falhou ao abrir"), primeira)
        assertTrue(primeira.contains(CAUSE_SEPARATOR + "${ClassCastException::class.qualifiedName}: asList cannot be cast to Int"), primeira)
    }

    @Test
    fun `release - so os tipos - a mensagem pode ter dado de usuario`() {
        val erro = ErroDeTela("paciente Maria da Silva", IllegalStateException("cpf 123"))
        val primeira = uncaughtExceptionLogLines(erro, includeMessage = false).single()

        assertFalse(primeira.contains("Maria"), primeira)
        assertFalse(primeira.contains("cpf"), primeira)
        assertTrue(primeira.contains("ErroDeTela"), primeira)
        assertTrue(primeira.contains(IllegalStateException::class.qualifiedName!!), primeira)
    }

    @Test
    fun `mensagem nula ou vazia nao deixa dois pontos pendurados`() {
        // O nome qualificado é o da plataforma (kotlin.* no iOS, java.lang.* na JVM).
        val tipo = IllegalStateException::class.qualifiedName
        assertEquals(tipo, describeThrowable(IllegalStateException(), includeMessage = true))
        assertEquals(tipo, describeThrowable(IllegalStateException(""), includeMessage = true))
    }

    @Test
    fun `cadeia de causas tem teto`() {
        var erro: Throwable = IllegalStateException("raiz")
        repeat(10) { erro = ErroDeTela("nivel $it", erro) }
        val primeira = uncaughtExceptionLogLines(erro, includeMessage = true, maxCauses = 5).single()
        assertEquals(4, primeira.split(CAUSE_SEPARATOR).size - 1)
    }

    @Test
    fun `pilha entra linha a linha - sem linha vazia - com teto de linhas e de tamanho`() {
        val pilha = "kotlin.Error: x\n    at 0 app 0x1 kfun:a\n\n    at 1 app 0x2 kfun:" + "b".repeat(2000) + "\n" +
            (2..200).joinToString("\n") { "    at $it app" }
        val linhas = uncaughtExceptionLogLines(Error("x"), includeMessage = true, stackTrace = pilha, maxStackLines = 10, maxLineLength = 100)

        assertEquals(1 + 10, linhas.size)
        assertTrue(linhas.all { it.isNotBlank() && it.length <= 100 })
        assertEquals("    at 0 app 0x1 kfun:a", linhas[2])
    }

    @Test
    fun `sem pilha - so a primeira linha`() {
        assertEquals(1, uncaughtExceptionLogLines(Error("x"), includeMessage = true, stackTrace = null).size)
    }

    @Test
    fun `porcentagem vira literal no formato do NSLog`() {
        assertEquals("100%% de %%@ e %%d", escapeLogFormat("100% de %@ e %d"))
        assertEquals("sem nada", escapeLogFormat("sem nada"))
    }
}
