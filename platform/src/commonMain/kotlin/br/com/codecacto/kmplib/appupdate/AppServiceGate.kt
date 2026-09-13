package br.com.codecacto.kmplib.appupdate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.codecacto.kmplib.platform.getUrlLauncher
import kotlinx.coroutines.launch

/**
 * Aviso de **manutenção programada** — o backend está fora do ar de propósito.
 *
 * Não é o mesmo que "sem internet" (isso é o `ConnectivityGate`) nem que "erro 500" (isso é
 * `ErrorState` com "tentar de novo"): manutenção é um estado **declarado pelo operador**, com hora
 * prevista de volta, e a única coisa útil que o app pode fazer é dizer isso e parar.
 *
 * @param message Texto do operador. Nulo/vazio ⇒ o gate usa o default de [AppServiceTexts].
 * @param untilEpochMillis Fim previsto, em epoch millis. Nulo quando o operador não estimou —
 *   preferível a inventar um horário, que vira promessa quebrada.
 */
data class MaintenanceNotice(
    val message: String? = null,
    val untilEpochMillis: Long? = null,
)

/**
 * O que o app precisa saber **antes de abrir**: se pode rodar (manutenção) e se esta versão ainda
 * serve (force update).
 *
 * As duas coisas viajam juntas porque são a mesma pergunta feita na mesma hora, e separá-las em duas
 * chamadas faria a abertura do app depender de dois round-trips — dos quais o segundo só existe para
 * descobrir que o primeiro já tinha bloqueado.
 */
data class AppServiceStatus(
    val update: AppUpdateStatus = AppUpdateStatus.None,
    val maintenance: MaintenanceNotice? = null,
)

/** Textos da tela de manutenção. Defaults pt-BR; a mensagem do servidor tem prioridade. */
data class AppServiceTexts(
    val maintenanceTitle: String = "Estamos em manutenção",
    val maintenanceMessage: String =
        "O serviço está passando por uma atualização. Tente novamente em alguns minutos.",
    val maintenanceRetryButton: String = "Tentar de novo",
    val maintenanceIconContentDescription: String = "Manutenção programada",
    /** Prefixo da linha de previsão. O horário é formatado pelo app e concatenado. */
    val maintenanceUntilPrefix: String = "Previsão de retorno:",
)

/**
 * Variante do [AppUpdateGate] para app cujo estado **não vem do admin-api central**.
 *
 * ## Por que este overload existe
 *
 * O [AppUpdateGate] fala com `GET {adminApiBaseUrl}/public/app-version` — o catálogo central da
 * fábrica. Projeto de **parceria** (NeuroCoreX, Clinnota, StatusHub) tem backend próprio e admin
 * próprio: o estado de versão e de manutenção mora lá, e apontar o app para o admin central
 * significaria manter a mesma configuração em dois lugares, onde um deles não é o dono do produto.
 *
 * Aqui quem consulta é o app, por [check]; a lib entra com a **política e a UI**, que é justamente o
 * que não deve ser reimplementado a cada projeto — a tela bloqueante sem botão de voltar, o diálogo
 * dispensável, e a decisão de qual vence.
 *
 * ## A ordem entre manutenção e atualização não é arbitrária
 *
 * Manutenção **vence**. Durante uma janela de manutenção, mandar a pessoa à loja atualizar produz um
 * app novo que também não funciona — e agora sem nenhuma explicação do porquê.
 *
 * ## Falha na consulta libera, nunca bloqueia
 *
 * Igual ao [AppUpdateGate]: [check] é best-effort e deve devolver [AppServiceStatus] vazio quando
 * não conseguir perguntar. Um gate que bloqueia por não conseguir consultar transforma qualquer
 * soluço de rede numa manutenção fantasma — que ninguém consegue desligar, porque desligá-la exige
 * a mesma rede.
 *
 * ```kotlin
 * AppServiceGate(check = { repository.estadoDoServico() }) {
 *     AppNavHost()
 * }
 * ```
 *
 * ## Reconsultar não desmonta o app (2.204.0)
 *
 * O [content] é composto **sempre no mesmo lugar da árvore**, e manutenção/atualização obrigatória
 * entram **por cima** dele (opaco, sem toque, sem voltar, fora da acessibilidade) — como o
 * `ConnectivityGate` e o `AppLockGate`. Uma reconsulta mantém o último status até a resposta chegar;
 * a pilha de navegação sobrevive a qualquer número delas, inclusive a uma janela de manutenção que
 * abre e fecha com o app aberto. A dispensa da atualização opcional vale **por versão**: dispensou a
 * 1.4.0, ela não volta; o servidor passou a recomendar a 1.5.0, o aviso volta.
 *
 * Consequência a conhecer: sob a tela de bloqueio o app continua vivo (e em `RESUMED`) — quem toca
 * mídia ou faz polling na raiz deve pausar por conta própria se isso importar.
 *
 * ## Reconsultar ao voltar ao primeiro plano
 *
 * `recheckOnForeground = true` pergunta de novo quando o app volta do segundo plano (`ON_STOP` →
 * `ON_START`) — é assim que uma manutenção ligada no admin chega a quem já estava com o app aberto.
 * Prefira isto a trocar o [key] num `LifecycleResumeEffect`: aquele truque dispara também na
 * abertura (consulta dupla) e na volta de qualquer diálogo do sistema.
 *
 * @param check Consulta o backend do projeto. Pedidos que chegam com uma consulta em voo são
 *   atendidos por ela — nunca há duas simultâneas. Exceção lançada aqui é tratada como "não consegui
 *   perguntar" e libera.
 * @param key Reexecuta a consulta quando muda. **Não zera nada**: status e dispensa sobrevivem.
 * @param formatUntil Formata o fim previsto da manutenção. Default: só o epoch não é mostrado —
 *   sem formatador, a linha de previsão é omitida, porque "1755302400000" não é informação.
 * @param recheckOnForeground Reconsulta ao voltar do segundo plano. Default `false`.
 */
@Composable
fun AppServiceGate(
    check: suspend () -> AppServiceStatus,
    key: Any? = Unit,
    texts: AppServiceTexts = AppServiceTexts(),
    updateTexts: AppUpdateTexts = AppUpdateTexts(),
    formatUntil: ((Long) -> String)? = null,
    recheckOnForeground: Boolean = false,
    content: @Composable () -> Unit,
) {
    // SEM chave: o estado vive o tempo do gate. `remember(key)` era o defeito da 2.203.0.
    val state = remember { AppServiceGateState() }
    val currentCheck = rememberUpdatedState(check)
    // As consultas saem num escopo que NÃO é o do efeito: trocar o [key] com uma consulta em voo não
    // a cancela para abrir outra — ela responde, e o pedido novo é atendido por ela.
    val scope = rememberCoroutineScope()
    val consultar: () -> Unit = remember(state, scope) {
        { scope.launch { state.refresh { currentCheck.value() } } }
    }

    LaunchedEffect(key) { consultar() }

    if (recheckOnForeground) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, consultar) {
            val tracker = ForegroundRecheck()
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> tracker.onStop()
                    Lifecycle.Event.ON_START -> if (tracker.onStart()) consultar()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    ServiceGateHost(
        state = state,
        texts = texts,
        updateTexts = updateTexts,
        formatUntil = formatUntil,
        onRetry = consultar,
        content = content,
    )
}

/**
 * A casca comum aos dois gates: [content] num lugar fixo da árvore, bloqueio por cima.
 *
 * Chamar `content()` em ramos diferentes de um `when` parece equivalente e não é — cada ramo é um
 * grupo de composição, e trocar de ramo descarta tudo o que foi lembrado dentro (a pilha do
 * `NavHost`, inclusive).
 */
@Composable
internal fun ServiceGateHost(
    state: AppServiceGateState,
    texts: AppServiceTexts,
    updateTexts: AppUpdateTexts,
    formatUntil: ((Long) -> String)?,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    val blocking = state.isBlocking
    val foco = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    LaunchedEffect(blocking) {
        if (blocking) {
            foco.clearFocus()
            teclado?.hide()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (blocking) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            content()
        }

        state.softUpdateToOffer?.let { soft ->
            SoftUpdateDialog(
                texts = updateTexts,
                serverMessage = soft.message,
                latestVersionName = soft.latestVersionName,
                onUpdate = {
                    state.dismissSoft()
                    abrirLoja(soft.storeUrl)
                },
                onDismiss = { state.dismissSoft() },
            )
        }

        // Manutenção VENCE atualização. As duas telas são `Surface` opaco de tela cheia, que
        // consome o toque — nada chega ao conteúdo por baixo.
        val status = state.status
        val manutencao = status.maintenance
        val update = status.update
        if (manutencao != null) {
            MaintenanceScreen(
                texts = texts,
                serverMessage = manutencao.message,
                untilLabel = manutencao.untilEpochMillis?.let { millis -> formatUntil?.invoke(millis) },
                onRetry = onRetry,
            )
        } else if (update is AppUpdateStatus.Hard) {
            HardUpdateScreen(
                texts = updateTexts,
                serverMessage = update.message,
                onUpdate = { abrirLoja(update.storeUrl) },
            )
        }

        SeguraVoltarDoSistema(enabled = blocking)
    }
}

/**
 * O voltar do sistema com a tela de bloqueio aberta não faz nada — sem isto ele desempilharia a
 * navegação escondida por baixo. `BackHandler` multiplataforma, marcado em favor do
 * `NavigationEventHandler`, que não publica variante Kotlin/Native (mesma escolha do `kmplib-ui`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
private fun SeguraVoltarDoSistema(enabled: Boolean) {
    BackHandler(enabled = enabled, onBack = { })
}

/**
 * Tela cheia de manutenção. **Tem** botão de tentar de novo — ao contrário da [HardUpdateScreen],
 * que não tem: da atualização obrigatória só se sai atualizando, mas a manutenção acaba sozinha, e
 * sem esse botão a pessoa precisaria matar o app para descobrir que já voltou.
 */
@Composable
fun MaintenanceScreen(
    texts: AppServiceTexts = AppServiceTexts(),
    serverMessage: String? = null,
    untilLabel: String? = null,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = texts.maintenanceIconContentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = texts.maintenanceTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = serverMessage?.takeIf { it.isNotBlank() } ?: texts.maintenanceMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!untilLabel.isNullOrBlank()) {
                    Text(
                        text = "${texts.maintenanceUntilPrefix} $untilLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(texts.maintenanceRetryButton)
                }
            }
        }
    }
}

private fun abrirLoja(storeUrl: String?) {
    val url = storeUrl?.trim().orEmpty()
    if (url.isNotEmpty()) getUrlLauncher().openUrl(url) else getUrlLauncher().openStorePage()
}
