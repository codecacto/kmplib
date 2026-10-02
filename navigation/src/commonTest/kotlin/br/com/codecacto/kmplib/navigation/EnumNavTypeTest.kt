package br.com.codecacto.kmplib.navigation

import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Ida e volta do `NavType` de enum pela ROTA (`serializeAsValue`/`parseValue`) e o `typeMap`.
 *
 * O `put`/`get` no `SavedState` é provado em `iosTest` (`EnumNavTypeSavedStateIosTest`): no Android o
 * `SavedState` é o `Bundle`, que no teste de unidade da JVM é um dublê vazio — e é no iOS que o defeito
 * de origem acontece.
 */
class EnumNavTypeTest {

    enum class Aba { RESUMO, HISTORICO, CONFIGURACOES }

    @Test
    fun `cada constante vai e volta pelo nome`() {
        val tipo = enumNavType<Aba>()
        Aba.entries.forEach { aba ->
            val naRota = tipo.serializeAsValue(aba)
            assertEquals(aba.name, naRota)
            assertEquals(aba, tipo.parseValue(naRota))
        }
    }

    @Test
    fun `nao anulavel nao aceita nulo`() {
        assertFalse(enumNavType<Aba>().isNullableAllowed)
    }

    @Test
    fun `valor que nao e constante do enum falha alto - nao vira outra aba`() {
        assertFailsWith<IllegalArgumentException> { enumNavType<Aba>().parseValue("INEXISTENTE") }
    }

    @Test
    fun `anulavel - constante e nulo vao e voltam`() {
        val tipo = enumNullableNavType<Aba>()
        assertTrue(tipo.isNullableAllowed)

        assertEquals("HISTORICO", tipo.serializeAsValue(Aba.HISTORICO))
        assertEquals(Aba.HISTORICO, tipo.parseValue("HISTORICO"))

        assertEquals("null", tipo.serializeAsValue(null))
        assertNull(tipo.parseValue("null"))
    }

    @Test
    fun `typeMap usa o tipo exato do argumento como chave`() {
        val mapa = enumTypeMap<Aba>()
        assertEquals(setOf(typeOf<Aba>()), mapa.keys)
        assertEquals(Aba.RESUMO, mapa.getValue(typeOf<Aba>()).parseValue("RESUMO"))
    }

    @Test
    fun `typeMap anulavel usa o tipo anulavel como chave - e o nao anulavel nao serve`() {
        // A rota declara `Aba?`; com a chave `Aba` a navegação não acharia o tipo e o app fecharia
        // do mesmo jeito.
        val mapa = enumNullableTypeMap<Aba>()
        assertEquals(setOf(typeOf<Aba?>()), mapa.keys)
        assertFalse(typeOf<Aba>() in mapa)
    }

    @Test
    fun `dois enums na mesma rota - os mapas se somam`() {
        val mapa = enumTypeMap<Aba>() + enumNullableTypeMap<Aba>()
        assertEquals(2, mapa.size)
        assertSame(false, mapa.getValue(typeOf<Aba>()).isNullableAllowed)
        assertSame(true, mapa.getValue(typeOf<Aba?>()).isNullableAllowed)
    }
}
