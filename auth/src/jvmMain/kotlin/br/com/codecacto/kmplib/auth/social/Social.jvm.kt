package br.com.codecacto.kmplib.auth.social

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Desktop (2.353.0): **login social ainda não existe no PC.** O BFF da fábrica devolve a sessão por
 * deep link de esquema próprio (`<app>://…`), que no desktop exige registrar o esquema no SO pelo
 * instalador; o caminho do RFC 8252 para app desktop (redirect de LOOPBACK `http://127.0.0.1:<porta>`)
 * precisa de suporte no `authLocalSocialFlowRoutes` da backlib. Até lá, [authenticate] recusa com
 * [SocialBrowserException] (`reason = "unsupported"`) e a `LoginScreen` não mostra os botões sociais
 * no desktop ([provedoresSociaisPara] `"desktop"` = vazio). Login por usuário + senha funciona inteiro.
 */
actual class SocialBrowserLogin actual constructor() {
    actual suspend fun authenticate(startUrl: String, redirectScheme: String): String =
        throw SocialBrowserException("Login social pelo navegador ainda não é suportado no computador.", reason = "unsupported")
}

actual object PkceCrypto {
    private val rng = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    actual fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
}

/** Desktop: não há "Entrar com a Apple" nativo fora dos aparelhos Apple. */
actual class AppleAuthProvider actual constructor() {
    actual suspend fun signIn(): AppleSignInResult = AppleSignInResult.error(UNSUPPORTED)
    actual suspend fun signIn(nonce: String): AppleSignInResult = AppleSignInResult.error(UNSUPPORTED)
}

/** Desktop: não há Credential Manager / GoogleSignIn nativo na JVM. */
actual class GoogleAuthProvider actual constructor(webClientId: String) {
    actual suspend fun signIn(): GoogleSignInResult = GoogleSignInResult.error(UNSUPPORTED)
    actual suspend fun signIn(nonce: String): GoogleSignInResult = GoogleSignInResult.error(UNSUPPORTED)
    actual suspend fun signIn(nonce: String, requestAuthTime: Boolean): GoogleSignInResult = GoogleSignInResult.error(UNSUPPORTED)
}

private const val UNSUPPORTED = "Login social nativo não é suportado no computador."
