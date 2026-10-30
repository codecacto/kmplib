package br.com.codecacto.kmplib.developer

import br.com.codecacto.kmplib.core.text.foldForSearch
import br.com.codecacto.kmplib.core.util.currentPlatform
import kotlinx.serialization.Serializable

/**
 * Um app do catálogo "Desenvolvido por CodeCacto".
 *
 * A partir da kmplib 2.36.0 os apps vêm do backend central **apps-api**
 * (`GET /public/developer`), gerenciados pelo admin web. O endpoint já devolve só os apps
 * ativos, ordenados; [active] permanece no modelo por compatibilidade, mas o filtro/ordenação
 * é responsabilidade do servidor.
 *
 * @param id ID do app no catálogo central (preenchido na leitura)
 * @param name Nome do app
 * @param description Descrição curta exibida no card
 * @param logoUrl URL pública do logo
 * @param storeUrl LEGADO: link único de loja (fallback quando Play/App Store vierem vazios)
 * @param playStoreUrl Link da Play Store (Android)
 * @param appStoreUrl Link da App Store (iOS)
 * @param active Se `false`, o app não aparece na grade (servidor já filtra)
 * @param order Ordem de exibição (crescente)
 */
@Serializable
data class DeveloperApp(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val logoUrl: String = "",
    val storeUrl: String = "",
    val playStoreUrl: String = "",
    val appStoreUrl: String = "",
    val active: Boolean = true,
    val order: Int = 0,
)

/**
 * Loja a abrir ao tocar no card, conforme a plataforma do dispositivo: iOS abre a
 * App Store, Android abre a Play Store. Se o link da loja da plataforma estiver
 * vazio, cai no link da outra loja e, por fim, no [DeveloperApp.storeUrl] legado.
 */
fun DeveloperApp.resolveStoreUrl(): String {
    val isIos = currentPlatform == "ios"
    val primary = if (isIos) appStoreUrl else playStoreUrl
    val secondary = if (isIos) playStoreUrl else appStoreUrl
    return primary.ifBlank { secondary }.ifBlank { storeUrl }
}

/**
 * A vitrine sem o app em que a pessoa já está (2.336.0): oferecer "conheça o Super 8" dentro do
 * próprio Super 8 é um cartão desperdiçado. Compara [currentAppName] (o nome que o sistema mostra,
 * `BuildInfo.appName`) com o nome cadastrado no Nexus ignorando caixa, acento e espaços extras, e
 * também sem o complemento entre parênteses do cadastro ("Calculadora BTU (app)" = "Calculadora BTU").
 * [currentAppName] nulo ou em branco devolve a lista inteira.
 */
internal fun List<DeveloperApp>.withoutCurrentApp(currentAppName: String?): List<DeveloperApp> {
    val atual = currentAppName?.let(::foldForSearch).orEmpty()
    if (atual.isEmpty()) return this
    return filterNot { app ->
        val nome = foldForSearch(app.name)
        nome == atual || foldForSearch(app.name.substringBefore('(')) == atual
    }
}

/**
 * Informações de contato do desenvolvedor (CodeCacto).
 *
 * A partir da kmplib 2.36.0 vem do bloco `contact` de `GET /public/developer` (apps-api).
 * Os valores padrão funcionam como fallback caso o endpoint não responda. O WhatsApp vai com o DDI
 * (55): o link é montado com os dígitos crus, e sem ele o número cai em outro país.
 */
@Serializable
data class DeveloperContact(
    val whatsapp: String = "5565998030240",
    val email: String = "contato@codecacto.com.br",
    val site: String = "https://codecacto.com.br",
)

// --- DTOs de fio do endpoint público GET /public/developer (apps-api) ---

/**
 * Resposta do endpoint `GET {appsApiBaseUrl}/public/developer`.
 *
 * Formato:
 * ```json
 * { "contact": { "whatsapp": "...", "email": "...", "site": "..." },
 *   "apps": [ { "id": "...", "name": "...", "description": "...",
 *               "logoUrl": "...", "storeUrl": "...", "order": 0 } ] }
 * ```
 * Campos ausentes caem nos defaults (o serviço é tolerante a payload parcial).
 */
@Serializable
internal data class DeveloperInfoResponse(
    val contact: DeveloperContactDto = DeveloperContactDto(),
    val apps: List<DeveloperAppDto> = emptyList(),
)

@Serializable
internal data class DeveloperContactDto(
    val whatsapp: String? = null,
    val email: String? = null,
    val site: String? = null,
)

@Serializable
internal data class DeveloperAppDto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val logoUrl: String = "",
    val storeUrl: String = "",
    val playStoreUrl: String = "",
    val appStoreUrl: String = "",
    val order: Int = 0,
)
