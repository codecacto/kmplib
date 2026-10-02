package br.com.codecacto.kmplib.navigation

import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `put`/`get` no `SavedState` de verdade — no iOS, a plataforma em que a rota com enum fechava o app.
 * Roda no Mac (`./gradlew :kmplib-navigation:iosSimulatorArm64Test`); aqui no servidor só compila.
 */
class EnumNavTypeSavedStateIosTest {

    enum class Metodo { PIX, CARTAO }

    @Test
    fun putEGetDevolvemAMesmaConstante() {
        val tipo = enumNavType<Metodo>()
        val estado = savedState()

        tipo.put(estado, "metodo", Metodo.CARTAO)

        assertEquals("CARTAO", estado.read { getString("metodo") })
        assertEquals(Metodo.CARTAO, tipo.get(estado, "metodo"))
    }

    @Test
    fun anulavelGuardaENulo() {
        val tipo = enumNullableNavType<Metodo>()
        val estado = savedState()

        tipo.put(estado, "metodo", null)
        assertNull(tipo.get(estado, "metodo"))

        tipo.put(estado, "metodo", Metodo.PIX)
        assertEquals(Metodo.PIX, tipo.get(estado, "metodo"))
    }

    @Test
    fun chaveAusenteDevolveNulo() {
        val estado = savedState()
        estado.write { putString("outra", "x") }

        assertNull(enumNavType<Metodo>().get(estado, "metodo"))
    }
}
