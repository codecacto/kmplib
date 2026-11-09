package br.com.codecacto.kmplib.help

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.codecacto.kmplib.ui.components.AppSearchField
import br.com.codecacto.kmplib.ui.components.BackTopBar
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.RefreshableBox
import br.com.codecacto.kmplib.ui.components.ScrollableFillBox
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.leituraMaxWidth
import br.com.codecacto.kmplib.video.formatVideoTime
import coil3.compose.AsyncImage

/**
 * Biblioteca: vídeos e fluxos do produto, com busca por partes e detalhe na própria tela.
 *
 * @param bottomBar rodapé fixo (o lugar do banner); a lista termina acima dele.
 */
@Composable
fun HelpLibraryScreen(
    config: HelpConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    texts: HelpTexts = rememberHelpTexts(),
) {
    val viewModel = viewModel(key = "kmplib-help-library-${config.contentKey()}") {
        HelpLibraryViewModel(RestHelpSource(config))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(HelpLibraryAction.Resume)
        onPauseOrDispose { }
    }
    HelpBackHandler(enabled = state.selection != null) { viewModel.onAction(HelpLibraryAction.CloseDetail) }

    Scaffold(
        modifier = modifier.testTag(HelpTestTags.LIBRARY_SCREEN),
        topBar = {
            BackTopBar(
                title = texts.libraryTitle,
                onBackClick = {
                    if (state.selection != null) viewModel.onAction(HelpLibraryAction.CloseDetail) else onBack()
                },
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        val selection = state.selection
        if (selection != null) {
            HelpLibraryDetail(selection, texts, Modifier.padding(innerPadding))
        } else {
            HelpLibraryContent(state, texts, viewModel::onAction, Modifier.padding(innerPadding))
        }
    }
}

@Composable
internal fun HelpLibraryContent(
    state: HelpLibraryState,
    texts: HelpTexts,
    onAction: (HelpLibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.status == HelpContentStatus.Ready && state.hasContent) {
            AppSearchField(
                query = state.query,
                onQueryChange = { onAction(HelpLibraryAction.QueryChanged(it)) },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(HelpTestTags.LIBRARY_SEARCH),
                placeholder = texts.librarySearch,
            )
        }
        RefreshableBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(HelpLibraryAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.status == HelpContentStatus.Loading -> ScrollableFillBox(Modifier.fillMaxSize()) { HelpProgress() }
                state.status == HelpContentStatus.Failed -> ErrorState(
                    message = texts.loadError,
                    onRetry = { onAction(HelpLibraryAction.Retry) },
                    modifier = Modifier.fillMaxSize(),
                )
                state.status == HelpContentStatus.Inactive -> ScrollableFillBox(Modifier.fillMaxSize()) {
                    EmptyState(icon = Icons.AutoMirrored.Outlined.HelpOutline, title = texts.inactive)
                }
                !state.hasContent -> ScrollableFillBox(Modifier.fillMaxSize()) {
                    EmptyState(icon = Icons.AutoMirrored.Outlined.MenuBook, title = texts.libraryEmpty)
                }
                !state.hasResults -> ScrollableFillBox(Modifier.fillMaxSize()) {
                    EmptyState(icon = Icons.Outlined.SearchOff, title = texts.librarySearchEmpty)
                }
                else -> HelpLibraryList(state, texts, onAction)
            }
        }
    }
}

@Composable
private fun HelpLibraryList(
    state: HelpLibraryState,
    texts: HelpTexts,
    onAction: (HelpLibraryAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = leituraMaxWidth(LocalWindowSizeClass.current)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.refreshFailed) {
            item(key = "refresh-failed") {
                Text(
                    text = texts.refreshFailed,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        librarySection(texts.libraryVideos, state.visibleVideos.isNotEmpty())
        items(state.visibleVideos, key = { "video-${it.id}" }) { video ->
            HelpVideoCard(video) { onAction(HelpLibraryAction.VideoSelected(video)) }
        }
        librarySection(texts.libraryFlows, state.visibleFlows.isNotEmpty())
        items(state.visibleFlows, key = { "flow-${it.id}" }) { flow ->
            HelpFlowCard(flow) { onAction(HelpLibraryAction.FlowSelected(flow)) }
        }
    }
}

private fun LazyListScope.librarySection(title: String, visible: Boolean) {
    if (!visible) return
    item(key = "section-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun HelpVideoCard(video: HelpVideo, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(HelpTestTags.libraryVideo(video.id))) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (video.posterUrl != null) {
                    AsyncImage(
                        model = video.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                video.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                video.durationSeconds?.let {
                    Text(
                        text = formatVideoTime(it * 1_000L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpFlowCard(flow: HelpFlow, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(HelpTestTags.libraryFlow(flow.slug))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(flow.title, style = MaterialTheme.typography.titleMedium)
            flow.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HelpLibraryDetail(selection: HelpLibrarySelection, texts: HelpTexts, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = leituraMaxWidth(LocalWindowSizeClass.current))
            .padding(16.dp)
            .testTag(HelpTestTags.LIBRARY_DETAIL),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (selection) {
            is HelpLibrarySelection.Flow -> HelpFlowBody(selection.flow, texts)
            is HelpLibrarySelection.Video -> {
                Text(
                    text = selection.video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                HelpVideoPlayer(selection.video)
                selection.video.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
