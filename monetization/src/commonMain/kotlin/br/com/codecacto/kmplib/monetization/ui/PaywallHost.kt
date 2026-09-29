package br.com.codecacto.kmplib.ui.screens.paywall

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.components.RefreshableBox
import kotlinx.coroutines.launch

/**
 * **A tela Premium inteira** (2.224.0): o [PaywallViewModel] ligado à [PaywallScreen], com o que toda
 * cópia nos apps repetia —
 *
 * - **relê a cada `ON_RESUME`** (e não num `LaunchedEffect(Unit)`): quem cancela ou troca de plano na
 *   loja e volta vê o estado novo; o primeiro resume cobre a carga inicial;
 * - **puxar para atualizar** ([RefreshableBox]) — a vitrine depende de leituras remotas, e sem o
 *   gesto a única saída de uma tela vazia era matar o app;
 * - executa os efeitos: documento legal no navegador, **gestão de assinatura da loja da plataforma**
 *   (`getUrlLauncher().openSubscriptionManagement()` — Play no Android, App Store no iOS), snackbar,
 *   voltar e "Precisa de ajuda?".
 *
 * O app injeta o ViewModel (`koinViewModel()`) e navega:
 * ```kotlin
 * PaywallHost(
 *     viewModel = koinViewModel(),
 *     onClose = { navController.popBackStack() },
 *     onOpenDeveloper = { navController.navigate(Route.Developer) },
 *     texts = rememberPaywallTexts().copy(headerTitle = stringResource(Res.string.premium_titulo)),
 * )
 * ```
 *
 * @param onOpenUrl override de como abrir o documento legal; `null` = navegador do sistema.
 * @param onManageSubscription override da gestão de assinatura; `null` = a loja da plataforma.
 * @param beforePlansContent / afterPlansContent os slots da [PaywallScreen] (teste grátis, Pix do portal).
 */
@Composable
fun PaywallHost(
    viewModel: PaywallViewModel,
    onClose: () -> Unit,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
    texts: PaywallTexts = rememberPaywallTexts(),
    headerIcon: ImageVector? = null,
    beforePlansContent: (@Composable () -> Unit)? = null,
    afterPlansContent: (@Composable () -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onManageSubscription: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val urlLauncher = remember { getUrlLauncher() }
    val scope = rememberCoroutineScope()

    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnOpenDeveloper by rememberUpdatedState(onOpenDeveloper)
    val currentOnOpenUrl by rememberUpdatedState(onOpenUrl)
    val currentOnManage by rememberUpdatedState(onManageSubscription)

    LifecycleResumeEffect(viewModel) {
        viewModel.dispatch(PaywallHostAction.Load)
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaywallHostEffect.Close -> currentOnClose()
                PaywallHostEffect.OpenDeveloper -> currentOnOpenDeveloper()
                is PaywallHostEffect.OpenUrl ->
                    currentOnOpenUrl?.invoke(effect.url) ?: urlLauncher.openUrl(effect.url)
                PaywallHostEffect.OpenSubscriptionManagement ->
                    currentOnManage?.invoke() ?: urlLauncher.openSubscriptionManagement()
                // Em outra corrotina: `showSnackbar` suspende até sumir, e seguraria o próximo efeito.
                is PaywallHostEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.message) }
            }
        }
    }

    RefreshableBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.dispatch(PaywallHostAction.Refresh) },
        modifier = modifier,
    ) {
        PaywallScreen(
            state = state.paywall,
            onAction = { viewModel.dispatch(PaywallHostAction.Paywall(it)) },
            texts = texts,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.fillMaxSize(),
            headerIcon = headerIcon,
            beforePlansContent = beforePlansContent,
            afterPlansContent = afterPlansContent,
        )
    }
}
