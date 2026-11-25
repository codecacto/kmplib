package br.com.codecacto.kmplib.fiscal

import androidx.lifecycle.viewModelScope
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════ Lista de notas ═════════════════════════════

data class FiscalInvoiceListState(
    val loading: Boolean = true,
    /** Puxar para atualizar (a lista fica; só o indicador gira). */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: FiscalError? = null,
    val items: List<FiscalDocumento> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val query: String = "",
    val status: String? = null,
    /** `ref` fixo (ex.: só as notas de uma locação). */
    val ref: String? = null,
) {
    /** A busca ainda não alcançou o que foi digitado: a lista some e vira "Buscando…" (nunca o resultado velho). */
    val searching: Boolean get() = loading && !refreshing
    val filtered: Boolean get() = query.isNotBlank() || status != null
}

sealed interface FiscalInvoiceListAction {
    data object Load : FiscalInvoiceListAction
    data object Refresh : FiscalInvoiceListAction
    data object LoadMore : FiscalInvoiceListAction
    data class Search(val query: String) : FiscalInvoiceListAction
    data class FilterStatus(val status: String?) : FiscalInvoiceListAction
}

sealed interface FiscalInvoiceListEffect

/**
 * Lista de notas (o `InvoiceList` da weblib, no app): busca por partes (com espera curta), filtro por situação,
 * paginação e puxar para atualizar. A tela chama [FiscalInvoiceListAction.Refresh] no `ON_RESUME`.
 */
class FiscalInvoiceListViewModel(
    private val repository: FiscalRepository,
    ref: String? = null,
    private val pageSize: Int = 20,
    private val searchDebounceMillis: Long = 350L,
) : BaseViewModel<FiscalInvoiceListState, FiscalInvoiceListAction, FiscalInvoiceListEffect>(FiscalInvoiceListState(ref = ref)) {

    private var job: Job? = null
    private var generation = 0

    override fun onAction(action: FiscalInvoiceListAction) {
        when (action) {
            FiscalInvoiceListAction.Load -> fetch(refreshing = false, debounce = false)
            FiscalInvoiceListAction.Refresh -> fetch(refreshing = currentState.items.isNotEmpty() || !currentState.loading, debounce = false)
            FiscalInvoiceListAction.LoadMore -> loadMore()
            is FiscalInvoiceListAction.Search -> {
                if (action.query == currentState.query) return
                setState { copy(query = action.query) }
                fetch(refreshing = false, debounce = true)
            }
            is FiscalInvoiceListAction.FilterStatus -> {
                setState { copy(status = action.status) }
                fetch(refreshing = false, debounce = false)
            }
        }
    }

    private fun filterFor(page: Int) = currentState.let { s ->
        FiscalDocumentoFiltro(status = s.status, ref = s.ref, q = s.query.trim().takeIf { it.isNotEmpty() }, pagina = page, tamanho = pageSize)
    }

    private fun fetch(refreshing: Boolean, debounce: Boolean) {
        job?.cancel()
        val gen = ++generation
        setState { if (refreshing) copy(refreshing = true, error = null) else copy(loading = true, items = emptyList(), error = null) }
        job = viewModelScope.launch {
            if (debounce) delay(searchDebounceMillis)
            val r = repository.invoices(filterFor(1))
            if (gen != generation) return@launch
            when (r) {
                is FiscalResult.Ok -> setState {
                    copy(loading = false, refreshing = false, items = r.value.items, page = 1, hasMore = r.value.temMais, error = null)
                }
                is FiscalResult.Failure -> setState { copy(loading = false, refreshing = false, error = r.error) }
            }
        }
    }

    private fun loadMore() {
        val s = currentState
        if (!s.hasMore || s.loadingMore || s.loading) return
        val gen = generation
        setState { copy(loadingMore = true) }
        viewModelScope.launch {
            val r = repository.invoices(filterFor(s.page + 1))
            if (gen != generation) return@launch
            when (r) {
                is FiscalResult.Ok -> setState {
                    val known = items.map { it.id }.toSet()
                    copy(loadingMore = false, items = items + r.value.items.filter { it.id !in known }, page = s.page + 1, hasMore = r.value.temMais)
                }
                is FiscalResult.Failure -> setState { copy(loadingMore = false) }
            }
        }
    }
}

// ═════════════════════════════ Detalhe da nota ═════════════════════════════

/** Qual arquivo está sendo baixado. */
enum class FiscalFileKind { PDF, XML }

data class FiscalInvoiceDetailState(
    val loading: Boolean = true,
    val error: FiscalError? = null,
    val invoice: FiscalDocumento? = null,
    val downloading: FiscalFileKind? = null,
    val showCancel: Boolean = false,
    val cancelReason: MotivoCancelamento = MotivoCancelamento.ERRO_EMISSAO,
    val cancelText: String = "",
    val cancelError: FiscalFieldError? = null,
    val cancelling: Boolean = false,
    val reconsulting: Boolean = false,
    /** Recusa de uma ação (download, cancelar, conferir) — mensagem curta na tela. */
    val actionError: FiscalError? = null,
)

sealed interface FiscalInvoiceDetailAction {
    data object Load : FiscalInvoiceDetailAction
    data class Download(val kind: FiscalFileKind) : FiscalInvoiceDetailAction
    data object AskCancel : FiscalInvoiceDetailAction
    data class SetCancelReason(val reason: MotivoCancelamento) : FiscalInvoiceDetailAction
    data class SetCancelText(val text: String) : FiscalInvoiceDetailAction
    data object ConfirmCancel : FiscalInvoiceDetailAction
    data object DismissCancel : FiscalInvoiceDetailAction
    data object Reconsult : FiscalInvoiceDetailAction
    data object DismissActionError : FiscalInvoiceDetailAction
}

sealed interface FiscalInvoiceDetailEffect {
    /** O arquivo chegou: a tela abre no visualizador do sistema (ou oferece compartilhar). */
    data class OpenFile(val file: FiscalArquivo) : FiscalInvoiceDetailEffect
    data object Cancelled : FiscalInvoiceDetailEffect
    data object Reconsulted : FiscalInvoiceDetailEffect
}

/** Detalhe de uma nota: dados, PDF/XML, cancelar com motivo, conferir de novo. Consulta sozinho enquanto a nota não decide. */
class FiscalInvoiceDetailViewModel(
    private val repository: FiscalRepository,
    private val invoiceId: String,
    private val pollIntervalMillis: Long = 3_000L,
) : BaseViewModel<FiscalInvoiceDetailState, FiscalInvoiceDetailAction, FiscalInvoiceDetailEffect>(FiscalInvoiceDetailState()) {

    private var pollJob: Job? = null

    init {
        onAction(FiscalInvoiceDetailAction.Load)
    }

    override fun onAction(action: FiscalInvoiceDetailAction) {
        when (action) {
            FiscalInvoiceDetailAction.Load -> load()
            is FiscalInvoiceDetailAction.Download -> download(action.kind)
            FiscalInvoiceDetailAction.AskCancel -> setState { copy(showCancel = true, cancelText = "", cancelError = null) }
            is FiscalInvoiceDetailAction.SetCancelReason -> setState { copy(cancelReason = action.reason) }
            is FiscalInvoiceDetailAction.SetCancelText -> setState { copy(cancelText = action.text, cancelError = null) }
            FiscalInvoiceDetailAction.ConfirmCancel -> cancel()
            FiscalInvoiceDetailAction.DismissCancel -> setState { copy(showCancel = false, cancelError = null) }
            FiscalInvoiceDetailAction.Reconsult -> reconsult()
            FiscalInvoiceDetailAction.DismissActionError -> setState { copy(actionError = null) }
        }
    }

    private fun load() {
        setState { copy(loading = invoice == null, error = null) }
        viewModelScope.launch {
            when (val r = repository.invoice(invoiceId)) {
                is FiscalResult.Ok -> {
                    setState { copy(loading = false, invoice = r.value, error = null) }
                    schedulePoll(r.value)
                }
                is FiscalResult.Failure -> setState { copy(loading = false, error = if (invoice == null) r.error else null) }
            }
        }
    }

    private fun schedulePoll(doc: FiscalDocumento) {
        pollJob?.cancel()
        if (!isPendingStatus(doc.status) || pollIntervalMillis <= 0) return
        pollJob = viewModelScope.launch {
            delay(pollIntervalMillis)
            load()
        }
    }

    private fun download(kind: FiscalFileKind) {
        val inv = currentState.invoice ?: return
        if (currentState.downloading != null) return
        setState { copy(downloading = kind, actionError = null) }
        viewModelScope.launch {
            val r = if (kind == FiscalFileKind.PDF) repository.pdf(inv.id, inv.numeroNfse) else repository.xml(inv.id, inv.numeroNfse)
            when (r) {
                is FiscalResult.Ok -> {
                    setState { copy(downloading = null) }
                    sendEffect(FiscalInvoiceDetailEffect.OpenFile(r.value))
                }
                is FiscalResult.Failure -> setState { copy(downloading = null, actionError = r.error) }
            }
        }
    }

    private fun cancel() {
        val s = currentState
        val inv = s.invoice ?: return
        if (s.cancelling) return
        if (s.cancelText.trim().length < FISCAL_CANCEL_MIN_LENGTH) {
            setState { copy(cancelError = FiscalFieldError.Issue(FiscalIssue.DESCRICAO_CURTA)) }
            return
        }
        setState { copy(cancelling = true) }
        viewModelScope.launch {
            when (val r = repository.cancel(inv.id, FiscalCancelamento(s.cancelReason, s.cancelText.trim()))) {
                is FiscalResult.Ok -> {
                    setState { copy(cancelling = false, showCancel = false, invoice = r.value) }
                    sendEffect(FiscalInvoiceDetailEffect.Cancelled)
                    schedulePoll(r.value)
                }
                is FiscalResult.Failure -> setState {
                    copy(
                        cancelling = false,
                        cancelError = r.error.fields["descricao"]?.let { FiscalFieldError.Text(it) },
                        actionError = r.error.takeIf { r.error.fields["descricao"] == null },
                    )
                }
            }
        }
    }

    private fun reconsult() {
        val inv = currentState.invoice ?: return
        if (currentState.reconsulting) return
        setState { copy(reconsulting = true, actionError = null) }
        viewModelScope.launch {
            when (val r = repository.reconsult(inv.id)) {
                is FiscalResult.Ok -> {
                    setState { copy(reconsulting = false, invoice = r.value) }
                    sendEffect(FiscalInvoiceDetailEffect.Reconsulted)
                    schedulePoll(r.value)
                }
                is FiscalResult.Failure -> setState { copy(reconsulting = false, actionError = r.error) }
            }
        }
    }
}
