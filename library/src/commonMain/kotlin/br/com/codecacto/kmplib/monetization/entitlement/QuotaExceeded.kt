package br.com.codecacto.kmplib.monetization.entitlement

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Detalhes do erro de cota estourada, mapeando o contrato do `402 Payment Required` (ou `429`)
 * emitido pelo `admin-api`/`backlib-quota` (doc 03 §3.1).
 *
 * Payload esperado no corpo do 402:
 * ```json
 * { "feature": "recibos", "limite": 5, "contagem": 5, "upgradeUrl": "https://..." }
 * ```
 *
 * O app usa estes dados para abrir o Paywall com contexto ("voce usou 5 de 5 recibos").
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
    return runCatching {
        quotaJson.decodeFromString(QuotaExceeded.serializer(), response.bodyAsText())
    }.getOrNull()
}

/**
 * Decodifica um corpo de resposta (string JSON) num [QuotaExceeded], ou `null` se nao casar.
 *
 * Util quando o codigo HTTP ja foi capturado por `handleApiCall` e so resta o corpo bruto, ou em
 * apps Firestore-only que recebem o mesmo contrato do admin-api por outro transporte.
 */
fun parseQuotaExceeded(body: String?): QuotaExceeded? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        quotaJson.decodeFromString(QuotaExceeded.serializer(), body)
    }.getOrNull()
}
