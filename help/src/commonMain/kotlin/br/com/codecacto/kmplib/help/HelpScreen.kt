package br.com.codecacto.kmplib.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppOutlinedButton
import br.com.codecacto.kmplib.ui.components.AppSearchField
import br.com.codecacto.kmplib.ui.components.BackTopBar
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.leituraMaxWidth

/**
 * Tela de ajuda: a pessoa pergunta e vê o fluxo que responde, com vídeo quando houver.
 *
 * O suporte é sempre do app ([onSupport]): nesta tela nada abre navegador nem leva a pagamento.
 *
 * @param bottomBar rodapé fixo (o lugar do banner); o conteúdo termina acima dele.
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
) {
    val viewModel = viewModel(key = "kmplib-help-${config.contentKey()}") { HelpViewModel(RestHelpSource(config)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasAnswer = state.answer != HelpAnswerState.Idle

    HelpBackHandler(enabled = hasAnswer) { viewModel.onAction(HelpAction.ClearAnswer) }
    Scaffold(
        modifier = modifier.testTag(HelpTestTags.HELP_SCREEN),
        topBar = {
            BackTopBar(
                title = texts.title,
                onBackClick = { if (hasAnswer) viewModel.onAction(HelpAction.ClearAnswer) else onBack() },
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        HelpContent(
            state = state,
            texts = texts,
            onAction = viewModel::onAction,
            onSupport = onSupport,
            onOpenLibrary = onOpenLibrary,
            modifier = Modifier.padding(innerPadding),
        )
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = leituraMaxWidth(LocalWindowSizeClass.current))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.status == HelpContentStatus.Inactive) {
            EmptyState(icon = Icons.AutoMirrored.Outlined.HelpOutline, title = texts.inactive)
        } else {
            HelpQuestionField(state, texts, onAction)
            HelpAnswerArea(state, texts, onAction)
        }
        AppOutlinedButton(
            text = texts.support,
            onClick = onSupport,
            modifier = Modifier.fillMaxWidth().testTag(HelpTestTags.SUPPORT_BUTTON),
        )
        if (state.status != HelpContentStatus.Inactive) {
            AppOutlinedButton(
                text = texts.openLibrary,
                onClick = onOpenLibrary,
                modifier = Modifier.fillMaxWidth().testTag(HelpTestTags.OPEN_LIBRARY_BUTTON),
            )
        }
    }
}

@Composable
private fun HelpQuestionField(state: HelpState, texts: HelpTexts, onAction: (HelpAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSearchField(
            query = state.question,
            onQueryChange = { onAction(HelpAction.QuestionChanged(it)) },
            modifier = Modifier.testTag(HelpTestTags.QUESTION_INPUT),
            placeholder = texts.questionPlaceholder,
            onSearch = { onAction(HelpAction.Submit) },
            // A dúvida é texto livre, pode trazer dado pessoal: não entra no estado salvo.
            saveable = false,
        )
        if (state.questionError) {
            Text(
                text = texts.questionTooShort,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AppButton(
            text = texts.ask,
            onClick = { onAction(HelpAction.Submit) },
            modifier = Modifier.fillMaxWidth().testTag(HelpTestTags.ASK_BUTTON),
            isLoading = state.answer == HelpAnswerState.Asking,
        )
    }
}

@Composable
private fun HelpAnswerArea(state: HelpState, texts: HelpTexts, onAction: (HelpAction) -> Unit) {
    when (val answer = state.answer) {
        HelpAnswerState.Idle -> HelpSuggestions(state, texts, onAction)
        HelpAnswerState.Asking -> HelpProgress()
        is HelpAnswerState.Found -> HelpFlowBody(
            flow = answer.flow,
            texts = texts,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(HelpTestTags.ANSWER),
        )
        HelpAnswerState.NotFound -> EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = texts.notFound,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(HelpTestTags.NOT_FOUND),
        )
        HelpAnswerState.Failed -> ErrorState(
            message = texts.askError,
            onRetry = { onAction(HelpAction.RetryAsk) },
            icon = Icons.Outlined.ErrorOutline,
        )
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
