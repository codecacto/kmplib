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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
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

/** Em que ponto está o gesto de puxar-para-atualizar (2.327.0) — o que um indicador próprio escreve. */
enum class RefreshIndicatorPhase {
    /** Parado (nada puxado). */
    Idle,

    /** Puxando, ainda antes do limiar — soltar agora NÃO atualiza ("Puxe para atualizar"). */
    Pulling,

    /** Passou do limiar — soltar atualiza ("Solte para atualizar"). */
    ReadyToRelease,

    /** Atualizando ("Atualizando…"). */
    Refreshing,
}

/**
 * Fase do gesto a partir da fração puxada ([distanceFraction]: `0` parado, `1` no limiar, `> 1` além) e
 * de [isRefreshing]. Pura, testada.
 */
fun refreshIndicatorPhase(distanceFraction: Float, isRefreshing: Boolean): RefreshIndicatorPhase = when {
    isRefreshing -> RefreshIndicatorPhase.Refreshing
    distanceFraction >= 1f -> RefreshIndicatorPhase.ReadyToRelease
    distanceFraction > 0f -> RefreshIndicatorPhase.Pulling
    else -> RefreshIndicatorPhase.Idle
}

/**
 * O que o indicador próprio de [RefreshableBox] recebe a cada quadro (2.327.0).
 *
 * @property distanceFraction quanto foi puxado: `0` parado, `1` no limiar de soltar, `> 1` além (é a
 *   `PullToRefreshState.distanceFraction` do Material 3) — para animar altura, opacidade, rotação.
 * @property isRefreshing a atualização está em curso.
 */
@Immutable
data class RefreshIndicatorState(val distanceFraction: Float, val isRefreshing: Boolean) {
    val phase: RefreshIndicatorPhase get() = refreshIndicatorPhase(distanceFraction, isRefreshing)
}

/**
 * Wrapper de **pull-to-refresh** (padrão-ouro: o [PullToRefreshBox] oficial do Material 3 — a lib não
 * reinventa gesto nem indicador). Baseline de resiliência/UX: toda lista do ecossistema puxa para
 * atualizar.
 *
 * `isRefreshing` é hoisted (vem do State do ViewModel). Para que o gesto funcione com a lista vazia,
 * o filho deve ser rolável — use [ScrollableFillBox] / [ErrorState] no branch de erro/vazio.
 * ⚠️ O [ErrorState] **já é** um `ScrollableFillBox`: vai direto, nunca `ScrollableFillBox { ErrorState(...) }`
 * (dois roláveis verticais aninhados derrubam o app — ver o KDoc do [ScrollableFillBox]).
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
 *
 * ### Indicador próprio (2.327.0)
 * Projeto com protótipo que desenha o próprio indicador ("Puxe para atualizar" → "Solte para atualizar"
 * → "Atualizando…") passa [indicator]: ele é desenhado no lugar do círculo do Material, por CIMA do
 * conteúdo, no `BoxScope` do gesto (alinhe com `Modifier.align(Alignment.TopCenter)`), e recebe a fase e
 * a fração puxada a cada quadro. O gesto, o limiar e a recarga continuam os do [PullToRefreshBox]
 * oficial — só o desenho muda.
 * ```kotlin
 * RefreshableBox(isRefreshing = state.refreshing, onRefresh = { … }, indicator = { s ->
 *     val texto = when (s.phase) {
 *         RefreshIndicatorPhase.Idle -> return@RefreshableBox
 *         RefreshIndicatorPhase.Pulling -> stringResource(Res.string.puxe)
 *         RefreshIndicatorPhase.ReadyToRelease -> stringResource(Res.string.solte)
 *         RefreshIndicatorPhase.Refreshing -> stringResource(Res.string.atualizando)
 *     }
 *     Text(texto, Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer { alpha = s.distanceFraction.coerceIn(0f, 1f) })
 * }) { … }
 * ```
 * Com indicador próprio, a fase [RefreshIndicatorPhase.Refreshing] é anunciada pelo app (o círculo do
 * Material não aparece) — escreva o "Atualizando…" visível.
 *
 * @param indicator `null` (default) = o indicador do Material 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicator: (@Composable BoxScope.(RefreshIndicatorState) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (enabled) onRefresh() },
        modifier = modifier,
        state = state,
        indicator = {
            if (indicator == null) {
                PullToRefreshDefaults.Indicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else {
                indicator(RefreshIndicatorState(state.distanceFraction, isRefreshing))
            }
        },
        content = content,
    )
}
