package br.com.codecacto.kmplib.fiscal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.brdata.Address
import br.com.codecacto.kmplib.ui.components.AddressErrors
import br.com.codecacto.kmplib.ui.components.AddressFields
import br.com.codecacto.kmplib.fiscal.generated.resources.*
import br.com.codecacto.kmplib.platform.FilePickResult
import br.com.codecacto.kmplib.platform.rememberFilePicker
import br.com.codecacto.kmplib.ui.components.AppBanner
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.AppPickerField
import br.com.codecacto.kmplib.ui.components.AppSwitch
import br.com.codecacto.kmplib.ui.components.AppTextArea
import br.com.codecacto.kmplib.ui.components.AppTextField
import br.com.codecacto.kmplib.ui.components.BackTopBar
import br.com.codecacto.kmplib.ui.components.ConfirmationDialog
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.PickerOption
import br.com.codecacto.kmplib.ui.components.SegmentedControl
import br.com.codecacto.kmplib.ui.components.StatusTone
import br.com.codecacto.kmplib.ui.components.UnsavedChangesGuard
import br.com.codecacto.kmplib.ui.components.rememberUnsavedChangesGuard

/**
 * Cadastro fiscal em etapas — Empresa · Regime · Serviço padrão · Certificado · Ambiente. Mesmo conteúdo do
 * `FiscalSettingsPanel` da weblib: regime de apuração em palavras, CST de PIS/COFINS e tributos aproximados, número
 * inicial da DPS, certificado A1 (.pfx/.p12) pelo seletor de arquivos + senha com o olho, e a troca de ambiente com
 * confirmação. Sair com algo não salvo pergunta antes ("Descartar o que foi preenchido?").
 *
 * @param repository o [FiscalRepository] do produto (ex. `RestFiscalRepository(FiscalApi(client, "/locaki/v2/fiscal"))`).
 */
@Composable
fun FiscalSettingsScreen(
    repository: FiscalRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm = viewModel(key = "kmplib-fiscal-config") { FiscalSettingsViewModel(repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = fiscalString(Res.string.fiscal_salvo)
    val certSent = fiscalString(Res.string.fiscal_certificado_enviado)
    val certRemoved = fiscalString(Res.string.fiscal_certificado_removido)
    LaunchedEffect(vm) {
        vm.effect.collect { e ->
            snackbar.showSnackbar(
                when (e) {
                    FiscalSettingsEffect.Saved -> savedText
                    FiscalSettingsEffect.CertificateSent -> certSent
                    FiscalSettingsEffect.CertificateRemoved -> certRemoved
                },
            )
        }
    }
    val guard = rememberUnsavedChangesGuard(dirty = state.dirty)
    UnsavedChangesGuard(guard, onBack = onBack)

    Scaffold(
        modifier = modifier.testTag(FiscalTestTags.CONFIG),
        topBar = { BackTopBar(title = fiscalString(Res.string.fiscal_config_titulo), onBackClick = { guard.requestExit(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.loading && state.loadError == null && state.step != FiscalSetupStep.AMBIENTE) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                        state.banner?.let { b ->
                            AppBanner(message = fiscalErrorMessage(b), tone = StatusTone.DANGER, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        val certStep = state.step == FiscalSetupStep.CERTIFICADO
                        AppButton(
                            text = when {
                                certStep && state.certificateBytes != null -> fiscalString(Res.string.fiscal_certificado_enviar)
                                else -> fiscalString(Res.string.fiscal_salvar_continuar)
                            },
                            onClick = { vm.onAction(FiscalSettingsAction.SaveStep) },
                            isLoading = state.saving || state.uploadingCertificate,
                            modifier = Modifier.testTag(if (certStep) FiscalTestTags.CERTIFICADO_ENVIAR else FiscalTestTags.CONFIG_SALVAR),
                        )
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
                onRetry = { vm.onAction(FiscalSettingsAction.Load) },
                modifier = Modifier.padding(inner),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepChips(state.step) { vm.onAction(FiscalSettingsAction.GoToStep(it)) }
                Text(
                    fiscalString(Res.string.fiscal_etapa_de, state.step.ordinal + 1, FiscalSetupStep.entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PendingCard(state) { vm.onAction(FiscalSettingsAction.GoToStep(it)) }
                when (state.step) {
                    FiscalSetupStep.EMPRESA -> CompanyStep(state, vm::onAction)
                    FiscalSetupStep.REGIME -> RegimeStep(state, vm::onAction)
                    FiscalSetupStep.SERVICO -> ServiceStep(state, vm::onAction)
                    FiscalSetupStep.CERTIFICADO -> CertificateStep(state, vm::onAction)
                    FiscalSetupStep.AMBIENTE -> EnvironmentStep(state, vm::onAction)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    ConfirmationDialog(
        show = state.confirmRemoveCertificate,
        title = fiscalString(Res.string.fiscal_certificado_remover_pergunta),
        message = fiscalString(Res.string.fiscal_certificado_remover_desc),
        confirmText = fiscalString(Res.string.fiscal_certificado_remover),
        cancelText = fiscalString(Res.string.fiscal_cancelar),
        onConfirm = { vm.onAction(FiscalSettingsAction.ConfirmRemoveCertificate) },
        onDismiss = { vm.onAction(FiscalSettingsAction.DismissDialogs) },
        primaryColor = MaterialTheme.colorScheme.error,
    )
    ConfirmationDialog(
        show = state.confirmProduction,
        title = fiscalString(Res.string.fiscal_passar_producao_pergunta),
        message = fiscalString(Res.string.fiscal_passar_producao_desc),
        confirmText = fiscalString(Res.string.fiscal_passar_producao),
        cancelText = fiscalString(Res.string.fiscal_cancelar),
        onConfirm = { vm.onAction(FiscalSettingsAction.ConfirmProduction) },
        onDismiss = { vm.onAction(FiscalSettingsAction.DismissDialogs) },
        isLoading = state.saving,
    )
}

@Composable
private fun StepChips(current: FiscalSetupStep, onSelect: (FiscalSetupStep) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FiscalSetupStep.entries.forEach { s ->
            FilterChip(
                selected = s == current,
                onClick = { onSelect(s) },
                label = { Text("${s.ordinal + 1}. ${stepLabel(s)}") },
                modifier = Modifier.testTag("${FiscalTestTags.CONFIG_ETAPA}-${s.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun PendingCard(state: FiscalSettingsState, onGo: (FiscalSetupStep) -> Unit) {
    val emitter = state.emitter ?: return
    if (emitter.pronto) {
        AppBanner(message = fiscalString(Res.string.fiscal_pronto), tone = StatusTone.SUCCESS)
        return
    }
    if (emitter.pendencias.isEmpty()) return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(fiscalString(Res.string.fiscal_pendencias_titulo), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            emitter.pendencias.forEach { p ->
                val step = setupStepOf(p.code, p.field)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("• ${p.message}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (step != state.step) TextButton(onClick = { onGo(step) }) { Text(fiscalString(Res.string.fiscal_resolver)) }
                }
            }
        }
    }
}

@Composable
private fun CompanyStep(state: FiscalSettingsState, on: (FiscalSettingsAction) -> Unit) {
    val d = state.draft
    val e = state.errors
    fun edit(f: (FiscalEmitterDraft) -> FiscalEmitterDraft) = on(FiscalSettingsAction.Edit(f))
    val pj = d.tipoPessoa == FiscalTipoPessoa.PJ
    Text(fiscalString(Res.string.fiscal_tipo_pessoa), style = MaterialTheme.typography.labelLarge)
    SegmentedControl(
        options = listOf(fiscalString(Res.string.fiscal_pj), fiscalString(Res.string.fiscal_pf)),
        selectedIndex = if (pj) 0 else 1,
        onOptionSelected = { i -> edit { it.copy(tipoPessoa = if (i == 0) FiscalTipoPessoa.PJ else FiscalTipoPessoa.PF, documento = "") } },
        testTag = "fiscal-config-tipo-pessoa",
        optionTestKeys = listOf("pj", "pf"),
    )
    FiscalTextField(
        value = d.documento, onValueChange = { v -> edit { it.copy(documento = v) } },
        label = fiscalString(if (pj) Res.string.fiscal_cnpj else Res.string.fiscal_cpf),
        mask = if (pj) FiscalMask.CNPJ else FiscalMask.CPF,
        error = e.textOf("documento"), testTag = "fiscal-config-input-documento",
    )
    FiscalTextField(
        value = d.razaoSocial, onValueChange = { v -> edit { it.copy(razaoSocial = v) } },
        label = fiscalString(if (pj) Res.string.fiscal_razao_social else Res.string.fiscal_nome_completo),
        error = e.textOf("razaoSocial"), testTag = "fiscal-config-input-razao",
    )
    if (pj) {
        FiscalTextField(value = d.nomeFantasia, onValueChange = { v -> edit { it.copy(nomeFantasia = v) } }, label = fiscalString(Res.string.fiscal_nome_fantasia), error = e.textOf("nomeFantasia"))
    }
    FiscalTextField(
        value = d.inscricaoMunicipal, onValueChange = { v -> edit { it.copy(inscricaoMunicipal = v) } },
        label = fiscalString(Res.string.fiscal_inscricao_municipal), helper = fiscalString(Res.string.fiscal_inscricao_municipal_dica),
        error = e.textOf("inscricaoMunicipal"),
    )
    FiscalTextField(
        value = d.email, onValueChange = { v -> edit { it.copy(email = v) } }, label = fiscalString(Res.string.fiscal_email),
        keyboardType = KeyboardType.Email, error = e.textOf("email"),
    )
    FiscalTextField(
        value = d.telefone, onValueChange = { v -> edit { it.copy(telefone = v) } }, label = fiscalString(Res.string.fiscal_whatsapp),
        mask = FiscalMask.PHONE, error = e.textOf("telefone"),
    )
    AddressBlock(
        title = fiscalString(Res.string.fiscal_endereco),
        value = d.endereco,
        onChange = { a -> edit { it.copy(endereco = a) } },
        errors = e,
        prefix = "endereco",
    )
    val proximo = state.emitter?.proximoNumeroDps
    FiscalTextField(
        value = d.numeroInicialDps, onValueChange = { v -> edit { it.copy(numeroInicialDps = v.take(15)) } },
        label = fiscalString(Res.string.fiscal_numero_dps), mask = FiscalMask.DIGITS,
        helper = fiscalString(Res.string.fiscal_numero_dps_dica) + (proximo?.let { " " + fiscalString(Res.string.fiscal_numero_dps_proximo, it.toString()) } ?: ""),
        error = e.textOf("numeroInicialDps"),
    )
}

/** Endereço com Estado e Cidade em seletores (a cidade só depois do estado), da `kmplib-brdata`. */
@Composable
internal fun AddressBlock(
    title: String,
    value: FiscalAddressDraft,
    onChange: (FiscalAddressDraft) -> Unit,
    errors: FiscalFieldErrors,
    prefix: String,
) {
    AddressFields(
        value = Address(
            cep = value.cep, logradouro = value.logradouro, numero = value.numero, complemento = value.complemento,
            bairro = value.bairro, cidade = value.cidade, uf = value.uf,
        ),
        onValueChange = { a ->
            onChange(
                FiscalAddressDraft(
                    cep = a.cep.filter { it.isDigit() }, logradouro = a.logradouro, numero = a.numero, complemento = a.complemento,
                    bairro = a.bairro, uf = a.uf, cidade = a.cidade,
                ),
            )
        },
        title = title,
        errors = AddressErrors(
            cep = errors.textOf("$prefix.cep"),
            logradouro = errors.textOf("$prefix.logradouro"),
            numero = errors.textOf("$prefix.numero"),
            bairro = errors.textOf("$prefix.bairro"),
            cidade = errors.textOf("$prefix.municipioIbge"),
            uf = errors.textOf("$prefix.uf"),
        ),
    )
}

@Composable
private fun RegimeStep(state: FiscalSettingsState, on: (FiscalSettingsAction) -> Unit) {
    val d = state.draft
    Text(fiscalString(Res.string.fiscal_regime_tributario), style = MaterialTheme.typography.labelLarge)
    state.errors.textOf("regime")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    val options = RegimeApuracao.entries.filter { it != RegimeApuracao.PESSOA_FISICA || d.tipoPessoa == FiscalTipoPessoa.PF }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { r ->
            ChoiceRow(
                selected = d.regime == r,
                title = regimeLabel(r),
                description = regimeDescription(r),
                onClick = { on(FiscalSettingsAction.SetRegime(r)) },
                testTag = "fiscal-config-regime-${r.name.lowercase()}",
            )
        }
    }
    if (d.regime == RegimeApuracao.SIMPLES_HIBRIDO) {
        Text(fiscalString(Res.string.fiscal_hibrido_rotulo), style = MaterialTheme.typography.labelLarge)
        Column(Modifier.selectableGroup()) {
            FiscalSimplesHibrido.entries.forEach { h ->
                ChoiceRow(
                    selected = d.simplesHibrido == h,
                    title = fiscalString(if (h == FiscalSimplesHibrido.ISS_FORA) Res.string.fiscal_hibrido_iss_fora else Res.string.fiscal_hibrido_tudo_fora),
                    onClick = { on(FiscalSettingsAction.Edit { it.copy(simplesHibrido = h) }) },
                )
            }
        }
    }
    val special = FiscalRegimeEspecial.entries.map { PickerOption(it.name, specialLabel(it)) }
    AppPickerField(
        value = d.regimeEspecial.name,
        onValueChange = { v -> on(FiscalSettingsAction.Edit { it.copy(regimeEspecial = FiscalRegimeEspecial.valueOf(v)) }) },
        options = special,
        label = fiscalString(Res.string.fiscal_regime_especial),
        helperText = fiscalString(Res.string.fiscal_regime_especial_dica),
    )
}

@Composable
internal fun ChoiceRow(selected: Boolean, title: String, onClick: () -> Unit, description: String? = null, testTag: String? = null) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ServiceStep(state: FiscalSettingsState, on: (FiscalSettingsAction) -> Unit) {
    val d = state.draft
    val p = d.padroes
    val e = state.errors
    fun editP(f: (FiscalDefaultsDraft) -> FiscalDefaultsDraft) = on(FiscalSettingsAction.Edit { it.copy(padroes = f(it.padroes)) })

    FiscalSectionTitle(fiscalString(Res.string.fiscal_servico_padrao))
    if (p.codigoTributacaoNacional.isNotBlank()) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(p.codigoTributacaoNacional, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (p.descricaoTributacaoNacional.isNotBlank()) Text(p.descricaoTributacaoNacional, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    FiscalTextField(
        value = state.serviceQuery,
        onValueChange = { on(FiscalSettingsAction.SearchServices(it)) },
        label = fiscalString(Res.string.fiscal_buscar_servico),
        helper = fiscalString(Res.string.fiscal_buscar_servico_placeholder),
        error = e.textOf("padroes.codigoTributacaoNacional"),
        testTag = "fiscal-config-busca-servico",
    )
    if (state.searchingServices) {
        Text(fiscalString(Res.string.fiscal_buscando), style = MaterialTheme.typography.bodySmall)
    } else if (state.serviceQuery.trim().length >= 2 && state.serviceResults.isEmpty()) {
        Text(fiscalString(Res.string.fiscal_servico_nao_encontrado), style = MaterialTheme.typography.bodySmall)
    }
    state.serviceResults.forEach { item ->
        Column(
            Modifier.fillMaxWidth().clickable { on(FiscalSettingsAction.PickService(item)) }.padding(vertical = 8.dp)
                .testTag("fiscal-config-servico-${item.codigo}"),
        ) {
            Text(item.codigo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(item.descricao, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
    }
    FiscalTextField(value = p.codigoTributacaoMunicipal, onValueChange = { v -> editP { it.copy(codigoTributacaoMunicipal = v) } }, label = fiscalString(Res.string.fiscal_codigo_municipal), error = e.textOf("padroes.codigoTributacaoMunicipal"))
    FiscalTextField(value = p.nbs, onValueChange = { v -> editP { it.copy(nbs = v) } }, label = fiscalString(Res.string.fiscal_nbs), mask = FiscalMask.DIGITS, error = e.textOf("padroes.nbs"))
    AppTextArea(
        value = p.descricaoServico,
        onValueChange = { v -> editP { it.copy(descricaoServico = v) } },
        label = fiscalString(Res.string.fiscal_descricao_padrao),
        showCharCounter = false,
        modifier = Modifier.fillMaxWidth(),
    )
    state.municipal?.let { m -> if (!m.conveniado) AppBanner(message = fiscalString(Res.string.fiscal_municipio_nao_conveniado), tone = StatusTone.WARNING) }
    FiscalTextField(
        value = p.aliquotaIss, onValueChange = { v -> editP { it.copy(aliquotaIss = v) } },
        label = fiscalString(Res.string.fiscal_aliquota_iss), keyboardType = KeyboardType.Decimal, error = e.textOf("padroes.aliquotaIss"),
    )
    suggestedRate(state.municipal, p.codigoTributacaoNacional)?.let { r ->
        if (toDecimalString(p.aliquotaIss) != r) {
            AppBanner(
                message = fiscalString(Res.string.fiscal_aliquota_sugerida, formatRate(r)),
                tone = StatusTone.INFO,
                action = { TextButton(onClick = { on(FiscalSettingsAction.UseSuggestedRate) }) { Text(fiscalString(Res.string.fiscal_usar_aliquota)) } },
            )
        }
    }
    SwitchRow(fiscalString(Res.string.fiscal_iss_retido_padrao), p.issRetido) { v -> editP { it.copy(issRetido = v) } }
    SwitchRow(fiscalString(Res.string.fiscal_pis_cofins_retidos), p.pisCofinsRetidos) { v -> editP { it.copy(pisCofinsRetidos = v) } }

    val regime = d.regime
    if (regime != null && regime != RegimeApuracao.MEI) {
        FiscalSectionTitle(fiscalString(Res.string.fiscal_tributos_titulo))
        if (usesPisCofinsDefaults(regime)) {
            val rates = pisCofinsBasicRates(regime)
            FiscalTextField(
                value = p.cstPisCofins, onValueChange = { v -> editP { it.copy(cstPisCofins = v.take(2)) } },
                label = fiscalString(Res.string.fiscal_cst_pis_cofins), mask = FiscalMask.DIGITS,
                helper = rates?.let { fiscalString(Res.string.fiscal_cst_dica, formatRate(it.first), formatRate(it.second)) },
                error = e.textOf("padroes.cstPisCofins"),
            )
        }
        Text(fiscalString(Res.string.fiscal_totais_titulo), style = MaterialTheme.typography.labelLarge)
        if (isNonOptantRegime(regime)) {
            Text(fiscalString(Res.string.fiscal_totais_explicacao), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                ChoiceRow(!p.totaisValorPorNota, fiscalString(Res.string.fiscal_totais_percentual), { editP { it.copy(totaisValorPorNota = false) } })
                ChoiceRow(
                    p.totaisValorPorNota, fiscalString(Res.string.fiscal_totais_valor_por_nota), { editP { it.copy(totaisValorPorNota = true) } },
                    description = fiscalString(Res.string.fiscal_totais_valor_por_nota_desc),
                )
            }
            if (!p.totaisValorPorNota) {
                val k = "padroes.totaisAproximados"
                FiscalTextField(p.totaisFederal, { v -> editP { it.copy(totaisFederal = v) } }, fiscalString(Res.string.fiscal_totais_federal), keyboardType = KeyboardType.Decimal, error = e.textOf("$k.federal"))
                FiscalTextField(p.totaisEstadual, { v -> editP { it.copy(totaisEstadual = v) } }, fiscalString(Res.string.fiscal_totais_estadual), keyboardType = KeyboardType.Decimal, error = e.textOf("$k.estadual"))
                FiscalTextField(p.totaisMunicipal, { v -> editP { it.copy(totaisMunicipal = v) } }, fiscalString(Res.string.fiscal_totais_municipal), keyboardType = KeyboardType.Decimal, error = e.textOf("$k.municipal"))
            }
        } else if (isSimplesRegime(regime)) {
            FiscalTextField(
                p.totaisSimples, { v -> editP { it.copy(totaisSimples = v) } }, fiscalString(Res.string.fiscal_totais_simples),
                keyboardType = KeyboardType.Decimal, helper = fiscalString(Res.string.fiscal_totais_simples_dica),
                error = e.textOf("padroes.totaisAproximados.simplesNacional"),
            )
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CertificateStep(state: FiscalSettingsState, on: (FiscalSettingsAction) -> Unit) {
    FiscalSectionTitle(fiscalString(Res.string.fiscal_certificado_titulo))
    val cert = state.emitter?.certificado
    if (cert != null) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                cert.titularNome?.let { FiscalInfoRow(fiscalString(Res.string.fiscal_certificado_titular), it + (cert.titularDocumento?.let { d -> " · ${formatDocument(d)}" } ?: "")) }
                val de = cert.validoDe?.let { formatFiscalDateTime(it)?.take(10) }
                val ate = cert.validoAte?.let { formatFiscalDateTime(it)?.take(10) }
                if (de != null && ate != null) Text(fiscalString(Res.string.fiscal_certificado_validade, de, ate), style = MaterialTheme.typography.bodySmall)
                val days = certificateDaysLeft(cert.validoAte)
                when {
                    days == null -> Unit
                    days < 0 -> AppBanner(message = fiscalString(Res.string.fiscal_certificado_vencido), tone = StatusTone.DANGER)
                    days <= CERTIFICATE_WARNING_DAYS -> AppBanner(
                        message = when (days) {
                            0L -> fiscalString(Res.string.fiscal_certificado_vence_hoje)
                            1L -> fiscalString(Res.string.fiscal_certificado_vence_amanha)
                            else -> fiscalString(Res.string.fiscal_certificado_vence_em, days.toInt())
                        },
                        tone = StatusTone.WARNING,
                    )
                }
                TextButton(onClick = { on(FiscalSettingsAction.AskRemoveCertificate) }, enabled = !state.removingCertificate) {
                    Text(fiscalString(Res.string.fiscal_certificado_remover), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (state.emitter == null) {
        AppBanner(message = fiscalString(Res.string.fiscal_certificado_antes_empresa), tone = StatusTone.WARNING)
        return
    }
    // Certificado não é foto: o seletor de ARQUIVOS abre direto (regra da fábrica de 27/out/2026).
    val pick = rememberFilePicker(mimeTypes = listOf("*/*"), maxBytes = FISCAL_CERTIFICATE_MAX_BYTES) { r ->
        when (r) {
            is FilePickResult.Picked ->
                if (isCertificateFile(r.file.name)) on(FiscalSettingsAction.CertificatePicked(r.file.name, r.file.data))
                else on(FiscalSettingsAction.CertificateRejected(FiscalIssue.CERTIFICADO_FORMATO))
            is FilePickResult.TooLarge -> on(FiscalSettingsAction.CertificateRejected(FiscalIssue.CERTIFICADO_GRANDE))
            is FilePickResult.Failed -> on(FiscalSettingsAction.CertificateRejected(FiscalIssue.CERTIFICADO_FORMATO))
            FilePickResult.Cancelled -> Unit
        }
    }
    Text(fiscalString(Res.string.fiscal_certificado_arquivo), style = MaterialTheme.typography.labelLarge)
    AppOutlinedButton(
        text = state.certificateFileName ?: fiscalString(if (cert != null) Res.string.fiscal_certificado_trocar else Res.string.fiscal_certificado_escolher),
        onClick = pick,
        icon = Icons.Outlined.AttachFile,
        modifier = Modifier.testTag(FiscalTestTags.CERTIFICADO_ESCOLHER),
    )
    val fileError = state.errors.textOf("arquivo")
    Text(
        fileError ?: fiscalString(Res.string.fiscal_certificado_arquivo_dica),
        style = MaterialTheme.typography.bodySmall,
        color = if (fileError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AppTextField(
        value = state.certificatePassword,
        onValueChange = { on(FiscalSettingsAction.SetCertificatePassword(it)) },
        label = fiscalString(Res.string.fiscal_certificado_senha),
        isPassword = true,
        keepTextLocally = true,
        errorMessage = state.errors.textOf("senha"),
        modifier = Modifier.fillMaxWidth().testTag(FiscalTestTags.CERTIFICADO_SENHA),
    )
}

@Composable
private fun EnvironmentStep(state: FiscalSettingsState, on: (FiscalSettingsAction) -> Unit) {
    val current = state.emitter?.ambiente ?: state.draft.ambiente
    FiscalSectionTitle(fiscalString(Res.string.fiscal_ambiente_rotulo))
    FiscalAmbiente.entries.forEach { a ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fiscalString(if (a == FiscalAmbiente.PRODUCAO) Res.string.fiscal_ambiente_producao else Res.string.fiscal_ambiente_teste),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    if (a == current) Text(fiscalString(Res.string.fiscal_ambiente_atual), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    fiscalString(if (a == FiscalAmbiente.PRODUCAO) Res.string.fiscal_ambiente_producao_desc else Res.string.fiscal_ambiente_teste_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    state.banner?.let { AppBanner(message = fiscalErrorMessage(it), tone = StatusTone.DANGER) }
    if (state.emitter == null) return
    if (current == FiscalAmbiente.PRODUCAO_RESTRITA) {
        AppButton(
            text = fiscalString(Res.string.fiscal_passar_producao),
            onClick = { on(FiscalSettingsAction.AskProduction) },
            isLoading = state.saving,
            modifier = Modifier.testTag("fiscal-config-btn-producao"),
        )
    } else {
        AppOutlinedButton(
            text = fiscalString(Res.string.fiscal_voltar_teste),
            onClick = { on(FiscalSettingsAction.BackToTest) },
            isLoading = state.saving,
            modifier = Modifier.testTag("fiscal-config-btn-teste"),
        )
    }
}
