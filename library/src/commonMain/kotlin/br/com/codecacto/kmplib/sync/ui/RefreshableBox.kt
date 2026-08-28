package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.sync.rest.RestCrudSyncEngine
import kotlinx.coroutines.launch

/**
 * O que deve acontecer quando o usuário puxa a lista para baixo. Função de decisão pura (testável),
 * separada do Compose.
 */
enum class RefreshAction {
    /** Dispara o ciclo de sync/refetch. */
    Sync,

    /** Sem rede: não vale ir à rede — avisa o usuário e encerra o indicador. */
    Offline,

    /** Gesto desabilitado (ex.: tela em edição): ignora. */
    Ignore,
}

/**
 * Decide a ação do pull-to-refresh. `enabled=false` ⇒ [RefreshAction.Ignore]; offline ⇒
 * [RefreshAction.Offline] (degradação honesta — não deixa o spinner girar contra o vazio); caso
 * contrário [RefreshAction.Sync].
 *
 * `isOnline = null` significa "app não observa conectividade" ⇒ tenta sincronizar (o transporte
 * decide).
 */
fun resolveRefreshAction(isOnline: Boolean?, enabled: Boolean = true): RefreshAction = when {
    !enabled -> RefreshAction.Ignore
    isOnline == false -> RefreshAction.Offline
    else -> RefreshAction.Sync
}

/**
 * Wrapper de **pull-to-refresh** (padrão-ouro: o [PullToRefreshBox] oficial do Material 3 — a lib não
 * reinventa gesto nem indicador). Baseline de resiliência/UX: toda lista do ecossistema puxa para
 * atualizar.
 *
 * `isRefreshing` é hoisted (vem do State do ViewModel). Para que o gesto funcione com a lista vazia,
 * o filho deve ser rolável — use [ScrollableFillBox] / [ErrorState] no branch de erro/vazio.
 *
 * ```kotlin
 * RefreshableBox(isRefreshing = state.isRefreshing, onRefresh = { vm.dispatch(Action.Refresh) }) {
 *     when {
 *         state.error != null -> ErrorState(state.error, onRetry = { vm.dispatch(Action.Refresh) })
 *         state.items.isEmpty() -> ScrollableFillBox { EmptyState(...) }
 *         else -> LazyColumn { ... }
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (enabled) onRefresh() },
        modifier = modifier,
        content = content,
    )
}

/**
 * Pull-to-refresh **offline-first**: o gesto dispara um **ciclo de sync completo** do
 * [RestCrudSyncEngine] (drena a outbox → só então reconcilia por GET), não um mero refetch. É o que
 * o usuário espera ao puxar depois de ter criado registros sem rede: primeiro o que ele fez sobe,
 * depois a lista volta consistente.
 *
 * **Degradação sem rede:** se [connectivity] indica offline, nada vai à rede — o indicador encerra na
 * hora e [onOffline] é chamado (mostre um `Toast`/`OfflineBanner`; o aviso bloqueante global é o
 * `ConnectivityGate`). A outbox permanece intacta e o engine sincroniza sozinho ao reconectar.
 *
 * O `isRefreshing` é gerenciado internamente (o ciclo é `suspend`), então a tela não precisa de flag
 * no State. Para refetch simples (app online-only, sem offline-first), use [RefreshableBox].
 *
 * ```kotlin
 * SyncRefreshBox(engine = koinInject(), connectivity = koinInject(), onOffline = { toast("Sem conexão") }) {
 *     LazyColumn { ... }
 * }
 * ```
 *
 * @param connectivity `null` ⇒ não checa rede (sempre tenta sincronizar).
 * @param onSyncResult recebe `true` se o ciclo completou (útil para snackbar de "sincronizado").
 */
@Composable
fun SyncRefreshBox(
    engine: RestCrudSyncEngine,
    modifier: Modifier = Modifier,
    connectivity: ConnectivityObserver? = null,
    enabled: Boolean = true,
    onOffline: () -> Unit = {},
    onSyncResult: (Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    if (connectivity != null) {
        DisposableEffect(connectivity) {
            connectivity.start()
            onDispose { connectivity.stop() }
        }
    }
    val isOnline: Boolean? =
        if (connectivity != null) connectivity.isOnline.collectAsState().value else null

    RefreshableBox(
        isRefreshing = isRefreshing,
        modifier = modifier,
        enabled = enabled,
        onRefresh = {
            when (resolveRefreshAction(isOnline, enabled)) {
                RefreshAction.Ignore -> Unit
                RefreshAction.Offline -> {
                    connectivity?.refresh()
                    onOffline()
                }

                RefreshAction.Sync -> scope.launch {
                    isRefreshing = true
                    try {
                        onSyncResult(engine.syncNow())
                    } finally {
                        isRefreshing = false
                    }
                }
            }
        },
        content = content,
    )
}
