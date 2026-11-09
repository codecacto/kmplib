package br.com.codecacto.kmplib.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.video.VideoMedia
import br.com.codecacto.kmplib.video.VideoPlayer
import br.com.codecacto.kmplib.video.VideoPlayerConfig
import br.com.codecacto.kmplib.video.rememberVideoPlayerState
import coil3.compose.AsyncImage

/**
 * O ícone "?" para o `actions` do `AppTopBar`. Para esconder em projeto com a ajuda desligada, condicione a
 * [rememberIsHelpEnabled].
 */
@Composable
fun HelpTopBarAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = rememberHelpTexts().title,
) {
    IconButton(onClick = onClick, modifier = modifier.testTag(HelpTestTags.TOP_BAR_ACTION)) {
        Icon(imageVector = Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = contentDescription)
    }
}

/** Título, vídeo, passos numerados e texto de um fluxo: o mesmo desenho na resposta e na Biblioteca. */
@Composable
internal fun HelpFlowBody(flow: HelpFlow, texts: HelpTexts, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = flow.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        flow.summary?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        flow.video?.let { HelpVideoPlayer(it) }
        if (flow.steps.isNotEmpty()) {
            Text(
                text = texts.steps,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            flow.steps.forEachIndexed { index, step -> HelpStepRow(number = index + 1, text = step) }
        }
        flow.text?.let { text ->
            Text(remember(text) { helpBoldText(text) }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HelpStepRow(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = remember(text) { helpBoldText(text) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Reprodutor do vídeo de ajuda: mudo por padrão, porque quem abre a ajuda está trabalhando. */
@Composable
internal fun HelpVideoPlayer(video: HelpVideo, modifier: Modifier = Modifier) {
    val media = remember(video.url, video.title) { VideoMedia(url = video.url, title = video.title) }
    val config = remember {
        VideoPlayerConfig(autoPlay = false, startMuted = true, soundControl = true, mediaSession = false)
    }
    val state = rememberVideoPlayerState(media, config)
    val isVertical = video.format == HelpVideoFormat.VERTICAL
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        VideoPlayer(
            state = state,
            modifier = Modifier
                .fillMaxWidth(if (isVertical) VERTICAL_WIDTH_FRACTION else 1f)
                .widthIn(max = if (isVertical) VERTICAL_MAX_WIDTH else HORIZONTAL_MAX_WIDTH)
                .aspectRatio(if (isVertical) 9f / 16f else 16f / 9f),
            overlay = {
                if (video.posterUrl != null && state.positionMillis == 0L && !state.isPlaying) {
                    AsyncImage(
                        model = video.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
        )
    }
}

/**
 * `BackHandler` multiplataforma, marcado em favor do `NavigationEventHandler`, que não publica variante
 * Kotlin/Native (mesma escolha do `kmplib-ui`).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
internal fun HelpBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

private const val VERTICAL_WIDTH_FRACTION = 0.72f
private val VERTICAL_MAX_WIDTH = 360.dp
private val HORIZONTAL_MAX_WIDTH = 720.dp
