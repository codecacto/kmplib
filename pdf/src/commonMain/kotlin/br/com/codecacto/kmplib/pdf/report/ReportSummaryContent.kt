package br.com.codecacto.kmplib.pdf.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_report_dre_title
import br.com.codecacto.kmplib.generated.resources.kmplib_report_export_pdf
import br.com.codecacto.kmplib.generated.resources.kmplib_report_full
import br.com.codecacto.kmplib.generated.resources.kmplib_report_highlights
import br.com.codecacto.kmplib.generated.resources.kmplib_report_pdf_column_line
import br.com.codecacto.kmplib.generated.resources.kmplib_report_pdf_column_value
import br.com.codecacto.kmplib.generated.resources.kmplib_report_pdf_generated_at
import br.com.codecacto.kmplib.generated.resources.kmplib_report_web_body
import br.com.codecacto.kmplib.generated.resources.kmplib_report_web_dismiss
import br.com.codecacto.kmplib.generated.resources.kmplib_report_web_open
import br.com.codecacto.kmplib.generated.resources.kmplib_report_web_title
import br.com.codecacto.kmplib.ui.components.AppAlertDialog
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import br.com.codecacto.kmplib.ui.theme.AppColors

/**
 * Textos da tela de Relatórios do app e do PDF do DRE. O default é pt-BR; [rememberReportSummaryTexts]
 * devolve no idioma do aparelho (pt-BR, en, es, pt-PT).
 */
@Immutable
data class ReportSummaryTexts(
    val dreTitle: String = "Demonstrativo de resultado",
    val highlightsTitle: String = "Destaques do período",
    val exportPdf: String = "Exportar PDF",
    val fullReport: String = "Ver relatório completo",
    val webTitle: String = "Relatório completo no portal",
    /** Recebe o endereço do portal ("locasys-portal.codecacto.com.br"). */
    val webBody: (portal: String) -> String = {
        "Os relatórios completos, com filtros e detalhes, ficam no portal. No computador, acesse $it " +
            "e entre com o mesmo usuário e senha do aplicativo."
    },
    val webOpen: String = "Abrir o portal",
    val webDismiss: String = "Agora não",
    val pdfColumnLine: String = "Descrição",
    val pdfColumnValue: String = "Valor",
    /** Rodapé do PDF; recebe a data/hora já formatada. */
    val generatedAt: (whenText: String) -> String = { "Gerado em $it" },
)

/** [ReportSummaryTexts] no idioma do aparelho, lembrado pelas próprias frases. */
@Composable
fun rememberReportSummaryTexts(): ReportSummaryTexts {
    val dre = kmpStringResource(Res.string.kmplib_report_dre_title)
    val destaques = kmpStringResource(Res.string.kmplib_report_highlights)
    val pdf = kmpStringResource(Res.string.kmplib_report_export_pdf)
    val completo = kmpStringResource(Res.string.kmplib_report_full)
    val webTitulo = kmpStringResource(Res.string.kmplib_report_web_title)
    val webCorpo = kmpStringResource(Res.string.kmplib_report_web_body)
    val webAbrir = kmpStringResource(Res.string.kmplib_report_web_open)
    val webAgoraNao = kmpStringResource(Res.string.kmplib_report_web_dismiss)
    val colunaLinha = kmpStringResource(Res.string.kmplib_report_pdf_column_line)
    val colunaValor = kmpStringResource(Res.string.kmplib_report_pdf_column_value)
    val geradoEm = kmpStringResource(Res.string.kmplib_report_pdf_generated_at)
    return remember(dre, destaques, pdf, completo, webTitulo, webCorpo, webAbrir, webAgoraNao, colunaLinha, colunaValor, geradoEm) {
        ReportSummaryTexts(
            dreTitle = dre,
            highlightsTitle = destaques,
            exportPdf = pdf,
            fullReport = completo,
            webTitle = webTitulo,
            webBody = { portal -> webCorpo.replace("%1\$s", portal) },
            webOpen = webAbrir,
            webDismiss = webAgoraNao,
            pdfColumnLine = colunaLinha,
            pdfColumnValue = colunaValor,
            generatedAt = { quando -> geradoEm.replace("%1\$s", quando) },
        )
    }
}

/**
 * **A tela de Relatórios do app**, padrão da fábrica: o resultado do período em destaque, os
 * [DreSummary.highlights], o demonstrativo, as notas e os dois botões — **Exportar PDF** e **Ver
 * relatório completo**.
 *
 * Não rola nem puxa para atualizar sozinha: o app a põe dentro do `RefreshableBox` + `verticalScroll`
 * dele, abaixo do seletor de período que já tem (cada produto escolhe mês, dia ou intervalo).
 *
 * @param onOpenFullReport abre o [FullReportOnWebDialog]. `null` esconde o **Ver relatório completo**
 *   (2.309.0) — para app SEM portal web (offline, com o financeiro só no aparelho), onde o botão não
 *   teria destino. Passe `null` explicitamente: o parâmetro não tem default de propósito, para app com
 *   portal não esquecer o botão.
 * @param onExportPdf `null` esconde o botão (PDF indisponível naquele app).
 * @param exporting troca o ícone do botão por um progresso e o desabilita.
 */
@Composable
fun ReportSummaryContent(
    summary: DreSummary,
    onOpenFullReport: (() -> Unit)?,
    onExportPdf: (() -> Unit)?,
    modifier: Modifier = Modifier,
    exporting: Boolean = false,
    texts: ReportSummaryTexts = rememberReportSummaryTexts(),
) {
    Column(modifier.fillMaxWidth().testTag("relatorio-resumo"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ResultHero(summary)
        if (summary.highlights.isNotEmpty()) HighlightsGrid(summary.highlights)
        DreStatementCard(summary, title = texts.dreTitle)
        ReportSummaryActions(
            onOpenFullReport = onOpenFullReport,
            onExportPdf = onExportPdf,
            exporting = exporting,
            texts = texts,
        )
    }
}

@Composable
private fun ResultHero(summary: DreSummary) {
    val cor = if (summary.result.negative) MaterialTheme.colorScheme.error else AppColors.current.success
    Card(
        modifier = Modifier.fillMaxWidth().testTag("relatorio-resultado"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cor.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                summary.result.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                summary.result.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = cor,
            )
            Text(
                summary.periodLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HighlightsGrid(highlights: List<ReportHighlight>) {
    // Dois por linha: em 360 dp, três cartões cortam "R$ 12.345,67".
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        highlights.chunked(2).forEach { par ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                par.forEach { HighlightCard(it, Modifier.weight(1f)) }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HighlightCard(h: ReportHighlight, modifier: Modifier) {
    val cor = toneColor(h.tone)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                h.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(h.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cor)
        }
    }
}

@Composable
private fun toneColor(tone: ReportTone): Color = when (tone) {
    ReportTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    ReportTone.POSITIVE -> AppColors.current.success
    ReportTone.NEGATIVE -> MaterialTheme.colorScheme.error
    ReportTone.WARNING -> AppColors.current.warning
}

@Composable
private fun DreStatementCard(summary: DreSummary, title: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("relatorio-dre"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            summary.lines.forEach { line ->
                if (line.kind == DreLineKind.SUBTOTAL || line.kind == DreLineKind.TOTAL) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                StatementRow(line)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.result.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Text(
                    summary.result.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (summary.result.negative) MaterialTheme.colorScheme.error else AppColors.current.success,
                )
            }
            if (summary.breakdown.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                summary.breakdownTitle?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                summary.breakdown.forEach { StatementRow(it) }
            }
            summary.notes.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatementRow(line: DreSummaryLine) {
    val recuada = line.kind == DreLineKind.DEDUCTION || line.kind == DreLineKind.ITEM
    val peso = when (line.kind) {
        DreLineKind.REVENUE -> FontWeight.Bold
        DreLineKind.SUBTOTAL, DreLineKind.TOTAL -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val corDoRotulo =
        if (line.kind == DreLineKind.DEDUCTION) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val corDoValor = when {
        line.negative -> MaterialTheme.colorScheme.error
        line.kind == DreLineKind.DEDUCTION -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            line.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = peso,
            color = corDoRotulo,
            modifier = Modifier.weight(1f).padding(start = if (recuada) 12.dp else 0.dp, end = 12.dp),
        )
        Text(line.value, style = MaterialTheme.typography.bodyMedium, fontWeight = peso, color = corDoValor)
    }
}

/**
 * Os dois botões do pé da tela de Relatórios. Cada um some com o seu callback `null`
 * ([onOpenFullReport] desde 2.309.0); sem nenhum dos dois, não desenha nada.
 */
@Composable
fun ReportSummaryActions(
    onOpenFullReport: (() -> Unit)?,
    onExportPdf: (() -> Unit)?,
    modifier: Modifier = Modifier,
    exporting: Boolean = false,
    texts: ReportSummaryTexts = rememberReportSummaryTexts(),
) {
    if (!reportSummaryShowsActions(onOpenFullReport != null, onExportPdf != null)) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onExportPdf != null) {
            OutlinedButton(
                onClick = onExportPdf,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("relatorio-exportar-pdf"),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (exporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(texts.exportPdf, fontWeight = FontWeight.SemiBold)
            }
        }
        if (onOpenFullReport != null) {
            Button(
                onClick = onOpenFullReport,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("relatorio-ver-completo"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(texts.fullReport, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Há botão a desenhar no pé do resumo? Sem nenhum, a coluna (e o seu espaçamento) não entra. */
internal fun reportSummaryShowsActions(hasFullReport: Boolean, hasExportPdf: Boolean): Boolean =
    hasFullReport || hasExportPdf

/**
 * O diálogo do **Ver relatório completo**: explica que o completo fica no portal e que se entra com o
 * mesmo usuário e senha do app; **Abrir o portal** chama [onOpen] (o app abre a URL no navegador).
 *
 * @param portal o endereço como a pessoa digitaria no computador, sem `https://`.
 */
@Composable
fun FullReportOnWebDialog(
    show: Boolean,
    portal: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    texts: ReportSummaryTexts = rememberReportSummaryTexts(),
) {
    if (!show) return
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
        title = { Text(texts.webTitle) },
        text = { Text(texts.webBody(portal)) },
        confirmButton = {
            TextButton(onClick = onOpen, modifier = Modifier.testTag("relatorio-abrir-portal")) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(texts.webOpen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(texts.webDismiss) } },
    )
}
