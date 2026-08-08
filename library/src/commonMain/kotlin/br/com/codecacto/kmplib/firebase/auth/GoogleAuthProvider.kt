package br.com.codecacto.kmplib.firebase.auth

/**
 * Alias de compatibilidade — o provider **nunca teve nada de Firebase** (Android é Credential
 * Manager + `GetGoogleIdOption`; iOS é o SDK oficial GoogleSignIn-iOS), só morava no pacote errado.
 * Mudou de pacote na 2.98.0 para poder ser usado também pelo **own-auth**
 * (`POST /auth/social`), sem sugerir uma dependência de Firebase que não existe.
 *
 * O alias mantém os apps Firebase compilando sem alteração. Migre o import para
 * `br.com.codecacto.kmplib.auth.social.GoogleAuthProvider`.
 */
@Deprecated(
    message = "Movido para br.com.codecacto.kmplib.auth.social (o provider não usa Firebase).",
    replaceWith = ReplaceWith(
        "GoogleAuthProvider",
        "br.com.codecacto.kmplib.auth.social.GoogleAuthProvider",
    ),
)
typealias GoogleAuthProvider = br.com.codecacto.kmplib.auth.social.GoogleAuthProvider
