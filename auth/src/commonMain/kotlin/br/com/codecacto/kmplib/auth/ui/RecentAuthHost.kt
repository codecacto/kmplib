package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.auth.OwnAuthException
import br.com.codecacto.kmplib.auth.ReauthAccountMismatchException
import br.com.codecacto.kmplib.auth.ReauthCancelledException
import br.com.codecacto.kmplib.auth.ReauthCredential
import br.com.codecacto.kmplib.auth.ReauthPrompt
import br.com.codecacto.kmplib.auth.ReauthRequest
import br.com.codecacto.kmplib.auth.RecentAuthCoordinator
import br.com.codecacto.kmplib.auth.SocialProvider
import br.com.codecacto.kmplib.core.network.ReauthRequiredException
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_account
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_account_mismatch
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_confirm
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_continue_apple
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_continue_google
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_message
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_message_social
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_password
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_required
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_title
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_wrong_password
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Estado da **reautenticação na tela** (2.261.0) — é o [ReauthPrompt] que o [RecentAuthHost] desenha.
 *
 * ```kotlin
 * val recentAuth = rememberRecentAuthState(koinInject<RecentAuthCoordinator>())
 * scope.launch {
 *     recentAuth.run { deletion.deleteAccountAndData(confirmation = digitado) }
 *         .onSuccess { … }
 *         .onFailure { e -> recentAuthTexts.errorMessage(e)?.let(toast::showError) }
 * }
 * RecentAuthHost(recentAuth)
 * ```
 * [run] executa a ação; se o servidor responder 401 `REAUTH_REQUIRED`, o host abre o diálogo de
 * senha (com o olho de mostrar) e/ou os botões sociais, o coordenador confere a conta, troca os
 * tokens e repete a ação **uma** vez. Ações simultâneas são serializadas.
 */
@Stable
class RecentAuthState(private val coordinator: RecentAuthCoordinator) : ReauthPrompt {
    private val gate = Mutex()

    internal var pending: PendingReauth? by mutableStateOf(null)
        private set

    /** Credencial enviada, aguardando o servidor — o diálogo mostra carregando e não fecha. */
    internal var verifying: Boolean by mutableStateOf(false)
        private set

    /** A última credencial foi senha? (decide se o erro vai NO campo ou junto do botão). */
    internal var lastWasPassword: Boolean by mutableStateOf(false)
        private set

    /** `true` enquanto o diálogo está aberto. */
    val isPrompting: Boolean get() = pending != null

    /** Executa [action] com step-up — ver [RecentAuthCoordinator.withRecentAuth]. */
    suspend fun <T> run(action: suspend () -> Result<T>): Result<T> = gate.withLock {
        try {
            coordinator.withRecentAuth(this, action)
        } finally {
            pending?.answer?.complete(null)
            pending = null
            verifying = false
        }
    }

    override suspend fun requestCredential(request: ReauthRequest): ReauthCredential? {
        val answer = CompletableDeferred<ReauthCredential?>()
        verifying = false
        pending = PendingReauth(request, answer)
        val credential = answer.await()
        verifying = credential != null
        return credential
    }

    internal fun submit(credential: ReauthCredential) {
        if (verifying) return
        lastWasPassword = credential is ReauthCredential.Password
        pending?.answer?.complete(credential)
    }

    internal fun dismiss() {
        if (verifying) return
        pending?.answer?.complete(null)
    }
}

internal class PendingReauth(val request: ReauthRequest, val answer: CompletableDeferred<ReauthCredential?>)

/** [RecentAuthState] lembrado na composição. */
@Composable
fun rememberRecentAuthState(coordinator: RecentAuthCoordinator): RecentAuthState =
    remember(coordinator) { RecentAuthState(coordinator) }

/**
 * O diálogo da reautenticação. Ponha **uma vez** na tela que usa [RecentAuthState.run]; fora de um
 * pedido ele não desenha nada.
 *
 * - Senha num `AppTextField` de senha (**com o olho** de mostrar/ocultar); "Concluído" no teclado
 *   envia. Senha errada → erro **no campo**; rede/limite → **junto do botão**.
 * - Conta social (ou app que passou o `SocialSignIn` ao coordenador): botões "Continuar com …", o
 *   provedor da sessão primeiro.
 * - Fechar = desistir: a ação NÃO é executada ([ReauthCancelledException], que a tela ignora).
 *
 * Ids para o Maestro: `dialogo-input`, `dialogo-btn-confirmar`, `dialogo-btn-cancelar`
 * ([DialogTestTags]) e [RecentAuthTestTags] para os botões sociais.
 */
@Composable
fun RecentAuthHost(state: RecentAuthState, texts: RecentAuthTexts = rememberRecentAuthTexts()) {
    val pending = state.pending ?: return
    val request = pending.request
    val loading = state.verifying
    var senha by remember(pending) { mutableStateOf("") }

    val erroAnterior = request.previousError
    val erroNoCampo = erroAnterior
        ?.takeIf { it is OwnAuthException.InvalidCredentials && state.lastWasPassword }
        ?.let { texts.wrongPassword }
    val erroGeral = erroAnterior
        ?.takeIf { erroNoCampo == null }
        ?.let { (it as? OwnAuthException)?.message ?: texts.failed }

    val enviarSenha = {
        if (senha.isNotEmpty()) state.submit(ReauthCredential.Password(senha))
    }

    AppDialog(
        show = true,
        onDismiss = { state.dismiss() },
        title = texts.title,
        dismissOnClickOutside = !loading,
        dismissOnBackPress = !loading,
    ) {
        Text(
            text = if (request.passwordAvailable) texts.message else texts.messageSocial,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(DialogTestTags.MENSAGEM),
        )
        request.identifier?.let { conta ->
            Text(
                text = texts.account(conta),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (request.passwordAvailable) {
            AppTextField(
                value = senha,
                onValueChange = { senha = it },
                modifier = Modifier.fillMaxWidth().testTag(DialogTestTags.INPUT),
                label = texts.passwordLabel,
                isPassword = true,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { enviarSenha() }),
                errorMessage = erroNoCampo,
                enabled = !loading,
            )
        }

        // Junto dos botões, onde o olho já está depois do toque.
        erroGeral?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (request.passwordAvailable) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppOutlinedButton(
                        text = texts.cancel,
                        onClick = { state.dismiss() },
                        modifier = Modifier.weight(1f).testTag(DialogTestTags.BTN_CANCELAR),
                        enabled = !loading,
                        primaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        height = 48.dp,
                    )
                    AppButton(
                        text = texts.confirm,
                        onClick = { enviarSenha() },
                        modifier = Modifier.weight(1f).testTag(DialogTestTags.BTN_CONFIRMAR),
                        isLoading = loading && state.lastWasPassword,
                        enabled = !loading && senha.isNotEmpty(),
                        height = 48.dp,
                    )
                }
            }
            orderedProviders(request).forEach { provider ->
                AppOutlinedButton(
                    text = texts.continueWith(provider),
                    onClick = { state.submit(ReauthCredential.Social(provider)) },
                    modifier = Modifier.fillMaxWidth().testTag(RecentAuthTestTags.social(provider)),
                    isLoading = loading && !state.lastWasPassword,
                    enabled = !loading,
                    height = 48.dp,
                )
            }
            if (!request.passwordAvailable) {
                AppOutlinedButton(
                    text = texts.cancel,
                    onClick = { state.dismiss() },
                    modifier = Modifier.fillMaxWidth().testTag(DialogTestTags.BTN_CANCELAR),
                    enabled = !loading,
                    primaryColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    height = 48.dp,
                )
            }
        }
    }
}

/** O provedor com que a sessão entrou vem primeiro; o resto na ordem do enum. */
internal fun orderedProviders(request: ReauthRequest): List<SocialProvider> =
    request.socialProviders.sortedBy { if (it == request.sessionProvider) -1 else it.ordinal }

/** Ids do [RecentAuthHost] além dos de [DialogTestTags]. */
object RecentAuthTestTags {
    /** `reauth-btn-google` / `reauth-btn-apple`. */
    fun social(provider: SocialProvider): String = "reauth-btn-${provider.wire}"
}

/** Textos do [RecentAuthHost] (defaults pt-BR; na tela use [rememberRecentAuthTexts]). */
data class RecentAuthTexts(
    val title: String = "Confirme sua identidade",
    val message: String = "Por segurança, digite sua senha para continuar.",
    val messageSocial: String = "Por segurança, entre de novo para continuar.",
    val passwordLabel: String = "Senha",
    val wrongPassword: String = "Senha incorreta",
    val confirm: String = "Confirmar",
    val cancel: String = "Cancelar",
    val continueWithGoogle: String = "Continuar com Google",
    val continueWithApple: String = "Continuar com Apple",
    val account: (String) -> String = { "Conta: $it" },
    val accountMismatch: String = "Esta não é a conta conectada. Confirme com a conta desta sessão.",
    val reauthRequired: String = ReauthRequiredException.DEFAULT_MESSAGE,
    val failed: String = "Não foi possível confirmar. Tente novamente.",
) {
    fun continueWith(provider: SocialProvider): String = when (provider) {
        SocialProvider.GOOGLE -> continueWithGoogle
        SocialProvider.APPLE -> continueWithApple
    }

    /**
     * A frase para a tela depois de um [RecentAuthState.run] que falhou **por causa da
     * reautenticação** — ou `null` quando não há o que dizer (a pessoa cancelou) ou o erro não é
     * daqui (devolve `null` e a tela usa a mensagem do próprio erro).
     */
    fun errorMessage(error: Throwable): String? = when (error) {
        is ReauthCancelledException -> null
        is ReauthAccountMismatchException -> accountMismatch
        is ReauthRequiredException -> reauthRequired
        else -> error.message ?: failed
    }
}

/** [RecentAuthTexts] no idioma do aparelho (pt-BR, en, es, pt-PT). */
@Composable
fun rememberRecentAuthTexts(): RecentAuthTexts {
    val conta = kmpStringResource(Res.string.kmplib_reauth_account)
    return RecentAuthTexts(
        title = kmpStringResource(Res.string.kmplib_reauth_title),
        message = kmpStringResource(Res.string.kmplib_reauth_message),
        messageSocial = kmpStringResource(Res.string.kmplib_reauth_message_social),
        passwordLabel = kmpStringResource(Res.string.kmplib_reauth_password),
        wrongPassword = kmpStringResource(Res.string.kmplib_reauth_wrong_password),
        confirm = kmpStringResource(Res.string.kmplib_reauth_confirm),
        cancel = kmpStringResource(Res.string.kmplib_cancel),
        continueWithGoogle = kmpStringResource(Res.string.kmplib_reauth_continue_google),
        continueWithApple = kmpStringResource(Res.string.kmplib_reauth_continue_apple),
        account = { conta.replace("%1\$s", it) },
        accountMismatch = kmpStringResource(Res.string.kmplib_reauth_account_mismatch),
        reauthRequired = kmpStringResource(Res.string.kmplib_reauth_required),
        failed = kmpStringResource(Res.string.kmplib_reauth_failed),
    )
}
