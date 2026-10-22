package br.com.codecacto.kmplib.inspection.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.label
import br.com.codecacto.kmplib.inspection.model.InspectionPhase
import br.com.codecacto.kmplib.inspection.model.InspectionValue
import br.com.codecacto.kmplib.inspection.model.ReviewEvent
import br.com.codecacto.kmplib.inspection.model.ReviewProblem
import br.com.codecacto.kmplib.inspection.model.ReviewState
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import br.com.codecacto.kmplib.inspection.ui.InspectionFooter
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionSkeletonBlock
import br.com.codecacto.kmplib.inspection.ui.InspectionStateChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.inspection.ui.items.InlineErrorBanner
import br.com.codecacto.kmplib.ui.components.ProportionalBar
import br.com.codecacto.kmplib.ui.components.ProportionalSegment

/**
 * **Revisão da vistoria** (§2.11, LK-M12): título + resumo tabular ("8 cômodos · 94 itens · 187
 * fotos · 4 vídeos"), o cartão âmbar de **pendências** com "Ir ao item" (some quando não há — D10),
 * o **resumo por estado** (barra proporcional de 14 dp com legenda por CONTAGEM em 3 colunas) e os
 * **itens com problema**. Na SAÍDA (D11) o resumo vira Conformes · Ressalvas · Não conformes e a lista
 * vira "Divergências". Rodapé: a frase do que falta e "Finalizar" — desligado com pendência. O diálogo
 * "Finalizar a vistoria?" não exige rede.
 */
@Composable
fun InspectionReview(
    state: ReviewState,
    onEvent: (ReviewEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = texts.rooms.review,
            onNavigate = { onEvent(ReviewEvent.Back) },
            navigationDescription = texts.common.back,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.title, style = inspectionTextStyle(17.sp, FontWeight.SemiBold), modifier = Modifier.semantics { heading() })
                if (!state.isLoading) {
                    Text(
                        texts.review.reviewSummary.fmt(state.counts.rooms, state.counts.items, state.counts.photos, state.counts.videos),
                        style = inspectionTextStyle(14.sp, color = t.muted, tabular = true),
                    )
                }
            }
            if (state.isLoading) {
                ReviewSkeleton()
            } else {
                if (state.pendencies.isNotEmpty()) PendenciesCard(state, texts, onEvent)
                SummaryBlock(state, texts)
                if (state.problems.isNotEmpty()) ProblemsCard(state, texts, onEvent)
            }
        }
        InspectionFooter {
            if (state.finishError != null) {
                InlineErrorBanner(state.finishError, texts.common.retry, { onEvent(ReviewEvent.Finish) })
            }
            val n = state.pendencies.size
            Text(
                if (n > 0) plural(n, texts.review.resolveToFinishOne, texts.review.resolveToFinishOther) else texts.review.allReady,
                style = inspectionTextStyle(13.sp, color = if (n > 0) t.muted else t.good.fg, tabular = true),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            InspectionPrimaryButton(
                text = texts.review.finish,
                onClick = { onEvent(ReviewEvent.Finish) },
                enabled = state.canFinish,
                testTag = InspectionTestTags.REVIEW_FINISH,
            )
        }
    }
    if (state.showFinishDialog) {
        InspectionDialog(
            title = texts.review.finishTitle,
            message = texts.review.finishMessage,
            confirmText = texts.review.finish,
            dismissText = texts.common.back,
            busy = state.isFinishing,
            onConfirm = { onEvent(ReviewEvent.ConfirmFinish) },
            onDismiss = { onEvent(ReviewEvent.DismissFinish) },
            confirmTestTag = InspectionTestTags.REVIEW_FINISH_CONFIRM,
            dismissTestTag = InspectionTestTags.REVIEW_FINISH_BACK,
        )
    }
}

@Composable
private fun PendenciesCard(state: ReviewState, texts: InspectionTexts, onEvent: (ReviewEvent) -> Unit) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(t.warn.soft)
            .border(1.dp, t.warn.fg, forma)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(InspectionTestTags.REVIEW_PENDENCIES),
    ) {
        Row(
            Modifier.heightIn(min = 36.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InspectionIconView(InspectionIcon.Warning, filled = true, size = 20.dp, tint = t.warn.fg)
            Text(texts.review.pendencies.fmt(state.pendencies.size), style = inspectionTextStyle(15.sp, FontWeight.Bold, t.warn.fg))
        }
        state.pendencies.forEach { p ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, style = inspectionTextStyle(14.sp), modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { onEvent(ReviewEvent.GoToPendency(p.id)) }
                        .testTag(InspectionTestTags.reviewGoTo(p.id))
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(texts.review.goToItem, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.brand))
                }
            }
        }
    }
}

@Composable
private fun SummaryBlock(state: ReviewState, texts: InspectionTexts) {
    val t = InspectionTheme.tokens
    val entradas: List<Triple<InspectionValue, String, Int>> = if (state.phase == InspectionPhase.ENTRY) {
        state.conditionCounts.entries.sortedBy { it.key.ordinal }.map { Triple(it.key, texts.values.label(it.key), it.value) }
    } else {
        state.resultCounts.entries.sortedBy { it.key.ordinal }.map { Triple(it.key, texts.values.label(it.key), it.value) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (state.phase == InspectionPhase.ENTRY) texts.review.summaryByCondition else texts.exit.resultLabel,
            style = inspectionTextStyle(15.sp, FontWeight.SemiBold),
            modifier = Modifier.semantics { heading() },
        )
        ProportionalBar(
            segments = entradas.filter { it.third > 0 }.map { (v, rotulo, n) ->
                ProportionalSegment(label = rotulo, value = n.toDouble(), color = t.tone(v.tone).fg)
            },
            height = 14.dp,
            gap = 2.dp,
            showLegend = false,
            trackColor = t.sunken,
        )
        // Legenda por CONTAGEM (a da lib mostra %): grade de 3 colunas, inclui os zeros.
        entradas.chunked(3).forEach { linha ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                linha.forEach { (v, rotulo, n) ->
                    Row(
                        Modifier.weight(1f).semantics(mergeDescendants = true) { },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InspectionIconView(v.icon, filled = true, size = 16.dp, tint = t.tone(v.tone).fg)
                        Text(rotulo, style = inspectionTextStyle(13.sp), maxLines = 1)
                        Text(n.toString(), style = inspectionTextStyle(13.sp, color = t.muted, mono = true))
                    }
                }
                repeat(3 - linha.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ProblemsCard(state: ReviewState, texts: InspectionTexts, onEvent: (ReviewEvent) -> Unit) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(6.dp)
    val titulo = if (state.phase == InspectionPhase.ENTRY) texts.review.problemItems else texts.review.divergences
    Column(Modifier.fillMaxWidth().clip(forma).background(t.surface).border(1.dp, t.line, forma)) {
        Text(
            titulo.fmt(state.problems.size),
            style = inspectionTextStyle(15.sp, FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).semantics { heading() },
        )
        state.problems.forEach { p ->
            HorizontalDivider(thickness = 1.dp, color = t.line)
            ProblemRow(p, texts) { onEvent(ReviewEvent.OpenProblem(p.itemId)) }
        }
    }
}

@Composable
private fun ProblemRow(p: ReviewProblem, texts: InspectionTexts, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = inspectionTextStyle(15.sp))
            Text(p.roomName, style = inspectionTextStyle(12.sp, color = InspectionTheme.tokens.muted))
        }
        when {
            p.divergence != null -> InspectionStateChip(p.divergence, texts = texts)
            p.result != null -> InspectionStateChip(p.result, texts = texts)
            p.condition != null -> InspectionStateChip(p.condition, texts = texts)
        }
    }
}

@Composable
private fun ReviewSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InspectionSkeletonBlock(Modifier.fillMaxWidth().height(96.dp))
        InspectionSkeletonBlock(Modifier.width(140.dp).height(14.dp))
        InspectionSkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
        InspectionSkeletonBlock(Modifier.fillMaxWidth().height(160.dp))
    }
}
