package br.com.codecacto.kmplib.inspection.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.enums.EnumEntries

/*
 * Os enums do contrato v1 do `backlib-inspection` (pacote `br.com.codecacto.backlib.inspection`), com os
 * MESMOS nomes de valor no fio. Duas diferenças, ambas de propósito:
 *
 * 1. **Leitor tolerante.** O contrato diz "o cliente ignora campo desconhecido — versão nova acrescenta".
 *    Isso vale para VALOR também: um app na loja não pode quebrar a tela inteira porque o servidor passou a
 *    mandar um status novo. Todo enum tem `UNKNOWN`, que é o que um valor desconhecido vira na leitura.
 *    `UNKNOWN` nunca deve ser ENVIADO (o servidor recusa com 400).
 * 2. **Nomes que colidiriam com o kit.** `Functioning` e `Divergence` já são valores de TELA do kit
 *    (`model`), com rótulo, ícone e tom; os do fio aqui são [ItemFunctioning] e [ItemDivergence]. O
 *    mapeador (`mapping`) faz a ponte.
 */

/** Base dos serializadores tolerantes: escreve o nome; lê o nome, e o que não conhece vira [unknown]. */
abstract class TolerantEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val entries: EnumEntries<E>,
    private val unknown: E,
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): E {
        val nome = decoder.decodeString()
        return entries.firstOrNull { it != unknown && it.name == nome } ?: unknown
    }
}

/** De onde veio o modelo: semente da lib (editável) ou criado pelo dono. */
@Serializable(with = TemplateOrigin.Serializer::class)
enum class TemplateOrigin {
    SEED, OWN, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<TemplateOrigin>("inspection.TemplateOrigin", TemplateOrigin.entries, TemplateOrigin.UNKNOWN)
}

/** Rótulo do eixo RESULTADO: Conforme… (CONFORMITY) ou Aceito… (ACCEPTANCE). Os valores gravados são sempre [ItemResult]. */
@Serializable(with = ResultAxis.Serializer::class)
enum class ResultAxis {
    CONFORMITY, ACCEPTANCE, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ResultAxis>("inspection.ResultAxis", ResultAxis.entries, ResultAxis.UNKNOWN)
}

/** Contexto da vistoria. A saída SEMPRE aponta para a entrada. */
@Serializable(with = InspectionContext.Serializer::class)
enum class InspectionContext {
    ENTRY, EXIT, OTHER, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<InspectionContext>("inspection.InspectionContext", InspectionContext.entries, InspectionContext.UNKNOWN)
}

/**
 * Ciclo: `DRAFT → FINISHED_LOCAL | SYNCED → SIGNED → LOCKED`. [FINISHED_LOCAL] = finalizada com mídia na
 * fila; vira [SYNCED] sozinha quando a última mídia confirma. Reabrir volta a [DRAFT].
 */
@Serializable(with = InspectionStatus.Serializer::class)
enum class InspectionStatus {
    DRAFT, FINISHED_LOCAL, SYNCED, SIGNED, LOCKED, UNKNOWN;

    /** Aceita edição de item/cômodo/cabeçalho (o servidor só deixa em rascunho). */
    val isEditable: Boolean get() = this == DRAFT

    /** Aceita mídia nova (rascunho, ou finalizada com mídia ainda chegando). */
    val acceptsMedia: Boolean get() = this == DRAFT || this == FINISHED_LOCAL

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<InspectionStatus>("inspection.InspectionStatus", InspectionStatus.entries, InspectionStatus.UNKNOWN)
}

/** Quem preenche: o dono ou o inquilino pela autovistoria por link. */
@Serializable(with = Performer.Serializer::class)
enum class Performer {
    OWNER, TENANT_LINK, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<Performer>("inspection.Performer", Performer.entries, Performer.UNKNOWN)
}

/** Eixo ESTADO. Padrão do item: [GOOD] (vistoria por exceção). [NA] = inexistente/não se aplica. */
@Serializable(with = ItemState.Serializer::class)
enum class ItemState {
    NEW, GOOD, FAIR, POOR, NA, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ItemState>("inspection.ItemState", ItemState.entries, ItemState.UNKNOWN)
}

/** Eixo RESULTADO (saída). Confirmado pela pessoa — nunca calculado sozinho. */
@Serializable(with = ItemResult.Serializer::class)
enum class ItemResult {
    CONFORM, CONFORM_WITH_REMARK, NON_CONFORM, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ItemResult>("inspection.ItemResult", ItemResult.entries, ItemResult.UNKNOWN)
}

/** Funcionamento no fio (`Functioning` do backlib). "Não testado" é resposta explícita. */
@Serializable(with = ItemFunctioning.Serializer::class)
enum class ItemFunctioning {
    YES, NO, NOT_TESTED, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ItemFunctioning>("inspection.Functioning", ItemFunctioning.entries, ItemFunctioning.UNKNOWN)
}

/** Divergência no fio (`Divergence` do backlib — art. 23, III da Lei 8.245/91). */
@Serializable(with = ItemDivergence.Serializer::class)
enum class ItemDivergence {
    NATURAL_WEAR, DAMAGE, MISSING, CLEANING, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ItemDivergence>("inspection.Divergence", ItemDivergence.entries, ItemDivergence.UNKNOWN)
}

/** Tipo da mídia. */
@Serializable(with = MediaKind.Serializer::class)
enum class MediaKind {
    PHOTO, VIDEO, SIGNATURE, ATTACHMENT, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<MediaKind>("inspection.MediaKind", MediaKind.entries, MediaKind.UNKNOWN)
}

/** Origem da mídia. Só [CAMERA] é PROVA; [GALLERY] é anexo e não conta para o mínimo de fotos. */
@Serializable(with = MediaSource.Serializer::class)
enum class MediaSource {
    CAMERA, GALLERY, PAD, SERVER, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<MediaSource>("inspection.MediaSource", MediaSource.entries, MediaSource.UNKNOWN)
}

/** `QUEUED` = registrada, esperando o arquivo; `CONFIRMED` = recebida com o SHA-256 conferido. */
@Serializable(with = MediaStatus.Serializer::class)
enum class MediaStatus {
    QUEUED, CONFIRMED, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<MediaStatus>("inspection.MediaStatus", MediaStatus.entries, MediaStatus.UNKNOWN)
}

/** Papel de quem assina. */
@Serializable(with = SignerRole.Serializer::class)
enum class SignerRole {
    LOCADOR, LOCATARIO, FIADOR, VISTORIADOR, IMOBILIARIA, TESTEMUNHA, OUTRO, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<SignerRole>("inspection.SignerRole", SignerRole.entries, SignerRole.UNKNOWN)
}

/** Como a assinatura foi colhida. */
@Serializable(with = SignatureMethod.Serializer::class)
enum class SignatureMethod {
    PRESENCIAL, LINK, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<SignatureMethod>("inspection.SignatureMethod", SignatureMethod.entries, SignatureMethod.UNKNOWN)
}

/** Canal do código. `NONE` = o próprio vistoriador autenticado (a sessão é o fator). */
@Serializable(with = OtpChannelKind.Serializer::class)
enum class OtpChannelKind {
    EMAIL, WHATSAPP, SMS, NONE, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<OtpChannelKind>("inspection.OtpChannelKind", OtpChannelKind.entries, OtpChannelKind.UNKNOWN)
}

/** Estado da contestação. */
@Serializable(with = ContestationStatus.Serializer::class)
enum class ContestationStatus {
    OPEN, ACCEPTED, REJECTED, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ContestationStatus>("inspection.ContestationStatus", ContestationStatus.entries, ContestationStatus.UNKNOWN)
}

/** Canal do convite de saída. */
@Serializable(with = InvitationChannel.Serializer::class)
enum class InvitationChannel {
    EMAIL, WHATSAPP, SMS, LETTER, IN_PERSON, OTHER, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<InvitationChannel>("inspection.InvitationChannel", InvitationChannel.entries, InvitationChannel.UNKNOWN)
}

/** Comparecimento ao convite de saída. */
@Serializable(with = Attendance.Serializer::class)
enum class Attendance {
    ATTENDED, NOT_ATTENDED, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<Attendance>("inspection.Attendance", Attendance.entries, Attendance.UNKNOWN)
}

/** Versão do laudo: o conteúdo que se assina, ou a selada (conteúdo + página de assinaturas). */
@Serializable(with = ReportKind.Serializer::class)
enum class ReportKind {
    CONTENT, SEALED, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ReportKind>("inspection.ReportKind", ReportKind.entries, ReportKind.UNKNOWN)
}

/** Base da bilateralidade da saída, impressa no laudo selado. */
@Serializable(with = BilateralBasis.Serializer::class)
enum class BilateralBasis {
    SIGNATURE, INVITATION_EXPIRED, NONE, NOT_APPLICABLE, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<BilateralBasis>("inspection.BilateralBasis", BilateralBasis.entries, BilateralBasis.UNKNOWN)
}

/** Quem fez a ação registrada na trilha. */
@Serializable(with = ActorKind.Serializer::class)
enum class ActorKind {
    OWNER, TENANT_LINK, SIGNER, SYSTEM, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<ActorKind>("inspection.ActorKind", ActorKind.entries, ActorKind.UNKNOWN)
}

/** Origem de um cômodo dentro da vistoria. */
@Serializable(with = GroupOrigin.Serializer::class)
enum class GroupOrigin {
    TEMPLATE, DUPLICATED, ADDED, ENTRY, REUSED, UNKNOWN;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<GroupOrigin>("inspection.GroupOrigin", GroupOrigin.entries, GroupOrigin.UNKNOWN)
}

/** Unidade de uma leitura de medidor, com as casas decimais aceitas pelo servidor. */
@Serializable(with = MeterUnit.Serializer::class)
enum class MeterUnit(val symbol: String, val maxDecimals: Int) {
    M3("m³", 3),
    KWH("kWh", 2),
    L("L", 3),
    KG("kg", 3),
    KM("km", 1),
    H("h", 2),
    PERCENT("%", 1),
    UNIT("un", 0),
    UNKNOWN("", 3),
    ;

    /** Leitor tolerante: valor desconhecido vira [UNKNOWN]. */
    object Serializer : TolerantEnumSerializer<MeterUnit>("inspection.MeterUnit", MeterUnit.entries, MeterUnit.UNKNOWN)
}

/** Tipos de sujeito que a fábrica já usa — o tipo é TEXTO LIVRE (`^[A-Z][A-Z0-9_]{0,31}$`). */
object SubjectTypes {
    const val PROPERTY: String = "PROPERTY"
    const val UNIT: String = "UNIT"
    const val VEHICLE: String = "VEHICLE"

    private val SHAPE = Regex("^[A-Z][A-Z0-9_]{0,31}$")

    fun isValid(value: String): Boolean = SHAPE.matches(value)
}

/** `FinishIssueDto.reason` (422 `INSPECTION_INCOMPLETE`). Texto no fio; motivo novo chega como está. */
object FinishIssueReasons {
    const val REQUIRED_FIELDS: String = "REQUIRED_FIELDS"
    const val RESULT_REQUIRED: String = "RESULT_REQUIRED"
    const val MIN_PHOTOS: String = "MIN_PHOTOS"
    const val HEADER_REQUIRED: String = "HEADER_REQUIRED"
    const val MEDIA_TASK: String = "MEDIA_TASK"
    const val READING_REQUIRED: String = "READING_REQUIRED"
}

/** `ItemPatch.clear` — campos opcionais que se LIMPAM (mandar `null` não limpa: ausente = não mexe). */
object ItemClearFields {
    const val RESULT: String = "result"
    const val FUNCTIONING: String = "functioning"
    const val QUANTITY: String = "quantity"
    const val OBSERVATION: String = "observation"
    const val VOICE_TRANSCRIPT: String = "voiceTranscript"
}

/** `UploadInstructionsDto.mode`. */
object UploadModes {
    const val SINGLE: String = "SINGLE"
    const val MULTIPART: String = "MULTIPART"
}

/** Segundo fator do link público (`LinkIssueInput.secondFactor`). */
object LinkSecondFactors {
    const val NONE: String = "NONE"
    const val PHONE_LAST4: String = "PHONE_LAST4"
    const val BIRTH_DATE: String = "BIRTH_DATE"
}

/**
 * O JSON do contrato: ignora campo desconhecido, não escreve `null` (ausente = não mexe, igual ao
 * servidor) e escreve os defaults (`accepted`, listas vazias). Use-o para ler/escrever os DTOs fora do
 * [InspectionApi] (cache local, fixtures).
 */
val InspectionJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}
