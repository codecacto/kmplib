package br.com.codecacto.kmplib.firebase.auth

/**
 * Alias de compatibilidade — o holder mudou para `br.com.codecacto.kmplib.auth.social` na 2.98.0,
 * junto do `GoogleAuthProvider` (que nunca dependeu de Firebase).
 */
@Deprecated(
    message = "Movido para br.com.codecacto.kmplib.auth.social.",
    replaceWith = ReplaceWith(
        "GoogleAuthHolder",
        "br.com.codecacto.kmplib.auth.social.GoogleAuthHolder",
    ),
)
typealias GoogleAuthHolder = br.com.codecacto.kmplib.auth.social.GoogleAuthHolder
