package br.com.codecacto.kmplib.firebase.auth

// Erro NEUTRO de autenticação (2.291.0). Morava no `kmplib-firebase`, junto do `AuthRepository`
// do Firebase, e era só por ele (mais `IAuthRepository`/`User`, que já moravam aqui) que o
// `kmplib-auth` — o own-auth — dependia do módulo Firebase inteiro. Essa dependência levava
// Firebase Auth/Storage/Remote Config a todo app com login, inclusive os que nunca configuram
// Firebase. O PACOTE ficou o mesmo para nenhum import quebrar (`kmplib-firebase` continua
// expondo-o via `api(kmplib-core)`), e o `AuthRepository` do Firebase segue lançando estes tipos.

/**
 * Exceções de autenticação — comuns ao own-auth (`EmailPasswordAuthRepository`) e ao Firebase
 * (`AuthRepository`).
 */
sealed class AuthException(message: String) : Exception(message) {
    data object InvalidEmail : AuthException("Email inválido")
    data object InvalidCredentials : AuthException("Email ou senha incorretos")
    data object UserNotFound : AuthException("Usuário não encontrado")
    data object EmailAlreadyInUse : AuthException("Este email já está em uso")
    data object WeakPassword : AuthException("Senha muito fraca. Use pelo menos 6 caracteres")
    data object TooManyRequests : AuthException("Muitas tentativas. Tente novamente mais tarde")
    data object NetworkError : AuthException("Erro de conexão. Verifique sua internet")
    data object RequiresRecentLogin : AuthException("Por segurança, faça login novamente")
    data object NotAuthenticated : AuthException("Usuário não autenticado")
    data class UnknownError(override val message: String) : AuthException(message)
}
