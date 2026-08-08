package br.com.codecacto.kmplib.auth

/**
 * Provedor de identidade social aceito pelo contrato own-auth (`POST {authBasePath}/social`).
 *
 * O [wire] é o valor **exato** que trafega no campo `provider` do corpo — fixado aqui para que o
 * app nunca precise digitar a string (e nunca a digite errado, o que o servidor recusaria com um
 * genérico "provedor inválido").
 */
enum class SocialProvider(val wire: String) {
    GOOGLE("google"),
    APPLE("apple");

    /**
     * `providerId` canônico do [User][br.com.codecacto.kmplib.firebase.auth.User] — o MESMO
     * vocabulário que o `AuthRepository` (Firebase) já usa (`google.com`/`apple.com`), para que
     * `user.isGoogleProvider` funcione igual nos dois mundos.
     */
    val userProviderId: String
        get() = when (this) {
            GOOGLE -> "google.com"
            APPLE -> "apple.com"
        }

    companion object {
        /** Converte o valor de fio de volta ao enum (tolerante a caixa); `null` se desconhecido. */
        fun fromWireOrNull(raw: String?): SocialProvider? {
            val value = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.wire == value }
        }
    }
}
