// ⚠️ O NOME DESTE ARQUIVO É PARTE DO CONTRATO.
//
// Ele declara `package br.com.codecacto.kmplib.ui.components`, o mesmo do `RefreshableBox.kt` do
// `:kmplib-ui`. Kotlin gera a classe-fachada a partir do NOME DO ARQUIVO, então dois arquivos
// homônimos no mesmo package — ainda que em módulos diferentes — produzem duas
// `ui/components/RefreshableBoxKt.class`. No umbrella, que traz `:kmplib-ui` e `:kmplib-sync`
// juntos, uma esconde a outra: o app compila e reclama de `Unresolved reference 'RefreshableBox'`
// apontando para as PRÓPRIAS telas, sem uma palavra sobre colisão. Foi o que aconteceu entre
// 2.176.0 e 2.177.0, em 13 arquivos do NeuroCoreX de uma vez.
//
// Enquanto o package for compartilhado, o nome do arquivo precisa ser único. Se um dia for preciso
// renomear a função daqui, renomeie o arquivo junto.

package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.sync.rest.RestCrudSyncEngine
import kotlinx.coroutines.launch

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
