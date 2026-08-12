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
 *
 * **Perder este payload NAO bloqueia o usuario — deixa de OFERECER o pagamento.** O item continua
 * barrado (isso vem do proprio codigo 402), mas sem `feature`/`limite`/`contagem`/`upgradeUrl` o
 * paywall abre sem contexto (ou nao abre) e o CTA que levaria a assinatura morre: o app diz "nao
 * pode" e nao diz "assine para poder". Por isso o parse aceita os tres formatos de corpo em uso no
 * ecossistema (ver [parseQuotaExceeded]) em vez de exigir um so.
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
 * `limite`/`contagem` sao lidos como **string OU numero** (o `details` do `ErrorResponse` da backlib
 * e um `Map<String, String>`; o admin-api serializa BigDecimal como string).
 *
 * **Tres formatos aceitos, nesta ordem de precedencia** (o primeiro que produzir um
 * [QuotaExceeded] completo vence — o envelope canonico continua ganhando):
 *
 * 1. **Envelope canonico do admin-api** — `details` aninhado em `error`:
 *    ```json
 *    { "ok": false, "error": { "code": "QUOTA_EXCEEDED", "details": { "feature": "...", ... } } }
 *    ```
 * 2. **`ErrorResponse` da backlib** — `details` no **topo** do corpo. E o que TODO backend proprio
 *    do ecossistema responde (o `ErrorHandlingPlugin` serializa `AppException.details` neste campo):
 *    ```json
 *    { "message": "cota estourada", "code": "QUOTA_EXCEEDED", "traceId": "...",
 *      "details": { "feature": "items", "limite": "50", "contagem": "50",
 *                   "upgradeUrl": "https://.../premium" } }
 *    ```
 * 3. **Payload direto** (retrocompat) — o proprio objeto raiz e o `details`:
 *    `{ "feature": "...", "limite": 5, "contagem": 5 }`.
 *
 * Corpo em branco, JSON invalido, objeto sem `feature`/`limite`/`contagem` em nenhum dos tres
 * lugares ⇒ `null` (o chamador trata como erro 402 comum). Nunca lanca.
 */
fun parseQuotaExceeded(body: String?): QuotaExceeded? {
    if (body.isNullOrBlank()) return null
    val element = runCatching { quotaJson.parseToJsonElement(body) }.getOrNull() ?: return null
    val obj = (element as? JsonObject) ?: return null

    // Ordem = precedencia. O primeiro candidato COMPLETO vence; candidato presente porem incompleto
    // (ex.: `error.details` sem `feature`) nao impede os seguintes de responderem — descartar o corpo
    // inteiro por causa de um envelope pela metade custaria o CTA de assinatura.
    val candidates = listOfNotNull(
        // 1. Envelope canonico do admin-api: { ok, error: { details: { ... } } }
        (obj["error"] as? JsonObject)?.get("details") as? JsonObject,
        // 2. ErrorResponse da backlib: details no TOPO do corpo.
        obj["details"] as? JsonObject,
        // 3. Retrocompat: payload direto (o proprio objeto e o details).
        obj,
    )
    return candidates.firstNotNullOfOrNull { fromDetails(it) }
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
