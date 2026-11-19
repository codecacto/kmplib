package br.com.codecacto.kmplib.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.BackTopBar
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.appKeyboardOptions
import br.com.codecacto.kmplib.ui.components.rememberSyncedTextFieldState
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.leituraMaxWidth

/**
 * Tela de ajuda em CONVERSA (2.367.0): a pessoa pergunta, e cada resposta é um fluxo escrito do produto —
 * título, texto (ou o resumo), vídeo quando houver e "Ver passo a passo", que abre o fluxo inteiro e volta à
 * conversa sem perdê-la. Nada é texto gerado.
 *
 * A conversa sobrevive a sair desta tela e voltar na mesma sessão do app (memória do processo, nunca disco) e some
 * com "Nova conversa" (top bar), com o fim do processo ou com [clearHelpConversations] (chame no logout).
 *
 * O suporte é do app ([onSupport]): nesta tela nada abre navegador nem leva a pagamento. Com [supportWhatsApp]
 * (2.375.0, opt-in), o suporte vira o botão de destaque "Chamar o suporte no WhatsApp" — no rodapé da conversa e na
 * bolha de sem resposta — e abre a conversa com a mensagem pronta; aí o [onSupport] NÃO é chamado (o toque já levou a
 * pessoa ao WhatsApp, e chamar os dois abriria duas saídas de uma vez). Ele segue obrigatório: é o caminho quando o
 * número do [HelpSupportWhatsApp] não é válido.
 *
 * @param supportWhatsApp suporte pelo WhatsApp; `null` (default) = o botão "Falar com o suporte" de sempre.
 * @param bottomBar rodapé fixo (o lugar do banner); o campo de pergunta fica acima dele e do teclado.
 */
@Composable
fun HelpScreen(
    config: HelpConfig,
    onBack: () -> Unit,
    onSupport: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    texts: HelpTexts = rememberHelpTexts(),
    supportWhatsApp: HelpSupportWhatsApp? = null,
) {
    val contentKey = config.contentKey()
    val viewModel = viewModel(key = "kmplib-help-$contentKey") {
        HelpViewModel(RestHelpSource(config), HelpConversations.forKey(contentKey))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Fora do ramo do detalhe: abrir e fechar o "passo a passo" volta à conversa na mesma posição.
    val listState = rememberLazyListState()
    val openFlow = state.openFlow

    HelpBackHandler(enabled = openFlow != null) { viewModel.onAction(HelpAction.CloseFlow) }
    FollowLatestTurn(state, listState)

    Scaffold(
        modifier = modifier.testTag(HelpTestTags.HELP_SCREEN),
        topBar = {
            BackTopBar(
                title = texts.title,
                onBackClick = { if (openFlow != null) viewModel.onAction(HelpAction.CloseFlow) else onBack() },
                actions = {
                    if (openFlow == null && state.turns.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.onAction(HelpAction.NewConversation) },
                            modifier = Modifier.testTag(HelpTestTags.NEW_CONVERSATION_BUTTON),
                        ) { Text(texts.newConversation) }
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        if (openFlow != null) {
            HelpFlowDetail(openFlow, texts, Modifier.padding(innerPadding))
        } else {
            HelpContent(
                state = state,
                texts = texts,
                onAction = viewModel::onAction,
                onSupport = onSupport,
                onOpenLibrary = onOpenLibrary,
                listState = listState,
                supportWhatsApp = supportWhatsApp,
                // O teclado empurra o campo; o que o Scaffold já descontou (banner, barra de gestos) não soma de novo.
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).imePadding(),
            )
        }
    }
}

/** Rola até a pergunta mais recente quando um turno entra ou muda de estado (a resposta começa logo abaixo). */
@Composable
private fun FollowLatestTurn(state: HelpState, listState: LazyListState) {
    val last = state.turns.lastOrNull()
    val lastIndex = (state.turns.size - 1) * ITEMS_PER_TURN
    LaunchedEffect(last?.id, last?.status) {
        if (last != null && state.openFlow == null) listState.animateScrollToItem(lastIndex)
    }
}

@Composable
internal fun HelpContent(
    state: HelpState,
    texts: HelpTexts,
    onAction: (HelpAction) -> Unit,
    onSupport: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    supportWhatsApp: HelpSupportWhatsApp? = null,
) {
    val whatsAppPhone = remember(supportWhatsApp) { supportWhatsApp?.normalizedPhone }
    val inactive = state.status == HelpContentStatus.Inactive
    val maxWidth = leituraMaxWidth(LocalWindowSizeClass.current)
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().widthIn(max = maxWidth).testTag(HelpTestTags.CONVERSATION),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    inactive -> item(key = "inativa") {
                        EmptyState(icon = Icons.AutoMirrored.Outlined.HelpOutline, title = texts.inactive)
                    }
                    state.turns.isEmpty() -> item(key = "sugestoes") { HelpSuggestions(state, texts, onAction) }
                    else -> state.turns.forEachIndexed { index, turn ->
                        item(key = "q-${turn.id}") { HelpQuestionBubble(turn, texts) }
                        item(key = "a-${turn.id}") {
                            HelpReplyBubble(
                                turn = turn,
                                isLatest = index == state.turns.lastIndex,
                                texts = texts,
                                onAction = onAction,
                                onSupport = onSupport,
                                supportWhatsApp = supportWhatsApp,
                                whatsAppPhone = whatsAppPhone,
                            )
                        }
                    }
                }
                item(key = "rodape") {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (supportWhatsApp != null && whatsAppPhone != null) {
                            HelpSupportWhatsAppButton(
                                support = supportWhatsApp,
                                phone = whatsAppPhone,
                                texts = texts,
                                testTag = HelpTestTags.SUPPORT_WHATSAPP_BUTTON,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            AppOutlinedButton(
                                text = texts.support,
                                onClick = onSupport,
                                modifier = Modifier.fillMaxWidth().testTag(HelpTestTags.SUPPORT_BUTTON),
                            )
                        }
                        if (!inactive) {
                            AppOutlinedButton(
                                text = texts.openLibrary,
                                onClick = onOpenLibrary,
                                modifier = Modifier.fillMaxWidth().testTag(HelpTestTags.OPEN_LIBRARY_BUTTON),
                            )
                        }
                    }
                }
            }
        }
        if (!inactive) {
            HorizontalDivider()
            HelpComposer(
                state = state,
                texts = texts,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = maxWidth),
            )
        }
    }
}

/** Campo fixo embaixo: o texto mora no campo (sem perder letra no iOS) e fora do estado salvo (é texto livre). */
@Composable
private fun HelpComposer(
    state: HelpState,
    texts: HelpTexts,
    onAction: (HelpAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldState = rememberSyncedTextFieldState(
        text = state.question,
        onTextChange = { onAction(HelpAction.QuestionChanged(it)) },
        saveable = false,
    )
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            state = fieldState,
            modifier = Modifier.weight(1f).testTag(HelpTestTags.QUESTION_INPUT),
            placeholder = { Text(texts.questionPlaceholder) },
            isError = state.questionError,
            supportingText = if (state.questionError) {
                { Text(texts.questionTooShort) }
            } else {
                null
            },
            keyboardOptions = appKeyboardOptions(imeAction = ImeAction.Send),
            onKeyboardAction = { onAction(HelpAction.Submit) },
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 4),
            shape = RoundedCornerShape(24.dp),
        )
        if (state.isAsking) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(
                onClick = { onAction(HelpAction.Submit) },
                modifier = Modifier.testTag(HelpTestTags.ASK_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = texts.ask,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** A pergunta, à direita. */
@Composable
private fun HelpQuestionBubble(turn: HelpTurn, texts: HelpTexts) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "${texts.youAsked}: ${turn.question}"
                testTag = HelpTestTags.questionBubble(turn.id)
            },
        ) {
            Text(
                text = turn.question,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** A resposta, à esquerda: o fluxo, "sem resposta", "Buscando…" ou o erro. */
@Composable
private fun HelpReplyBubble(
    turn: HelpTurn,
    isLatest: Boolean,
    texts: HelpTexts,
    onAction: (HelpAction) -> Unit,
    onSupport: () -> Unit,
    supportWhatsApp: HelpSupportWhatsApp? = null,
    whatsAppPhone: String? = null,
) {
    val status = turn.status
    val fill = status is HelpTurnStatus.Found
    Row(modifier = Modifier.fillMaxWidth().padding(end = if (fill) 0.dp else 48.dp)) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = (if (fill) Modifier.fillMaxWidth() else Modifier)
                .semantics { if (isLatest) liveRegion = LiveRegionMode.Polite }
                .testTag(HelpTestTags.answerBubble(turn.id)),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (status) {
                    HelpTurnStatus.Asking -> Row(
                        modifier = Modifier.testTag(HelpTestTags.ASKING),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(texts.searching, style = MaterialTheme.typography.bodyMedium)
                    }
                    is HelpTurnStatus.Found -> HelpFlowAnswer(status.flow, texts, onAction)
                    is HelpTurnStatus.NotFound -> {
                        Text(
                            text = if (status.needsDetails) texts.needsDetails else texts.notFound,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag(
                                if (status.needsDetails) HelpTestTags.NEEDS_DETAILS else HelpTestTags.NOT_FOUND,
                            ),
                        )
                        if (supportWhatsApp != null && whatsAppPhone != null) {
                            HelpSupportWhatsAppButton(
                                support = supportWhatsApp,
                                phone = whatsAppPhone,
                                texts = texts,
                                testTag = HelpTestTags.SUPPORT_WHATSAPP_IN_ANSWER_BUTTON,
                            )
                        } else {
                            AppOutlinedButton(
                                text = texts.support,
                                onClick = onSupport,
                                modifier = Modifier.testTag(HelpTestTags.SUPPORT_IN_ANSWER_BUTTON),
                            )
                        }
                    }
                    HelpTurnStatus.Failed -> {
                        Text(
                            text = texts.askError,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(HelpTestTags.ASK_ERROR),
                        )
                        // Só o turno mais recente repete: o histórico de quem perguntou depois já seguiu sem ele.
                        if (isLatest) {
                            AppOutlinedButton(
                                text = texts.retry,
                                onClick = { onAction(HelpAction.RetryAsk) },
                                modifier = Modifier.testTag(HelpTestTags.RETRY_BUTTON),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Título + texto do fluxo (o resumo, se o texto vier vazio) + vídeo + "Ver passo a passo". */
@Composable
private fun HelpFlowAnswer(flow: HelpFlow, texts: HelpTexts, onAction: (HelpAction) -> Unit) {
    Column(
        modifier = Modifier.testTag(HelpTestTags.ANSWER),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = flow.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        (flow.text ?: flow.summary)?.let { body ->
            Text(
                text = remember(body) { helpBoldText(body) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        flow.video?.let { HelpVideoPlayer(it) }
        AppOutlinedButton(
            text = texts.seeSteps,
            onClick = { onAction(HelpAction.OpenFlow(flow)) },
            modifier = Modifier.testTag(HelpTestTags.SEE_STEPS_BUTTON),
        )
    }
}

/** O fluxo inteiro, aberto pelo "Ver passo a passo": o mesmo desenho do detalhe da Biblioteca. */
@Composable
private fun HelpFlowDetail(flow: HelpFlow, texts: HelpTexts, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = leituraMaxWidth(LocalWindowSizeClass.current))
            .padding(16.dp),
    ) {
        HelpFlowBody(flow = flow, texts = texts, modifier = Modifier.testTag(HelpTestTags.FLOW_DETAIL))
    }
}

@Composable
private fun HelpSuggestions(state: HelpState, texts: HelpTexts, onAction: (HelpAction) -> Unit) {
    when (state.status) {
        HelpContentStatus.Loading -> HelpProgress()
        HelpContentStatus.Failed -> ErrorState(
            message = texts.loadError,
            onRetry = { onAction(HelpAction.RetryLoad) },
        )
        HelpContentStatus.Ready, HelpContentStatus.Inactive -> {
            if (state.suggestions.isEmpty()) return
            Column {
                Text(
                    text = texts.suggestions,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
                )
                state.suggestions.forEachIndexed { index, flow ->
                    if (index > 0) HorizontalDivider()
                    Text(
                        text = flow.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(HelpAction.SuggestionSelected(flow)) }
                            .padding(vertical = 14.dp)
                            .testTag(HelpTestTags.suggestion(flow.slug)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HelpProgress(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Cada turno ocupa dois itens da lista: a pergunta e a resposta. */
private const val ITEMS_PER_TURN = 2
