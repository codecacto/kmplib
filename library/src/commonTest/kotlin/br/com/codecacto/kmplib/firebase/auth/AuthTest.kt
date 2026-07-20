package br.com.codecacto.kmplib.firebase.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes de contrato do módulo `firebase/auth` — via [FakeAuthRepository] (sem Firebase real),
 * mais os helpers puros de [User] e as transições do [AuthStateManager].
 */
class AuthTest {

    // --- User (helpers de provider) -----------------------------------------

    @Test
    fun user_helpers_de_provider() {
        assertTrue(User("1", "e@x.com", providerId = "password").isEmailProvider)
        assertTrue(User("1", "e@x.com", providerId = "google.com").isGoogleProvider)
        assertTrue(User("1", "e@x.com", providerId = "apple.com").isAppleProvider)
        val email = User("1", "e@x.com", providerId = "password")
        assertFalse(email.isGoogleProvider)
        assertFalse(email.isAppleProvider)
    }

    // --- FakeAuthRepository (contrato) --------------------------------------

    @Test
    fun login_email_emite_usuario_e_marca_logado() = runTest {
        val auth = FakeAuthRepository()
        assertFalse(auth.isLoggedInSync)

        val result = auth.signInWithEmail("joao@x.com", "senha")

        assertTrue(result.isSuccess)
        assertEquals("joao@x.com", auth.currentUserSync?.email)
        assertTrue(auth.isLoggedInSync)
        assertTrue(auth.isLoggedIn.first())
        assertEquals("joao@x.com", auth.currentUser.first()?.email)
    }

    @Test
    fun login_pode_falhar_de_forma_determinista() = runTest {
        val auth = FakeAuthRepository()
        auth.nextResult = IllegalArgumentException("credenciais inválidas")

        val result = auth.signInWithEmail("x@x.com", "errada")

        assertTrue(result.isFailure)
        assertNull(auth.currentUserSync)
        // A falha é consumida: o próximo login sucede.
        assertTrue(auth.signInWithEmail("x@x.com", "certa").isSuccess)
    }

    @Test
    fun signOut_limpa_o_usuario() = runTest {
        val auth = FakeAuthRepository(User("1", "e@x.com"))
        assertTrue(auth.isLoggedInSync)

        auth.signOut()

        assertNull(auth.currentUserSync)
        assertFalse(auth.isLoggedIn.first())
    }

    @Test
    fun getIdToken_exige_usuario_logado() = runTest {
        val auth = FakeAuthRepository()
        assertTrue(auth.getIdToken().isFailure)

        auth.signInWithGoogle("id-token")
        val token = auth.getIdToken()
        assertTrue(token.isSuccess)
        assertEquals("fake-id-token", token.getOrNull())
    }

    @Test
    fun google_e_apple_carregam_o_provider_correto() = runTest {
        val auth = FakeAuthRepository()
        auth.signInWithGoogle("id")
        assertTrue(auth.currentUserSync!!.isGoogleProvider)
        auth.signInWithApple("id", "nonce")
        assertTrue(auth.currentUserSync!!.isAppleProvider)
    }

    // --- AuthStateManager (estado global) -----------------------------------

    @AfterTest
    fun resetAuthState() = AuthStateManager.setLoading()

    @Test
    fun authStateManager_transicoes() {
        AuthStateManager.setLoading()
        assertTrue(AuthStateManager.authState.value is AuthState.Loading)
        assertFalse(AuthStateManager.isAuthenticated)
        assertNull(AuthStateManager.currentUser)

        val user = User("42", "maria@x.com")
        AuthStateManager.setAuthenticated(user)
        assertTrue(AuthStateManager.isAuthenticated)
        assertEquals(user, AuthStateManager.currentUser)
        assertEquals(user, (AuthStateManager.authState.value as AuthState.Authenticated).user)

        AuthStateManager.setNotAuthenticated()
        assertFalse(AuthStateManager.isAuthenticated)
        assertNull(AuthStateManager.currentUser)
    }
}
