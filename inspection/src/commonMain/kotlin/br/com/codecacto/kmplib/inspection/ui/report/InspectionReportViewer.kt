package br.com.codecacto.kmplib.inspection.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.model.ReportProof
import br.com.codecacto.kmplib.inspection.model.ReportStatus
import br.com.codecacto.kmplib.inspection.model.ReportVersion
import br.com.codecacto.kmplib.inspection.model.ReportViewerEvent
import br.com.codecacto.kmplib.inspection.model.ReportViewerState
import br.com.codecacto.kmplib.inspection.model.SignatureStatus
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.shortHash
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.theme.InspectionTokens
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionSecondaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionStateChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.KitIconButton
import br.com.codecacto.kmplib.inspection.ui.ProofText
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.inspection.ui.media.MediaUploadProgressBlock
import br.com.codecacto.kmplib.inspection.ui.rooms.MenuRow
import br.com.codecacto.kmplib.pdf.viewer.PdfViewer
import br.com.codecacto.kmplib.pdf.viewer.PdfViewerState
import br.com.codecacto.kmplib.ui.components.QrCodeView
import br.com.codecacto.kmplib.ui.components.AppDropdownMenu
import br.com.codecacto.kmplib.ui.components.AppModalBottomSheet
import br.com.codecacto.kmplib.ui.components.ErrorState

/**
 * **O laudo no app** (§2.12, LK-M13): barra "Laudo · Apto 302 · v1" com ⋮ (Versões · Criar nova
 * versão), e três estados:
 * - **Gerando** (não é erro): "Gerando o laudo…" + barra de 8 dp + "Faltam N mídias · X de Y enviadas";
 * - **Pronto**: o palco `sunken` com as páginas REAIS do PDF ([PdfViewer], que já é PdfRenderer/PDFKit —
 *   a página é branca nos dois temas) e "página 3 de 18", o [ProofCard] e as ações
 *   "Compartilhar PDF" / "Copiar link de verificação";
 * - **Falhou**: `ErrorState` com "Tentar de novo".
 *
 * Versão substituída mostra o aviso âmbar "Há uma versão mais recente".
 *
 * @param pdf o estado do leitor, criado pelo produto com `rememberPdfViewerState(PdfSource…)` — o kit
 *   não sabe de onde vêm os bytes (arquivo local, URL assinada).
 */
@Composable
fun InspectionReportViewer(
    state: ReportViewerState,
    pdf: PdfViewerState?,
    onEvent: (ReportViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = state.title,
            titleSize = 19.sp,
            onNavigate = { onEvent(ReportViewerEvent.Back) },
            navigationDescription = texts.common.back,
            actions = {
                Box {
                    KitIconButton(
                        InspectionIcon.MoreVert,
                        texts.common.moreOptions,
                        { menu = true },
                        size = 22.dp,
                        testTag = InspectionTestTags.REPORT_MENU,
                    )
                    AppDropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        shape = RoundedCornerShape(6.dp),
                        containerColor = t.surface,
                    ) {
                        MenuRow(texts.report.versions, InspectionIcon.LibraryAdd, t.ink, "vistoria-laudo-menu-versoes") {
                            menu = false
                            onEvent(ReportViewerEvent.OpenVersions)
                        }
                        MenuRow(texts.report.newVersion, InspectionIcon.EditNote, t.ink, "vistoria-laudo-menu-nova-versao") {
                            menu = false
                            onEvent(ReportViewerEvent.CreateNewVersion)
                        }
                    }
                }
            },
        )
        if (state.isOutdated) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(t.warn.soft)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InspectionIconView(InspectionIcon.Update, filled = true, size = 18.dp, tint = t.warn.fg)
                Text(texts.values.verificationNewer, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.warn.fg))
            }
        }
        when (val status = state.status) {
            is ReportStatus.Generating -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    texts.report.generatingReport,
                    style = inspectionTextStyle(18.sp, FontWeight.SemiBold),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                MediaUploadProgressBlock(status.uploaded, status.total, texts = texts)
            }
            is ReportStatus.Failed -> Box(Modifier.weight(1f).fillMaxWidth()) {
                ErrorState(message = status.message ?: texts.report.reportError, onRetry = { onEvent(ReportViewerEvent.Retry) })
            }
            ReportStatus.Ready -> {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(t.sunken)
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(2.dp))
                            .background(InspectionTokens.PaperBackground),
                    ) {
                        if (pdf != null) PdfViewer(pdf, Modifier.fillMaxSize())
                    }
                    if (pdf != null && pdf.pageCount > 0 && pdf.currentPage > 0) {
                        Text(
                            texts.report.pageOf.fmt(pdf.currentPage, pdf.pageCount),
                            style = inspectionTextStyle(13.sp, color = t.muted, tabular = true),
                        )
                    }
                }
                state.proof?.let { ProofCard(it, texts, Modifier.padding(16.dp)) }
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InspectionPrimaryButton(
                        text = texts.report.sharePdf,
                        onClick = { onEvent(ReportViewerEvent.SharePdf) },
                        icon = InspectionIcon.Share,
                        testTag = InspectionTestTags.REPORT_SHARE,
                    )
                    InspectionSecondaryButton(
                        text = texts.report.copyVerifyLink,
                        onClick = { onEvent(ReportViewerEvent.CopyVerifyLink) },
                        icon = InspectionIcon.Link,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = InspectionTestTags.REPORT_COPY_LINK,
                    )
                }
            }
        }
    }
    if (state.showVersions) {
        ReportVersionsSheet(
            versions = state.versions,
            onOpen = { onEvent(ReportViewerEvent.OpenVersion(it)) },
            onCreateNew = { onEvent(ReportViewerEvent.CreateNewVersion) },
            onDismiss = { onEvent(ReportViewerEvent.DismissVersions) },
            texts = texts,
        )
    }
}

/**
 * **Cartão de prova** do laudo: QR 72×72 (sempre preto no branco — é para ser lido por câmera), selo
 * "Assinado por N" em verde (ou o chip "Pendente" sem assinatura), "hash 9f2c…e1" em mono 14/500 e
 * "data · coordenadas" em mono 12 `muted`.
 */
@Composable
fun ProofCard(proof: ReportProof, texts: InspectionTexts, modifier: Modifier = Modifier) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(6.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(t.surface)
            .border(1.dp, t.line, forma)
            .padding(14.dp)
            .testTag(InspectionTestTags.REPORT_PROOF),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(2.dp)).background(InspectionTokens.PaperBackground)) {
            QrCodeView(
                value = proof.verifyUrl,
                size = 72.dp,
                foregroundColor = Color.Black,
                backgroundColor = InspectionTokens.PaperBackground,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (proof.signedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                ) {
                    InspectionIconView(InspectionIcon.Verified, filled = true, size = 20.dp, tint = t.good.fg)
                    Text(texts.report.signedBy.fmt(proof.signedCount), style = inspectionTextStyle(15.sp, FontWeight.Bold, t.good.fg))
                }
            } else {
                InspectionStateChip(SignatureStatus.PENDING, texts = texts)
            }
            ProofText(texts.report.hashShort.fmt(shortHash(proof.hash)), size = 14.sp, weight = FontWeight.Medium)
            val linha = listOfNotNull(proof.signedAtLabel, proof.locationLabel).joinToString(" · ")
            if (linha.isNotEmpty()) ProofText(linha, size = 12.sp, color = t.muted, lineHeight = 16.sp)
        }
    }
}

/** As versões do laudo (hash e data de cada uma) e "Criar nova versão (correção)". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportVersionsSheet(
    versions: List<ReportVersion>,
    onOpen: (String) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    AppModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                texts.report.versions,
                style = inspectionTextStyle(18.sp, FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
            versions.forEach { v ->
                HorizontalDivider(thickness = 1.dp, color = t.line)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onOpen(v.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(v.label, style = inspectionTextStyle(16.sp, FontWeight.SemiBold))
                    Column(Modifier.weight(1f)) {
                        ProofText(v.createdAtLabel, size = 12.sp, color = t.muted)
                        ProofText(texts.report.hashShort.fmt(shortHash(v.hash)), size = 12.sp, color = t.muted)
                    }
                    if (v.isCurrent) {
                        InspectionStateChip(br.com.codecacto.kmplib.inspection.theme.InspectionTone.BRAND, InspectionIcon.Check, texts.report.currentVersion)
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = t.line)
            InspectionSecondaryButton(
                text = texts.report.newVersion,
                onClick = onCreateNew,
                icon = InspectionIcon.EditNote,
                contentColor = t.brand,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}
