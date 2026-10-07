package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Digitação rápida não perde caractere" (2.258.0) — o defeito do MinhaOS ("teste qa" → "tete a").
 *
 * Simula o campo com o ViewModel ATRASADO: cada tecla emite o texto, mas o valor de fora só volta
 * ao campo alguns quadros depois (e às vezes conflado, como o `StateFlow` faz). O campo com texto
 * local ([Campo]) é a mesma lógica do `AppTextField(keepTextLocally = true)` e do
 * `rememberSyncedTextFieldState`: tecla aplicada sobre o texto LOCAL, valor de fora passando pelo
 * [TextInputReconciler].
 */
class TextInputReconcilerTest {

    /** ViewModel que recebe cada valor e só o devolve [atraso] entregas depois. */
    private class ViewModelAtrasado(inicial: String) {
        var valor: String = inicial
            private set
        private val fila = ArrayDeque<String>()
        fun receber(v: String) { fila.addLast(v) }
        /** Entrega o próximo da fila (um quadro). [conflar] = pula para o mais recente. */
        fun quadro(conflar: Boolean = false) {
            if (fila.isEmpty()) return
            valor = if (conflar) fila.last().also { fila.clear() } else fila.removeFirst()
        }
        fun drenar() { while (fila.isNotEmpty()) quadro() }
        /** A tela muda o valor por outro motivo (limpar, restaurar). */
        fun definir(v: String) { fila.clear(); valor = v }
    }

    /** O campo com texto local — o caminho corrigido. */
    private class Campo(private val vm: ViewModelAtrasado) {
        var texto: String = vm.valor
        private val reconciler = TextInputReconciler(vm.valor)
        fun teclar(c: Char) = editar(texto + c)
        fun apagar() = editar(texto.dropLast(1))
        fun editar(novo: String) {
            texto = novo
            if (reconciler.onLocalText(novo)) vm.receber(novo)
        }
        /** Recomposição com o valor de fora do momento. */
        fun recompor() { reconciler.onExternal(vm.valor, texto)?.let { texto = it } }
    }

    /** O campo controlado de antes: desenha o valor do ViewModel e a tecla cai sobre ele. */
    private class CampoControlado(private val vm: ViewModelAtrasado) {
        var mostrado: String = vm.valor
        fun teclar(c: Char) { mostrado += c; vm.receber(mostrado) }
        fun recompor() { mostrado = vm.valor }
    }

    private fun digitarEmRajada(texto: String, quadroACada: Int, conflar: Boolean = false): String {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm)
        texto.forEachIndexed { i, c ->
            campo.teclar(c)
            if (i % quadroACada == 0) { vm.quadro(conflar); campo.recompor() }
        }
        vm.drenar(); campo.recompor()
        assertEquals(texto, vm.valor, "o ViewModel termina com o texto digitado")
        return campo.texto
    }

    @Test
    fun `campo controlado de antes PERDE letras com o ViewModel atrasado - reproduz o defeito`() {
        val vm = ViewModelAtrasado("")
        val campo = CampoControlado(vm)
        "teste qa".forEachIndexed { i, c ->
            campo.teclar(c)
            // A cada duas teclas a tela recompõe com o valor que o ViewModel tem NAQUELE quadro.
            if (i % 2 == 1) { vm.quadro(); campo.recompor() }
        }
        vm.drenar(); campo.recompor()
        assertTrue(campo.mostrado != "teste qa", "o modelo do defeito tem de perder letras: ${campo.mostrado}")
    }

    @Test
    fun `digitacao rapida nao perde caractere com eco atrasado`() {
        for (passo in 1..5) assertEquals("teste qa", digitarEmRajada("teste qa", passo))
    }

    @Test
    fun `digitacao rapida nao perde caractere com StateFlow conflado`() {
        for (passo in 1..5) {
            assertEquals("busca com acentuação", digitarEmRajada("busca com acentuação", passo, conflar = true))
        }
    }

    @Test
    fun `apagar e redigitar em rajada nao volta texto velho`() {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm)
        "abc".forEach { campo.teclar(it) }
        campo.apagar(); campo.apagar(); campo.apagar()
        "xy".forEach { campo.teclar(it) }
        // Os ecos de "a", "ab", "abc", "ab", "a", "", "x" chegam um a um, com o campo já em "xy".
        repeat(7) { vm.quadro(); campo.recompor(); assertEquals("xy", campo.texto) }
        vm.drenar(); campo.recompor()
        assertEquals("xy", campo.texto)
        assertEquals("xy", vm.valor)
    }

    @Test
    fun `limpar pela tela reescreve o campo`() {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm)
        "joao".forEach { campo.teclar(it) }
        vm.drenar(); campo.recompor()
        vm.definir("")
        campo.recompor()
        assertEquals("", campo.texto)
    }

    @Test
    fun `restaurar pela tela reescreve o campo mesmo com eco em voo`() {
        val vm = ViewModelAtrasado("")
        val campo = Campo(vm)
        "jo".forEach { campo.teclar(it) }
        vm.definir("maria") // ex.: escolheu um filtro salvo
        campo.recompor()
        assertEquals("maria", campo.texto)
        campo.teclar('!')
        vm.drenar(); campo.recompor()
        assertEquals("maria!", campo.texto)
        assertEquals("maria!", vm.valor)
    }

    @Test
    fun `valor transformado pelo ViewModel vence o digitado`() {
        val r = TextInputReconciler("")
        assertTrue(r.onLocalText("ABC"))
        // A tela guardou em minúsculas: valor que ninguém digitou → o campo assume.
        assertEquals("abc", r.onExternal("abc", "ABC"))
        assertEquals(0, r.pendingCount)
    }

    @Test
    fun `valor inicial e texto reescrito de fora nao voltam ao ViewModel`() {
        val r = TextInputReconciler("inicial")
        assertTrue(!r.onLocalText("inicial"))
        assertEquals("novo", r.onExternal("novo", "inicial"))
        assertTrue(!r.onLocalText("novo"))
    }

    @Test
    fun `texto restaurado do estado salvo e reenviado ao ViewModel vazio`() {
        // rememberTextFieldState restaura "abc"; o ViewModel novo nasce vazio.
        val r = TextInputReconciler("")
        assertTrue(r.onLocalText("abc"))
        assertNull(r.onExternal("abc", "abc"))
    }

    @Test
    fun `valor de fora que nunca ecoa nao faz a fila crescer sem limite`() {
        val r = TextInputReconciler("")
        var t = ""
        repeat(500) { t += "a"; r.onLocalText(t) }
        assertTrue(r.pendingCount <= 64)
    }

    @Test
    fun `valor externo igual ao local zera a fila sem reescrever`() {
        val r = TextInputReconciler("")
        r.onLocalText("a")
        assertNull(r.onExternal("zz", "zz"))
        assertEquals(0, r.pendingCount)
    }
}
