package br.com.codecacto.kmplib.monetization.entitlement

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Detalhes do erro de cota estourada, mapeando o `402 Payment Required` emitido pelo `admin-api`
 * (contrato `/monet/{slug}/...` §2/§3 — paywall com `error.details`).
 *
 * O corpo do 402 vem no **envelope canonico** com os detalhes em `error.details` e os numeros como
 * **string** (BigDecimal serializado):
 * ```json
 * { "ok": false, "error": { "code": "QUOTA_EXCEEDED", "message": "...",
 *   "details": { "feature": "active_loans", "limite": "5", "contagem": "5",
 *                "upgradeUrl": "https://.../upgrade" } } }
 * ```
 *
 * O app usa estes dados para abrir o Paywall com contexto ("voce usou 5 de 5").
 */
@Serializable
data class QuotaExceeded(
    @SerialName("feature") val feature: String,
    @SerialName("limite") val limite: Int,
    @SerialName("contagem") val contagem: Int,
    @SerialName("upgradeUrl") val upgradeUrl: String? = null
) {
    /** Constroi um [UsageSnapshot] coerente para alimentar o UsageMeter no Paywall. */
    fun toUsageSnapshot(): UsageSnapshot =
        UsageSnapshot(feature = feature, contagem = contagem, limite = limite)
}

private val quotaJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Tenta extrair um [QuotaExceeded] de uma [ResponseException] do Ktor.
 *
 * Retorna `null` se o status nao for 402/429 ou se o corpo nao casar com o contrato.
 * Use no ramo de erro de uma chamada consumivel para decidir abrir o Paywall.
 */
suspend fun ResponseException.quotaExceededOrNull(): QuotaExceeded? {
    val status = response.status.value
    if (status != 402 && status != 429) return null
    return parseQuotaExceeded(runCatching { response.bodyAsText() }.getOrNull())
}

/**
 * Decodifica um corpo de resposta (string JSON) num [QuotaExceeded], ou `null` se nao casar.
 *
 * Le os detalhes de `error.details` do envelope canonico (admin-api), com `limite`/`contagem` como
 * string OU numero, convertendo para Int. Por retrocompatibilidade, tambem aceita o payload
 * **direto** (sem envelope) — ex.: apps Firestore-only que repassem `details` cru.
 */
fun parseQuotaExceeded(body: String?): QuotaExceeded? {
    if (body.isNullOrBlank()) return null
    val element = runCatching { quotaJson.parseToJsonElement(body) }.getOrNull() ?: return null
    val obj = (element as? JsonObject) ?: return null

    // Caminho canonico: { ok, error: { details: { ... } } }
    val details = (obj["error"] as? JsonObject)?.get("details") as? JsonObject
    // Retrocompat: payload direto (o proprio objeto e o details)
    val source = details ?: obj
    return fromDetails(source)
}

/** Mapeia um objeto `details` (numeros como string OU number) para [QuotaExceeded]. */
private fun fromDetails(details: JsonObject): QuotaExceeded? {
    val feature = details["feature"]?.jsonPrimitiveOrNull()?.contentOrNull ?: return null
    val limite = details["limite"]?.toIntOrNull() ?: return null
    val contagem = details["contagem"]?.toIntOrNull() ?: return null
    val upgradeUrl = details["upgradeUrl"]?.jsonPrimitiveOrNull()?.contentOrNull
    return QuotaExceeded(feature = feature, limite = limite, contagem = contagem, upgradeUrl = upgradeUrl)
}

private fun JsonElement.jsonPrimitiveOrNull() = (this as? JsonPrimitive)

/** Converte um JsonElement numerico OU string ("5") para Int. */
private fun JsonElement.toIntOrNull(): Int? {
    val prim = jsonPrimitiveOrNull() ?: return null
    val content = prim.contentOrNull ?: return null
    return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt()
}
