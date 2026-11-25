package br.com.codecacto.kmplib.testing.net

import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Respostas no ENVELOPE da backlib (`backlib-errors` → `ErrorResponse`: `{message, code, details}` na raiz) — o
// mesmo que o `DomainApiClient` classifica em `DomainResult.Error`/`Quota`. Montar o JSON de erro à mão em cada
// teste é como o formato diverge do servidor sem ninguém notar.

/** `200` (ou [status]) com corpo JSON. */
fun RespostaScope.json(corpo: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    responder(corpo, status, ServidorDeTeste.JSON)

/** Sem corpo — `204` por default (excluir, ação sem retorno). */
fun RespostaScope.vazio(status: HttpStatusCode = HttpStatusCode.NoContent): HttpResponseData = responder("", status)

/**
 * Recusa do servidor (400/403/404/409/422…) no envelope da backlib: `{"message", "code", "details"}`.
 * [details] com frase por campo chega ao app como `DomainResult.Error.fieldError(campo)`.
 */
fun RespostaScope.recusa(
    status: HttpStatusCode,
    codigo: String,
    mensagem: String = codigo,
    details: JsonObject? = null,
): HttpResponseData = json(envelope(mensagem, codigo, details).toString(), status)

/** Atalho de [recusa] com a frase de cada campo (`mapOf("telefone" to "Informe o WhatsApp")`). */
fun RespostaScope.recusaNosCampos(
    campos: Map<String, String>,
    codigo: String = "VALIDATION_ERROR",
    mensagem: String = "Confira os campos.",
    status: HttpStatusCode = HttpStatusCode.BadRequest,
): HttpResponseData = recusa(status, codigo, mensagem, JsonObject(campos.mapValues { JsonPrimitive(it.value) }))

/**
 * `402 QUOTA_EXCEEDED` do `backlib-quota` (`QuotaExceededException`): `details = {feature, limite, contagem,
 * upgradeUrl?}` — o que o `parseQuotaExceeded` lê e o `DomainApiClient` entrega como `DomainResult.Quota`.
 */
fun RespostaScope.cotaEsgotada(
    recurso: String,
    limite: Int,
    usado: Int,
    upgradeUrl: String? = null,
    mensagem: String = "Cota da feature '$recurso' excedida ($usado/$limite)",
): HttpResponseData = json(
    envelope(
        mensagem,
        "QUOTA_EXCEEDED",
        buildJsonObject {
            put("feature", recurso)
            put("limite", limite.toString())
            put("contagem", usado.toString())
            upgradeUrl?.let { put("upgradeUrl", it) }
        },
    ).toString(),
    HttpStatusCode.PaymentRequired,
)

/**
 * `402` do `SubscriptionGuard` (`backlib-billing`): teste de 7 dias vencido sem pagamento ([codigo]
 * `TRIAL_EXPIRED`) ou assinatura exigida (`SUBSCRIPTION_REQUIRED`). Chega como `DomainResult.Error` com
 * `isSubscriptionBlocked`, e liga o `SubscriptionBlockState` quando o cliente tem `subscriptionBlock`.
 */
fun RespostaScope.assinaturaBloqueada(
    codigo: String = "TRIAL_EXPIRED",
    mensagem: String = "Seu teste grátis terminou.",
): HttpResponseData {
    require(codigo == "TRIAL_EXPIRED" || codigo == "SUBSCRIPTION_REQUIRED") {
        "o SubscriptionGuard só responde TRIAL_EXPIRED ou SUBSCRIPTION_REQUIRED: $codigo"
    }
    return json(envelope(mensagem, codigo, null).toString(), HttpStatusCode.PaymentRequired)
}

/**
 * `401` — o `DomainApiClient` pede o token renovado e repete UMA vez (o `tokenRenovado` do
 * [ServidorDeTeste.domainApiClient]). Para provar a renovação, responda 401 só ao token velho.
 */
fun RespostaScope.naoAutorizado(codigo: String = "UNAUTHORIZED", mensagem: String = "Sessão expirada."): HttpResponseData =
    json(envelope(mensagem, codigo, null).toString(), HttpStatusCode.Unauthorized)

private fun envelope(mensagem: String, codigo: String, details: JsonObject?): JsonObject = buildJsonObject {
    put("message", mensagem)
    put("code", codigo)
    details?.let { put("details", it) }
}
