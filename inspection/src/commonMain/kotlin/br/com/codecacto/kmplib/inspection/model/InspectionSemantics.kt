package br.com.codecacto.kmplib.inspection.model

import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.theme.InspectionTone

/**
 * Um valor de um eixo do kit (estado, resultado, funcionamento…): tom + ícone fixos e uma [key]
 * estável (id de teste `vistoria-<tela>-<eixo>-<key>`, e o valor que o mapeador do domínio usa).
 * **Cor nunca sozinha**: todo valor vai com ícone e texto (`InspectionTexts.label`).
 */
interface InspectionValue {
    val tone: InspectionTone
    val icon: InspectionIcon
    val key: String
}

/** Estado do item na ENTRADA. O item nasce [GOOD] ("por exceção"). */
enum class ItemCondition(
    override val tone: InspectionTone,
    override val icon: InspectionIcon,
    override val key: String,
) : InspectionValue {
    NEW(InspectionTone.GOOD, InspectionIcon.NewReleases, "novo"),
    GOOD(InspectionTone.GOOD, InspectionIcon.CheckCircle, "bom"),
    REGULAR(InspectionTone.WARN, InspectionIcon.Error, "regular"),
    BAD(InspectionTone.BAD, InspectionIcon.Cancel, "ruim"),
    NOT_APPLICABLE(InspectionTone.NEUTRAL, InspectionIcon.DoNotDisturbOn, "na"),
    ;

    /** "Com problema" no filtro e na revisão: Regular ou Ruim. */
    val isProblem: Boolean get() = this == REGULAR || this == BAD
}

/** Resultado do item na SAÍDA. */
enum class ExitResult(
    override val tone: InspectionTone,
    override val icon: InspectionIcon,
    override val key: String,
) : InspectionValue {
    CONFORMING(InspectionTone.GOOD, InspectionIcon.CheckCircle, "conforme"),
    RESERVATION(InspectionTone.WARN, InspectionIcon.Error, "ressalva"),
    NON_CONFORMING(InspectionTone.BAD, InspectionIcon.Cancel, "nconf"),
    ;

    /** Divergência obrigatória (e visível) só quando o resultado não é Conforme (D7). */
    val requiresDivergence: Boolean get() = this != CONFORMING
}

/** "Funciona?" — eixo próprio de 3 estados (D15), não pergunta de formulário. */
enum class Functioning(
    override val tone: InspectionTone,
    override val icon: InspectionIcon,
    override val key: String,
) : InspectionValue {
    WORKS(InspectionTone.GOOD, InspectionIcon.CheckCircle, "sim"),
    DOES_NOT_WORK(InspectionTone.BAD, InspectionIcon.Cancel, "nao"),
    NOT_TESTED(InspectionTone.NEUTRAL, InspectionIcon.Help, "nao-testado"),
}

/** Natureza da divergência na saída. Os ícones vão em `FILL 0` no tile (desenho do kit). */
enum class Divergence(
    override val tone: InspectionTone,
    override val icon: InspectionIcon,
    override val key: String,
) : InspectionValue {
    NATURAL_WEAR(InspectionTone.NEUTRAL, InspectionIcon.HourglassBottom, "desgaste"),
    DAMAGE(InspectionTone.BAD, InspectionIcon.BrokenImage, "dano"),
    MISSING(InspectionTone.BAD, InspectionIcon.SearchOff, "falta"),
    CLEANING(InspectionTone.WARN, InspectionIcon.CleaningServices, "limpeza"),
}

/**
 * Estado VISÍVEL de uma mídia (D-f/D8). "Capturada" não aparece: a miniatura só existe depois de
 * gravada no disco, e daí ela já está [QUEUED].
 */
enum class MediaUploadStatus(
    override val tone: InspectionTone,
    override val icon: InspectionIcon,
    override val key: String,
) : InspectionValue {
    QUEUED(InspectionTone.NEUTRAL, InspectionIcon.Schedule, "na-fila"),
    UPLOADING(InspectionTone.BRAND, InspectionIcon.Upload, "enviando"),
    CONFIRMED(InspectionTone.GOOD, InspectionIcon.CloudDone, "confirmada"),
    FAILED(InspectionTone.BAD, InspectionIcon.Error, "falhou"),
}

/** Situação de um signatário. Sem o código (OTP), nunca [SIGNED]. */
enum class SignatureStatus(
    override val tone: InspectionTone,
    override val icon: InspectionIcon,
    override val key: String,
) : InspectionValue {
    PENDING(InspectionTone.NEUTRAL, InspectionIcon.Schedule, "pendente"),
    AWAITING_CODE(InspectionTone.WARN, InspectionIcon.Sms, "aguardando-codigo"),
    SIGNED(InspectionTone.GOOD, InspectionIcon.Verified, "assinou"),
}

/** Resultado da verificação de um laudo (página pública e app). */
enum class VerificationOutcome(
    override val tone: InspectionTone,
    override val icon: InspectionIcon,
    override val key: String,
) : InspectionValue {
    AUTHENTIC(InspectionTone.GOOD, InspectionIcon.Verified, "autentico"),
    NEWER_VERSION(InspectionTone.WARN, InspectionIcon.Update, "versao-mais-recente"),
    MISMATCH(InspectionTone.BAD, InspectionIcon.GppBad, "nao-confere"),
    UNAVAILABLE(InspectionTone.NEUTRAL, InspectionIcon.LinkOff, "indisponivel"),
}

/** Tipo de mídia capturada no item. */
enum class InspectionMediaKind { PHOTO, VIDEO, VOICE }

/**
 * De onde a mídia veio. Só [CAMERA] é **prova** (capturada pelo app, com hash, hora e GPS);
 * [GALLERY] é **anexo** e aparece marcada como tal.
 */
enum class InspectionMediaSource { CAMERA, GALLERY }
