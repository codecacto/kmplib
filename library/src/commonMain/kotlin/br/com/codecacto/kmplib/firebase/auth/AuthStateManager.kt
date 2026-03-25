package br.com.codecacto.kmplib.firebase.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado global de autenticação do app.
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
}

/**
 * Gerenciador centralizado do estado de autenticação.
 *
 * Uso típico:
 * ```kotlin
 * // No login
 * AuthStateManager.setAuthenticated(user)
 *
 * // No logout
 * AuthStateManager.setNotAuthenticated()
 *
 * // Observar estado
 * AuthStateManager.authState.collect { state ->
 *     when (state) {
 *         is AuthState.Loading -> showLoading()
 *         is AuthState.Authenticated -> navigateToHome(state.user)
 *         is AuthState.NotAuthenticated -> navigateToLogin()
 *     }
 * }
 * ```
 */
object AuthStateManager {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun setAuthenticated(user: User) {
        _authState.value = AuthState.Authenticated(user)
    }

    fun setNotAuthenticated() {
        _authState.value = AuthState.NotAuthenticated
    }

    fun setLoading() {
        _authState.value = AuthState.Loading
    }

    val currentUser: User?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    val isAuthenticated: Boolean
        get() = _authState.value is AuthState.Authenticated
}
