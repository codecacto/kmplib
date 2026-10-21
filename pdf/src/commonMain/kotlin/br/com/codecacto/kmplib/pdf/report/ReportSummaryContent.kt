package br.com.codecacto.kmplib.pdf.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
 * **A tela de Relatórios do app — DRE** (2.306.0). Desde 2.315.0 é o resumo genérico
 * ([ReportSummaryContent] com [ReportSummary]) do [DreSummary.toReportSummary]: o resultado em
 * destaque, os [DreSummary.highlights], o demonstrativo (id `relatorio-dre`), a abertura por eixo, as
 * notas e os dois botões. Mesma assinatura de antes.
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
    val generic = remember(summary, texts.dreTitle) { summary.toReportSummary(texts.dreTitle) }
    ReportSummaryContent(
        summary = generic,
        onOpenFullReport = onOpenFullReport,
        onExportPdf = onExportPdf,
        modifier = modifier,
        exporting = exporting,
        texts = texts,
    )
}

/**
 * **A tela de Relatórios do app, de qualquer relatório** (2.315.0), padrão da fábrica: o número
 * principal ([ReportSummary.hero]) ou o cabeçalho com título e período, até 4 destaques com variação,
 * as listas curtas, as notas e os dois botões — **Exportar PDF** e **Ver relatório completo**.
 *
 * Não rola nem puxa para atualizar sozinha: o app a põe dentro do `RefreshableBox` + `verticalScroll`
 * dele, abaixo do seletor de período que já tem (cada produto escolhe mês, dia ou intervalo).
 *
 * @param onOpenFullReport abre o [FullReportOnWebDialog]. `null` esconde o **Ver relatório completo**
 *   — para app SEM portal web. Sem default de propósito, para app com portal não esquecer o botão.
 * @param onExportPdf `null` esconde o botão (PDF indisponível naquele app). O PDF:
 *   [exportReportSummaryPdf].
 * @param exporting troca o ícone do botão por um progresso e o desabilita.
 */
@Composable
fun ReportSummaryContent(
    summary: ReportSummary,
    onOpenFullReport: (() -> Unit)?,
    onExportPdf: (() -> Unit)?,
    modifier: Modifier = Modifier,
    exporting: Boolean = false,
    texts: ReportSummaryTexts = rememberReportSummaryTexts(),
) {
    Column(modifier.fillMaxWidth().testTag("relatorio-resumo"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val hero = summary.hero
        if (hero != null) HeroCard(hero, summary.periodLabel) else ReportHeader(summary.title, summary.periodLabel)
        val highlights = summary.visibleHighlights
        if (highlights.isNotEmpty()) HighlightsGrid(highlights)
        summary.sections.forEachIndexed { index, section ->
            if (section.isVisible) ListSectionCard(section, section.testTag ?: reportSectionTestTag(index))
        }
        if (summary.notes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().testTag("relatorio-notas"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                summary.notes.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ReportSummaryActions(
            onOpenFullReport = onOpenFullReport,
            onExportPdf = onExportPdf,
            exporting = exporting,
            texts = texts,
        )
    }
}

/** Id da seção sem `testTag` próprio. */
internal fun reportSectionTestTag(index: Int): String = "relatorio-secao-$index"

@Composable
private fun ReportHeader(title: String, period: String) {
    Column(
        Modifier.fillMaxWidth().testTag("relatorio-cabecalho").semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCard(hero: ReportHighlight, period: String) {
    val cor = if (hero.tone == ReportTone.NEUTRAL) MaterialTheme.colorScheme.primary else toneColor(hero.tone)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("relatorio-resultado").semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cor.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hero.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(hero.value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cor)
            hero.change?.let { ChangeRow(it) }
            Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HighlightsGrid(highlights: List<ReportHighlight>) {
    // Dois por linha: em 360 dp, três cartões cortam "R$ 12.345,67".
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        highlights.chunked(2).forEachIndexed { row, par ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                par.forEachIndexed { col, h -> HighlightCard(h, row * 2 + col, Modifier.weight(1f).fillMaxHeight()) }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HighlightCard(h: ReportHighlight, index: Int, modifier: Modifier) {
    val cor = toneColor(h.tone)
    Card(
        modifier = modifier.testTag("relatorio-destaque-$index").semantics(mergeDescendants = true) {},
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
            h.change?.let { ChangeRow(it) }
        }
    }
}

/** Seta + texto da variação. A seta é decorativa; o leitor lê o texto (ou o `contentDescription`). */
@Composable
private fun ChangeRow(change: ReportChange) {
    val cor = if (change.tone == ReportTone.NEUTRAL) MaterialTheme.colorScheme.onSurfaceVariant else toneColor(change.tone)
    val icon = when (change.trend) {
        ReportTrend.UP -> Icons.AutoMirrored.Outlined.TrendingUp
        ReportTrend.DOWN -> Icons.AutoMirrored.Outlined.TrendingDown
        ReportTrend.FLAT -> Icons.AutoMirrored.Outlined.TrendingFlat
    }
    Row(
        // Sem merge próprio: o cartão em volta já junta tudo num foco só. Com `contentDescription`, a
        // frase do leitor substitui o texto curto ("+12%") dentro desse foco.
        change.contentDescription?.let { cd -> Modifier.clearAndSetSemantics { contentDescription = cd } } ?: Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = cor, modifier = Modifier.size(16.dp))
        Text(change.text, style = MaterialTheme.typography.labelSmall, color = cor, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun ListSectionCard(section: ReportListSection, tag: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (section.items.isEmpty() && section.total == null) {
                section.emptyText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            section.items.forEach { item ->
                if (item.style == ReportLineStyle.SUBTOTAL) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListRow(item)
            }
            section.total?.let { total ->
                if (section.items.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        total.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Text(total.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = toneColor(total.tone))
                }
            }
        }
    }
}

@Composable
private fun ListRow(item: ReportListItem) {
    val recuada = item.style == ReportLineStyle.INDENTED || item.style == ReportLineStyle.MUTED_INDENTED
    val muted = item.style == ReportLineStyle.MUTED_INDENTED
    val peso = when (item.style) {
        ReportLineStyle.STRONG -> FontWeight.Bold
        ReportLineStyle.SUBTOTAL -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    val corDoRotulo = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val corDoValor = when {
        item.tone != ReportTone.NEUTRAL -> toneColor(item.tone)
        muted -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = if (recuada) 12.dp else 0.dp, end = 12.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium, fontWeight = peso, color = corDoRotulo)
            item.supporting?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(item.value, style = MaterialTheme.typography.bodyMedium, fontWeight = peso, color = corDoValor)
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
