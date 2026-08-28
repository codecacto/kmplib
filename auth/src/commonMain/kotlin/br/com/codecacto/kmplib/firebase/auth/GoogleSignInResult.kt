package br.com.codecacto.kmplib.firebase.auth

/**
 * Alias de compatibilidade. O tipo mudou para `br.com.codecacto.kmplib.auth.social` na 2.98.0, junto
 * do provider (que nunca dependeu de Firebase).
 *
 * **Não confundir** com `br.com.codecacto.kmplib.ui.screens.login.GoogleSignInResult`, que é um tipo
 * DIFERENTE (contrato da tela de login, com `idToken` não-nulo). Os dois convivem de propósito —
 * unificá-los mudaria a nulidade de um campo público de tela e quebraria consumidores.
 */
@Deprecated(
    message = "Movido para br.com.codecacto.kmplib.auth.social.",
    replaceWith = ReplaceWith(
        "GoogleSignInResult",
        "br.com.codecacto.kmplib.auth.social.GoogleSignInResult",
    ),
)
typealias GoogleSignInResult = br.com.codecacto.kmplib.auth.social.GoogleSignInResult
