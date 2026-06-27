package br.com.codecacto.kmplib.contact

import kotlinx.serialization.Serializable

/**
 * Corpo (camelCase) de `POST {appsApiBaseUrl}/contact/v1` — casa exatamente o contrato do módulo
 * `contact` do apps-api (`CreateContactRequest`) e o payload do `ContactForm` da weblib.
 *
 * Obrigatórios: [projectSlug], [name], [email], [message]. Opcionais: [whatsapp] (apenas dígitos),
 * [subject], [source]. Campos nulos NÃO são serializados (`encodeDefaults = false` no service).
 */
@Serializable
data class ContactRequest(
    val projectSlug: String,
    val name: String,
    val email: String,
    val message: String,
    val whatsapp: String? = null,
    val subject: String? = null,
    val source: String? = null,
)

/** Resposta 201 de `POST /contact/v1`. */
@Serializable
data class ContactResponse(
    val id: String = "",
    val projectSlug: String = "",
    val createdAt: Long = 0L,
)

/** Falha não fatal de envio de contato (best-effort). [code] = status HTTP ou -1 para rede. */
class ContactSendException(val code: Int, message: String) : Exception(message)
