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
