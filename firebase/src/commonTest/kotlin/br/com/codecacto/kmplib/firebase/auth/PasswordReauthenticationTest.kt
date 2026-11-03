package br.com.codecacto.kmplib.firebase.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A sequência oficial do Firebase para operação sensível: reconfirmar a credencial NA MESMA conta
 * (`reauthenticate`) e só depois `updatePassword`/`delete` — sem `signIn` no meio.
 */
class PasswordReauthenticationTest {

    private class FakeTarget(
        override val email: String? = "ana@x.com",
        private val reauthError: Exception? = null,
        private val updateError: Exception? = null,
    ) : ReauthTarget {
        val calls = mutableListOf<String>()

        override suspend fun reauthenticateWithPassword(email: String, password: String) {
            calls += "reauth:$email:$password"
            reauthError?.let { throw it }
        }

        override suspend fun updatePassword(newPassword: String) {
            calls += "update:$newPassword"
            updateError?.let { throw it }
        }

        override suspend fun delete() {
            calls += "delete"
        }
    }

    @Test
    fun troca_reautentica_a_mesma_conta_antes_de_trocar() = runTest {
        val target = FakeTarget()
        val result = changePasswordReauthenticated(target, "atual", "nova-forte")
        assertTrue(result.isSuccess)
        assertEquals(listOf("reauth:ana@x.com:atual", "update:nova-forte"), target.calls)
    }

    @Test
    fun senha_atual_errada_nao_chega_ao_update() = runTest {
        val target = FakeTarget(reauthError = Exception("[ERROR_WRONG_PASSWORD] wrong-password"))
        val result = changePasswordReauthenticated(target, "errada", "nova")
        assertEquals(AuthException.InvalidCredentials, result.exceptionOrNull())
        assertEquals(listOf("reauth:ana@x.com:errada"), target.calls)
    }

    @Test
    fun credencial_invalida_tambem_e_senha_errada() = runTest {
        val target = FakeTarget(reauthError = Exception("invalid-credential"))
        assertEquals(
            AuthException.InvalidCredentials,
            changePasswordReauthenticated(target, "x", "y").exceptionOrNull(),
        )
    }

    @Test
    fun senha_nova_fraca() = runTest {
        val target = FakeTarget(updateError = Exception("weak-password: Password should be at least 6 characters"))
        assertEquals(
            AuthException.WeakPassword,
            changePasswordReauthenticated(target, "atual", "1").exceptionOrNull(),
        )
    }

    @Test
    fun excesso_de_tentativas() = runTest {
        val target = FakeTarget(reauthError = Exception("too-many-requests"))
        assertEquals(
            AuthException.TooManyRequests,
            changePasswordReauthenticated(target, "a", "b").exceptionOrNull(),
        )
    }

    @Test
    fun sem_rede() = runTest {
        val target = FakeTarget(reauthError = Exception("A network error has occurred"))
        assertEquals(
            AuthException.NetworkError,
            changePasswordReauthenticated(target, "a", "b").exceptionOrNull(),
        )
    }

    @Test
    fun sem_usuario_logado() = runTest {
        assertEquals(
            AuthException.NotAuthenticated,
            changePasswordReauthenticated(null, "a", "b").exceptionOrNull(),
        )
    }

    @Test
    fun conta_sem_email_nao_tenta_reautenticar() = runTest {
        val target = FakeTarget(email = null)
        val error = changePasswordReauthenticated(target, "a", "b").exceptionOrNull()
        assertIs<AuthException.UnknownError>(error)
        assertTrue(target.calls.isEmpty())
    }

    @Test
    fun exclusao_com_senha_reautentica_antes_de_excluir() = runTest {
        val target = FakeTarget()
        assertTrue(deleteAccountReauthenticated(target, "atual").isSuccess)
        assertEquals(listOf("reauth:ana@x.com:atual", "delete"), target.calls)
    }

    @Test
    fun exclusao_sem_senha_vai_direto() = runTest {
        val target = FakeTarget()
        assertTrue(deleteAccountReauthenticated(target, null).isSuccess)
        assertEquals(listOf("delete"), target.calls)
    }

    @Test
    fun exclusao_com_senha_errada_nao_exclui() = runTest {
        val target = FakeTarget(reauthError = Exception("wrong-password"))
        assertEquals(
            AuthException.InvalidCredentials,
            deleteAccountReauthenticated(target, "errada").exceptionOrNull(),
        )
        assertEquals(listOf("reauth:ana@x.com:errada"), target.calls)
    }

    @Test
    fun mensagem_desconhecida_preserva_o_texto() {
        val error = mapFirebaseAuthException(Exception("algo novo"))
        assertEquals(AuthException.UnknownError("algo novo"), error)
    }
}
