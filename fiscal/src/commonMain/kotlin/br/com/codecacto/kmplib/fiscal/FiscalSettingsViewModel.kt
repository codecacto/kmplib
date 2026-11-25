package br.com.codecacto.kmplib.fiscal

import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/** Estado do cadastro fiscal em etapas (empresa → regime → serviço padrão → certificado → ambiente). */
data class FiscalSettingsState(
    val loading: Boolean = true,
    /** Falha ao carregar (tela de erro com "tentar de novo"). */
    val loadError: FiscalError? = null,
    /** O cadastro como está no servidor (`null` = ainda não existe). */
    val emitter: FiscalEmissor? = null,
    val draft: FiscalEmitterDraft = FiscalEmitterDraft(),
    /** O rascunho como estava ao carregar/salvar — diferença = há o que salvar (aviso ao sair). */
    val savedDraft: FiscalEmitterDraft = FiscalEmitterDraft(),
    val step: FiscalSetupStep = FiscalSetupStep.EMPRESA,
    val errors: FiscalFieldErrors = emptyMap(),
    /** Recusa que não é de campo nenhum (mostrada junto do botão). */
    val banner: FiscalError? = null,
    val saving: Boolean = false,
    // Serviço da lista nacional
    val serviceQuery: String = "",
    val serviceResults: List<FiscalCatalogoItem> = emptyList(),
    val searchingServices: Boolean = false,
    val municipal: FiscalParametrosMunicipais? = null,
    // Certificado
    val certificateFileName: String? = null,
    val certificateBytes: ByteArray? = null,
    val certificatePassword: String = "",
    val uploadingCertificate: Boolean = false,
    val confirmRemoveCertificate: Boolean = false,
    val removingCertificate: Boolean = false,
    // Ambiente
    val confirmProduction: Boolean = false,
) {
    val dirty: Boolean get() = draft != savedDraft || certificateBytes != null || certificatePassword.isNotEmpty()
    val pendencias: List<FiscalPendencia> get() = emitter?.pendencias.orEmpty()
    val ready: Boolean get() = emitter?.pronto == true

    override fun toString(): String = "FiscalSettingsState(step=$step, loading=$loading, saving=$saving, dirty=$dirty)"
}

sealed interface FiscalSettingsAction {
    data object Load : FiscalSettingsAction
    data class GoToStep(val step: FiscalSetupStep) : FiscalSettingsAction
    /** Edita o rascunho. A tela passa a transformação do campo que mudou. */
    data class Edit(val change: (FiscalEmitterDraft) -> FiscalEmitterDraft) : FiscalSettingsAction
    data class SetRegime(val regime: RegimeApuracao) : FiscalSettingsAction
    data class SearchServices(val query: String) : FiscalSettingsAction
    data class PickService(val item: FiscalCatalogoItem) : FiscalSettingsAction
    data object UseSuggestedRate : FiscalSettingsAction
    /** Salva a etapa atual (e as anteriores) e avança. */
    data object SaveStep : FiscalSettingsAction
    data class CertificatePicked(val fileName: String, val bytes: ByteArray) : FiscalSettingsAction {
        override fun toString(): String = "CertificatePicked(${bytes.size} bytes)"
    }
    /** O arquivo escolhido não serve (formato ou tamanho) — só marca o campo. */
    data class CertificateRejected(val issue: FiscalIssue) : FiscalSettingsAction
    data class SetCertificatePassword(val password: String) : FiscalSettingsAction {
        override fun toString(): String = "SetCertificatePassword(***)"
    }
    data object UploadCertificate : FiscalSettingsAction
    data object AskRemoveCertificate : FiscalSettingsAction
    data object ConfirmRemoveCertificate : FiscalSettingsAction
    data object DismissDialogs : FiscalSettingsAction
    data object AskProduction : FiscalSettingsAction
    data object ConfirmProduction : FiscalSettingsAction
    data object BackToTest : FiscalSettingsAction
}

sealed interface FiscalSettingsEffect {
    data object Saved : FiscalSettingsEffect
    data object CertificateSent : FiscalSettingsEffect
    data object CertificateRemoved : FiscalSettingsEffect
}

/**
 * Cadastro fiscal guiado (o `FiscalSettingsPanel` da weblib, no app). Cada "Salvar e continuar" grava o cadastro
 * inteiro (`PUT {base}/emissor`): o servidor devolve o que falta (`pendencias`), e a etapa da primeira recusa abre.
 */
class FiscalSettingsViewModel(
    private val repository: FiscalRepository,
    private val searchDebounceMillis: Long = 350L,
) : BaseViewModel<FiscalSettingsState, FiscalSettingsAction, FiscalSettingsEffect>(FiscalSettingsState()) {

    private var searchJob: Job? = null
    private var municipalIbge: String? = null

    init {
        onAction(FiscalSettingsAction.Load)
    }

    override fun onAction(action: FiscalSettingsAction) {
        when (action) {
            FiscalSettingsAction.Load -> load()
            is FiscalSettingsAction.GoToStep -> setState { copy(step = action.step, errors = emptyMap(), banner = null) }
            is FiscalSettingsAction.Edit -> setState {
                val next = action.change(draft)
                copy(draft = next, errors = errors.withoutChanged(draft, next))
            }
            is FiscalSettingsAction.SetRegime -> setState {
                copy(draft = draft.copy(regime = action.regime, padroes = defaultsForRegime(action.regime, draft.padroes)), errors = errors - "regime")
            }
            is FiscalSettingsAction.SearchServices -> searchServices(action.query)
            is FiscalSettingsAction.PickService -> {
                setState {
                    copy(
                        draft = draft.copy(
                            padroes = draft.padroes.copy(
                                codigoTributacaoNacional = action.item.codigo,
                                descricaoTributacaoNacional = action.item.descricao,
                            ),
                        ),
                        serviceQuery = "",
                        serviceResults = emptyList(),
                        errors = errors - "padroes.codigoTributacaoNacional",
                    )
                }
            }
            FiscalSettingsAction.UseSuggestedRate -> suggestedRate(currentState.municipal, currentState.draft.padroes.codigoTributacaoNacional)?.let { r ->
                setState { copy(draft = draft.copy(padroes = draft.padroes.copy(aliquotaIss = decimalToInput(r))), errors = errors - "padroes.aliquotaIss") }
            }
            FiscalSettingsAction.SaveStep -> saveStep()
            is FiscalSettingsAction.CertificatePicked -> setState {
                copy(certificateFileName = action.fileName, certificateBytes = action.bytes, errors = errors - "arquivo")
            }
            is FiscalSettingsAction.CertificateRejected -> setState {
                copy(certificateFileName = null, certificateBytes = null, errors = errors + ("arquivo" to FiscalFieldError.Issue(action.issue)))
            }
            is FiscalSettingsAction.SetCertificatePassword -> setState { copy(certificatePassword = action.password, errors = errors - "senha") }
            FiscalSettingsAction.UploadCertificate -> uploadCertificate()
            FiscalSettingsAction.AskRemoveCertificate -> setState { copy(confirmRemoveCertificate = true) }
            FiscalSettingsAction.ConfirmRemoveCertificate -> removeCertificate()
            FiscalSettingsAction.DismissDialogs -> setState { copy(confirmRemoveCertificate = false, confirmProduction = false) }
            FiscalSettingsAction.AskProduction -> setState { copy(confirmProduction = true) }
            FiscalSettingsAction.ConfirmProduction -> saveAmbiente(FiscalAmbiente.PRODUCAO)
            FiscalSettingsAction.BackToTest -> saveAmbiente(FiscalAmbiente.PRODUCAO_RESTRITA)
        }
    }

    private fun load() {
        setState { copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = repository.emitter()) {
                is FiscalResult.Ok -> {
                    val draft = emitterToDraft(r.value)
                    setState {
                        copy(
                            loading = false,
                            emitter = r.value,
                            draft = draft,
                            savedDraft = draft,
                            step = firstPendingStep(r.value) ?: step,
                        )
                    }
                    loadMunicipal(draft)
                }
                is FiscalResult.Failure -> setState { copy(loading = false, loadError = r.error) }
            }
        }
    }

    /** Primeira etapa com pendência (cadastro novo = empresa; pronto = fica onde está). */
    private fun firstPendingStep(e: FiscalEmissor?): FiscalSetupStep? {
        if (e == null) return FiscalSetupStep.EMPRESA
        return e.pendencias.map { setupStepOf(it.code, it.field) }.minByOrNull { it.ordinal }
    }

    private fun loadMunicipal(d: FiscalEmitterDraft) {
        val ibge = d.endereco.municipioIbge ?: return
        if (ibge == municipalIbge) return
        municipalIbge = ibge
        viewModelScope.launch {
            repository.municipalParameters(ibge).getOrNull()?.let { p -> setState { copy(municipal = p) } }
        }
    }

    private fun searchServices(query: String) {
        setState { copy(serviceQuery = query) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            setState { copy(serviceResults = emptyList(), searchingServices = false) }
            return
        }
        searchJob = viewModelScope.launch {
            setState { copy(searchingServices = true) }
            delay(searchDebounceMillis)
            val r = repository.searchServices(q)
            // Resposta velha (a pessoa já digitou outra coisa) não aparece.
            if (currentState.serviceQuery.trim() != q) return@launch
            setState { copy(serviceResults = r.getOrNull().orEmpty(), searchingServices = false) }
        }
    }

    private fun stepsUpTo(step: FiscalSetupStep): List<FiscalSetupStep> =
        FiscalSetupStep.entries.filter { it.ordinal <= step.ordinal && it != FiscalSetupStep.CERTIFICADO && it != FiscalSetupStep.AMBIENTE }

    private fun saveStep() {
        val s = currentState
        if (s.saving) return
        if (s.step == FiscalSetupStep.CERTIFICADO) {
            if (s.certificateBytes != null) uploadCertificate() else next()
            return
        }
        val local = stepsUpTo(s.step).fold(emptyMap<String, FiscalFieldError>()) { acc, st -> acc + validateEmitterStep(st, s.draft) }
        if (local.isNotEmpty()) {
            val firstStep = local.keys.map { setupStepOf("", it) }.minByOrNull { it.ordinal } ?: s.step
            setState { copy(errors = local, step = firstStep, banner = null) }
            return
        }
        persist(s.draft) { next() }
    }

    private fun next() {
        val st = currentState.step
        val nxt = FiscalSetupStep.entries.getOrNull(st.ordinal + 1) ?: st
        setState { copy(step = nxt, errors = emptyMap(), banner = null) }
    }

    private fun persist(draft: FiscalEmitterDraft, afterSave: () -> Unit) {
        setState { copy(saving = true, banner = null) }
        viewModelScope.launch {
            when (val r = repository.saveEmitter(toEmitterBody(draft))) {
                is FiscalResult.Ok -> {
                    val saved = emitterToDraft(r.value).let { fromServer ->
                        // O servidor não devolve o número inicial nem a descrição do catálogo: a tela mantém.
                        fromServer.copy(
                            padroes = fromServer.padroes.copy(descricaoTributacaoNacional = draft.padroes.descricaoTributacaoNacional),
                        )
                    }
                    setState { copy(saving = false, emitter = r.value, draft = saved, savedDraft = saved, errors = emptyMap(), confirmProduction = false) }
                    loadMunicipal(saved)
                    sendEffect(FiscalSettingsEffect.Saved)
                    afterSave()
                }
                is FiscalResult.Failure -> applyFailure(r.error)
            }
        }
    }

    private fun applyFailure(e: FiscalError) {
        val fields: FiscalFieldErrors = e.fields.mapValues { FiscalFieldError.Text(it.value) }
        val firstStep = fields.keys.map { setupStepOf("", it) }.minByOrNull { it.ordinal }
        setState {
            copy(
                saving = false,
                uploadingCertificate = false,
                removingCertificate = false,
                confirmProduction = false,
                errors = fields,
                step = firstStep ?: step,
                banner = if (fields.isEmpty() || e.kind != FiscalErrorKind.FIELDS) e else null,
            )
        }
    }

    private fun uploadCertificate() {
        val s = currentState
        if (s.uploadingCertificate) return
        val local = linkedMapOf<String, FiscalFieldError>()
        if (s.emitter == null) {
            setState { copy(banner = FiscalError(FiscalErrorKind.FIELDS, code = FISCAL_CODE_CERTIFICADO_ANTES_DA_EMPRESA)) }
            return
        }
        val bytes = s.certificateBytes
        if (bytes == null) local["arquivo"] = FiscalFieldError.Issue(FiscalIssue.CERTIFICADO_OBRIGATORIO)
        if (s.certificatePassword.isEmpty()) local["senha"] = FiscalFieldError.Issue(FiscalIssue.OBRIGATORIO)
        if (local.isNotEmpty() || bytes == null) {
            setState { copy(errors = local) }
            return
        }
        setState { copy(uploadingCertificate = true, banner = null) }
        viewModelScope.launch {
            when (val r = repository.uploadCertificate(bytes, s.certificateFileName ?: "certificado.pfx", s.certificatePassword)) {
                is FiscalResult.Ok -> {
                    setState {
                        copy(
                            uploadingCertificate = false, emitter = r.value, certificateBytes = null, certificateFileName = null,
                            certificatePassword = "", errors = emptyMap(),
                        )
                    }
                    sendEffect(FiscalSettingsEffect.CertificateSent)
                    next()
                }
                is FiscalResult.Failure -> applyFailure(r.error)
            }
        }
    }

    private fun removeCertificate() {
        setState { copy(removingCertificate = true, confirmRemoveCertificate = false) }
        viewModelScope.launch {
            when (val r = repository.removeCertificate()) {
                is FiscalResult.Ok -> {
                    val fresh = repository.emitter().getOrNull()
                    setState { copy(removingCertificate = false, emitter = fresh ?: emitter?.copy(certificado = null)) }
                    sendEffect(FiscalSettingsEffect.CertificateRemoved)
                }
                is FiscalResult.Failure -> applyFailure(r.error)
            }
        }
    }

    private fun saveAmbiente(ambiente: FiscalAmbiente) {
        if (currentState.saving) return
        val d = currentState.draft.copy(ambiente = ambiente)
        // Trocar o ambiente grava o cadastro como o servidor o conhece + o ambiente novo (o resto do rascunho também vai).
        persist(d) {}
    }
}

/** Código do [FiscalError] local "salve a empresa antes do certificado". */
const val FISCAL_CODE_CERTIFICADO_ANTES_DA_EMPRESA: String = "CERTIFICADO_ANTES_DA_EMPRESA"

/** Ao digitar, o erro do campo que mudou some (vermelho só até a pessoa corrigir). */
private fun FiscalFieldErrors.withoutChanged(before: FiscalEmitterDraft, after: FiscalEmitterDraft): FiscalFieldErrors {
    if (isEmpty()) return this
    val changed = buildSet {
        if (before.documento != after.documento || before.tipoPessoa != after.tipoPessoa) add("documento")
        if (before.razaoSocial != after.razaoSocial) add("razaoSocial")
        if (before.email != after.email) add("email")
        if (before.telefone != after.telefone) add("telefone")
        if (before.inscricaoMunicipal != after.inscricaoMunicipal) add("inscricaoMunicipal")
        if (before.numeroInicialDps != after.numeroInicialDps) add("numeroInicialDps")
        val a = before.endereco; val b = after.endereco
        if (a.cep != b.cep) add("endereco.cep")
        if (a.logradouro != b.logradouro) add("endereco.logradouro")
        if (a.numero != b.numero) add("endereco.numero")
        if (a.bairro != b.bairro) add("endereco.bairro")
        if (a.uf != b.uf || a.cidade != b.cidade) { add("endereco.municipioIbge"); add("endereco.uf") }
        val p = before.padroes; val q = after.padroes
        if (p.aliquotaIss != q.aliquotaIss) add("padroes.aliquotaIss")
        if (p.cstPisCofins != q.cstPisCofins) add("padroes.cstPisCofins")
        if (p.codigoTributacaoNacional != q.codigoTributacaoNacional) add("padroes.codigoTributacaoNacional")
        if (p.totaisFederal != q.totaisFederal || p.totaisEstadual != q.totaisEstadual || p.totaisMunicipal != q.totaisMunicipal || p.totaisValorPorNota != q.totaisValorPorNota) {
            add("padroes.totaisAproximados.federal"); add("padroes.totaisAproximados.estadual"); add("padroes.totaisAproximados.municipal")
        }
        if (p.totaisSimples != q.totaisSimples) add("padroes.totaisAproximados.simplesNacional")
    }
    return filterKeys { it !in changed }
}
