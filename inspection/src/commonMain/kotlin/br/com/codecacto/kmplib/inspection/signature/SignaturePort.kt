package br.com.codecacto.kmplib.inspection.signature

import br.com.codecacto.kmplib.inspection.capture.InspectionGeo
import br.com.codecacto.kmplib.inspection.data.InspectionApi
import br.com.codecacto.kmplib.inspection.data.InspectionError
import br.com.codecacto.kmplib.inspection.data.InspectionResult
import br.com.codecacto.kmplib.inspection.data.OtpChannelKind
import br.com.codecacto.kmplib.inspection.data.SignatureDto
import br.com.codecacto.kmplib.inspection.data.SignerDto
import br.com.codecacto.kmplib.inspection.model.OtpChannel
import br.com.codecacto.kmplib.inspection.model.SignatureState
import br.com.codecacto.kmplib.inspection.model.SignatureStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ═══════════════════════════════════════════════════════════════════════════════════════════════════
//  ASSINATURA NO APARELHO — o código (OTP) e o traço. Área sensível (assinatura eletrônica avançada,
//  dado pessoal): nada daqui vai para log; o código, o traço e o destino do código não aparecem em
//  `toString`. Quem confere o código, grava IP/GPS/hash do PDF e trava a versão é o SERVIDOR
//  (`backlib-inspection` SignatureService) — o cliente só valida a FORMA (6 dígitos, PNG, tamanho) para
//  não gastar tentativa com entrada malformada.
// ═══════════════════════════════════════════════════════════════════════════════════════════════════

@Serializable
data class OtpSendInput(val signerId: String, val channel: OtpChannelKind? = null)

/** Código enviado. [destination] já vem MASCARADO do servidor (`m***@g***.com`) — mesmo assim, fora do log. */
@Serializable
data class OtpSentDto(
    val channel: OtpChannelKind,
    val destination: String,
    val expiresAt: String,
    val resendAfterSeconds: Long = 60,
) {
    override fun toString(): String = "OtpSentDto(channel=$channel, expiresAt=$expiresAt, resendAfterSeconds=$resendAfterSeconds)"
}

/** Corpo do `POST …/signatures`. Leva o código e o traço: o `toString` não os mostra. */
@Serializable
data class SignInput(
    val signerId: String,
    val otp: String? = null,
    val accepted: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Double? = null,
    val tracePngBase64: String? = null,
) {
    override fun toString(): String =
        "SignInput(signerId=$signerId, otp=${if (otp != null) "…" else "null"}, accepted=$accepted, " +
            "gps=${lat != null}, trace=${tracePngBase64 != null})"
}

/**
 * O pedido de assinatura que sai da tela (`SignatureEvent.Submit`).
 *
 * @param code o código de 6 dígitos que a parte recebeu. `null` SÓ para o VISTORIADOR autenticado que
 *   cadastrou a parte (a sessão é o fator — o servidor confere e recusa os demais com `OTP_REQUIRED`).
 * @param accepted aceite do meio eletrônico (o checkbox). `false` não chega ao servidor.
 * @param strokePng o traço exportado do quadro (PNG), opcional.
 * @param location GPS no momento da assinatura (best-effort).
 */
class SignatureRequest(
    val signerId: String,
    val code: String?,
    val accepted: Boolean,
    val strokePng: ByteArray? = null,
    val location: InspectionGeo? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is SignatureRequest && other.signerId == signerId && other.code == code && other.accepted == accepted &&
            other.strokePng.contentEquals(strokePng) && other.location == location

    override fun hashCode(): Int = signerId.hashCode() * 31 + (strokePng?.contentHashCode() ?: 0)

    override fun toString(): String =
        "SignatureRequest(signerId=$signerId, code=${if (code != null) "…" else "null"}, accepted=$accepted, " +
            "stroke=${strokePng?.size ?: 0} bytes, gps=${location != null})"
}

/**
 * **A porta de assinatura do kit** — o que o ViewModel do produto chama ao receber `SignatureEvent.Resend` e
 * `SignatureEvent.Submit`. A implementação de produção é [RestSignaturePort] (rotas do dono do
 * `backlib-inspection`); em teste, um dublê.
 */
interface SignaturePort {
    /** Pede o código para a parte ([channel] `null` = o servidor escolhe). 429 = espere (reenvio cedo). */
    suspend fun sendCode(signerId: String, channel: OtpChannel? = null): InspectionResult<OtpSentDto>

    /** Assina a versão atual do laudo. A 1ª assinatura TRAVA a versão no servidor. */
    suspend fun sign(request: SignatureRequest): InspectionResult<SignatureDto>
}

/**
 * [SignaturePort] sobre o [InspectionApi]: `POST /inspections/{id}/signatures/otp` e
 * `POST /inspections/{id}/signatures`.
 *
 * Validação LOCAL (antes da rede, para não queimar tentativa): aceite marcado; código só com dígitos e com
 * [CODE_LENGTH] posições; traço em PNG de até [MAX_TRACE_BYTES]. Recusa local vem como
 * [InspectionError.Validation] com o `serverCode` que o servidor usaria (`TERMS_NOT_ACCEPTED`,
 * `OTP_INVALID`, `TRACE_INVALID`).
 */
class RestSignaturePort(
    private val api: InspectionApi,
    private val inspectionId: String,
) : SignaturePort {

    override suspend fun sendCode(signerId: String, channel: OtpChannel?): InspectionResult<OtpSentDto> {
        if (signerId.isBlank()) return signerNotFound()
        return api.post(
            "/inspections/${InspectionApi.seg(inspectionId)}/signatures/otp",
            OtpSendInput.serializer(),
            OtpSendInput(signerId, channel?.toKind()),
            OtpSentDto.serializer(),
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun sign(request: SignatureRequest): InspectionResult<SignatureDto> {
        if (request.signerId.isBlank()) return signerNotFound()
        if (!request.accepted) return localError(SignatureErrorCodes.TERMS_NOT_ACCEPTED, "Marque o aceite para assinar.")
        val codigo = request.code?.let(::normalizeCode)
        if (request.code != null && codigo == null) return localError(SignatureErrorCodes.OTP_INVALID, "O código tem $CODE_LENGTH dígitos.")
        val traco = request.strokePng
        if (traco != null && !isAcceptableTrace(traco)) return localError(SignatureErrorCodes.TRACE_INVALID, "Assinatura desenhada inválida.")
        val geo = request.location?.takeIf { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
        return api.post(
            "/inspections/${InspectionApi.seg(inspectionId)}/signatures",
            SignInput.serializer(),
            SignInput(
                signerId = request.signerId,
                otp = codigo,
                accepted = true,
                lat = geo?.latitude,
                lng = geo?.longitude,
                accuracyM = geo?.accuracyMeters?.toDouble()?.takeIf { it >= 0 && !it.isNaN() },
                tracePngBase64 = traco?.let { Base64.Default.encode(it) },
            ),
            SignatureDto.serializer(),
        )
    }

    private fun localError(code: String, message: String): InspectionResult.Failed =
        InspectionResult.Failed(InspectionError.Validation(400, code, message))

    /** Parte em branco = parte que não existe: o mesmo 404 `SIGNER_NOT_FOUND` do servidor. */
    private fun signerNotFound(): InspectionResult.Failed =
        InspectionResult.Failed(InspectionError.NotFound(SignatureErrorCodes.SIGNER_NOT_FOUND, "Parte não encontrada."))

    companion object {
        /** Posições do código (o `OtpCodeInput` do kit). */
        const val CODE_LENGTH: Int = 6

        /**
         * Teto do traço NO CLIENTE. O servidor aceita até 300 KB decodificados, mas o corpo JSON da rota é
         * limitado a 256 KB (`InspectionRoutesConfig.maxBodyBytes`) e o base64 cresce 4/3 — acima disto o
         * servidor responde 413. O `SignaturePad` exporta bem abaixo (dezenas de KB).
         */
        const val MAX_TRACE_BYTES: Int = 190 * 1024

        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /** Só dígitos (espaço e hífen do colar são tolerados), exatamente [CODE_LENGTH]; senão `null`. */
        fun normalizeCode(raw: String): String? {
            val digitos = raw.filterNot { it == ' ' || it == '-' }
            return digitos.takeIf { it.length == CODE_LENGTH && it.all { c -> c in '0'..'9' } }
        }

        /** PNG (assinatura de arquivo) e dentro do teto. */
        fun isAcceptableTrace(png: ByteArray): Boolean =
            png.size in (PNG.size + 1)..MAX_TRACE_BYTES && png.copyOfRange(0, PNG.size).contentEquals(PNG)
    }
}

/** Códigos de recusa da assinatura (o `code` do envelope do `backlib-inspection`). */
object SignatureErrorCodes {
    const val SIGNER_NOT_FOUND: String = "SIGNER_NOT_FOUND"
    const val OTP_REQUIRED: String = "OTP_REQUIRED"
    const val OTP_INVALID: String = "OTP_INVALID"
    const val OTP_EXPIRED: String = "OTP_EXPIRED"
    const val OTP_LOCKED: String = "OTP_LOCKED"
    const val OTP_RESEND_TOO_SOON: String = "OTP_RESEND_TOO_SOON"
    const val OTP_TOO_MANY: String = "OTP_TOO_MANY"
    const val OTP_EXHAUSTED: String = "OTP_EXHAUSTED"
    const val OTP_DELIVERY_FAILED: String = "OTP_DELIVERY_FAILED"
    const val OTP_UNAVAILABLE: String = "OTP_UNAVAILABLE"
    const val OTP_CHANNEL_UNAVAILABLE: String = "OTP_CHANNEL_UNAVAILABLE"
    const val ALREADY_SIGNED: String = "ALREADY_SIGNED"
    const val TERMS_NOT_ACCEPTED: String = "TERMS_NOT_ACCEPTED"
    const val TRACE_INVALID: String = "TRACE_INVALID"
    const val INSPECTION_NOT_SIGNABLE: String = "INSPECTION_NOT_SIGNABLE"
    const val REPORT_NOT_GENERATED: String = "REPORT_NOT_GENERATED"
}

/** Por que a assinatura (ou o envio do código) não passou — o que a tela de assinatura precisa saber. */
sealed interface SignatureFailure {
    /**
     * Código recusado. [attemptsLeft] restantes; `null` = o servidor NÃO contou (código já usado, laudo mudou,
     * formato inválido) — aí a tela mostra [message], a frase do servidor (ou a local, na recusa de formato).
     */
    data class InvalidCode(val attemptsLeft: Int?, val message: String? = null) : SignatureFailure

    /** Código vencido: pedir outro. */
    data object Expired : SignatureFailure

    /** Tentativas do código esgotadas: pedir outro. */
    data object Locked : SignatureFailure

    /** Faltou o código (só o vistoriador assina sem). */
    data object CodeRequired : SignatureFailure

    /** Reenvio antes da hora; [retryAfterSeconds] = o `Retry-After` do servidor (a contagem da tela). */
    data class ResendTooSoon(val blockedUntil: String?, val retryAfterSeconds: Long? = null) : SignatureFailure

    /** Limite de códigos por hora; [retryAfterSeconds] = quando dá para pedir de novo. */
    data class TooManyCodes(val retryAfterSeconds: Long? = null) : SignatureFailure

    /** Limite de códigos da parte nesta versão: o link de assinatura foi revogado; gere outro. */
    data object Exhausted : SignatureFailure

    /** O código não saiu (canal fora, contato ausente) — não contou tentativa. */
    data class CannotSend(val serverCode: String?) : SignatureFailure

    /** Esta parte já assinou esta versão. */
    data object AlreadySigned : SignatureFailure

    /** O laudo não está assinável (sem versão gerada, selado, reaberto). */
    data class NotSignable(val serverCode: String?) : SignatureFailure

    /** Sem rede: o traço fica na tela aguardando (nunca "assinado" sem o servidor). */
    data object Offline : SignatureFailure

    /** Outra falha (validação, servidor) — mostrar [error] `.userMessage`. */
    data class Other(val error: InspectionError) : SignatureFailure
}

/** Classifica o erro da porta. Função pura (testada). */
fun InspectionError.toSignatureFailure(): SignatureFailure = when {
    this is InspectionError.Offline -> SignatureFailure.Offline
    serverCode == SignatureErrorCodes.OTP_INVALID -> SignatureFailure.InvalidCode(
        ((this as? InspectionError.Rejected)?.details?.get("attemptsLeft") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.coerceAtLeast(0),
        userMessage,
    )
    serverCode == SignatureErrorCodes.OTP_EXPIRED -> SignatureFailure.Expired
    serverCode == SignatureErrorCodes.OTP_LOCKED -> SignatureFailure.Locked
    serverCode == SignatureErrorCodes.OTP_REQUIRED -> SignatureFailure.CodeRequired
    serverCode == SignatureErrorCodes.OTP_RESEND_TOO_SOON ->
        SignatureFailure.ResendTooSoon((this as? InspectionError.RateLimited)?.blockedUntil, (this as? InspectionError.RateLimited)?.retryAfterSeconds)
    serverCode == SignatureErrorCodes.OTP_TOO_MANY -> SignatureFailure.TooManyCodes((this as? InspectionError.RateLimited)?.retryAfterSeconds)
    serverCode == SignatureErrorCodes.OTP_EXHAUSTED -> SignatureFailure.Exhausted
    serverCode == SignatureErrorCodes.OTP_DELIVERY_FAILED || serverCode == SignatureErrorCodes.OTP_UNAVAILABLE ||
        serverCode == SignatureErrorCodes.OTP_CHANNEL_UNAVAILABLE -> SignatureFailure.CannotSend(serverCode)
    serverCode == SignatureErrorCodes.ALREADY_SIGNED -> SignatureFailure.AlreadySigned
    serverCode == SignatureErrorCodes.INSPECTION_NOT_SIGNABLE || serverCode == SignatureErrorCodes.REPORT_NOT_GENERATED ->
        SignatureFailure.NotSignable(serverCode)
    else -> SignatureFailure.Other(this)
}

/** Canal da tela ↔ do fio. */
fun OtpChannel.toKind(): OtpChannelKind = when (this) {
    OtpChannel.SMS -> OtpChannelKind.SMS
    OtpChannel.EMAIL -> OtpChannelKind.EMAIL
    OtpChannel.WHATSAPP -> OtpChannelKind.WHATSAPP
}

fun OtpChannelKind.toChannel(): OtpChannel? = when (this) {
    OtpChannelKind.SMS -> OtpChannel.SMS
    OtpChannelKind.EMAIL -> OtpChannel.EMAIL
    OtpChannelKind.WHATSAPP -> OtpChannel.WHATSAPP
    OtpChannelKind.NONE, OtpChannelKind.UNKNOWN -> null
}

/**
 * Transições do [SignatureState] a partir das respostas da porta — puras, para o ViewModel do produto não
 * reinventar a regra ("nunca assinado sem o servidor", "código errado limpa o campo").
 */
object SignatureStateMapping {
    /** Estado inicial da tela para a parte [signer] ([roleLabel] e [consentText] montados pelo produto). */
    fun initial(signer: SignerDto, roleLabel: String, consentText: String, alreadySigned: Boolean = false): SignatureState =
        SignatureState(
            signerName = signer.name,
            signerRole = roleLabel,
            consentText = consentText,
            status = if (alreadySigned) SignatureStatus.SIGNED else SignatureStatus.PENDING,
        )

    /** O código saiu: aguarda a digitação, contagem do reenvio. */
    fun codeSent(state: SignatureState, sent: OtpSentDto): SignatureState = state.copy(
        codeSent = true,
        channel = sent.channel.toChannel() ?: state.channel,
        resendInSeconds = sent.resendAfterSeconds.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        codeExpired = false,
        attemptsLeft = null,
        offline = false,
        errorMessage = null,
        status = if (state.status == SignatureStatus.SIGNED) SignatureStatus.SIGNED else SignatureStatus.AWAITING_CODE,
    )

    /** Começou a enviar a assinatura. */
    fun submitting(state: SignatureState): SignatureState = state.copy(isSubmitting = true, showValidation = true, errorMessage = null)

    /**
     * O servidor respondeu 2xx: só vira ASSINADO se a resposta é a assinatura DESTA parte
     * ([expectedSignerId]) e traz `signedAt` e `signedHash`. Qualquer outra coisa é resposta ilegível —
     * a tela não afirma assinatura que não pode provar.
     */
    fun signed(state: SignatureState, dto: SignatureDto, expectedSignerId: String): SignatureState {
        val valida = expectedSignerId.isNotBlank() && dto.signerId == expectedSignerId &&
            dto.signedAt.isNotBlank() && dto.signedHash.isNotBlank()
        if (!valida) {
            return failed(state, SignatureFailure.Other(InspectionError.InvalidResponse(InspectionError.INVALID_RESPONSE_MESSAGE)))
        }
        return state.copy(
            isSubmitting = false, status = SignatureStatus.SIGNED, offline = false, attemptsLeft = null,
            codeExpired = false, code = "", errorMessage = null,
        )
    }

    /** Falhou: o que a tela mostra. Nunca muda para "assinado" (salvo o servidor dizer que esta parte JÁ assinou). */
    fun failed(state: SignatureState, failure: SignatureFailure): SignatureState {
        val base = state.copy(isSubmitting = false, errorMessage = null)
        return when (failure) {
            // `null` preservado: sem contagem, a tela mostra a frase (código já usado, laudo mudou, formato).
            is SignatureFailure.InvalidCode -> base.copy(
                code = "",
                attemptsLeft = failure.attemptsLeft,
                errorMessage = if (failure.attemptsLeft == null) failure.message else null,
            )
            SignatureFailure.Expired, SignatureFailure.Locked -> base.copy(code = "", codeExpired = true)
            SignatureFailure.Offline -> base.copy(offline = true)
            SignatureFailure.AlreadySigned -> base.copy(status = SignatureStatus.SIGNED)
            is SignatureFailure.ResendTooSoon -> base.copy(resendInSeconds = seconds(failure.retryAfterSeconds) ?: state.resendInSeconds)
            is SignatureFailure.TooManyCodes -> base.copy(resendInSeconds = seconds(failure.retryAfterSeconds) ?: state.resendInSeconds)
            is SignatureFailure.Other -> base.copy(errorMessage = failure.error.userMessage)
            else -> base
        }
    }

    private fun seconds(v: Long?): Int? = v?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt()
}
