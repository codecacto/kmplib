package br.com.codecacto.kmplib.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundEffectRegistryTest {

    @Test
    fun put_de_chave_nova_nao_devolve_nada_para_descarregar() {
        val registry = SoundEffectRegistry<Int>()

        assertNull(registry.put("volta", 10))
        assertTrue(registry.isLoaded("volta"))
        assertEquals(10, registry.handleOf("volta"))
    }

    @Test
    fun recarga_devolve_o_identificador_anterior() {
        // A invariante que evita o vazamento silencioso: o sample/SystemSoundID antigo TEM de voltar
        // para quem o criou descarregar. Sem isso o audio antigo fica na memoria para sempre.
        val registry = SoundEffectRegistry<Int>()
        registry.put("volta", 10)

        assertEquals(10, registry.put("volta", 11))
        assertEquals(11, registry.handleOf("volta"))
        assertEquals(1, registry.size)
    }

    @Test
    fun remove_devolve_o_identificador_e_esquece_a_chave() {
        val registry = SoundEffectRegistry<Int>()
        registry.put("volta", 10)

        assertEquals(10, registry.remove("volta"))
        assertFalse(registry.isLoaded("volta"))
        assertNull(registry.remove("volta"), "remover duas vezes nao pode descarregar duas vezes")
    }

    @Test
    fun releaseAll_devolve_todos_e_e_idempotente() {
        val registry = SoundEffectRegistry<Int>()
        registry.put("volta", 10)
        registry.put("meta", 11)

        assertEquals(listOf(10, 11), registry.releaseAll())
        assertTrue(registry.isReleased)
        assertEquals(emptyList(), registry.releaseAll(), "release duas vezes descarregaria em dobro")
        assertEquals(0, registry.size)
    }

    @Test
    fun keys_preserva_a_ordem_de_carga() {
        val registry = SoundEffectRegistry<Int>()
        registry.put("volta", 10)
        registry.put("meta", 11)

        assertEquals(listOf("volta", "meta"), registry.keys.toList())
    }

    @Test
    fun rejectionFor_recusa_chave_em_branco() {
        val registry = SoundEffectRegistry<Int>()

        assertEquals(SoundEffectError.InvalidKey, registry.rejectionFor("  "))
        assertNull(registry.rejectionFor("volta"))
    }

    @Test
    fun rejectionFor_depois_do_release_e_sempre_released() {
        val registry = SoundEffectRegistry<Int>()
        registry.releaseAll()

        // Released vence InvalidKey: usar instancia morta e o diagnostico que importa.
        assertEquals(SoundEffectError.Released, registry.rejectionFor("volta"))
        assertEquals(SoundEffectError.Released, registry.rejectionFor(""))
    }
}
