package br.com.codecacto.kmplib.monetization.entitlement

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Corpo de erro de quota excedida, retornado pelo admin-api com HTTP 402 (ou 429).
 *
 * O servidor e a fonte de verdade da quota; este DTO so existe para que o cliente saiba
 * **abrir o paywall** e exibir "X de Y" via [toUsageSnapshot].
 *
 * @property feature Feature consumivel que estourou o limite.
 * @property limite Limite da janela.
 * @property contagem Contagem atual (>= limite quando esgotada).
 * @property upgradeUrl URL de upgrade sugerida pelo servidor (opcional; web/AbacatePay).
 */
@Serializable
data class QuotaExceeded(
    val feature: String,
    val limite: Long,
    val contagem: Long,
    @SerialName("upgrade_url") val upgradeUrl: String? = null,
) {
    /** Converte para [UsageSnapshot] (esgotado) para alimentar o UsageMeter no paywall. */
    fun toUsageSnapshot(): UsageSnapshot = UsageSnapshot(
        feature = feature,
        contagem = contagem,
        limite = limite,
    )
}

/** Json tolerante para parse de corpos de erro de quota (ignora campos desconhecidos). */
private val quotaJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Faz parse tolerante de um corpo bruto em [QuotaExceeded]. Retorna `null` se o corpo nao for um
 * payload de quota valido (sem `feature`). Nunca lanca.
 */
fun parseQuotaExceeded(body: String?): QuotaExceeded? {
    if (body.isNullOrBlank()) return null
    return runCatching {
        val parsed = quotaJson.decodeFromString<QuotaExceeded>(body)
        // `feature` e o discriminante minimo de um payload de quota valido.
        if (parsed.feature.isBlank()) null else parsed
    }.getOrNull()
}

/**
 * Extrai [QuotaExceeded] de uma [ResponseException] do Ktor quando o status for 402 (Payment
 * Required) ou 429 (Too Many Requests) e o corpo for um payload de quota valido. Caso contrario,
 * retorna `null`. Nunca lanca.
 *
 * Uso tipico no ViewModel do app:
 * ```kotlin
 * try { client.post(...) } catch (e: ResponseException) {
 *     e.quotaExceededOrNull()?.let { setState { copy(ent = ent.showingPaywall(it)) } }
 * }
 * ```
 */
suspend fun ResponseException.quotaExceededOrNull(): QuotaExceeded? {
    val status = response.status.value
    if (status != 402 && status != 429) return null
    val body = runCatching { response.bodyAsText() }.getOrNull()
    return parseQuotaExceeded(body)
}
