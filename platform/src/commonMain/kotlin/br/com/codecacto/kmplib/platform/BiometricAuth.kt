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

    /**
     * `true` quando existe **alguma** forma de o dono do aparelho se identificar: biometria
     * cadastrada **ou** a trava de tela (PIN, padrão ou senha).
     *
     * Não confundir com [isAvailable], que responde só por biometria. Quem tranca o app precisa
     * desta pergunta: aparelho sem digital cadastrada mas com PIN ainda dá para destravar, e
     * recusar o acesso a essa pessoa é trancá-la para fora do próprio app.
     */
    fun isDeviceSecured(): Boolean = isAvailable()

    /**
     * Autentica **aceitando a trava de tela como alternativa** à biometria (`allowDeviceCredential
     * = true`) — é a variante que o `AppLockGate` usa.
     *
     * A digital falha com a mão molhada, o rosto falha no escuro, e há aparelho sem sensor nenhum:
     * sem o PIN como saída, o app fica trancado para quem tem todo o direito de entrar. Com
     * `allowDeviceCredential = false` o comportamento é idêntico ao [authenticate] de cinco
     * argumentos.
     *
     * O default delega para a versão sem alternativa, para não quebrar quem implementa esta
     * interface fora da lib (dublê de teste, por exemplo).
     */
    fun authenticate(
        title: String,
        subtitle: String,
        allowDeviceCredential: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) = authenticate(title, subtitle, onSuccess, onError, onCancel)
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
