package br.com.codecacto.kmplib.platform

/**
 * Autenticação biométrica multiplataforma.
 *
 * Uso:
 * ```kotlin
 * val biometricAuth = getBiometricAuth()
 *
 * // Verificar disponibilidade
 * if (biometricAuth.isAvailable()) {
 *     val type = biometricAuth.getBiometricType()
 *     println("Tipo: $type") // FACE_ID, TOUCH_ID, FINGERPRINT, etc.
 *
 *     // Autenticar
 *     biometricAuth.authenticate(
 *         title = "Autenticação necessária",
 *         subtitle = "Use sua biometria para continuar",
 *         onSuccess = { println("Autenticado!") },
 *         onError = { error -> println("Erro: $error") },
 *         onCancel = { println("Cancelado") }
 *     )
 * }
 * ```
 */
interface BiometricAuth {
    /**
     * Verifica se a autenticação biométrica está disponível no dispositivo.
     */
    fun isAvailable(): Boolean

    /**
     * Retorna o tipo de biometria disponível.
     */
    fun getBiometricType(): BiometricType

    /**
     * Realiza a autenticação biométrica.
     *
     * @param title Título exibido no prompt
     * @param subtitle Subtítulo/descrição
     * @param onSuccess Callback de sucesso
     * @param onError Callback de erro com mensagem
     * @param onCancel Callback quando usuário cancela
     */
    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    )
}

/**
 * Tipos de biometria disponíveis.
 */
enum class BiometricType {
    /** Face ID (iOS) */
    FACE_ID,

    /** Touch ID (iOS) */
    TOUCH_ID,

    /** Impressão digital (Android) */
    FINGERPRINT,

    /** Reconhecimento facial (Android) */
    FACE,

    /** Biometria disponível mas tipo desconhecido */
    UNKNOWN,

    /** Nenhuma biometria disponível */
    NONE
}

/**
 * Obtém a implementação do BiometricAuth para a plataforma atual.
 */
expect fun getBiometricAuth(): BiometricAuth
