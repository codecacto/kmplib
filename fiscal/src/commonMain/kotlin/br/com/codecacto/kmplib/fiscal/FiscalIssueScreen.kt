package br.com.codecacto.kmplib.fiscal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.fiscal.generated.resources.*
import br.com.codecacto.kmplib.ui.components.AppBanner
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppDatePicker
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.AppPickerField
import br.com.codecacto.kmplib.ui.components.AppTextArea
import br.com.codecacto.kmplib.ui.components.BackTopBar
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.PickerOption
import br.com.codecacto.kmplib.ui.components.StatusTone
import br.com.codecacto.kmplib.ui.components.UnsavedChangesGuard
import br.com.codecacto.kmplib.ui.components.rememberUnsavedChangesGuard
import kotlinx.datetime.LocalDate

/**
 * Emitir (ou substituir) uma NFS-e — tela inteira (formulário no celular nunca é folha de baixo). Abre já preenchida
 * com o tomador, a descrição, o valor e a competência que o produto sugere para [refExterna] (ex. `locacao:123`);
 * "Ver prévia" mostra os valores calculados pelo serviço; "Emitir" envia com uma `Idempotency-Key` desta abertura e a
 * tela passa a acompanhar a nota até ela ser autorizada ou recusada. Sair com algo preenchido pergunta antes.
 *
 * @param substituteId id da nota a substituir (modo substituir; pede o motivo).
 * @param onOpenInvoice "Ver a nota" depois de aceita.
 * @param onCompleteSetup leva ao cadastro fiscal quando ele ainda não está completo; `null` esconde o botão.
 */
@Composable
fun FiscalIssueScreen(
    repository: FiscalRepository,
    onBack: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    modifier: Modifier = Modifier,
    refExterna: String? = null,
    substituteId: String? = null,
    onCompleteSetup: (() -> Unit)? = null,
    onAccepted: ((String) -> Unit)? = null,
) {
    val vm = viewModel(key = "kmplib-fiscal-emitir-${refExterna.orEmpty()}-${substituteId.orEmpty()}") {
        FiscalIssueViewModel(repository, refExterna = refExterna, substituteId = substituteId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnAccepted = onAccepted
    androidx.compose.runtime.LaunchedEffect(vm) {
        vm.effect.collect { e -> if (e is FiscalIssueEffect.Accepted) currentOnAccepted?.invoke(e.id) }
    }
    val guard = rememberUnsavedChangesGuard(dirty = state.dirty)
    UnsavedChangesGuard(guard, onBack = onBack)
    val title = state.substitute?.let { fiscalString(Res.string.fiscal_substituir_titulo, it.numeroNfse.orEmpty()) }
        ?: fiscalString(Res.string.fiscal_emitir_titulo)
    val inForm = state.phase == FiscalIssuePhase.Form

    Scaffold(
        modifier = modifier.testTag(FiscalTestTags.EMITIR),
        topBar = { BackTopBar(title = title, onBackClick = { guard.requestExit(onBack) }) },
        bottomBar = {
            if (!state.loading && state.loadError == null && inForm) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.banner?.let { AppBanner(message = fiscalErrorMessage(it), tone = StatusTone.DANGER) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppOutlinedButton(
                                text = fiscalString(if (state.previewing) Res.string.fiscal_calculando else Res.string.fiscal_ver_previa),
                                onClick = { vm.onAction(FiscalIssueAction.Preview) },
                                isLoading = state.previewing,
                                modifier = Modifier.weight(1f).testTag(FiscalTestTags.EMITIR_PREVIA),
                            )
                            AppButton(
                                text = fiscalString(
                                    when {
                                        state.sending -> Res.string.fiscal_emitindo
                                        state.isSubstitution -> Res.string.fiscal_substituir_nota
                                        else -> Res.string.fiscal_emitir_nota
                                    },
                                ),
                                onClick = { vm.onAction(FiscalIssueAction.Submit) },
                                isLoading = state.sending,
                                modifier = Modifier.weight(1f).testTag(FiscalTestTags.EMITIR_ENVIAR),
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(inner), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp)); CircularProgressIndicator()
            }
            state.loadError != null -> ErrorState(
                message = fiscalErrorMessage(state.loadError!!),
                onRetry = { vm.onAction(FiscalIssueAction.Load) },
                modifier = Modifier.padding(inner),
            )
            state.phase is FiscalIssuePhase.Progress -> ProgressContent(
                phase = state.phase as FiscalIssuePhase.Progress,
                modifier = Modifier.padding(inner),
                onFix = { vm.onAction(FiscalIssueAction.FixAfterRejection) },
                onOpenInvoice = onOpenInvoice,
                onClose = onBack,
            )
            else -> Column(
                Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.emitterReady) {
                    AppBanner(
                        message = fiscalString(Res.string.fiscal_emissor_nao_pronto),
                        tone = StatusTone.WARNING,
                        action = onCompleteSetup?.let { go ->
                            { androidx.compose.material3.TextButton(onClick = go) { Text(fiscalString(Res.string.fiscal_completar_cadastro)) } }
                        },
                    )
                }
                IssueForm(state, vm::onAction)
            }
        }
    }
}

@Composable
private fun IssueForm(state: FiscalIssueState, on: (FiscalIssueAction) -> Unit) {
    val d = state.draft
    val e = state.errors
    fun edit(f: (FiscalInvoiceDraft) -> FiscalInvoiceDraft) = on(FiscalIssueAction.Edit(f))

    // ── Tomador ──
    FiscalSectionTitle(fiscalString(Res.string.fiscal_tomador))
    AppPickerField(
        value = d.tipoTomador.name,
        onValueChange = { v -> edit { it.copy(tipoTomador = TipoTomador.valueOf(v), tomadorDocumento = "") } },
        options = TipoTomador.entries.map { PickerOption(it.name, takerTypeLabel(it)) },
        label = fiscalString(Res.string.fiscal_tipo_tomador),
    )
    if (d.tipoTomador != TipoTomador.NAO_IDENTIFICADO) {
        when (d.tipoTomador) {
            TipoTomador.CPF, TipoTomador.CNPJ -> FiscalTextField(
                value = d.tomadorDocumento, onValueChange = { v -> edit { it.copy(tomadorDocumento = v) } },
                label = fiscalString(if (d.tipoTomador == TipoTomador.CNPJ) Res.string.fiscal_cnpj else Res.string.fiscal_cpf),
                mask = if (d.tipoTomador == TipoTomador.CNPJ) FiscalMask.CNPJ else FiscalMask.CPF,
                error = e.textOf("tomador.documento"), testTag = "fiscal-emitir-input-documento",
            )
            else -> {
                FiscalTextField(value = d.tomadorNif, onValueChange = { v -> edit { it.copy(tomadorNif = v) } }, label = fiscalString(Res.string.fiscal_nif), error = e.textOf("tomador.nif"))
                FiscalTextField(
                    value = d.tomadorPais, onValueChange = { v -> edit { it.copy(tomadorPais = v.uppercase().take(2)) } },
                    label = fiscalString(Res.string.fiscal_pais_tomador), error = e.textOf("tomador.endereco.pais"),
                )
            }
        }
        FiscalTextField(value = d.tomadorNome, onValueChange = { v -> edit { it.copy(tomadorNome = v) } }, label = fiscalString(Res.string.fiscal_nome), error = e.textOf("tomador.nome"), testTag = "fiscal-emitir-input-nome")
        FiscalTextField(value = d.tomadorEmail, onValueChange = { v -> edit { it.copy(tomadorEmail = v) } }, label = fiscalString(Res.string.fiscal_email), keyboardType = KeyboardType.Email, error = e.textOf("tomador.email"))
        if (d.tipoTomador == TipoTomador.ESTRANGEIRO) {
            FiscalTextField(value = d.tomadorTelefone, onValueChange = { v -> edit { it.copy(tomadorTelefone = v) } }, label = fiscalString(Res.string.fiscal_telefone), keyboardType = KeyboardType.Phone, error = e.textOf("tomador.telefone"))
        } else {
            FiscalTextField(value = d.tomadorTelefone, onValueChange = { v -> edit { it.copy(tomadorTelefone = v) } }, label = fiscalString(Res.string.fiscal_whatsapp), mask = FiscalMask.PHONE, error = e.textOf("tomador.telefone"))
            AddressBlock(
                title = fiscalString(Res.string.fiscal_endereco_tomador),
                value = d.tomadorEndereco,
                onChange = { a -> edit { it.copy(tomadorEndereco = a) } },
                errors = e,
                prefix = "tomador.endereco",
            )
        }
    }

    // ── Serviço ──
    FiscalSectionTitle(fiscalString(Res.string.fiscal_servico))
    FiscalTextField(
        value = d.codigoTributacaoNacional, onValueChange = { v -> edit { it.copy(codigoTributacaoNacional = v.take(6)) } },
        label = fiscalString(Res.string.fiscal_codigo_servico), mask = FiscalMask.DIGITS, error = e.textOf("servico.codigoTributacaoNacional"),
    )
    AppTextArea(
        value = d.descricao, onValueChange = { v -> edit { it.copy(descricao = v) } },
        label = fiscalString(Res.string.fiscal_descricao_servico), errorMessage = e.textOf("servico.descricao"),
        showCharCounter = false, modifier = Modifier.fillMaxWidth().testTag("fiscal-emitir-input-descricao"),
    )
    AppDatePicker(
        selectedDate = runCatching { LocalDate.parse(d.competencia) }.getOrNull(),
        onDateSelected = { date -> edit { it.copy(competencia = date.toString()) } },
        label = fiscalString(Res.string.fiscal_competencia),
        errorMessage = e.textOf("competencia"),
        modifier = Modifier.fillMaxWidth(),
    )

    // ── Valores ──
    FiscalSectionTitle(fiscalString(Res.string.fiscal_valores))
    FiscalTextField(
        value = d.valorServico, onValueChange = { v -> edit { it.copy(valorServico = v) } },
        label = fiscalString(Res.string.fiscal_valor_servico), keyboardType = KeyboardType.Decimal,
        error = e.textOf("valores.servico"), testTag = "fiscal-emitir-input-valor",
    )
    if (d.pedeTotaisEmValor) {
        Text(fiscalString(Res.string.fiscal_totais_titulo), style = MaterialTheme.typography.labelLarge)
        Text(fiscalString(Res.string.fiscal_totais_nota_dica), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val k = "valores.totaisAproximados"
        FiscalTextField(d.totaisFederal, { v -> edit { it.copy(totaisFederal = v) } }, fiscalString(Res.string.fiscal_totais_federal_valor), keyboardType = KeyboardType.Decimal, error = e.textOf("$k.federal"))
        FiscalTextField(d.totaisEstadual, { v -> edit { it.copy(totaisEstadual = v) } }, fiscalString(Res.string.fiscal_totais_estadual_valor), keyboardType = KeyboardType.Decimal, error = e.textOf("$k.estadual"))
        FiscalTextField(d.totaisMunicipal, { v -> edit { it.copy(totaisMunicipal = v) } }, fiscalString(Res.string.fiscal_totais_municipal_valor), keyboardType = KeyboardType.Decimal, error = e.textOf("$k.municipal"))
    }

    // ── Avançado (recolhido) ──
    Row(
        Modifier.fillMaxWidth().clickable { on(FiscalIssueAction.ToggleAdvanced(!state.advancedOpen)) }.padding(vertical = 8.dp)
            .testTag("fiscal-emitir-avancado"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fiscalString(Res.string.fiscal_impostos_retencoes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(if (state.advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
    }
    if (state.advancedOpen) {
        AppPickerField(
            value = d.tributacaoIss.name,
            onValueChange = { v -> edit { it.copy(tributacaoIss = TributacaoIss.valueOf(v)) } },
            options = TributacaoIss.entries.map { PickerOption(it.name, issTaxationLabel(it)) },
            label = fiscalString(Res.string.fiscal_tributacao_iss),
        )
        FiscalTextField(
            d.aliquotaIss, { v -> edit { it.copy(aliquotaIss = v) } }, fiscalString(Res.string.fiscal_aliquota_iss),
            keyboardType = KeyboardType.Decimal, helper = fiscalString(Res.string.fiscal_aliquota_do_municipio), error = e.textOf("valores.iss.aliquota"),
        )
        SwitchRow(fiscalString(Res.string.fiscal_iss_retido), d.issRetido) { v -> edit { it.copy(issRetido = v) } }
        FiscalTextField(d.descontoIncondicionado, { v -> edit { it.copy(descontoIncondicionado = v) } }, fiscalString(Res.string.fiscal_desconto_incondicionado), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.descontoIncondicionado"))
        FiscalTextField(d.descontoCondicionado, { v -> edit { it.copy(descontoCondicionado = v) } }, fiscalString(Res.string.fiscal_desconto_condicionado), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.descontoCondicionado"))
        FiscalTextField(d.deducoes, { v -> edit { it.copy(deducoes = v) } }, fiscalString(Res.string.fiscal_deducoes), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.deducoes"))
        FiscalTextField(d.pis, { v -> edit { it.copy(pis = v) } }, fiscalString(Res.string.fiscal_pis), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.federais.pis"))
        FiscalTextField(d.cofins, { v -> edit { it.copy(cofins = v) } }, fiscalString(Res.string.fiscal_cofins), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.federais.cofins"))
        FiscalTextField(d.csll, { v -> edit { it.copy(csll = v) } }, fiscalString(Res.string.fiscal_csll), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.federais.csll"))
        FiscalTextField(d.irrf, { v -> edit { it.copy(irrf = v) } }, fiscalString(Res.string.fiscal_irrf), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.federais.irrf"))
        FiscalTextField(d.cp, { v -> edit { it.copy(cp = v) } }, fiscalString(Res.string.fiscal_cp), keyboardType = KeyboardType.Decimal, error = e.textOf("valores.federais.cp"))
        SwitchRow(fiscalString(Res.string.fiscal_pis_cofins_retidos), d.pisCofinsRetidos) { v -> edit { it.copy(pisCofinsRetidos = v) } }
        FiscalTextField(d.codigoTributacaoMunicipal, { v -> edit { it.copy(codigoTributacaoMunicipal = v) } }, fiscalString(Res.string.fiscal_codigo_municipal), error = e.textOf("servico.codigoTributacaoMunicipal"))
        FiscalTextField(d.nbs, { v -> edit { it.copy(nbs = v) } }, fiscalString(Res.string.fiscal_nbs), mask = FiscalMask.DIGITS, error = e.textOf("servico.nbs"))
        AppTextArea(
            value = d.informacoesComplementares, onValueChange = { v -> edit { it.copy(informacoesComplementares = v) } },
            label = fiscalString(Res.string.fiscal_info_complementares), errorMessage = e.textOf("servico.informacoesComplementares"),
            showCharCounter = false, minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
    }
    if (d.tipoTomador != TipoTomador.NAO_IDENTIFICADO) {
        SwitchRow(fiscalString(Res.string.fiscal_enviar_tomador), d.enviarParaTomador) { v -> edit { it.copy(enviarParaTomador = v) } }
    }

    // ── Motivo (substituição) ──
    if (state.isSubstitution) {
        FiscalSectionTitle(fiscalString(Res.string.fiscal_motivo_substituicao))
        Column(Modifier.selectableGroup()) {
            MotivoCancelamento.entries.forEach { m -> ChoiceRow(state.reason == m, cancelReasonLabel(m), { on(FiscalIssueAction.SetReason(m)) }) }
        }
        AppTextArea(
            value = state.reasonText, onValueChange = { on(FiscalIssueAction.SetReasonText(it)) },
            label = fiscalString(Res.string.fiscal_descricao_motivo), errorMessage = e.textOf(FiscalIssueViewModel.REASON_FIELD),
            showCharCounter = false, minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
    }

    state.preview?.let { PreviewCard(it) }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PreviewCard(p: NfsePrevia) {
    OutlinedCard(Modifier.fillMaxWidth().testTag("fiscal-emitir-previa")) {
        Column(Modifier.padding(12.dp)) {
            Text(fiscalString(Res.string.fiscal_previa_titulo), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            p.valores?.let { v ->
                FiscalInfoRow(fiscalString(Res.string.fiscal_valor_total), formatFiscalMoney(v.servico))
                v.baseCalculo?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_base_calculo), formatFiscalMoney(it)) }
                v.iss?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_valor_iss), formatFiscalMoney(it) + (v.aliquotaIss?.let { a -> " (${formatRate(a)}%)" } ?: "")) }
                v.retencoesFederais?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_retencoes_federais), formatFiscalMoney(it)) }
                v.liquido?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_valor_liquido), formatFiscalMoney(it), strong = true) }
            }
            val loose = p.erros.filter { it.field == null }
            if (loose.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(fiscalString(Res.string.fiscal_previa_erros), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                FiscalMessages(loose.map { messageText(it) })
            }
        }
    }
}

@Composable
private fun ProgressContent(
    phase: FiscalIssuePhase.Progress,
    modifier: Modifier,
    onFix: () -> Unit,
    onOpenInvoice: (String) -> Unit,
    onClose: () -> Unit,
) {
    val doc = phase.invoice
    val status = doc?.status ?: FiscalStatus.RECEBIDO
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag(FiscalTestTags.EMITIR_ANDAMENTO),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FiscalStatusBadge(status)
        when {
            status == FiscalStatus.AUTORIZADO -> AppBanner(
                message = fiscalString(Res.string.fiscal_andamento_autorizada, doc?.numeroNfse.orEmpty()), tone = StatusTone.SUCCESS,
            )
            status == FiscalStatus.REJEITADO -> {
                AppBanner(
                    title = fiscalString(Res.string.fiscal_andamento_rejeitada),
                    message = doc?.erros.orEmpty().joinToString("\n") { messageText(it) },
                    tone = StatusTone.DANGER,
                )
                AppButton(text = fiscalString(Res.string.fiscal_corrigir_e_reenviar), onClick = onFix)
            }
            else -> {
                CircularProgressIndicator()
                Text(
                    fiscalString(if (status == FiscalStatus.INCERTO) Res.string.fiscal_andamento_incerto else Res.string.fiscal_andamento_processando),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (phase.slow) Text(fiscalString(Res.string.fiscal_andamento_demorando), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlinedButton(text = fiscalString(Res.string.fiscal_fechar), onClick = onClose, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(0.dp))
            AppButton(text = fiscalString(Res.string.fiscal_ver_nota), onClick = { onOpenInvoice(phase.id) }, modifier = Modifier.weight(1f))
        }
    }
}
