package br.com.codecacto.kmplib.fiscal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.fiscal.generated.resources.*
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppSearchField
import br.com.codecacto.kmplib.ui.components.BackTopBar
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.RefreshableBox

/**
 * Lista de notas fiscais: busca por partes, filtro por situação, puxar para atualizar, recarrega ao voltar à tela
 * (`ON_RESUME`), linha clicável inteira e o "Nova nota" no topo e no estado vazio.
 *
 * @param ref só as notas de um registro do produto (ex. `locacao:123`); `null` = todas.
 * @param onIssue abre a emissão de nota nova; `null` esconde o botão.
 */
@Composable
fun FiscalInvoiceListScreen(
    repository: FiscalRepository,
    onBack: () -> Unit,
    onOpenInvoice: (FiscalDocumento) -> Unit,
    onIssue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    ref: String? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val vm = viewModel(key = "kmplib-fiscal-notas-${ref.orEmpty()}") { FiscalInvoiceListViewModel(repository, ref) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Quem emite/cancela e volta encontra a lista atualizada (e o primeiro ON_RESUME é a carga inicial).
    LifecycleResumeEffect(vm) {
        vm.onAction(FiscalInvoiceListAction.Refresh)
        onPauseOrDispose { }
    }
    Scaffold(
        modifier = modifier.testTag(FiscalTestTags.LISTA),
        topBar = {
            BackTopBar(
                title = fiscalString(Res.string.fiscal_notas_titulo),
                onBackClick = onBack,
                actions = {
                    if (onIssue != null) {
                        IconButton(onClick = onIssue, modifier = Modifier.testTag(FiscalTestTags.LISTA_NOVA)) {
                            Icon(Icons.Default.Add, contentDescription = fiscalString(Res.string.fiscal_nova_nota))
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            AppSearchField(
                query = state.query,
                onQueryChange = { vm.onAction(FiscalInvoiceListAction.Search(it)) },
                placeholder = fiscalString(Res.string.fiscal_buscar_notas),
                saveable = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(FiscalTestTags.LISTA_BUSCA),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = state.status == null, onClick = { vm.onAction(FiscalInvoiceListAction.FilterStatus(null)) }, label = { Text(fiscalString(Res.string.fiscal_todas)) })
                FiscalStatus.ALL.forEach { s ->
                    FilterChip(
                        selected = state.status == s,
                        onClick = { vm.onAction(FiscalInvoiceListAction.FilterStatus(if (state.status == s) null else s)) },
                        label = { Text(fiscalStatusLabel(s)) },
                        modifier = Modifier.testTag("fiscal-notas-filtro-${s.lowercase()}"),
                    )
                }
            }
            RefreshableBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.onAction(FiscalInvoiceListAction.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            if (state.query.isNotBlank()) Text(fiscalString(Res.string.fiscal_buscando), modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    state.error != null && state.items.isEmpty() -> ErrorState(
                        message = fiscalErrorMessage(state.error!!),
                        onRetry = { vm.onAction(FiscalInvoiceListAction.Load) },
                    )
                    state.items.isEmpty() -> EmptyStateScroll(
                        filtered = state.filtered,
                        onIssue = onIssue,
                    )
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(state.items, key = { it.id }) { inv ->
                            InvoiceRow(inv, onClick = { onOpenInvoice(inv) })
                            HorizontalDivider()
                        }
                        if (state.hasMore) item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) CircularProgressIndicator()
                                else TextButton(onClick = { vm.onAction(FiscalInvoiceListAction.LoadMore) }) { Text(fiscalString(Res.string.fiscal_carregar_mais)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateScroll(filtered: Boolean, onIssue: (() -> Unit)?) {
    // Em LazyColumn para o puxar-para-atualizar funcionar também com a lista vazia.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = fiscalString(if (filtered) Res.string.fiscal_vazio_busca else Res.string.fiscal_vazio_titulo),
                description = if (filtered) null else fiscalString(Res.string.fiscal_vazio_desc),
                action = if (onIssue != null && !filtered) {
                    { AppButton(text = fiscalString(Res.string.fiscal_nova_nota), onClick = onIssue, icon = Icons.Default.Add) }
                } else null,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun InvoiceRow(inv: FiscalDocumento, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("${FiscalTestTags.LISTA_ITEM}-${inv.id}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                inv.numeroNfse?.let { fiscalString(Res.string.fiscal_detalhe_titulo, it) } ?: fiscalString(Res.string.fiscal_sem_numero),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            )
            inv.valores?.tomadorNome?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val sub = listOfNotNull(
                inv.valores?.tomadorDocumento?.let { formatDocument(it) },
                formatFiscalDateTime(inv.autorizadoEm ?: inv.criadoEm),
                if (inv.ambiente == FiscalAmbiente.PRODUCAO_RESTRITA) fiscalString(Res.string.fiscal_nota_de_teste) else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatFiscalMoney(inv.valores?.servico), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            FiscalStatusBadge(inv.status)
        }
    }
}
