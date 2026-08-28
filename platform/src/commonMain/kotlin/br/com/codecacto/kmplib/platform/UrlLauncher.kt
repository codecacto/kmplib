package br.com.codecacto.kmplib.platform

/**
 * Abre URLs e realiza ações de sistema.
 *
 * Uso:
 * ```kotlin
 * val urlLauncher = getUrlLauncher()
 *
 * // Abrir URL no navegador
 * urlLauncher.openUrl("https://exemplo.com")
 *
 * // Abrir email
 * urlLauncher.openEmail(
 *     to = "contato@exemplo.com",
 *     subject = "Assunto",
 *     body = "Corpo do email"
 * )
 *
 * // Abrir telefone
 * urlLauncher.openPhone("11987654321")
 *
 * // Abrir WhatsApp
 * urlLauncher.openWhatsApp("5511987654321", "Olá!")
 * ```
 */
interface UrlLauncher {
    /**
     * Abre uma URL no navegador padrão.
     */
    fun openUrl(url: String)

    /**
     * Abre o app de email com dados pré-preenchidos.
     */
    fun openEmail(to: String, subject: String = "", body: String = "")

    /**
     * Abre o discador de telefone.
     */
    fun openPhone(phoneNumber: String)

    /**
     * Abre o WhatsApp com uma mensagem.
     * @param phone Número com código do país (ex: 5511987654321)
     * @param message Mensagem a ser enviada
     */
    fun openWhatsApp(phone: String, message: String = "")

    /**
     * Abre a página do app na loja (Play Store / App Store).
     * @param androidPackage Package name do app no Android (opcional)
     * @param iosAppId ID do app na App Store (opcional)
     */
    fun openStorePage(androidPackage: String? = null, iosAppId: String? = null)

    /**
     * Abre um mapa com um endereço ou coordenadas.
     * @param query Endereço ou "latitude,longitude"
     */
    fun openMap(query: String)

    /**
     * Abre a página de gerenciamento de assinaturas na loja (Play Store / App Store).
     */
    fun openSubscriptionManagement()

    /**
     * Abre a tela de **Configurações do próprio app** no sistema — onde o usuário reativa uma
     * permissão que negou em definitivo.
     *
     * É o único caminho de saída quando uma permissão fica em
     * [br.com.codecacto.kmplib.platform.permission.PermissionStatus.PERMANENTLY_DENIED]: pedir de
     * novo não abre diálogo nenhum, e sem esta ação a tela vira um beco sem saída. Usado pelo
     * [br.com.codecacto.kmplib.platform.permission.PermissionState.openAppSettings].
     *
     * - **Android:** `Intent(ACTION_APPLICATION_DETAILS_SETTINGS, package:<id>)`.
     * - **iOS:** `UIApplication.openSettingsURLString` (Apple não permite abrir uma sub-tela
     *   específica; cai na página do app).
     *
     * O corpo default apenas registra um aviso — existe para não quebrar implementações de
     * `UrlLauncher` mantidas por apps (fakes de teste). As implementações da lib sobrescrevem.
     */
    fun openAppSettings() {
        br.com.codecacto.kmplib.core.util.AppLogger.w(
            "UrlLauncher",
            "openAppSettings() não implementado nesta implementação de UrlLauncher",
        )
    }
}

/**
 * Obtém a implementação do UrlLauncher para a plataforma atual.
 */
expect fun getUrlLauncher(): UrlLauncher

/**
 * Abre um mapa a partir de coordenadas (e label opcional).
 */
fun UrlLauncher.openMap(latitude: Double, longitude: Double, label: String? = null) {
    val query = if (label.isNullOrBlank()) {
        "$latitude,$longitude"
    } else {
        "$latitude,$longitude ($label)"
    }
    openMap(query)
}

/**
 * Abre um mapa a partir de um endereco.
 */
fun UrlLauncher.openMapByAddress(address: String) {
    openMap(address)
}
