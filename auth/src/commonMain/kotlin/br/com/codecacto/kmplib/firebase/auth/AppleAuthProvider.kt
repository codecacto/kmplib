package br.com.codecacto.kmplib.firebase.auth

/**
 * Alias de compatibilidade — o provider **nunca teve nada de Firebase** (iOS é
 * `ASAuthorizationController` puro). Mudou de pacote na 2.98.0 para poder ser usado também pelo
 * **own-auth** (`POST /auth/social`).
 *
 * Migre o import para `br.com.codecacto.kmplib.auth.social.AppleAuthProvider`.
 */
@Deprecated(
    message = "Movido para br.com.codecacto.kmplib.auth.social (o provider não usa Firebase).",
    replaceWith = ReplaceWith(
        "AppleAuthProvider",
        "br.com.codecacto.kmplib.auth.social.AppleAuthProvider",
    ),
)
typealias AppleAuthProvider = br.com.codecacto.kmplib.auth.social.AppleAuthProvider
