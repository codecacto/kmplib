package br.com.codecacto.kmplib.inspection

import br.com.codecacto.kmplib.inspection.model.Divergence
import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.Functioning
import br.com.codecacto.kmplib.inspection.model.ItemCondition
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.model.SignatureStatus
import br.com.codecacto.kmplib.inspection.model.VerificationOutcome
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Preenche um modelo `%1$s`/`%2$s`… (também aceita `%1$d`). Os textos do kit são lidos CRUS dos
 * recursos e formatados aqui: assim o mesmo modelo serve a número e a texto, sem a armadilha do
 * `%d` com argumento `String`.
 */
fun String.fmt(vararg args: Any): String {
    var s = this
    args.forEachIndexed { i, a ->
        val n = i + 1
        s = s.replace("%$n\$s", a.toString()).replace("%$n\$d", a.toString())  // regras:ignorar LIKE-MANUAL — modelo de texto %1$s dos recursos, não SQL
    }
    return s
}

/** Escolhe a forma singular/plural (pt, en, es: 1 = singular) e formata com [n]. */
fun plural(n: Int, one: String, other: String): String = (if (n == 1) one else other).fmt(n)

/** Rótulo do valor no tile/chip. */
fun InspectionValuesTexts.label(value: ItemCondition): String = when (value) {
    ItemCondition.NEW -> conditionNew
    ItemCondition.GOOD -> conditionGood
    ItemCondition.REGULAR -> conditionRegular
    ItemCondition.BAD -> conditionBad
    ItemCondition.NOT_APPLICABLE -> conditionNotApplicable
}

fun InspectionValuesTexts.label(value: ExitResult): String = when (value) {
    ExitResult.CONFORMING -> resultConforming
    ExitResult.RESERVATION -> resultReservation
    ExitResult.NON_CONFORMING -> resultNonConforming
}

/** "Sim · Não · Não testado" no tile da folha do item. */
fun InspectionValuesTexts.tileLabel(value: Functioning): String = when (value) {
    Functioning.WORKS -> functioningYes
    Functioning.DOES_NOT_WORK -> functioningNo
    Functioning.NOT_TESTED -> functioningNotTested
}

/** "Funciona · Não funciona · Não testado" no chip (fora do contexto da pergunta). */
fun InspectionValuesTexts.chipLabel(value: Functioning): String = when (value) {
    Functioning.WORKS -> functioningWorks
    Functioning.DOES_NOT_WORK -> functioningBroken
    Functioning.NOT_TESTED -> functioningNotTested
}

fun InspectionValuesTexts.label(value: Divergence): String = when (value) {
    Divergence.NATURAL_WEAR -> divergenceWear
    Divergence.DAMAGE -> divergenceDamage
    Divergence.MISSING -> divergenceMissing
    Divergence.CLEANING -> divergenceCleaning
}

fun InspectionValuesTexts.label(value: MediaUploadStatus): String = when (value) {
    MediaUploadStatus.QUEUED -> mediaQueued
    MediaUploadStatus.UPLOADING -> mediaUploading
    MediaUploadStatus.CONFIRMED -> mediaConfirmed
    MediaUploadStatus.FAILED -> mediaFailed
}

fun InspectionValuesTexts.label(value: SignatureStatus): String = when (value) {
    SignatureStatus.PENDING -> signaturePending
    SignatureStatus.AWAITING_CODE -> signatureAwaitingCode
    SignatureStatus.SIGNED -> signatureSigned
}

fun InspectionValuesTexts.label(value: VerificationOutcome): String = when (value) {
    VerificationOutcome.AUTHENTIC -> verificationAuthentic
    VerificationOutcome.NEWER_VERSION -> verificationNewer
    VerificationOutcome.MISMATCH -> verificationMismatch
    VerificationOutcome.UNAVAILABLE -> verificationUnavailable
}

/**
 * Forma curta de um hash para leitura humana: os 4 primeiros, "…" e os 2 últimos (`9f2c…e1`).
 * A forma INTEIRA é a que se copia e confere (`HashField`); esta só identifica.
 */
fun shortHash(hash: String, head: Int = 4, tail: Int = 2): String {
    val h = hash.trim()
    return if (h.length <= head + tail + 1) h else h.take(head) + "…" + h.takeLast(tail)
}

/** Sinal de menos tipográfico (U+2212) — o que o kit usa em coordenada. */
const val TYPOGRAPHIC_MINUS: Char = '−'

/**
 * Coordenada como o kit escreve: vírgula decimal, menos tipográfico, [decimals] casas
 * (`−23,5505 −46,6333`). Pura e independente de região: é prova, não número para fazer conta.
 */
fun formatCoordinates(latitude: Double, longitude: Double, decimals: Int = 4): String =
    formatCoordinate(latitude, decimals) + " " + formatCoordinate(longitude, decimals)

/** Um número da coordenada no formato do kit. */
fun formatCoordinate(value: Double, decimals: Int = 4): String {
    require(decimals in 0..8) { "decimals fora de 0..8" }
    var fator = 1L
    repeat(decimals) { fator *= 10 }
    val inteiro = (abs(value) * fator).roundToLong()
    val parteInteira = inteiro / fator
    val fracao = (inteiro % fator).toString().padStart(decimals, '0')
    val negativo = value < 0 && inteiro != 0L
    val sinal = if (negativo) TYPOGRAPHIC_MINUS.toString() else ""
    return if (decimals == 0) "$sinal$parteInteira" else "$sinal$parteInteira,$fracao"
}
