package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.sync.rest.DomainResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.cancellation.CancellationException

/** Códigos de erro estáveis do `backlib-inspection` (o `code` do envelope). */
object InspectionErrorCodes {
    const val INSPECTION_NOT_EDITABLE: String = "INSPECTION_NOT_EDITABLE"
    const val INSPECTION_INCOMPLETE: String = "INSPECTION_INCOMPLETE"
    const val ENTRY_NOT_FINISHED: String = "ENTRY_NOT_FINISHED"
    const val EXIT_FROM_ENTRY_ONLY: String = "EXIT_FROM_ENTRY_ONLY"
    const val MEDIA_HASH_MISMATCH: String = "MEDIA_HASH_MISMATCH"
    const val MEDIA_CONTENT_INVALID: String = "MEDIA_CONTENT_INVALID"
    const val MEDIA_ALREADY_REGISTERED: String = "MEDIA_ALREADY_REGISTERED"
    const val MEDIA_ALREADY_CONFIRMED: String = "MEDIA_ALREADY_CONFIRMED"
    const val MEDIA_NOT_UPLOADED: String = "MEDIA_NOT_UPLOADED"
    const val MEDIA_NOT_FOUND: String = "MEDIA_NOT_FOUND"
    const val MEDIA_IN_SIGNED_REPORT: String = "MEDIA_IN_SIGNED_REPORT"
    const val MEDIA_REQUIRED_BY_ITEM: String = "MEDIA_REQUIRED_BY_ITEM"
    const val MEDIA_TASK_NOT_FOUND: String = "MEDIA_TASK_NOT_FOUND"
    const val MEDIA_TASK_KIND_MISMATCH: String = "MEDIA_TASK_KIND_MISMATCH"
    const val METER_READING_INVALID: String = "METER_READING_INVALID"
    const val NOT_AN_EXIT: String = "NOT_AN_EXIT"
    const val COMPARISON_DECISION_INVALID: String = "COMPARISON_DECISION_INVALID"
    const val REPORT_NOT_GENERATED: String = "REPORT_NOT_GENERATED"
    const val INSPECTION_NOT_READY_FOR_REPORT: String = "INSPECTION_NOT_READY_FOR_REPORT"
    const val REPORT_NOT_SIGNED: String = "REPORT_NOT_SIGNED"
    const val REPORT_NOT_FOUND: String = "REPORT_NOT_FOUND"
    const val QUOTA_EXCEEDED: String = "QUOTA_EXCEEDED"
    const val LINK_UNAVAILABLE: String = "LINK_UNAVAILABLE"
}

/**
 * Por que uma chamada ao motor não deu certo — **tipado**, para a tela escolher a saída certa sem ler
 * número de status: cota → paywall; pendências → revisão; conflito de estado → recarregar; sem rede →
 * fila/tentar de novo.
 *
 * [userMessage] é a frase para a tela: a do servidor em 4xx (já em pt-BR, escrita para gente), a local
 * (no idioma da tela, do `DomainApiClient`) no resto. Nunca contém URL, token ou dado pessoal.
 */
sealed interface InspectionError {
    /** Status HTTP; [OFFLINE] quando não houve resposta. */
    val httpStatus: Int

    /** O `code` de negócio do servidor ([InspectionErrorCodes]), quando veio. */
    val serverCode: String?

    val userMessage: String

    /** Vale tentar de novo sozinho (rede, 5xx, 429)? Recusa de regra não é. */
    val isRetryable: Boolean get() = false

    /** Sem conexão (nem chegou ao servidor). */
    data class Offline(override val userMessage: String) : InspectionError {
        override val httpStatus: Int get() = OFFLINE
        override val serverCode: String? get() = null
        override val isRetryable: Boolean get() = true
    }

    /** 402 da cota do produto (vistorias criadas no mês): a saída é o paywall — [quota] traz recurso/limite/degrau. */
    data class QuotaExceeded(val quota: DomainResult.Quota) : InspectionError {
        override val httpStatus: Int get() = 402
        override val serverCode: String? get() = quota.serverCode
        override val userMessage: String get() = quota.userMessage
    }

    /** 402 que não é cota (assinatura vencida, teste encerrado): o `SubscriptionBlockGate` cuida. */
    data class PaymentRequired(override val serverCode: String?, override val userMessage: String) : InspectionError {
        override val httpStatus: Int get() = 402
    }

    /**
     * 422 `INSPECTION_INCOMPLETE` ao finalizar: [issues] diz o que falta, item a item — vira a lista de
     * pendências da revisão (`InspectionMappers.reviewPendencies`).
     */
    data class Incomplete(val issues: List<FinishIssueDto>, override val userMessage: String) : InspectionError {
        override val httpStatus: Int get() = 422
        override val serverCode: String? get() = InspectionErrorCodes.INSPECTION_INCOMPLETE
    }

    /**
     * 409: o estado do servidor não permite a ação ([InspectionErrorCodes.INSPECTION_NOT_EDITABLE],
     * `MEDIA_ALREADY_REGISTERED`, `ALREADY_SIGNED`, `MEDIA_IN_SIGNED_REPORT`…). Saída: recarregar a vistoria.
     */
    data class Conflict(
        override val serverCode: String?,
        override val userMessage: String,
        val details: JsonObject = JsonObject(emptyMap()),
    ) : InspectionError {
        override val httpStatus: Int get() = 409

        /** A vistoria saiu de rascunho (finalizada/assinada em outro aparelho): reabrir para corrigir. */
        val isNotEditable: Boolean get() = serverCode == InspectionErrorCodes.INSPECTION_NOT_EDITABLE

        /** `details.mediaId` (o arquivo já está registrado em outro lugar da vistoria). */
        val mediaId: String? get() = details.text("mediaId")

        /** `details.status` (estado atual da vistoria), quando veio. */
        val currentStatus: String? get() = details.text("status")
    }

    /** 410: o recurso existiu e não existe mais (link vencido/consumido — `LINK_UNAVAILABLE`). */
    data class Gone(override val serverCode: String?, override val userMessage: String) : InspectionError {
        override val httpStatus: Int get() = 410
    }

    /** 404: id de outro dono ou apagado. */
    data class NotFound(override val serverCode: String?, override val userMessage: String) : InspectionError {
        override val httpStatus: Int get() = 404
    }

    /** 400/413: pedido malformado; [fieldErrors] = frase por campo (`fieldError("sha256")`). */
    data class Validation(
        override val httpStatus: Int,
        override val serverCode: String?,
        override val userMessage: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : InspectionError {
        fun fieldError(field: String): String? = fieldErrors[field]
    }

    /** Outro 422 de regra de negócio (`MEDIA_HASH_MISMATCH`, `OTP_INVALID`…). [details] = o objeto inteiro. */
    data class Rejected(
        override val serverCode: String?,
        override val userMessage: String,
        val details: JsonObject = JsonObject(emptyMap()),
    ) : InspectionError {
        override val httpStatus: Int get() = 422
    }

    /** 429: devagar. [blockedUntil] (ISO-8601) quando o servidor disse. */
    data class RateLimited(
        override val serverCode: String?,
        override val userMessage: String,
        val blockedUntil: String? = null,
    ) : InspectionError {
        override val httpStatus: Int get() = 429
        override val isRetryable: Boolean get() = true
    }

    /** 401/403. [reauthRequired] = ação sensível pedindo senha de novo (`RecentAuthHost`). */
    data class Unauthorized(
        override val httpStatus: Int,
        override val serverCode: String?,
        override val userMessage: String,
        val reauthRequired: Boolean = false,
    ) : InspectionError

    /** 503: recurso indisponível agora (PDF, IA, envio de código, storage). */
    data class Unavailable(override val serverCode: String?, override val userMessage: String) : InspectionError {
        override val httpStatus: Int get() = 503
        override val isRetryable: Boolean get() = true
    }

    /** Outro status (5xx, 408…). */
    data class Server(override val httpStatus: Int, override val serverCode: String?, override val userMessage: String) : InspectionError {
        override val isRetryable: Boolean get() = httpStatus == 408 || httpStatus >= 500
    }

    /** 2xx com corpo que não é o DTO esperado. Não adianta repetir igual. */
    data class InvalidResponse(override val userMessage: String) : InspectionError {
        override val httpStatus: Int get() = INVALID_RESPONSE
        override val serverCode: String? get() = null
    }

    companion object {
        const val OFFLINE: Int = DomainResult.OFFLINE_CODE

        /** Sentinela de [InvalidResponse]. */
        const val INVALID_RESPONSE: Int = -20

        /** Frase de [InvalidResponse] (o servidor respondeu algo que este app não entende — atualize). */
        const val INVALID_RESPONSE_MESSAGE: String = "Resposta do servidor ilegível."
    }
}

/** Resultado de uma chamada ao motor: [Ok] com o DTO, ou [Failed] com o [InspectionError] tipado. */
sealed interface InspectionResult<out T> {
    data class Ok<T>(val value: T) : InspectionResult<T>
    data class Failed(val error: InspectionError) : InspectionResult<Nothing>

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): InspectionError? = (this as? Failed)?.error
    val isOk: Boolean get() = this is Ok
}

inline fun <T, R> InspectionResult<T>.map(transform: (T) -> R): InspectionResult<R> = when (this) {
    is InspectionResult.Ok -> InspectionResult.Ok(transform(value))
    is InspectionResult.Failed -> this
}

inline fun <T> InspectionResult<T>.onOk(block: (T) -> Unit): InspectionResult<T> {
    if (this is InspectionResult.Ok) block(value)
    return this
}

inline fun <T> InspectionResult<T>.onFailed(block: (InspectionError) -> Unit): InspectionResult<T> {
    if (this is InspectionResult.Failed) block(error)
    return this
}

/**
 * [DomainResult] do `DomainApiClient` → [InspectionResult]. Função PURA (testada em `InspectionErrorsTest`):
 * o 2xx passa por [decode] (exceção de leitura = [InspectionError.InvalidResponse]); o resto vira o
 * [InspectionError] da tabela do KDoc dele.
 */
fun <T> DomainResult<String>.toInspectionResult(decode: (String) -> T): InspectionResult<T> = when (this) {
    is DomainResult.Success -> try {
        InspectionResult.Ok(decode(data))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        InspectionResult.Failed(InspectionError.InvalidResponse(InspectionError.INVALID_RESPONSE_MESSAGE))
    }
    is DomainResult.Quota -> InspectionResult.Failed(InspectionError.QuotaExceeded(this))
    is DomainResult.Error -> InspectionResult.Failed(toInspectionError())
}

/** O erro do `DomainApiClient` como [InspectionError]. */
fun DomainResult.Error.toInspectionError(): InspectionError {
    val frase = userMessage
    return when {
        code == DomainResult.OFFLINE_CODE -> InspectionError.Offline(message)
        code == 402 -> InspectionError.PaymentRequired(serverCode, frase)
        code == 401 || code == 403 -> InspectionError.Unauthorized(code, serverCode, frase, reauthRequired = isReauthRequired)
        code == 404 -> InspectionError.NotFound(serverCode, frase)
        code == 409 -> InspectionError.Conflict(serverCode, frase, detailsJson.withTextDetails(details))
        code == 410 -> InspectionError.Gone(serverCode, frase)
        code == 422 && serverCode == InspectionErrorCodes.INSPECTION_INCOMPLETE ->
            InspectionError.Incomplete(parseFinishIssues(detailsJson), frase)
        code == 422 -> InspectionError.Rejected(serverCode, frase, detailsJson.withTextDetails(details))
        code == 400 || code == 413 -> InspectionError.Validation(code, serverCode, frase, details)
        code == 429 -> InspectionError.RateLimited(serverCode, frase, detailsJson.text("blockedUntil") ?: details["blockedUntil"])
        code == 503 -> InspectionError.Unavailable(serverCode, frase)
        code == InspectionError.INVALID_RESPONSE -> InspectionError.InvalidResponse(frase)
        else -> InspectionError.Server(code, serverCode, frase)
    }
}

/** `details.issues` do 422 `INSPECTION_INCOMPLETE` — pendência ilegível é descartada, nunca derruba a lista. */
fun parseFinishIssues(details: JsonObject): List<FinishIssueDto> {
    val lista = details["issues"] as? JsonArray ?: return emptyList()
    return lista.mapNotNull { el ->
        runCatching { InspectionJson.decodeFromJsonElement(FinishIssueDto.serializer(), el) }.getOrNull()
    }
}

/** Serializador da lista de pendências (fixtures, cache). */
val FinishIssueListSerializer = ListSerializer(FinishIssueDto.serializer())

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

/** O objeto inteiro de `details`: o `detailsJson` quando veio, senão a visão de texto. */
private fun JsonObject.withTextDetails(text: Map<String, String>): JsonObject =
    if (isNotEmpty()) this else JsonObject(text.mapValues { JsonPrimitive(it.value) })
