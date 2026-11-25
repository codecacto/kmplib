package br.com.codecacto.kmplib.fiscal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.fiscal.generated.resources.*
import br.com.codecacto.kmplib.platform.FileOpenResult
import br.com.codecacto.kmplib.platform.getFileOpener
import br.com.codecacto.kmplib.platform.getShareHandler
import br.com.codecacto.kmplib.ui.components.AppBanner
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppDialog
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.AppTextArea
import br.com.codecacto.kmplib.ui.components.BackTopBar
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.StatusTone

/**
 * Detalhe de uma nota: situação, número, chave, valores, recusa da Receita, histórico; PDF/XML abrem no visualizador
 * do sistema (sem visualizador, a folha de compartilhar); Cancelar pede motivo + justificativa (15+ caracteres) e
 * confirma; Substituir chama [onSubstitute]. Enquanto a nota não decide, a tela consulta sozinha.
 */
@Composable
fun FiscalInvoiceDetailScreen(
    repository: FiscalRepository,
    invoiceId: String,
    onBack: () -> Unit,
    onSubstitute: ((FiscalDocumento) -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenInvoice: ((String) -> Unit)? = null,
) {
    val vm = viewModel(key = "kmplib-fiscal-nota-$invoiceId") { FiscalInvoiceDetailViewModel(repository, invoiceId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val cancelled = fiscalString(Res.string.fiscal_nota_cancelada)
    val reconsulted = fiscalString(Res.string.fiscal_reconsultado)
    val noApp = fiscalString(Res.string.fiscal_erro_abrir_arquivo)
    LaunchedEffect(vm) {
        vm.effect.collect { e ->
            when (e) {
                is FiscalInvoiceDetailEffect.OpenFile -> {
                    when (getFileOpener().open(e.file.bytes, e.file.nome, e.file.mimeType)) {
                        FileOpenResult.Opened -> Unit
                        // Sem visualizador no aparelho: compartilhar (salvar, mandar por e-mail/WhatsApp).
                        FileOpenResult.NoAppAvailable, FileOpenResult.Failed ->
                            runCatching { getShareHandler().shareFile(e.file.bytes, e.file.nome, e.file.mimeType) }
                                .onFailure { snackbar.showSnackbar(noApp) }
                    }
                }
                FiscalInvoiceDetailEffect.Cancelled -> snackbar.showSnackbar(cancelled)
                FiscalInvoiceDetailEffect.Reconsulted -> snackbar.showSnackbar(reconsulted)
            }
        }
    }
    val inv = state.invoice
    Scaffold(
        modifier = modifier.testTag(FiscalTestTags.DETALHE),
        topBar = {
            BackTopBar(
                title = inv?.numeroNfse?.let { fiscalString(Res.string.fiscal_detalhe_titulo, it) } ?: fiscalString(Res.string.fiscal_detalhe_titulo_sem_numero),
                onBackClick = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(inner), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp)); CircularProgressIndicator()
            }
            state.error != null || inv == null -> ErrorState(
                message = state.error?.let { fiscalErrorMessage(it) } ?: fiscalString(Res.string.fiscal_erro_carregar),
                onRetry = { vm.onAction(FiscalInvoiceDetailAction.Load) },
                modifier = Modifier.padding(inner),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiscalStatusBadge(inv.status)
                    if (inv.ambiente == FiscalAmbiente.PRODUCAO_RESTRITA) {
                        Text(fiscalString(Res.string.fiscal_nota_de_teste), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isPendingStatus(inv.status)) {
                    Text(
                        fiscalString(if (inv.status == FiscalStatus.INCERTO) Res.string.fiscal_andamento_incerto else Res.string.fiscal_andamento_processando),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        inv.numeroNfse?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_numero_nfse), it, strong = true) }
                        inv.valores?.tomadorNome?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_tomador), it) }
                        inv.valores?.tomadorDocumento?.let { FiscalInfoRow(takerDocLabel(it), formatDocument(it)) }
                        FiscalInfoRow(fiscalString(Res.string.fiscal_valor_total), formatFiscalMoney(inv.valores?.servico), strong = true)
                        inv.valores?.iss?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_valor_iss), formatFiscalMoney(it)) }
                        inv.valores?.liquido?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_valor_liquido), formatFiscalMoney(it)) }
                        formatFiscalDateTime(inv.criadoEm)?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_enviada_em), it) }
                        formatFiscalDateTime(inv.autorizadoEm)?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_autorizada_em), it) }
                        inv.chave?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_chave_acesso), it) }
                    }
                }
                if (inv.erros.isNotEmpty()) {
                    AppBanner(
                        title = fiscalString(Res.string.fiscal_erros_nota),
                        message = inv.erros.joinToString("\n") { messageText(it) },
                        tone = StatusTone.DANGER,
                    )
                }
                inv.substituiId?.let { id ->
                    LinkRow(fiscalString(Res.string.fiscal_substitui_nota), onOpenInvoice?.let { { it(id) } })
                }
                inv.substituidoPorId?.let { id ->
                    LinkRow(fiscalString(Res.string.fiscal_substituida_por), onOpenInvoice?.let { { it(id) } })
                }
                state.actionError?.let { AppBanner(message = fiscalErrorMessage(it), tone = StatusTone.DANGER, onDismiss = { vm.onAction(FiscalInvoiceDetailAction.DismissActionError) }) }
                if (hasInvoiceFiles(inv.status)) {
                    AppButton(
                        text = fiscalString(Res.string.fiscal_abrir_pdf),
                        onClick = { vm.onAction(FiscalInvoiceDetailAction.Download(FiscalFileKind.PDF)) },
                        isLoading = state.downloading == FiscalFileKind.PDF,
                        icon = Icons.Outlined.PictureAsPdf,
                        modifier = Modifier.testTag(FiscalTestTags.DETALHE_PDF),
                    )
                    AppOutlinedButton(
                        text = fiscalString(Res.string.fiscal_abrir_xml),
                        onClick = { vm.onAction(FiscalInvoiceDetailAction.Download(FiscalFileKind.XML)) },
                        isLoading = state.downloading == FiscalFileKind.XML,
                        icon = Icons.Outlined.Code,
                        modifier = Modifier.testTag(FiscalTestTags.DETALHE_XML),
                    )
                }
                if (canReconsultInvoice(inv.status)) {
                    AppOutlinedButton(
                        text = fiscalString(Res.string.fiscal_reconsultar),
                        onClick = { vm.onAction(FiscalInvoiceDetailAction.Reconsult) },
                        isLoading = state.reconsulting,
                        modifier = Modifier.testTag(FiscalTestTags.DETALHE_RECONSULTAR),
                    )
                }
                if (canSubstituteInvoice(inv.status) && onSubstitute != null) {
                    AppOutlinedButton(
                        text = fiscalString(Res.string.fiscal_substituir),
                        onClick = { onSubstitute(inv) },
                        modifier = Modifier.testTag(FiscalTestTags.DETALHE_SUBSTITUIR),
                    )
                }
                if (canCancelInvoice(inv.status)) {
                    AppOutlinedButton(
                        text = fiscalString(Res.string.fiscal_cancelar_nota),
                        onClick = { vm.onAction(FiscalInvoiceDetailAction.AskCancel) },
                        primaryColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(FiscalTestTags.DETALHE_CANCELAR),
                    )
                }
                if (inv.eventos.isNotEmpty()) {
                    FiscalSectionTitle(fiscalString(Res.string.fiscal_historico))
                    inv.eventos.forEach { ev ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                listOfNotNull(ev.tipo, ev.status?.let { s -> fiscalStatusLabel(s) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            )
                            listOfNotNull(formatFiscalDateTime(ev.criadoEm), ev.descricao).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ev.erros.forEach { m -> Text(messageText(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    CancelDialog(state, vm::onAction)
}

@Composable
private fun takerDocLabel(doc: String): String =
    if (documentDigits(doc).length == 14) fiscalString(Res.string.fiscal_cnpj) else fiscalString(Res.string.fiscal_cpf)

@Composable
private fun LinkRow(text: String, onClick: (() -> Unit)?) {
    if (onClick == null) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    } else {
        TextButton(onClick = onClick) { Text(text) }
    }
}

@Composable
private fun CancelDialog(state: FiscalInvoiceDetailState, on: (FiscalInvoiceDetailAction) -> Unit) {
    AppDialog(
        show = state.showCancel,
        onDismiss = { if (!state.cancelling) on(FiscalInvoiceDetailAction.DismissCancel) },
        title = fiscalString(Res.string.fiscal_cancelar_pergunta),
        dismissOnClickOutside = !state.cancelling,
        dismissOnBackPress = !state.cancelling,
    ) {
        Text(fiscalString(Res.string.fiscal_cancelar_desc), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text(fiscalString(Res.string.fiscal_motivo), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.selectableGroup()) {
            MotivoCancelamento.entries.forEach { m ->
                ChoiceRow(state.cancelReason == m, cancelReasonLabel(m), { on(FiscalInvoiceDetailAction.SetCancelReason(m)) })
            }
        }
        AppTextArea(
            value = state.cancelText,
            onValueChange = { on(FiscalInvoiceDetailAction.SetCancelText(it)) },
            label = fiscalString(Res.string.fiscal_descricao_motivo),
            errorMessage = state.cancelError?.let { e -> if (e is FiscalFieldError.Issue) issueText(e.issue) else (e as FiscalFieldError.Text).text },
            showCharCounter = false,
            modifier = Modifier.fillMaxWidth(),
        )
        state.actionError?.let { Text(fiscalErrorMessage(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { on(FiscalInvoiceDetailAction.DismissCancel) }, enabled = !state.cancelling) { Text(fiscalString(Res.string.fiscal_voltar)) }
            Spacer(Modifier.height(0.dp))
            AppButton(
                text = fiscalString(Res.string.fiscal_cancelar_nota),
                onClick = { on(FiscalInvoiceDetailAction.ConfirmCancel) },
                isLoading = state.cancelling,
                primaryColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f).testTag("fiscal-nota-cancelar-confirmar"),
            )
        }
    }
}
