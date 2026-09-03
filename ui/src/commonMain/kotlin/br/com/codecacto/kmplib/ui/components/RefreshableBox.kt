/*
 * `RefreshableBox` mora aqui, e não em `kmplib-sync`, desde 03/set/2026.
 *
 * O package sempre foi `ui.components` — o ARQUIVO é que estava no módulo errado. A consequência não
 * era cosmética: um app online-only que só queria "puxar para atualizar" tinha de declarar
 * `kmplib-sync` e arrastar junto o SQLDelight, a outbox e o motor de sincronização de que não usa
 * nada. Foi o que apareceu no bootstrap do Backhand (arquétipo B online-only, sem cache local).
 *
 * `SyncRefreshBox` continua no `sync`, e ali é o lugar certo: ele depende do `RestCrudSyncEngine`.
 * Como `kmplib-sync` já declara `api(project(":kmplib-ui"))`, quem hoje importa os dois pelo módulo
 * de sync continua enxergando ambos — a mudança é aditiva para todos os consumidores.
 */
package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
