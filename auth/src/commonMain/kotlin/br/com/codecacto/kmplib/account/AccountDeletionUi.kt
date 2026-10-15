package br.com.codecacto.kmplib.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_account_deletion_dialog_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_account_deletion_dialog_confirm
import br.com.codecacto.kmplib.generated.resources.kmplib_account_deletion_dialog_field_error
import br.com.codecacto.kmplib.generated.resources.kmplib_account_deletion_dialog_field_label
import br.com.codecacto.kmplib.generated.resources.kmplib_account_deletion_dialog_instruction
import br.com.codecacto.kmplib.generated.resources.kmplib_account_deletion_dialog_message
import br.com.codecacto.kmplib.generated.resources.kmplib_account_deletion_dialog_title
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_message
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_restore
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_restore_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_restore_needs_sign_in
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_account_mismatch
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_sign_out
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_remaining_days
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_remaining_none
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_remaining_one_day
import br.com.codecacto.kmplib.generated.resources.kmplib_account_pending_title
import br.com.codecacto.kmplib.auth.RecentAuthCoordinator
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.RecentAuthHost
import br.com.codecacto.kmplib.ui.components.AppInputDialog
import br.com.codecacto.kmplib.ui.components.typedConfirmationMatches
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A palavra que se digita para confirmar a exclusão — a mesma que o servidor confere (backlib). */
const val ACCOUNT_DELETION_CONFIRMATION_WORD: String = "EXCLUIR"

/** Os dias de carência da exclusão (política da fábrica; quem conta de verdade é o servidor). */
const val ACCOUNT_DELETION_GRACE_DAYS: Int = 30

/**
 * Ids estáveis para o Maestro (2.301.0). O diálogo de confirmação é o `AppInputDialog` e traz os ids
 * dele (`dialogo-input`, `dialogo-btn-confirmar`, `dialogo-btn-cancelar`); daqui saem os da tela de
 * conta pendente.
 */
object AccountDeletionTestTags {
    const val TELA: String = "conta-exclusao-pendente"
    const val TITULO: String = "conta-exclusao-pendente-titulo"
    const val PRAZO: String = "conta-exclusao-pendente-prazo"
    const val MENSAGEM: String = "conta-exclusao-pendente-mensagem"
    const val ERRO: String = "conta-exclusao-pendente-erro"
    const val BTN_RESTAURAR: String = "conta-exclusao-pendente-btn-restaurar"
    const val BTN_SAIR: String = "conta-exclusao-pendente-btn-sair"
}

// ---------------------------------------------------------------------------------------- diálogo

/**
 * Textos do [AccountDeletionDialog]. Default: [rememberAccountDeletionDialogTexts] (4 idiomas).
 * [message] já traz os dias de carência e a instrução de digitar a palavra.
 *
 * **Nunca prometa data nem prazo exato** (fundador, 14/out/2026): a eliminação definitiva é manual e
 * pode acontecer depois do dia 30. A forma canônica é "Sua conta será excluída definitivamente após
 * 30 dias." — nada de "em X dias", "no dia tal" ou "hoje".
 */
data class AccountDeletionDialogTexts(
    val title: String,
    val message: String,
    val fieldLabel: String,
    val fieldError: String,
    val confirm: String,
    val cancel: String,
)

/** [AccountDeletionDialogTexts] no idioma da tela. */
@Composable
fun rememberAccountDeletionDialogTexts(
    confirmationWord: String = ACCOUNT_DELETION_CONFIRMATION_WORD,
    graceDays: Int = ACCOUNT_DELETION_GRACE_DAYS,
): AccountDeletionDialogTexts {
    val corpo = kmpStringResource(Res.string.kmplib_account_deletion_dialog_message, graceDays)
    val instrucao = kmpStringResource(Res.string.kmplib_account_deletion_dialog_instruction, confirmationWord)
    return AccountDeletionDialogTexts(
        title = kmpStringResource(Res.string.kmplib_account_deletion_dialog_title),
        message = "$corpo\n\n$instrucao",
        fieldLabel = kmpStringResource(Res.string.kmplib_account_deletion_dialog_field_label, confirmationWord),
        fieldError = kmpStringResource(Res.string.kmplib_account_deletion_dialog_field_error, confirmationWord),
        confirm = kmpStringResource(Res.string.kmplib_account_deletion_dialog_confirm),
        cancel = kmpStringResource(Res.string.kmplib_account_deletion_dialog_cancel),
    )
}

/**
 * O erro do campo de confirmação: só depois de a pessoa ter escrito algo que não confere — vermelho
 * num campo vazio que ela nem tocou é ruído. Puro e testado.
 */
internal fun accountDeletionFieldError(typed: String, confirmationWord: String, errorText: String): String? =
    errorText.takeIf { typed.isNotBlank() && !typedConfirmationMatches(typed, confirmationWord) }

/**
 * **"Excluir sua conta?"** com carência (2.301.0) — exige DIGITAR [confirmationWord] (padrão de todos
 * os projetos, fundador 21/ago/2026): o confirmar fica desligado até a palavra conferir, ignorando
 * caixa e espaços. O texto diz os 30 dias para desistir, a conta bloqueada com os dados guardados, a
 * restauração ao entrar de novo e o definitivo depois. Sem tarja/aviso colorido: só o texto.
 *
 * [onConfirm] recebe o que foi digitado — passe para `AccountDeletionService.scheduleAccountDeletion`
 * (vai no corpo do `DELETE`, para o servidor conferir). O campo se limpa a cada abertura e o texto
 * digitado não entra no estado salvo.
 *
 * [errorMessage] (2.302.0) = a recusa do pedido, mostrada junto dos botões (`dialogo-erro`). Passe a
 * frase da falha do `scheduleAccountDeletion`: a `message` da [AccountDeletionRequestException] já é
 * a do SERVIDOR quando ele recusou por regra (ex.: 409 "Transfira o estabelecimento antes…"). Com o
 * `RecentAuthState.run`, `rememberRecentAuthTexts().errorMessage(falha)` dá a frase certa (e `null`
 * quando a pessoa desistiu da senha). Limpe ao fechar ou ao digitar de novo.
 */
@Composable
fun AccountDeletionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (typed: String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    confirmationWord: String = ACCOUNT_DELETION_CONFIRMATION_WORD,
    texts: AccountDeletionDialogTexts = rememberAccountDeletionDialogTexts(confirmationWord),
    errorMessage: String? = null,
) {
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(show) { if (!show) typed = "" }
    val confere = typedConfirmationMatches(typed, confirmationWord)

    AppInputDialog(
        show = show,
        onDismiss = { if (!isLoading) onDismiss() },
        title = texts.title,
        message = texts.message,
        textFieldValue = typed,
        onTextFieldValueChange = { typed = it },
        textFieldLabel = texts.fieldLabel,
        textFieldPlaceholder = confirmationWord,
        textFieldError = accountDeletionFieldError(typed, confirmationWord, texts.fieldError),
        confirmText = texts.confirm,
        dismissText = texts.cancel,
        isLoading = isLoading,
        confirmEnabled = confere,
        onConfirm = { if (confere && !isLoading) onConfirm(typed) },
        modifier = modifier,
        errorMessage = errorMessage,
    )
}

// ------------------------------------------------------------------------- tela da conta pendente

/**
 * Textos da [AccountPendingDeletionScreen]. Default: [rememberAccountPendingDeletionTexts].
 *
 * **Sem promessa de data** (fundador, 14/out/2026): o [title] diz que a exclusão definitiva acontece
 * *após* os 30 dias do pedido, e a linha de prazo ([remainingFor]) diz quanto falta para ela ficar
 * *liberada* — nunca "será excluída em X dias" nem "hoje", porque a eliminação é manual e pode vir
 * depois do dia 30.
 */
data class AccountPendingDeletionTexts(
    val title: String,
    val remainingDays: (Int) -> String,
    val remainingOneDay: String,
    val remainingNone: String,
    val message: String,
    val restore: String,
    val signOut: String,
    val restoreFailed: String,
    /** (2.302.0) A restauração exige uma sessão nova e não deu para reautenticar aqui. */
    val restoreNeedsSignIn: String = "Entre de novo para restaurar a conta.",
    /** (2.302.0) A reautenticação foi feita com outra conta. */
    val restoreAccountMismatch: String = "Esta não é a conta conectada. Confirme com a conta desta sessão.",
) {
    /** A frase da falha da restauração (2.302.0) — `null` = nada a mostrar. */
    fun restoreErrorMessage(error: AccountDeletionGateError?): String? = when {
        error == null -> null
        error.needsSignIn -> restoreNeedsSignIn
        error.accountMismatch -> restoreAccountMismatch
        else -> error.message ?: restoreFailed
    }

    /** A linha de prazo para [days] (`null` = o servidor não disse ⇒ sem linha). */
    fun remainingFor(days: Int?): String? = when {
        days == null -> null
        days <= 0 -> remainingNone
        days == 1 -> remainingOneDay
        else -> remainingDays(days)
    }
}

/** [AccountPendingDeletionTexts] no idioma da tela. [graceDays] = a carência (30). */
@Composable
fun rememberAccountPendingDeletionTexts(
    graceDays: Int = ACCOUNT_DELETION_GRACE_DAYS,
): AccountPendingDeletionTexts {
    // O modelo é lido cru (`%1$d` intacto) e preenchido na hora: o número só se sabe depois, e
    // `kmpStringResource` é composable.
    val modelo = kmpStringResource(Res.string.kmplib_account_pending_remaining_days)
    return AccountPendingDeletionTexts(
        title = kmpStringResource(Res.string.kmplib_account_pending_title, graceDays),
        remainingDays = { dias -> fillDaysTemplate(modelo, dias) },
        remainingOneDay = kmpStringResource(Res.string.kmplib_account_pending_remaining_one_day),
        remainingNone = kmpStringResource(Res.string.kmplib_account_pending_remaining_none, graceDays),
        message = kmpStringResource(Res.string.kmplib_account_pending_message),
        restore = kmpStringResource(Res.string.kmplib_account_pending_restore),
        signOut = kmpStringResource(Res.string.kmplib_account_pending_sign_out),
        restoreFailed = kmpStringResource(Res.string.kmplib_account_pending_restore_failed),
        restoreNeedsSignIn = kmpStringResource(Res.string.kmplib_account_pending_restore_needs_sign_in),
        restoreAccountMismatch = kmpStringResource(Res.string.kmplib_reauth_account_mismatch),
    )
}

/** Preenche o `%1$d` (ou `%d`) do modelo com [days]. Puro e testado. */
internal fun fillDaysTemplate(template: String, days: Int): String =
    template.replace("%1\$d", days.toString()).replace("%d", days.toString())

/**
 * **"Sua conta será excluída definitivamente após 30 dias do pedido"** (2.301.0) — a tela que cobre a
 * área logada enquanto a conta tem exclusão programada, com a linha "Faltam X dias para liberar a
 * exclusão definitiva" (0 ⇒ "o prazo já terminou; pode acontecer a qualquer momento"). Duas saídas, e só elas: **Restaurar conta** e **Sair**.
 * Normalmente não se chama direto: o [AccountDeletionGate] a mostra.
 *
 * Tema do app (`MaterialTheme`/`AppTheme`), largura de leitura em tablet, rola em tela baixa, respeita
 * as barras do sistema. O erro da restauração aparece junto do botão (região viva para o leitor de tela).
 *
 * [signOutIsPrimary] (2.302.0): a restauração só funciona entrando de novo — o **Sair** vira o botão
 * cheio e o **Restaurar** passa a contorno (continua tocável: a pessoa pode ter reautenticado).
 */
@OptIn(ExperimentalTime::class)
@Composable
fun AccountPendingDeletionScreen(
    status: AccountDeletionStatus,
    onRestore: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isRestoring: Boolean = false,
    errorMessage: String? = null,
    texts: AccountPendingDeletionTexts = rememberAccountPendingDeletionTexts(),
    nowEpochMillis: Long = remember(status) { Clock.System.now().toEpochMilliseconds() },
    signOutIsPrimary: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag(AccountDeletionTestTags.TELA),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = texts.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(AccountDeletionTestTags.TITULO).semantics { heading() },
                )
                texts.remainingFor(status.daysRemainingAt(nowEpochMillis))?.let { prazo ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = prazo,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(AccountDeletionTestTags.PRAZO),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = texts.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(AccountDeletionTestTags.MENSAGEM),
                )
                Spacer(Modifier.height(32.dp))
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag(AccountDeletionTestTags.ERRO)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (signOutIsPrimary) {
                    AppButton(
                        text = texts.signOut,
                        onClick = onSignOut,
                        enabled = !isRestoring,
                        modifier = Modifier.fillMaxWidth().testTag(AccountDeletionTestTags.BTN_SAIR),
                    )
                    Spacer(Modifier.height(16.dp))
                    AppOutlinedButton(
                        text = texts.restore,
                        onClick = onRestore,
                        isLoading = isRestoring,
                        modifier = Modifier.fillMaxWidth().testTag(AccountDeletionTestTags.BTN_RESTAURAR),
                    )
                } else {
                    AppButton(
                        text = texts.restore,
                        onClick = onRestore,
                        isLoading = isRestoring,
                        modifier = Modifier.fillMaxWidth().testTag(AccountDeletionTestTags.BTN_RESTAURAR),
                    )
                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = onSignOut,
                        enabled = !isRestoring,
                        modifier = Modifier.height(48.dp).testTag(AccountDeletionTestTags.BTN_SAIR),
                    ) {
                        Text(text = texts.signOut)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- portão

/**
 * **O portão da conta com exclusão programada** (2.301.0): mostra [content] (a área logada) e a cobre
 * com a [AccountPendingDeletionScreen] quando a conta está com exclusão programada — **logo depois do
 * login** (o gate consulta `GET {me}/deletion` a cada conta nova em [accountKey]) e em **qualquer 403
 * `ACCOUNT_PENDING_DELETION`** — inclusive o do `GET /v1/auth/me` que o own-auth faz logo após o login
 * (o `AccountDeletionDetector`, ligado por default no `createHttpClient`; instale-o com
 * `HttpClientOptions.accountDeletionHosts` = o host da API do projeto).
 *
 * Uma linha na raiz do app, por fora do `NavHost`:
 * ```kotlin
 * AccountDeletionGate(repository = koinInject(), accountKey = user?.id, onSignOut = { viewModel.signOut() }) {
 *     AppNavHost()
 * }
 * ```
 *
 * - **Nada acessível atrás:** a cobertura é opaca, engole o toque, esconde o conteúdo do leitor de
 *   tela e segura o voltar do sistema. O conteúdo continua composto por baixo (a pilha de navegação é
 *   preservada: restaurou, a pessoa está onde estava).
 * - **Restaurar conta** → `POST {me}/deletion/restore`; deu certo, a cobertura sai e [onRestored] é
 *   chamado (recarregue o que estiver na tela). Falhou → a frase aparece junto do botão — a do
 *   servidor quando ele recusou por regra (2.302.0).
 * - **Sessão anterior ao pedido** (backlib ≥ 0.183.0: 401 `REAUTH_REQUIRED`): com [recentAuth] o gate
 *   pede a senha/login social da mesma conta e repete a restauração uma vez (2.302.0); sem ele — ou
 *   sem como reautenticar — diz "Entre de novo para restaurar a conta." e o **Sair** vira o botão
 *   principal. Passe o `RecentAuthCoordinator` do app: `recentAuth = koinInject()`.
 * - **Sair** → [onSignOut]; o pedido de exclusão segue. A cobertura fica até [accountKey] virar `null`.
 * - [accountKey] é **obrigatório** (o id da conta logada, `null` deslogado): é o que dispara a consulta
 *   pós-login e impede a conta seguinte de herdar o bloqueio da anterior.
 * - A consulta que falha (sem rede) não bloqueia: o servidor recusa toda rota protegida com 403, e
 *   esse 403 cobre a tela.
 */
@Composable
fun AccountDeletionGate(
    repository: AccountDeletionRepository,
    accountKey: Any?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onRestored: () -> Unit = {},
    state: AccountDeletionState = AccountDeletionState.Shared,
    texts: AccountPendingDeletionTexts = rememberAccountPendingDeletionTexts(),
    recentAuth: RecentAuthCoordinator? = null,
    content: @Composable () -> Unit,
) {
    val viewModel = viewModel(key = GATE_VIEW_MODEL_KEY) {
        AccountDeletionGateViewModel(repository, state, recentAuth)
    }
    AccountDeletionGate(
        viewModel = viewModel,
        accountKey = accountKey,
        onSignOut = onSignOut,
        modifier = modifier,
        onRestored = onRestored,
        texts = texts,
        content = content,
    )
}

/** Variante com o ViewModel fornecido (Koin `koinViewModel()`, ou teste). */
@Composable
fun AccountDeletionGate(
    viewModel: AccountDeletionGateViewModel,
    accountKey: Any?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onRestored: () -> Unit = {},
    texts: AccountPendingDeletionTexts = rememberAccountPendingDeletionTexts(),
    content: @Composable () -> Unit,
) {
    val gate by viewModel.state.collectAsState()
    val currentOnSignOut by rememberUpdatedState(onSignOut)
    val currentOnRestored by rememberUpdatedState(onRestored)

    LaunchedEffect(viewModel, accountKey) {
        viewModel.dispatch(AccountDeletionGateAction.AccountChanged(accountKey))
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AccountDeletionGateEffect.Restored -> currentOnRestored()
                AccountDeletionGateEffect.SignOutRequested -> currentOnSignOut()
            }
        }
    }

    val pending = gate.pending
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (pending != null) Modifier.clearAndSetSemantics { } else Modifier,
            ),
        ) {
            content()
        }
        if (pending != null) {
            HoldSystemBack()
            DeletionCover {
                AccountPendingDeletionScreen(
                    status = pending,
                    isRestoring = gate.isRestoring,
                    errorMessage = texts.restoreErrorMessage(gate.restoreError),
                    onRestore = { viewModel.dispatch(AccountDeletionGateAction.Restore) },
                    onSignOut = { viewModel.dispatch(AccountDeletionGateAction.SignOut) },
                    texts = texts,
                    signOutIsPrimary = gate.restoreError?.needsSignIn == true,
                )
            }
            // O pedido de senha da restauração (2.302.0): janela própria, por cima da cobertura.
            viewModel.recentAuthState?.let { RecentAuthHost(it) }
        }
    }
}

private const val GATE_VIEW_MODEL_KEY = "kmplib-account-deletion-gate"

/** Opaca, ocupa tudo e engole o toque — nada embaixo reage (mesma cobertura do `SubscriptionBlockGate`). */
@Composable
private fun DeletionCover(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Só os eventos que ninguém da cobertura consumiu: os botões dela reagem antes.
                        awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        content()
    }
}

/** O voltar do sistema com a cobertura aberta não desempilha a navegação escondida. */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
private fun HoldSystemBack() {
    BackHandler(enabled = true) { }
}
