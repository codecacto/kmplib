package br.com.codecacto.kmplib.developer

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
 * @param storeUrl Link da loja aberto ao tocar no card
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
    val active: Boolean = true,
    val order: Int = 0,
)

/**
 * Informações de contato do desenvolvedor (CodeCacto).
 *
 * A partir da kmplib 2.36.0 vem do bloco `contact` de `GET /public/developer` (apps-api).
 * Os valores padrão funcionam como fallback caso o endpoint não responda.
 */
@Serializable
data class DeveloperContact(
    val whatsapp: String = "55998030240",
    val email: String = "raphael.am.dev@gmail.com",
    val site: String = "",
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
    val order: Int = 0,
)
