package br.com.codecacto.kmplib.fiscal

import androidx.lifecycle.viewModelScope
import br.com.codecacto.kmplib.core.util.newUuidV7
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fase da emissão: o formulário, ou o acompanhamento da nota que entrou na fila. */
sealed interface FiscalIssuePhase {
    data object Form : FiscalIssuePhase

    /** A nota foi aceita; [invoice] é a última leitura (nula até a primeira). [slow] = passou do tempo normal. */
    data class Progress(val id: String, val invoice: FiscalDocumento? = null, val slow: Boolean = false) : FiscalIssuePhase
}

data class FiscalIssueState(
    val loading: Boolean = true,
    val loadError: FiscalError? = null,
    val emitter: FiscalEmissor? = null,
    val emitterReady: Boolean = true,
    /** A nota que está sendo substituída (modo substituir). */
    val substitute: FiscalDocumento? = null,
    val draft: FiscalInvoiceDraft = FiscalInvoiceDraft(),
    val initialDraft: FiscalInvoiceDraft = FiscalInvoiceDraft(),
    val errors: FiscalFieldErrors = emptyMap(),
    val banner: FiscalError? = null,
    /** Mensagens da prévia sem campo (mostradas na caixa da prévia). */
    val preview: NfsePrevia? = null,
    val previewing: Boolean = false,
    val sending: Boolean = false,
    val advancedOpen: Boolean = false,
    // Substituição
    val reason: MotivoCancelamento = MotivoCancelamento.ERRO_EMISSAO,
    val reasonText: String = "",
    val phase: FiscalIssuePhase = FiscalIssuePhase.Form,
    /** Uma chave por ABERTURA: reenviar o mesmo pedido (rede caiu, resposta perdida) nunca emite duas notas. */
    val idempotencyKey: String = "",
) {
    val dirty: Boolean get() = phase == FiscalIssuePhase.Form && (draft != initialDraft || reasonText.isNotBlank())
    val isSubstitution: Boolean get() = substitute != null
}

sealed interface FiscalIssueAction {
    data object Load : FiscalIssueAction
    data class Edit(val change: (FiscalInvoiceDraft) -> FiscalInvoiceDraft) : FiscalIssueAction
    data class ToggleAdvanced(val open: Boolean) : FiscalIssueAction
    data class SetReason(val reason: MotivoCancelamento) : FiscalIssueAction
    data class SetReasonText(val text: String) : FiscalIssueAction
    data object Preview : FiscalIssueAction
    data object Submit : FiscalIssueAction
    /** A nota foi recusada: volta ao formulário com o que estava, e uma chave NOVA (o pedido corrigido é outro). */
    data object FixAfterRejection : FiscalIssueAction
}

sealed interface FiscalIssueEffect {
    /** A nota entrou na fila (o produto pode marcar o registro como "enviada"). */
    data class Accepted(val id: String) : FiscalIssueEffect
}

/**
 * Emitir (ou substituir) uma NFS-e — o `IssueInvoiceDialog` da weblib no app. Abre com os padrões do emissor e a
 * sugestão do produto ([refExterna], ex. `locacao:123`: tomador, descrição, valor e competência); valida, mostra a
 * prévia, emite com `Idempotency-Key` estável por abertura e acompanha o status até a nota decidir.
 *
 * @param substituteId id da nota a substituir (modo substituir), ou `null` para nota nova.
 */
class FiscalIssueViewModel(
    private val repository: FiscalRepository,
    private val refExterna: String? = null,
    private val substituteId: String? = null,
    private val pollIntervalMillis: Long = 2_000L,
    private val slowAfterMillis: Long = 60_000L,
    private val newKey: () -> String = { newUuidV7() },
) : BaseViewModel<FiscalIssueState, FiscalIssueAction, FiscalIssueEffect>(FiscalIssueState()) {

    private var pollJob: Job? = null

    init {
        setState { copy(idempotencyKey = newKey()) }
        onAction(FiscalIssueAction.Load)
    }

    override fun onAction(action: FiscalIssueAction) {
        when (action) {
            FiscalIssueAction.Load -> load()
            is FiscalIssueAction.Edit -> setState {
                val next = action.change(draft)
                copy(draft = next, errors = errors.filterKeys { !fieldChanged(it, draft, next) }, preview = null)
            }
            is FiscalIssueAction.ToggleAdvanced -> setState { copy(advancedOpen = action.open) }
            is FiscalIssueAction.SetReason -> setState { copy(reason = action.reason) }
            is FiscalIssueAction.SetReasonText -> setState { copy(reasonText = action.text, errors = errors - REASON_FIELD) }
            FiscalIssueAction.Preview -> preview()
            FiscalIssueAction.Submit -> submit()
            FiscalIssueAction.FixAfterRejection -> {
                pollJob?.cancel()
                setState { copy(phase = FiscalIssuePhase.Form, idempotencyKey = newKey(), preview = null, initialDraft = draft) }
            }
        }
    }

    private fun load() {
        setState { copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val status = repository.status()
            val st = when (status) {
                is FiscalResult.Ok -> status.value
                is FiscalResult.Failure -> {
                    setState { copy(loading = false, loadError = status.error) }
                    return@launch
                }
            }
            val emitter = st.emissor
            if (!st.habilitado) {
                setState { copy(loading = false, loadError = FiscalError(FiscalErrorKind.DISABLED)) }
                return@launch
            }
            val substitute = substituteId?.let { id ->
                when (val r = repository.invoice(id)) {
                    is FiscalResult.Ok -> r.value
                    is FiscalResult.Failure -> {
                        setState { copy(loading = false, loadError = r.error) }
                        return@launch
                    }
                }
            }
            val draft = if (substitute != null) {
                substituteDraft(substitute, emitter)
            } else {
                // Sem sugestão (registro sem cliente, 404) abre com os padrões: a sugestão é ajuda, não requisito.
                val suggestion = refExterna?.let { repository.suggestion(it).getOrNull() }
                initialInvoiceDraft(emitter, suggestion, refExterna)
            }
            setState {
                copy(
                    loading = false,
                    emitter = emitter,
                    emitterReady = st.pronto,
                    substitute = substitute,
                    draft = draft,
                    initialDraft = draft,
                )
            }
        }
    }

    private fun checkLocally(): Boolean {
        val s = currentState
        val local = validateInvoiceDraft(s.draft).toMutableMap()
        if (s.substitute != null && s.reasonText.isBlank()) local[REASON_FIELD] = FiscalFieldError.Issue(FiscalIssue.OBRIGATORIO)
        if (local.isNotEmpty()) {
            setState { copy(errors = local, advancedOpen = advancedOpen || local.keys.any(::isAdvancedInvoiceField)) }
            return false
        }
        return true
    }

    private fun showServerErrors(e: FiscalError) {
        val fields: FiscalFieldErrors = e.fields.mapValues { FiscalFieldError.Text(it.value) }
        setState {
            copy(
                errors = fields,
                banner = e,
                advancedOpen = advancedOpen || fields.keys.any(::isAdvancedInvoiceField),
            )
        }
    }

    private fun preview() {
        if (currentState.previewing) return
        setState { copy(banner = null) }
        if (!checkLocally()) return
        setState { copy(previewing = true) }
        viewModelScope.launch {
            when (val r = repository.preview(toInvoiceBody(currentState.draft))) {
                is FiscalResult.Ok -> {
                    val fields = fieldsOfMessages(r.value.erros)
                    setState {
                        copy(
                            previewing = false,
                            preview = r.value,
                            errors = fields,
                            advancedOpen = advancedOpen || fields.keys.any(::isAdvancedInvoiceField),
                        )
                    }
                }
                is FiscalResult.Failure -> {
                    setState { copy(previewing = false) }
                    showServerErrors(r.error)
                }
            }
        }
    }

    private fun submit() {
        val s = currentState
        if (s.sending || s.phase != FiscalIssuePhase.Form) return
        setState { copy(banner = null) }
        if (!checkLocally()) return
        setState { copy(sending = true) }
        viewModelScope.launch {
            val body = toInvoiceBody(s.draft)
            val r = if (s.substitute != null) {
                repository.substitute(s.substitute.id, FiscalSubstituicao(body, s.reason.name, s.reasonText.trim()), s.idempotencyKey)
            } else {
                repository.issue(body, s.idempotencyKey)
            }
            when (r) {
                is FiscalResult.Ok -> {
                    setState { copy(sending = false, phase = FiscalIssuePhase.Progress(r.value.id)) }
                    sendEffect(FiscalIssueEffect.Accepted(r.value.id))
                    track(r.value.id)
                }
                is FiscalResult.Failure -> {
                    setState { copy(sending = false) }
                    showServerErrors(r.error)
                }
            }
        }
    }

    /** Acompanha a nota até ela decidir; depois de [slowAfterMillis] avisa que pode fechar. */
    private fun track(id: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var elapsed = 0L
            while (true) {
                val doc = repository.invoice(id).getOrNull()
                val phase = currentState.phase as? FiscalIssuePhase.Progress ?: return@launch
                val slow = elapsed >= slowAfterMillis
                setState { copy(phase = phase.copy(invoice = doc ?: phase.invoice, slow = slow)) }
                if (doc != null && !isPendingStatus(doc.status)) return@launch
                if (pollIntervalMillis <= 0) return@launch
                delay(pollIntervalMillis)
                elapsed += pollIntervalMillis
            }
        }
    }

    companion object {
        /** Chave do erro do motivo da substituição. */
        const val REASON_FIELD: String = "motivoDescricao"
    }
}

/** O campo [key] mudou entre [a] e [b]? (o erro dele some ao digitar). */
private fun fieldChanged(key: String, a: FiscalInvoiceDraft, b: FiscalInvoiceDraft): Boolean = when (key) {
    "tomador.documento" -> a.tomadorDocumento != b.tomadorDocumento || a.tipoTomador != b.tipoTomador
    "tomador.nome" -> a.tomadorNome != b.tomadorNome
    "tomador.email" -> a.tomadorEmail != b.tomadorEmail
    "tomador.telefone" -> a.tomadorTelefone != b.tomadorTelefone
    "tomador.endereco.pais" -> a.tomadorPais != b.tomadorPais
    "competencia" -> a.competencia != b.competencia
    "servico.codigoTributacaoNacional" -> a.codigoTributacaoNacional != b.codigoTributacaoNacional
    "servico.descricao" -> a.descricao != b.descricao
    "valores.servico" -> a.valorServico != b.valorServico
    "valores.iss.aliquota" -> a.aliquotaIss != b.aliquotaIss
    else -> if (key.startsWith("tomador.endereco")) a.tomadorEndereco != b.tomadorEndereco
    else if (key.startsWith("valores.totaisAproximados")) a.totaisFederal != b.totaisFederal || a.totaisEstadual != b.totaisEstadual || a.totaisMunicipal != b.totaisMunicipal
    else false
}
