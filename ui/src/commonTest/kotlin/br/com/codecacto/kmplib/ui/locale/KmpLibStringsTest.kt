package br.com.codecacto.kmplib.ui.locale

import kotlin.test.Test
import kotlin.test.assertEquals

class KmpLibStringsTest {

    @Test
    fun `argumentos posicionais como o compose-resources`() {
        assertEquals("Erro 503 em x", formatResourceArgs("Erro %1\$d em %2\$s", arrayOf<Any>(503, "x")))
        assertEquals("x antes de 503", formatResourceArgs("%2\$s antes de %1\$d", arrayOf<Any>(503, "x")))
        assertEquals("sem argumento", formatResourceArgs("sem argumento", emptyArray<Any>()))
    }

    @Test
    fun `indice sem argumento fica como esta em vez de derrubar a tela`() {
        assertEquals("a %2\$s", formatResourceArgs("%1\$s %2\$s", arrayOf<Any>("a")))
    }
}
