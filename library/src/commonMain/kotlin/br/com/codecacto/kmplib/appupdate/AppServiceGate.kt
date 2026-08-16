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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.platform.getUrlLauncher

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
 * @param check Consulta o backend do projeto. Chamada uma vez por valor de [key].
 * @param key Reexecuta a consulta quando muda (ex.: um contador de "tentar de novo").
 * @param formatUntil Formata o fim previsto da manutenção. Default: só o epoch não é mostrado —
 *   sem formatador, a linha de previsão é omitida, porque "1755302400000" não é informação.
 */
@Composable
fun AppServiceGate(
    check: suspend () -> AppServiceStatus,
    key: Any? = Unit,
    texts: AppServiceTexts = AppServiceTexts(),
    updateTexts: AppUpdateTexts = AppUpdateTexts(),
    formatUntil: ((Long) -> String)? = null,
    content: @Composable () -> Unit,
) {
    var status by remember(key) { mutableStateOf(AppServiceStatus()) }
    var tentativa by remember(key) { mutableStateOf(0) }
    var softDismissed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, tentativa) {
        status = check()
    }

    val manutencao = status.maintenance
    if (manutencao != null) {
        MaintenanceScreen(
            texts = texts,
            serverMessage = manutencao.message,
            untilLabel = manutencao.untilEpochMillis?.let { millis -> formatUntil?.invoke(millis) },
            onRetry = { tentativa++ },
        )
        return
    }

    when (val update = status.update) {
        is AppUpdateStatus.Hard -> HardUpdateScreen(
            texts = updateTexts,
            serverMessage = update.message,
            onUpdate = { abrirLoja(update.storeUrl) },
        )

        is AppUpdateStatus.Soft -> {
            content()
            if (!softDismissed) {
                SoftUpdateDialog(
                    texts = updateTexts,
                    serverMessage = update.message,
                    latestVersionName = update.latestVersionName,
                    onUpdate = {
                        softDismissed = true
                        abrirLoja(update.storeUrl)
                    },
                    onDismiss = { softDismissed = true },
                )
            }
        }

        AppUpdateStatus.None -> content()
    }
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
