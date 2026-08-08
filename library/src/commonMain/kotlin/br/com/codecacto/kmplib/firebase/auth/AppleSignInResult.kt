package br.com.codecacto.kmplib.firebase.auth

/**
 * Alias de compatibilidade. O tipo mudou para `br.com.codecacto.kmplib.auth.social` na 2.98.0, junto
 * do provider (que nunca dependeu de Firebase).
 *
 * **Não confundir** com `br.com.codecacto.kmplib.ui.screens.login.AppleSignInResult` (contrato da
 * tela de login, campos não-nulos) — são tipos diferentes, de propósito.
 */
@Deprecated(
    message = "Movido para br.com.codecacto.kmplib.auth.social.",
    replaceWith = ReplaceWith(
        "AppleSignInResult",
        "br.com.codecacto.kmplib.auth.social.AppleSignInResult",
    ),
)
typealias AppleSignInResult = br.com.codecacto.kmplib.auth.social.AppleSignInResult
