package br.com.codecacto.kmplib.inspection.mapping

import br.com.codecacto.kmplib.inspection.data.InspectionContext
import br.com.codecacto.kmplib.inspection.data.ItemDivergence
import br.com.codecacto.kmplib.inspection.data.ItemFunctioning
import br.com.codecacto.kmplib.inspection.data.ItemResult
import br.com.codecacto.kmplib.inspection.data.ItemState
import br.com.codecacto.kmplib.inspection.data.MediaKind
import br.com.codecacto.kmplib.inspection.model.Divergence
import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.Functioning
import br.com.codecacto.kmplib.inspection.model.InspectionMediaKind
import br.com.codecacto.kmplib.inspection.model.InspectionPhase
import br.com.codecacto.kmplib.inspection.model.ItemCondition

/*
 * A ponte entre os valores do FIO (`data`, nomes do `backlib-inspection`) e os valores de TELA do kit
 * (`model`, com rótulo/ícone/tom). Ida e volta, total: todo valor de tela tem o seu no fio; o `UNKNOWN`
 * do fio (versão nova do servidor) vira `null` na tela — a tela mostra "sem valor", nunca um valor errado.
 */

fun ItemState.toCondition(): ItemCondition? = when (this) {
    ItemState.NEW -> ItemCondition.NEW
    ItemState.GOOD -> ItemCondition.GOOD
    ItemState.FAIR -> ItemCondition.REGULAR
    ItemState.POOR -> ItemCondition.BAD
    ItemState.NA -> ItemCondition.NOT_APPLICABLE
    ItemState.UNKNOWN -> null
}

fun ItemCondition.toItemState(): ItemState = when (this) {
    ItemCondition.NEW -> ItemState.NEW
    ItemCondition.GOOD -> ItemState.GOOD
    ItemCondition.REGULAR -> ItemState.FAIR
    ItemCondition.BAD -> ItemState.POOR
    ItemCondition.NOT_APPLICABLE -> ItemState.NA
}

fun ItemResult.toExitResult(): ExitResult? = when (this) {
    ItemResult.CONFORM -> ExitResult.CONFORMING
    ItemResult.CONFORM_WITH_REMARK -> ExitResult.RESERVATION
    ItemResult.NON_CONFORM -> ExitResult.NON_CONFORMING
    ItemResult.UNKNOWN -> null
}

fun ExitResult.toItemResult(): ItemResult = when (this) {
    ExitResult.CONFORMING -> ItemResult.CONFORM
    ExitResult.RESERVATION -> ItemResult.CONFORM_WITH_REMARK
    ExitResult.NON_CONFORMING -> ItemResult.NON_CONFORM
}

fun ItemFunctioning.toFunctioning(): Functioning? = when (this) {
    ItemFunctioning.YES -> Functioning.WORKS
    ItemFunctioning.NO -> Functioning.DOES_NOT_WORK
    ItemFunctioning.NOT_TESTED -> Functioning.NOT_TESTED
    ItemFunctioning.UNKNOWN -> null
}

fun Functioning.toItemFunctioning(): ItemFunctioning = when (this) {
    Functioning.WORKS -> ItemFunctioning.YES
    Functioning.DOES_NOT_WORK -> ItemFunctioning.NO
    Functioning.NOT_TESTED -> ItemFunctioning.NOT_TESTED
}

fun ItemDivergence.toDivergence(): Divergence? = when (this) {
    ItemDivergence.NATURAL_WEAR -> Divergence.NATURAL_WEAR
    ItemDivergence.DAMAGE -> Divergence.DAMAGE
    ItemDivergence.MISSING -> Divergence.MISSING
    ItemDivergence.CLEANING -> Divergence.CLEANING
    ItemDivergence.UNKNOWN -> null
}

fun Divergence.toItemDivergence(): ItemDivergence = when (this) {
    Divergence.NATURAL_WEAR -> ItemDivergence.NATURAL_WEAR
    Divergence.DAMAGE -> ItemDivergence.DAMAGE
    Divergence.MISSING -> ItemDivergence.MISSING
    Divergence.CLEANING -> ItemDivergence.CLEANING
}

/** Fase da tela: SAÍDA só no contexto `EXIT`; entrada e "outra" se preenchem como entrada. */
fun InspectionContext.toPhase(): InspectionPhase = if (this == InspectionContext.EXIT) InspectionPhase.EXIT else InspectionPhase.ENTRY

/** Tipo de miniatura. Anexo (imagem/PDF) aparece como foto marcada "Anexo"; assinatura não vira miniatura. */
fun MediaKind.toKitKind(): InspectionMediaKind? = when (this) {
    MediaKind.PHOTO, MediaKind.ATTACHMENT -> InspectionMediaKind.PHOTO
    MediaKind.VIDEO -> InspectionMediaKind.VIDEO
    MediaKind.SIGNATURE, MediaKind.UNKNOWN -> null
}
