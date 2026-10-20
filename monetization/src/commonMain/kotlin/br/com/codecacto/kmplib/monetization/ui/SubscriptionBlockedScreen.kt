package br.com.codecacto.kmplib.ui.screens.subscription

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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_paywall_subscribe
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_message
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_required_title
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_sign_out
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_suspended_message
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_suspended_title
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_trial_title
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_whatsapp
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_whatsapp_required
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_whatsapp_required_noname
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_whatsapp_suspended
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_whatsapp_suspended_noname
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_whatsapp_trial
import br.com.codecacto.kmplib.generated.resources.kmplib_subscription_block_whatsapp_trial_noname
import br.com.codecacto.kmplib.monetization.access.SubscriptionBlock
import br.com.codecacto.kmplib.monetization.access.SubscriptionBlockReason
import br.com.codecacto.kmplib.monetization.access.SubscriptionBlockState
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Ids estáveis da tela de bloqueio para o Maestro (2.300.0) — `tapOn: id: assinatura-bloqueio-btn-assinar`.
 */
object SubscriptionBlockedTestTags {
    const val TELA: String = "assinatura-bloqueio"
    const val TITULO: String = "assinatura-bloqueio-titulo"
    const val MENSAGEM: String = "assinatura-bloqueio-mensagem"
    const val BTN_ASSINAR: String = "assinatura-bloqueio-btn-assinar"
    const val BTN_WHATSAPP: String = "assinatura-bloqueio-btn-whatsapp"
    const val BTN_SAIR: String = "assinatura-bloqueio-btn-sair"

    /** O contêiner do paywall aberto pelo "Assinar". */
    const val PAYWALL: String = "assinatura-bloqueio-paywall"
}

/** Defaults da tela de bloqueio (2.300.0). */
object SubscriptionBlockDefaults {
    /** O WhatsApp da CodeCacto (65 99803-0240), em E.164 sem o `+`. */
    const val WHATSAPP_PHONE: String = "5565998030240"
}

/**
 * Textos da [SubscriptionBlockedScreen]. Default: [rememberSubscriptionBlockedTexts] (4 idiomas).
 *
 * @property whatsAppMessage a mensagem pronta do WhatsApp por motivo — diz de qual app a pessoa veio.
 * @property suspendedTitle título do modo SEM compra (`paywallEnabled = false`, 2.308.0) — "Acesso suspenso".
 * @property suspendedMessage mensagem do modo sem compra — manda falar com o administrador, sem preço.
 * @property suspendedWhatsAppMessage mensagem pronta do WhatsApp no modo sem compra.
 */
data class SubscriptionBlockedTexts(
    val trialExpiredTitle: String,
    val subscriptionRequiredTitle: String,
    val message: String,
    val subscribe: String,
    val whatsApp: String,
    val signOut: String,
    val whatsAppMessage: (SubscriptionBlockReason) -> String,
    val suspendedTitle: String = "Acesso suspenso",
    val suspendedMessage: String =
        "O acesso da sua organização está suspenso. Fale com o administrador para reativar.",
    val suspendedWhatsAppMessage: String = "Olá! Meu acesso ao app está suspenso.",
) {
    /** O título para o [reason]. */
    fun titleFor(reason: SubscriptionBlockReason): String = when (reason) {
        SubscriptionBlockReason.TRIAL_EXPIRED -> trialExpiredTitle
        SubscriptionBlockReason.SUBSCRIPTION_REQUIRED -> subscriptionRequiredTitle
    }

    /**
     * O título para o [reason] considerando se a tela oferece compra (2.308.0). Sem compra
     * ([paywallEnabled] = `false`), o motivo não importa para quem está do outro lado — o vigia não
     * assina —, e o título é sempre o de acesso suspenso.
     */
    fun titleFor(reason: SubscriptionBlockReason, paywallEnabled: Boolean): String =
        if (paywallEnabled) titleFor(reason) else suspendedTitle

    /** A mensagem do corpo: a de assinar, ou a de falar com o administrador (sem compra). */
    fun messageFor(paywallEnabled: Boolean): String = if (paywallEnabled) message else suspendedMessage

    /** A mensagem pronta do WhatsApp, coerente com o modo da tela. */
    fun whatsAppMessageFor(reason: SubscriptionBlockReason, paywallEnabled: Boolean): String =
        if (paywallEnabled) whatsAppMessage(reason) else suspendedWhatsAppMessage
}

/**
 * [SubscriptionBlockedTexts] no idioma da tela. A mensagem do WhatsApp leva o nome do app
 * ([appName], default `BuildInfo.appName` — o rótulo do launcher, certo em todo flavor).
 */
@Composable
fun rememberSubscriptionBlockedTexts(
    appName: String? = remember { BuildInfo.appName },
): SubscriptionBlockedTexts {
    val name = appName?.trim()?.takeIf { it.isNotEmpty() }
    val trialMsg = if (name != null) {
        kmpStringResource(Res.string.kmplib_subscription_block_whatsapp_trial, name)
    } else {
        kmpStringResource(Res.string.kmplib_subscription_block_whatsapp_trial_noname)
    }
    val requiredMsg = if (name != null) {
        kmpStringResource(Res.string.kmplib_subscription_block_whatsapp_required, name)
    } else {
        kmpStringResource(Res.string.kmplib_subscription_block_whatsapp_required_noname)
    }
    val suspendedMsg = if (name != null) {
        kmpStringResource(Res.string.kmplib_subscription_block_whatsapp_suspended, name)
    } else {
        kmpStringResource(Res.string.kmplib_subscription_block_whatsapp_suspended_noname)
    }
    return SubscriptionBlockedTexts(
        trialExpiredTitle = kmpStringResource(Res.string.kmplib_subscription_block_trial_title),
        subscriptionRequiredTitle = kmpStringResource(Res.string.kmplib_subscription_block_required_title),
        message = kmpStringResource(Res.string.kmplib_subscription_block_message),
        subscribe = kmpStringResource(Res.string.kmplib_paywall_subscribe),
        whatsApp = kmpStringResource(Res.string.kmplib_subscription_block_whatsapp),
        signOut = kmpStringResource(Res.string.kmplib_subscription_block_sign_out),
        whatsAppMessage = { reason ->
            when (reason) {
                SubscriptionBlockReason.TRIAL_EXPIRED -> trialMsg
                SubscriptionBlockReason.SUBSCRIPTION_REQUIRED -> requiredMsg
            }
        },
        suspendedTitle = kmpStringResource(Res.string.kmplib_subscription_block_suspended_title),
        suspendedMessage = kmpStringResource(Res.string.kmplib_subscription_block_suspended_message),
        suspendedWhatsAppMessage = suspendedMsg,
    )
}

/**
 * **"Seu teste grátis terminou"** (2.300.0) — a tela que substitui a área logada inteira de um SaaS
 * quando o servidor recusa a conta com 402 `TRIAL_EXPIRED` (ou "Sua assinatura não está ativa" no
 * `SUBSCRIPTION_REQUIRED`). Três saídas, e só elas: **Assinar** (no app, SÓ pela loja — quem chama
 * abre o `PaywallHost`; nunca checkout web), **Falar no WhatsApp** e **Sair**.
 *
 * Usa o tema do app (`MaterialTheme`/`AppTheme`), largura de leitura em tablet, rola em tela baixa e
 * respeita as barras do sistema. Normalmente não se chama direto: o [SubscriptionBlockGate] a mostra.
 *
 * [paywallEnabled] = `false` (2.308.0) é o modo **sistema B2B**: quem usa o app não é quem contrata
 * (o vigia × a empresa de segurança). A tela vira "Acesso suspenso — fale com o administrador", SEM
 * botão de assinar e sem preço; ficam só o WhatsApp e o Sair.
 */
@Composable
fun SubscriptionBlockedScreen(
    reason: SubscriptionBlockReason,
    onSubscribe: () -> Unit,
    onWhatsApp: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    texts: SubscriptionBlockedTexts = rememberSubscriptionBlockedTexts(),
    paywallEnabled: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag(SubscriptionBlockedTestTags.TELA),
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
                    imageVector = when {
                        !paywallEnabled -> Icons.Filled.PersonOff
                        reason == SubscriptionBlockReason.TRIAL_EXPIRED -> Icons.Filled.HourglassEmpty
                        else -> Icons.Filled.Lock
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = texts.titleFor(reason, paywallEnabled),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(SubscriptionBlockedTestTags.TITULO).semantics { heading() },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = texts.messageFor(paywallEnabled),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(SubscriptionBlockedTestTags.MENSAGEM),
                )
                Spacer(Modifier.height(32.dp))
                if (paywallEnabled) {
                    AppButton(
                        text = texts.subscribe,
                        onClick = onSubscribe,
                        modifier = Modifier.fillMaxWidth().testTag(SubscriptionBlockedTestTags.BTN_ASSINAR),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                AppOutlinedButton(
                    text = texts.whatsApp,
                    onClick = onWhatsApp,
                    icon = Icons.AutoMirrored.Filled.Chat,
                    modifier = Modifier.fillMaxWidth().testTag(SubscriptionBlockedTestTags.BTN_WHATSAPP),
                )
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.height(48.dp).testTag(SubscriptionBlockedTestTags.BTN_SAIR),
                ) {
                    Text(text = texts.signOut, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * **O portão pós-teste do app** (2.300.0): mostra [content] (a área logada) enquanto a conta tem
 * acesso, e a cobre com a [SubscriptionBlockedScreen] quando o servidor respondeu 402
 * `TRIAL_EXPIRED`/`SUBSCRIPTION_REQUIRED` (registrado em [state] pelo `SubscriptionBlockDetector` do
 * `HttpClient` — `HttpClientOptions(subscriptionBlock = SubscriptionBlockState.Shared)`).
 *
 * - **O conteúdo continua composto por baixo** (como o `AppLockGate`): a pilha de navegação é
 *   preservada e, quando o acesso volta, a pessoa está onde estava. A cobertura é opaca, engole o
 *   toque, esconde o conteúdo do leitor de tela e segura o voltar do sistema.
 * - **Assinar** abre o [paywall] (o `PaywallHost` do app — SÓ a loja) por cima, e o voltar/fechar
 *   retorna à tela de bloqueio. **Falar no WhatsApp** abre a conversa com a mensagem pronta (nome do
 *   app). **Sair** limpa o bloqueio e chama [onSignOut].
 * - [hasAccess] = acesso confirmado pelo app (premium da loja — `MonetizationManager.premiumStatus` —
 *   ou entitlement por contrato/isento): com ele a cobertura nunca aparece, e o bloqueio é limpo para
 *   a tela refazer as chamadas (se o servidor ainda não souber da compra, ele bloqueia de novo).
 * - Trocar [accountKey] (id da conta) limpa o bloqueio — a conta seguinte não herda o da anterior.
 * - O acesso volta sozinho quando uma rota do mesmo servidor responde 2xx (recarga no `ON_RESUME`).
 *
 * **Sistema B2B (2.308.0):** com [paywallEnabled] = `false` a cobertura diz "Acesso suspenso — fale
 * com o administrador" e não oferece compra (sem "Assinar", sem preço) — para o app em que quem usa
 * não é quem contrata (RondAki: o vigia não assina; a empresa contrata pelo portal). Nesse modo
 * passe `paywall = null`. [paywall] nulo com [paywallEnabled] `true` também cai no modo sem compra:
 * botão "Assinar" sem paywall seria um controle mudo.
 *
 * ```kotlin
 * // B2B: o usuário do app não compra
 * SubscriptionBlockGate(
 *     onSignOut = { viewModel.signOut() },
 *     paywall = null,
 *     paywallEnabled = false,
 *     whatsAppPhone = telefoneDoAdministrador,
 * ) { AppNavHost() }
 *
 * SubscriptionBlockGate(
 *     onSignOut = { viewModel.signOut() },
 *     hasAccess = premium is PremiumStatus.Premium,
 *     accountKey = user?.id,
 *     paywall = { fechar -> PaywallHost(koinViewModel(), onClose = fechar, onOpenDeveloper = {}) },
 * ) { AppNavHost() }
 * ```
 */
@Composable
fun SubscriptionBlockGate(
    onSignOut: () -> Unit,
    paywall: (@Composable (onClose: () -> Unit) -> Unit)?,
    modifier: Modifier = Modifier,
    state: SubscriptionBlockState = SubscriptionBlockState.Shared,
    hasAccess: Boolean = false,
    accountKey: Any? = null,
    whatsAppPhone: String = SubscriptionBlockDefaults.WHATSAPP_PHONE,
    onWhatsApp: ((SubscriptionBlock) -> Unit)? = null,
    texts: SubscriptionBlockedTexts = rememberSubscriptionBlockedTexts(),
    paywallEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val purchaseOffered = subscriptionBlockOffersPurchase(paywallEnabled, paywall != null)
    val block by state.block.collectAsState()
    val currentOnSignOut by rememberUpdatedState(onSignOut)
    val currentOnWhatsApp by rememberUpdatedState(onWhatsApp)
    val urlLauncher = remember { getUrlLauncher() }
    var paywallOpen by rememberSaveable { mutableStateOf(false) }

    // Acesso confirmado pelo app (premium da loja, contrato): o bloqueio sai e a tela refaz as chamadas.
    LaunchedEffect(hasAccess) { if (hasAccess) state.clear() }

    // Troca de conta: o bloqueio era da anterior.
    val lastAccount = remember { AccountHolder(accountKey) }
    LaunchedEffect(accountKey) {
        if (lastAccount.value != accountKey) {
            lastAccount.value = accountKey
            state.clear()
        }
    }

    val current = block?.takeIf { !hasAccess }
    LaunchedEffect(current == null, purchaseOffered) {
        if (current == null || !purchaseOffered) paywallOpen = false
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (current != null) Modifier.clearAndSetSemantics { } else Modifier,
            ),
        ) {
            content()
        }
        if (current != null) {
            HoldSystemBack(enabled = true) { if (paywallOpen) paywallOpen = false }
            BlockCover {
                val openPaywall = paywall
                if (paywallOpen && purchaseOffered && openPaywall != null) {
                    Box(Modifier.fillMaxSize().testTag(SubscriptionBlockedTestTags.PAYWALL)) {
                        openPaywall { paywallOpen = false }
                    }
                } else {
                    SubscriptionBlockedScreen(
                        reason = current.reason,
                        onSubscribe = { if (purchaseOffered) paywallOpen = true },
                        onWhatsApp = {
                            val handler = currentOnWhatsApp
                            if (handler != null) {
                                handler(current)
                            } else {
                                urlLauncher.openWhatsApp(
                                    whatsAppPhone,
                                    texts.whatsAppMessageFor(current.reason, purchaseOffered),
                                )
                            }
                        },
                        onSignOut = {
                            state.clear()
                            currentOnSignOut()
                        },
                        texts = texts,
                        paywallEnabled = purchaseOffered,
                    )
                }
            }
        }
    }
}

/**
 * A tela de bloqueio oferece compra? Só com o paywall ligado **e** um paywall para abrir (2.308.0).
 * Pura, para teste.
 */
fun subscriptionBlockOffersPurchase(paywallEnabled: Boolean, hasPaywall: Boolean): Boolean =
    paywallEnabled && hasPaywall

private class AccountHolder(var value: Any?)

/** Opaca, ocupa tudo e engole o toque no passe `Initial` — nada embaixo reage. */
@Composable
private fun BlockCover(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Só os eventos que NINGUÉM da cobertura consumiu: os botões dela reagem antes.
                        awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        content()
    }
}

/**
 * O voltar do sistema com a cobertura aberta não desempilha a navegação escondida: fecha o paywall,
 * se aberto, e senão não faz nada. Mesma escolha do `AppServiceGate` (BackHandler multiplataforma).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
private fun HoldSystemBack(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
