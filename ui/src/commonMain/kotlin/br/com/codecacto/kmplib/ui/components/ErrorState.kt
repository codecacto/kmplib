package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_error_state_offline_message
import br.com.codecacto.kmplib.generated.resources.kmplib_error_state_offline_title
import br.com.codecacto.kmplib.generated.resources.kmplib_error_state_title
import br.com.codecacto.kmplib.generated.resources.kmplib_retry
import org.jetbrains.compose.resources.stringResource

/**
 * Textos do [ErrorState]. Sem `texts`, o componente usa [rememberErrorStateTexts] — os recursos da lib
 * no idioma do aparelho (pt-BR, en, es, pt-PT). Os defaults literais desta classe são pt-BR e servem a
 * quem monta o objeto fora da composição (teste, preview).
 */
data class ErrorStateTexts(
    val title: String = "Não foi possível carregar",
    val retryButton: String = "Tentar novamente",
    val offlineTitle: String = "Você está sem conexão",
    val offlineMessage: String = "Verifique sua internet e tente novamente.",
)

/** [ErrorStateTexts] no idioma do aparelho (2.219.0). Para trocar um texto: `.copy(title = …)`. */
@Composable
fun rememberErrorStateTexts(): ErrorStateTexts = ErrorStateTexts(
    title = stringResource(Res.string.kmplib_error_state_title),
    retryButton = stringResource(Res.string.kmplib_retry),
    offlineTitle = stringResource(Res.string.kmplib_error_state_offline_title),
    offlineMessage = stringResource(Res.string.kmplib_error_state_offline_message),
)

/**
 * Caixa **rolável** que preenche o espaço disponível e centraliza o conteúdo.
 *
 * Existe para que gestos verticais (notadamente o **pull-to-refresh** do [RefreshableBox]) continuem
 * funcionando sobre uma área "vazia" — sem um filho rolável, o `PullToRefreshBox` não recebe o gesto.
 * Também garante que o conteúdo permaneça alcançável em tela pequena / com teclado aberto.
 *
 * ## Dentro de outra rolagem: não derruba mais o app (2.242.1)
 *
 * Com altura limitada (o caso certo — corpo de tela, filho do [RefreshableBox]) ela preenche e rola.
 * **Medida sem teto de altura** — dentro de `Column(Modifier.verticalScroll)`, de um item de
 * `LazyColumn`/`LazyVerticalGrid`, de outro `ScrollableFillBox`, ou com `verticalScroll` no
 * [modifier] — ela **não rola**: ocupa a altura do conteúdo e quem rola é o pai. Até a 2.242.0 ela
 * aplicava `verticalScroll` assim mesmo e o Compose abortava (*"Vertically scrollable component was
 * measured with an infinity maximum height constraints"*; no iOS, `SIGABRT` em
 * `MetalRedrawer.draw`) — 68 casos em 21 apps na varredura de 02/out/2026, todos compilando verde.
 *
 * Aninhar continua **não sendo o desenho certo**: dentro de rolagem não há "espaço disponível" a
 * preencher, então o conteúdo deixa de ficar centrado na tela. Erro dentro de lista: um item com
 * altura limitada (`Modifier.heightIn(max = 320.dp)`) volta a centrar. `ScrollableFillBox { ErrorState(...) }`
 * é redundante — o [ErrorState] já é um `ScrollableFillBox`.
 */
@Composable
fun ScrollableFillBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // O estado fica fora da troca de ramo: se a altura ficar limitada depois, a posição sobrevive.
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier) {
        if (scrollsItself(constraints)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                contentAlignment = Alignment.Center,
                content = content,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

/**
 * O componente que rola por conta própria ([ScrollableFillBox], [ErrorState], [FormContainer]) só
 * aplica `verticalScroll` quando é medido com **altura limitada**. Sem teto, rolar é do pai — e
 * `verticalScroll` ali é exatamente o que o Compose recusa abortando o app.
 */
internal fun scrollsItself(constraints: Constraints): Boolean = constraints.hasBoundedHeight

/**
 * **Estado de erro de carregamento, rolável e com retry** — o que uma tela de lista/detalhe deve
 * mostrar quando a busca de dados falha.
 *
 * Promovido de ≥2 apps (MinhaFrota `ListStateComponents.kt`, MeuFrete `core/ui/ErrorState.kt`) e
 * motivado por um defeito real e repetido na onda de onboarding: **falha de rede virava lista vazia
 * silenciosa** (`EmptyState`), sem dizer que deu erro e sem oferecer "Tentar novamente" — o usuário
 * conclui que não tem dados.
 *
 * Diferenças (deliberadas) em relação aos vizinhos:
 * - **[EmptyState]** = "não há nada aqui" (sucesso, zero itens). Não é erro.
 * - **[ErrorModal]** = diálogo bloqueante, para falhas de **ação** (salvar, excluir).
 * - **[ErrorState]** = falha de **carregamento**, dentro do corpo da tela, não bloqueante e
 *   **rolável** (funciona sob pull-to-refresh e em tela pequena com teclado aberto).
 *
 * Tema 100% via [MaterialTheme] (sem cor hardcoded).
 *
 * ## Já rola — é o corpo da tela, não um bloco dentro de uma rolagem
 *
 * Por baixo é um [ScrollableFillBox]. Use-o **direto** no ramo de erro do `when` (inclusive como
 * filho do [RefreshableBox]) — **sem** embrulhar em `ScrollableFillBox`/`Box(Modifier.verticalScroll)`.
 * Até a 2.242.0, dentro de um pai que rola na vertical (`Column(Modifier.verticalScroll)`, item de
 * `LazyColumn`/`LazyVerticalGrid`) **o app caía justamente na tela de erro** — o ramo que ninguém
 * abre no teste (a varredura de 02/out/2026 achou isso em 20 apps). **Desde a 2.242.1 não cai**: sem
 * teto de altura ele ocupa a altura do conteúdo e quem rola é o pai. Ainda assim, quando o erro
 * precisa aparecer **dentro** de uma rolagem (o cabeçalho e os filtros ficam, só o miolo falhou),
 * limite a altura dele, para voltar a centrar:
 *
 * ```kotlin
 * item { ErrorState(msg, onRetry, modifier = Modifier.heightIn(max = 320.dp)) }
 * ```
 *
 * O auditor (`Nexus/fabrica/rolagem_aninhada.py`) cobra o aninhamento.
 *
 * ```kotlin
 * when {
 *     state.isLoading -> LoadingOverlay()
 *     state.error != null -> ErrorState(state.error, onRetry = { vm.dispatch(Action.Load) })
 *     state.items.isEmpty() -> EmptyState(icon = Icons.Default.Inbox, title = "Nenhum cliente")
 *     else -> ClientList(state.items)
 * }
 * ```
 *
 * @param message mensagem amigável (o app traduz o erro técnico antes de chegar aqui).
 * @param onRetry ação de "Tentar novamente" (recarregar / re-disparar sync).
 * @param icon ícone ilustrativo; use [Icons.Default.CloudOff] para falha de rede ([OfflineErrorState]).
 * @param texts textos i18n (título e rótulo do botão).
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    texts: ErrorStateTexts = rememberErrorStateTexts(),
    title: String = texts.title,
    retryLabel: String = texts.retryButton,
) {
    val colors = MaterialTheme.colorScheme
    ScrollableFillBox(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            AppButton(
                text = retryLabel,
                onClick = onRetry,
                primaryColor = colors.primary,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .widthIn(max = 320.dp),
            )
        }
    }
}

/**
 * Atalho de [ErrorState] para **falha por falta de rede** (ícone de nuvem cortada + textos de
 * offline). Use no branch "sem conexão" de uma tela de lista; o aviso global bloqueante é o
 * `ConnectivityGate`.
 */
@Composable
fun OfflineErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    texts: ErrorStateTexts = rememberErrorStateTexts(),
) {
    ErrorState(
        message = texts.offlineMessage,
        onRetry = onRetry,
        modifier = modifier,
        icon = Icons.Default.CloudOff,
        texts = texts,
        title = texts.offlineTitle,
    )
}
