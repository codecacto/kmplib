package br.com.codecacto.kmplib.ui.screens.feedback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.feedback.FeedbackContactPolicy
import br.com.codecacto.kmplib.feedback.FeedbackField
import br.com.codecacto.kmplib.feedback.feedbackPrefillValue
import br.com.codecacto.kmplib.feedback.FeedbackMotivo
import br.com.codecacto.kmplib.feedback.FeedbackService
import br.com.codecacto.kmplib.feedback.FeedbackSource
import br.com.codecacto.kmplib.feedback.feedbackInitialMessage
import br.com.codecacto.kmplib.feedback.shouldSeedFeedbackField
import br.com.codecacto.kmplib.feedback.validateFeedbackForm
import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.platform.automation.TopBarTestTags
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppTextArea
import br.com.codecacto.kmplib.ui.components.AppTextField
import br.com.codecacto.kmplib.ui.components.FormDefaults
import br.com.codecacto.kmplib.ui.locale.rememberDevicePhoneInputFormat
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass
import br.com.codecacto.kmplib.ui.theme.readableContentColorFor
import kotlinx.coroutines.launch

/**
 * Ids estáveis da [FeedbackScreen] para flows Maestro (2.347.0). O voltar continua sendo o
 * [TopBarTestTags.VOLTAR] (`topbar-voltar`) e o título o [TopBarTestTags.TITULO] (`topbar-titulo`).
 */
object FeedbackTestTags {
    /** Cartão de um motivo: `feedback-motivo-sugestao`, `feedback-motivo-bug`… (o `valor` do enum). */
    fun motivo(motivo: FeedbackMotivo): String = "feedback-motivo-${motivo.valor}"
    const val MENSAGEM: String = "feedback-input-mensagem"
    const val NOME: String = "feedback-input-nome"
    const val EMAIL: String = "feedback-input-email"
    const val WHATSAPP: String = "feedback-input-whatsapp"
    const val BTN_ENVIAR: String = "feedback-btn-enviar"

    /** Faixa da recusa do servidor, logo acima do botão de enviar. */
    const val ERRO: String = "feedback-erro"

    /** A confirmação de envio (só existe depois do envio aceito). */
    const val SUCESSO: String = "feedback-sucesso"
    const val BTN_CONTINUAR: String = "feedback-btn-continuar"
}

/**
 * Tela completa de feedback com formulário.
 *
 * Gerencia seu próprio estado internamente e envia o feedback
 * via [FeedbackService] (deve estar inicializado). Mensagem e WhatsApp
 * são obrigatórios para permitir retorno ao usuário.
 *
 * ## Desenho (2.347.0)
 *
 * Cabeçalho só com voltar e título; no corpo, o título da seção do motivo com a linha de contexto
 * logo abaixo ([FeedbackTexts.subtitle], 2.349.0); o motivo em CARTÕES com ícone (grade de 2 colunas no telefone, 3 fora dele); a mensagem num campo generoso; o
 * bloco "Para te responder" com o canal obrigatório primeiro, nome e o canal opcional (ver
 * [FeedbackContactPolicy]: app BR = WhatsApp obrigatório; app GLOBAL = e-mail obrigatório); e o botão **Enviar fixo no rodapé**, que
 * acompanha o teclado. O botão envia sempre: os campos recusados são marcados todos de uma vez, no
 * próprio campo, e o primeiro é trazido à vista; a recusa do SERVIDOR aparece numa faixa logo acima do
 * botão. Enviado, a tela vira uma confirmação (selo animado, mensagem, resumo do motivo e do WhatsApp
 * de retorno) com "Continuar" no rodapé. Cores só do tema (e de [primaryColor]); com "reduzir
 * movimento" ligado, nada anima.
 *
 * ```kotlin
 * FeedbackScreen(
 *     onBack = { navController.popBackStack() },
 *     texts = rememberFeedbackTexts().copy(title = "Send Feedback")
 * )
 * ```
 *
 * O feedback é IDENTIFICADO. **Nasce preenchido com quem está logado** (2.349.0): nome, e-mail e
 * WhatsApp vêm de [defaultName]/[defaultEmail]/[defaultWhatsapp] e, sem eles, do que o app entregou
 * ao `FeedbackService.updateUser(userId, userEmail, userName, userPhone)` — o app que já chama o
 * `updateUser` no login não precisa passar nada à tela. Valor que chega depois de a tela abrir entra
 * uma vez por campo, e nunca por cima do que a pessoa digitou.
 *
 * @param onBack Callback para voltar à tela anterior
 * @param primaryColor Cor de destaque (motivo escolhido, botão, selo da confirmação). Default: a
 *   primária do tema.
 * @param backgroundColor Cor de fundo da tela (e do cabeçalho e do rodapé)
 * @param cardColor Cor de fundo dos cartões (motivos e resumo da confirmação)
 * @param texts Textos customizáveis (suporta i18n)
 * @param defaultName Nome pré-preenchido (opcional; ex.: nome do usuário logado)
 * @param defaultEmail E-mail pré-preenchido (opcional; ex.: e-mail do usuário logado)
 * @param defaultWhatsapp WhatsApp pré-preenchido em dígitos (opcional; ex.: telefone do perfil)
 * @param bottomBar Rodapé fixo, opcional — o lugar do banner de house ad. Vai direto para o
 *   `bottomBar` do `Scaffold` interno, e o botão de enviar fica COLADO acima dele (nunca por baixo).
 *   Vazio por default.
 *
 *   ```kotlin
 *   FeedbackScreen(
 *       onBack = { navController.popBackStack() },
 *       bottomBar = { ManagedBannerAd(Modifier.fillMaxWidth(), size = BannerSize.STANDARD) },
 *   )
 *   ```
 * @param initialMotivo Motivo com que a tela ABRE já marcado (2.341.0) — editável: a pessoa troca
 *   pelos cartões. Par de [defaultName]: se chegar depois da primeira composição, entra uma vez, e
 *   só se nenhum motivo foi escolhido.
 * @param initialMessage Texto inicial da mensagem (2.341.0) — editável, a pessoa completa ou
 *   apaga. Não é aparado (pode terminar em `"\n\n"` para ela continuar embaixo); o envio apara.
 *   Mesma semeadura tardia do [initialMotivo].
 *
 *   ```kotlin
 *   FeedbackScreen(
 *       onBack = { navController.popBackStack() },
 *       initialMotivo = FeedbackMotivo.OUTRO,
 *       initialMessage = "Quero o contrato personalizado (R$ 30).\n\n",
 *   )
 *   ```
 * @param onFeedbackSent Callback opcional chamado após envio com sucesso
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    cardColor: Color = MaterialTheme.colorScheme.surface,
    texts: FeedbackTexts? = null,
    defaultName: String? = null,
    defaultEmail: String? = null,
    defaultWhatsapp: String? = null,
    bottomBar: @Composable () -> Unit = {},
    /**
     * Formato do WhatsApp (2.219.0). Default: o da região do aparelho — o brasileiro de sempre no
     * Brasil (11 dígitos com DDD), o internacional E.164 fora dele (`+351 912 345 678`), que é
     * como o número é enviado. Antes, quem estava fora do Brasil não conseguia enviar o feedback.
     */
    phoneFormat: PhoneInputFormat = rememberDevicePhoneInputFormat(),
    // Antes de `onFeedbackSent` para não roubar a posição de trailing lambda (2.341.0).
    initialMotivo: FeedbackMotivo? = null,
    initialMessage: String? = null,
    /**
     * Canal obrigatório de retorno (2.349.0). `null` (default) = o declarado na inicialização
     * ([br.com.codecacto.kmplib.feedback.FeedbackConfig.contactPolicy] /
     * `CentralServicesConfig.feedbackContactPolicy`), que por sua vez é WhatsApp por default. Passe
     * aqui só para uma tela que precise fugir da regra do app.
     */
    contactPolicy: FeedbackContactPolicy? = null,
    onFeedbackSent: (() -> Unit)? = null
) {
    @Suppress("NAME_SHADOWING")
    val texts: FeedbackTexts = texts ?: rememberFeedbackTexts(phoneFormat)
    // O usuário logado que o serviço já conhece (`FeedbackService.updateUser`, 2.349.0): é o valor
    // com que nome, e-mail e WhatsApp nascem quando o app não passa `default*`. Observado, porque o
    // perfil costuma chegar depois de a tela abrir.
    val serviceConfig by FeedbackService.configFlow.collectAsState()
    val policy = contactPolicy ?: serviceConfig?.contactPolicy ?: FeedbackContactPolicy.WHATSAPP_REQUIRED
    @Suppress("NAME_SHADOWING")
    val defaultName = feedbackPrefillValue(defaultName, serviceConfig?.userName)
    @Suppress("NAME_SHADOWING")
    val defaultEmail = feedbackPrefillValue(defaultEmail, serviceConfig?.userEmail)
    @Suppress("NAME_SHADOWING")
    val defaultWhatsapp = feedbackPrefillValue(defaultWhatsapp, serviceConfig?.userPhone)
    var selectedMotivo by remember { mutableStateOf(initialMotivo) }
    var mensagem by remember { mutableStateOf(feedbackInitialMessage(initialMessage)) }
    var nome by remember { mutableStateOf(defaultName.orEmpty()) }
    var email by remember { mutableStateOf(defaultEmail.orEmpty()) }
    var whatsapp by remember { mutableStateOf(phoneFormat.fromStoredValue(defaultWhatsapp)) }
    var isLoading by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }
    var motivoError by remember { mutableStateOf<String?>(null) }
    var mensagemError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var whatsappError by remember { mutableStateOf<String?>(null) }
    // Recusa do servidor: não é de campo nenhum, vai junto do botão (2.347.0; era um snackbar).
    var serverError by remember { mutableStateOf<String?>(null) }

    // ## Os defaults chegam DEPOIS, e os `remember` acima não os veriam
    //
    // Quem chama esta tela lê o perfil de forma assíncrona (`produceState`, `collectAsState`), então
    // na primeira composição os três defaults são `null` e os `remember` capturam string vazia.
    // Semeia **uma vez por campo, quando o valor aparece**, e só se a pessoa ainda não digitou nada.
    // Um valor presente já na 1ª composição conta como semeado (entrou pelo `remember`).
    var semeouNome by remember { mutableStateOf(!defaultName.isNullOrBlank()) }
    var semeouEmail by remember { mutableStateOf(!defaultEmail.isNullOrBlank()) }
    var semeouWhatsapp by remember { mutableStateOf(!defaultWhatsapp.isNullOrBlank()) }
    LaunchedEffect(defaultName, defaultEmail, defaultWhatsapp) {
        if (shouldSeedFeedbackField(semeouNome, !defaultName.isNullOrBlank(), nome.isBlank())) {
            semeouNome = true
            nome = defaultName.orEmpty()
        }
        if (shouldSeedFeedbackField(semeouEmail, !defaultEmail.isNullOrBlank(), email.isBlank())) {
            semeouEmail = true
            email = defaultEmail.orEmpty()
        }
        if (shouldSeedFeedbackField(semeouWhatsapp, !defaultWhatsapp.isNullOrBlank(), whatsapp.isBlank())) {
            semeouWhatsapp = true
            // Normaliza dígitos, E.164 ou número já mascarado; a máscara do campo desenha o resto.
            whatsapp = phoneFormat.fromStoredValue(defaultWhatsapp)
        }
    }
    // Mesma semeadura para motivo e mensagem iniciais (2.341.0).
    var semeouMotivo by remember { mutableStateOf(initialMotivo != null) }
    var semeouMensagem by remember { mutableStateOf(!initialMessage.isNullOrBlank()) }
    LaunchedEffect(initialMotivo, initialMessage) {
        if (shouldSeedFeedbackField(semeouMotivo, initialMotivo != null, selectedMotivo == null)) {
            semeouMotivo = true
            selectedMotivo = initialMotivo
        }
        if (shouldSeedFeedbackField(semeouMensagem, !initialMessage.isNullOrBlank(), mensagem.isBlank())) {
            semeouMensagem = true
            mensagem = feedbackInitialMessage(initialMessage)
        }
    }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val reduceMotion = LocalReduceMotion.current
    val maxWidth = FormDefaults.maxContentWidth(LocalWindowSizeClass.current)
    val motivoView = remember { BringIntoViewRequester() }
    val mensagemFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val whatsappFocus = remember { FocusRequester() }

    fun enviar() {
        if (isLoading) return
        serverError = null
        val recusados = validateFeedbackForm(
            motivo = selectedMotivo,
            mensagem = mensagem,
            email = email,
            whatsapp = whatsapp,
            whatsappComplete = phoneFormat.isCompleteWhatsapp(whatsapp),
            policy = policy,
        )
        motivoError = if (FeedbackField.MOTIVO in recusados) texts.motivoError else null
        mensagemError = if (FeedbackField.MENSAGEM in recusados) texts.mensagemError else null
        emailError = when {
            FeedbackField.EMAIL !in recusados -> null
            email.isBlank() -> texts.emailRequiredError
            else -> texts.emailError
        }
        whatsappError = if (FeedbackField.WHATSAPP in recusados) texts.whatsappError else null
        when (recusados.firstOrNull()) {
            FeedbackField.MOTIVO -> {
                focusManager.clearFocus()
                scope.launch { motivoView.bringIntoView() }
            }
            // O foco leva o campo à vista (e abre o teclado onde é para digitar).
            FeedbackField.MENSAGEM -> mensagemFocus.requestFocus()
            FeedbackField.EMAIL -> emailFocus.requestFocus()
            FeedbackField.WHATSAPP -> whatsappFocus.requestFocus()
            null -> {
                focusManager.clearFocus()
                val motivo = selectedMotivo ?: return
                isLoading = true
                scope.launch {
                    FeedbackService.sendFeedback(
                        source = FeedbackSource.FEEDBACK_SCREEN,
                        motivo = motivo.valor,
                        mensagem = mensagem.trim(),
                        nome = nome.trim(),
                        email = email.trim(),
                        whatsapp = phoneFormat.toSubmitValue(whatsapp)
                    ).onSuccess {
                        isLoading = false
                        isSent = true
                        onFeedbackSent?.invoke()
                    }.onFailure { e ->
                        isLoading = false
                        serverError = e.message ?: texts.errorMessage
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            FeedbackHeader(
                title = texts.title,
                backContentDescription = texts.backContentDescription,
                backgroundColor = backgroundColor,
                onBack = onBack,
            )
        },
        bottomBar = bottomBar,
    ) { paddingValues ->
        // `paddingValues` já traz o cabeçalho em cima e, embaixo, o rodapé do app (ou a barra de
        // navegação do sistema, sem rodapé). Consumido, o `imePadding` só soma o que o TECLADO passa
        // disso: o botão de enviar sobe junto com ele, sem folga dobrada.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = isSent,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(140))
                    }
                },
                label = "feedback-estado",
            ) { sent ->
                if (sent) {
                    FeedbackSuccess(
                        texts = texts,
                        motivo = selectedMotivo,
                        policy = policy,
                        email = email.trim(),
                        whatsappFormatado = formatWhatsapp(phoneFormat, whatsapp),
                        primaryColor = primaryColor,
                        backgroundColor = backgroundColor,
                        cardColor = cardColor,
                        maxWidth = maxWidth,
                        reduceMotion = reduceMotion,
                        onContinue = onBack,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    // `widthIn` antes de `fillMaxWidth`: na ordem inversa o teto não vale.
                                    .widthIn(max = maxWidth)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 8.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(28.dp),
                            ) {
                                // Motivo
                                Column(
                                    modifier = Modifier.bringIntoViewRequester(motivoView),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Título da seção + a linha de contexto (o que acontece com a
                                    // mensagem), mais leve logo abaixo — 2.349.0: saiu do cabeçalho.
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        SectionTitle(texts.motivoLabel)
                                        Text(
                                            text = if (policy == FeedbackContactPolicy.EMAIL_REQUIRED) texts.subtitleEmail else texts.subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    MotivoGrid(
                                        texts = texts,
                                        selected = selectedMotivo,
                                        hasError = motivoError != null,
                                        enabled = !isLoading,
                                        primaryColor = primaryColor,
                                        cardColor = cardColor,
                                        reduceMotion = reduceMotion,
                                        onSelect = {
                                            selectedMotivo = it
                                            motivoError = null
                                            serverError = null
                                        },
                                    )
                                    motivoError?.let { erro ->
                                        Text(
                                            text = erro,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                        )
                                    }
                                }

                                // Mensagem
                                AppTextArea(
                                    value = mensagem,
                                    onValueChange = {
                                        mensagem = it
                                        mensagemError = null
                                        serverError = null
                                    },
                                    modifier = Modifier
                                        .focusRequester(mensagemFocus)
                                        .testTag(FeedbackTestTags.MENSAGEM),
                                    label = texts.mensagemLabel,
                                    placeholder = texts.mensagemPlaceholder,
                                    minLines = 6,
                                    maxLines = 12,
                                    errorMessage = mensagemError,
                                    enabled = !isLoading,
                                    primaryColor = primaryColor,
                                    keepTextLocally = true,
                                )

                                // Contato — o canal obrigatório da política vem primeiro, o nome no
                                // meio e o canal opcional por último (2.349.0).
                                val emailObrigatorio = policy == FeedbackContactPolicy.EMAIL_REQUIRED
                                val campoWhatsapp: @Composable (ImeAction) -> Unit = { ime ->
                                    AppTextField(
                                        value = whatsapp,
                                        onValueChange = {
                                            whatsapp = phoneFormat.filter(it)
                                            whatsappError = null
                                            serverError = null
                                        },
                                        modifier = Modifier
                                            .focusRequester(whatsappFocus)
                                            .testTag(FeedbackTestTags.WHATSAPP),
                                        label = if (emailObrigatorio) texts.whatsappOptionalLabel else texts.whatsappLabel,
                                        placeholder = texts.whatsappPlaceholder,
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ime,
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        visualTransformation = phoneFormat.visualTransformation,
                                        errorMessage = whatsappError,
                                        enabled = !isLoading,
                                        primaryColor = primaryColor,
                                        // Sem `keepTextLocally`: o filtro RECUSA o que não é dígito, e com o
                                        // texto local a letra recusada ficaria na tela.
                                    )
                                }
                                val campoEmail: @Composable (ImeAction) -> Unit = { ime ->
                                    AppTextField(
                                        value = email,
                                        onValueChange = {
                                            email = it
                                            emailError = null
                                            serverError = null
                                        },
                                        modifier = Modifier
                                            .focusRequester(emailFocus)
                                            .testTag(FeedbackTestTags.EMAIL),
                                        label = if (emailObrigatorio) texts.emailRequiredLabel else texts.emailLabel,
                                        placeholder = texts.emailPlaceholder,
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ime,
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        errorMessage = emailError,
                                        enabled = !isLoading,
                                        primaryColor = primaryColor,
                                        keepTextLocally = true,
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SectionTitle(texts.contactSectionLabel)
                                    if (emailObrigatorio) campoEmail(ImeAction.Next) else campoWhatsapp(ImeAction.Next)
                                    AppTextField(
                                        value = nome,
                                        onValueChange = {
                                            nome = it
                                            serverError = null
                                        },
                                        modifier = Modifier.testTag(FeedbackTestTags.NOME),
                                        label = texts.nomeLabel,
                                        placeholder = texts.nomePlaceholder,
                                        keyboardType = KeyboardType.Text,
                                        enabled = !isLoading,
                                        primaryColor = primaryColor,
                                        keepTextLocally = true,
                                    )
                                    if (emailObrigatorio) campoWhatsapp(ImeAction.Done) else campoEmail(ImeAction.Done)
                                }
                            }
                        }

                        FeedbackFooter(backgroundColor = backgroundColor, maxWidth = maxWidth) {
                            serverError?.let { ServerErrorBanner(it) }
                            AppButton(
                                text = texts.submitButton,
                                onClick = ::enviar,
                                modifier = Modifier.fillMaxWidth().testTag(FeedbackTestTags.BTN_ENVIAR),
                                isLoading = isLoading,
                                enabled = !isLoading,
                                primaryColor = primaryColor,
                                icon = Icons.AutoMirrored.Filled.Send,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Peças da tela
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackHeader(
    title: String,
    backContentDescription: String,
    backgroundColor: Color,
    onBack: () -> Unit,
) {
    val contentColor = contentColorFor(backgroundColor).takeOrElse(MaterialTheme.colorScheme.onSurface)
    TopAppBar(
        title = {
            // Só o título (2.349.0): a linha de contexto mora no corpo, acima dos motivos.
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .testTag(TopBarTestTags.TITULO)
                    .semantics { heading() },
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag(TopBarTestTags.VOLTAR)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backContentDescription)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            scrolledContainerColor = backgroundColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
        ),
    )
}

/** `contentColorFor` devolve `Color.Unspecified` fora dos papéis do tema; aí vale o fallback. */
private fun Color.takeOrElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

private fun FeedbackMotivo.icon(): ImageVector = when (this) {
    FeedbackMotivo.SUGESTAO -> Icons.Outlined.Lightbulb
    FeedbackMotivo.BUG -> Icons.Outlined.BugReport
    FeedbackMotivo.RECLAMACAO -> Icons.Outlined.SentimentDissatisfied
    FeedbackMotivo.DUVIDA -> Icons.AutoMirrored.Outlined.HelpOutline
    FeedbackMotivo.ELOGIO -> Icons.Outlined.FavoriteBorder
    FeedbackMotivo.OUTRO -> Icons.Outlined.MoreHoriz
}

private fun FeedbackTexts.labelOf(motivo: FeedbackMotivo): String = when (motivo) {
    FeedbackMotivo.SUGESTAO -> motivoSugestao
    FeedbackMotivo.BUG -> motivoBug
    FeedbackMotivo.RECLAMACAO -> motivoReclamacao
    FeedbackMotivo.DUVIDA -> motivoDuvida
    FeedbackMotivo.ELOGIO -> motivoElogio
    FeedbackMotivo.OUTRO -> motivoOutro
}

/** Grade de cartões de motivo: 2 colunas no telefone, 3 em janela média/expandida. */
@Composable
private fun MotivoGrid(
    texts: FeedbackTexts,
    selected: FeedbackMotivo?,
    hasError: Boolean,
    enabled: Boolean,
    primaryColor: Color,
    cardColor: Color,
    reduceMotion: Boolean,
    onSelect: (FeedbackMotivo) -> Unit,
) {
    val columns = if (LocalWindowSizeClass.current == WindowSizeClass.COMPACTA) 2 else 3
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeedbackMotivo.entries.chunked(columns).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                linha.forEach { motivo ->
                    MotivoCard(
                        motivo = motivo,
                        label = texts.labelOf(motivo),
                        selected = motivo == selected,
                        hasError = hasError,
                        enabled = enabled,
                        primaryColor = primaryColor,
                        cardColor = cardColor,
                        reduceMotion = reduceMotion,
                        onClick = { onSelect(motivo) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                repeat(columns - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MotivoCard(
    motivo: FeedbackMotivo,
    label: String,
    selected: Boolean,
    hasError: Boolean,
    enabled: Boolean,
    primaryColor: Color,
    cardColor: Color,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val colorSpec = if (reduceMotion) snap() else tween<Color>(180)
    val container by animateColorAsState(
        targetValue = if (selected) primaryColor.copy(alpha = 0.10f).compositeOver(cardColor) else cardColor,
        animationSpec = colorSpec,
        label = "motivo-fundo",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> primaryColor
            hasError -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = colorSpec,
        label = "motivo-borda",
    )
    val onPrimary = readableContentColorFor(primaryColor)
    Box(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(container)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .testTag(FeedbackTestTags.motivo(motivo))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (selected) primaryColor else primaryColor.copy(alpha = 0.12f).compositeOver(cardColor)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = motivo.icon(),
                    contentDescription = null,
                    tint = if (selected) onPrimary else primaryColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(primaryColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Rodapé fixo: divisória fina, conteúdo centrado no teto de largura do formulário. */
@Composable
private fun FeedbackFooter(
    backgroundColor: Color,
    maxWidth: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = backgroundColor, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ServerErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(FeedbackTestTags.ERRO),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** O WhatsApp como a pessoa o viu no campo — com a máscara do formato do aparelho. */
private fun formatWhatsapp(phoneFormat: PhoneInputFormat, digits: String): String =
    if (digits.isBlank()) "" else phoneFormat.visualTransformation.filter(AnnotatedString(digits)).text.text

@Composable
private fun FeedbackSuccess(
    texts: FeedbackTexts,
    motivo: FeedbackMotivo?,
    policy: FeedbackContactPolicy,
    email: String,
    whatsappFormatado: String,
    primaryColor: Color,
    backgroundColor: Color,
    cardColor: Color,
    maxWidth: Dp,
    reduceMotion: Boolean,
    onContinue: () -> Unit,
) {
    // Selo: entra com mola (escala) e o halo abre um pouco depois. Reduzir movimento = já no lugar.
    val badgeScale = remember { Animatable(if (reduceMotion) 1f else 0.4f) }
    val haloScale = remember { Animatable(if (reduceMotion) 1f else 0.6f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        launch { badgeScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { haloScale.animateTo(1f, tween(durationMillis = 520, delayMillis = 80)) }
    }
    val onPrimary = readableContentColorFor(primaryColor)

    Column(Modifier.fillMaxSize().testTag(FeedbackTestTags.SUCESSO)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(128.dp)
                            .scale(haloScale.value)
                            .graphicsLayer { alpha = haloScale.value.coerceIn(0f, 1f) }
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.12f).compositeOver(backgroundColor))
                    )
                    Box(
                        Modifier
                            .size(80.dp)
                            .scale(badgeScale.value)
                            .clip(CircleShape)
                            .background(primaryColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = onPrimary, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = texts.successTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (policy == FeedbackContactPolicy.EMAIL_REQUIRED) texts.successMessageEmail else texts.successMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // O canal de retorno do resumo é o da política (o obrigatório, sempre preenchido aqui).
                val retornoPorEmail = policy == FeedbackContactPolicy.EMAIL_REQUIRED
                val retorno = if (retornoPorEmail) email else whatsappFormatado
                if (motivo != null || retorno.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardColor,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            if (motivo != null) {
                                SummaryRow(
                                    icon = motivo.icon(),
                                    label = texts.successReasonLabel,
                                    value = texts.labelOf(motivo),
                                    primaryColor = primaryColor,
                                )
                            }
                            if (motivo != null && retorno.isNotEmpty()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            if (retorno.isNotEmpty()) {
                                SummaryRow(
                                    icon = if (retornoPorEmail) Icons.Outlined.Email else Icons.AutoMirrored.Outlined.Chat,
                                    label = if (retornoPorEmail) texts.emailRequiredLabel else texts.whatsappLabel,
                                    value = retorno,
                                    primaryColor = primaryColor,
                                )
                            }
                        }
                    }
                }
            }
        }
        FeedbackFooter(backgroundColor = backgroundColor, maxWidth = maxWidth) {
            AppButton(
                text = texts.continueButton,
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().testTag(FeedbackTestTags.BTN_CONTINUAR),
                primaryColor = primaryColor,
            )
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String, primaryColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
