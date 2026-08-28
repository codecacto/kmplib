package br.com.codecacto.kmplib.media

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundEffectOutcomeTest {

    @Test
    fun sucesso_nao_carrega_erro() {
        val outcome: SoundEffectOutcome = SoundEffectOutcome.Success

        assertTrue(outcome.isSuccess)
        assertNull(outcome.errorOrNull)
    }

    @Test
    fun falha_expoe_o_erro_tipado() {
        val outcome: SoundEffectOutcome = failWith(SoundEffectError.InvalidAudio)

        assertFalse(outcome.isSuccess)
        assertEquals(SoundEffectError.InvalidAudio, outcome.errorOrNull)
    }

    @Test
    fun unknown_guarda_a_mensagem_de_diagnostico() {
        val outcome = failWith(SoundEffectError.Unknown("pool sem stream"))

        assertEquals(SoundEffectError.Unknown("pool sem stream"), outcome.errorOrNull)
    }

    @Test
    fun reprodutor_inerte_falha_sem_lancar_e_segue_utilizavel() = runTest {
        // Android sem KmpLib.init: o produto tem de continuar contando voltas, mudo.
        val player: SoundEffectPlayer = UnavailableSoundEffectPlayer()

        assertEquals(SoundEffectError.NotInitialized, player.load("volta", ByteArray(64)).errorOrNull)
        assertEquals(SoundEffectError.NotInitialized, player.play("volta").errorOrNull)
        assertFalse(player.isLoaded("volta"))
        assertTrue(player.loadedKeys.isEmpty())

        player.unload("volta")
        player.release()
        player.release()

        assertEquals(SoundEffectError.NotInitialized, player.play("volta").errorOrNull)
    }
}
