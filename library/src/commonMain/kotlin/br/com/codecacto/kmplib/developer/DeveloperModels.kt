package br.com.codecacto.kmplib.developer

import kotlinx.serialization.Serializable

/**
 * Um app do catálogo "Desenvolvido por CodeCacto".
 *
 * Os documentos vivem na coleção `developer_apps` do projeto Firebase central
 * `code-cacto` e são gerenciados pelo admin web (monitoramento).
 *
 * @param id ID do documento no Firestore (preenchido na leitura)
 * @param name Nome do app
 * @param description Descrição curta exibida no card
 * @param logoUrl URL pública do logo (Firebase Storage)
 * @param storeUrl Link da loja aberto ao tocar no card
 * @param active Se `false`, o app não aparece na grade
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
 * Singleton no documento `developer_info/contact` do projeto `code-cacto`.
 * Os valores padrão funcionam como fallback caso o Firestore não responda.
 */
@Serializable
data class DeveloperContact(
    val whatsapp: String = "55998030240",
    val email: String = "raphael.am.dev@gmail.com",
    val site: String = "",
)
