package br.com.codecacto.kmplib.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.media.formatVoiceNoteDuration
import br.com.codecacto.kmplib.media.voicenote.VoiceNotePlayer
import br.com.codecacto.kmplib.media.voicenote.VoiceNoteSource
import br.com.codecacto.kmplib.media.voicenote.rememberVoiceNotePlayerState
import br.com.codecacto.kmplib.media.voicenote.voiceNotePlayerColors
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.RefreshableBox
import br.com.codecacto.kmplib.ui.components.ScrollableFillBox
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * **A conversa na tela** (2.287.0): lista de balões com a mais nova embaixo, rolagem ao fim quando
 * chega/sai mensagem (se a pessoa já estava no fim — lendo o histórico, aparece "Novas mensagens"),
 * divisores de dia e de **não lidas**, estados enviando/não enviada/enviada/lida, nota de voz com
 * onda, carga das anteriores ao chegar no topo e puxar para atualizar.
 *
 * Liga o polling do [controller] no `ON_RESUME` e desliga no `ON_PAUSE`; marca como lidas quando o
 * fim da lista está à vista. O campo de escrever fica fora: [ChatComposer], no `bottomBar`.
 *
 * @param resolveAudioUrl URL assinada do áudio recebido (o `read-urls` do app), pedida ao tocar/baixar
 *   e de novo se vencer. Sem ele, os áudios recebidos não tocam.
 * @param audioHttpClient cliente **sem** Bearer para baixar o áudio da URL assinada.
 */
@Composable
fun ChatThread(
    controller: ChatController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    texts: ChatTexts = rememberChatTexts(),
    resolveAudioUrl: (suspend (assetId: String) -> String?)? = null,
    audioHttpClient: HttpClient? = null,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(controller) {
        controller.start()
        onPauseOrDispose { controller.stop() }
    }
    ChatThreadContent(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        texts = texts,
        timeZone = timeZone,
        onLoadOlder = controller::loadOlder,
        onRefresh = controller::refresh,
        onLatestVisible = controller::markLatestAsRead,
        onRetry = { id -> scope.launch { controller.retry(id) } },
        onDiscard = { id -> scope.launch { controller.discard(id) } },
        audio = { entry, mine ->
            ChatAudioBubbleContent(entry, mine, texts, resolveAudioUrl, audioHttpClient)
        },
    )
}

/**
 * O mesmo, **sem estado** — para quem monta o estado de outro jeito (teste, preview, MVI próprio).
 * [audio] desenha o conteúdo de um balão de áudio.
 */
@Composable
fun ChatThreadContent(
    state: ChatThreadState,
    onLoadOlder: () -> Unit,
    onRefresh: () -> Unit,
    onLatestVisible: () -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    texts: ChatTexts = rememberChatTexts(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    audio: @Composable (entry: ChatEntry, mine: Boolean) -> Unit = { entry, _ ->
        Text(texts.voiceMessage(formatVoiceNoteDuration(entry.audio?.durationMillis ?: 0L)))
    },
) {
    val lista = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // reverseLayout: o índice 0 é a mais NOVA (embaixo). A lista da tela é da mais antiga à mais nova.
    val invertidas = remember(state.entries) { state.entries.asReversed() }
    val noFim by remember { derivedStateOf { lista.firstVisibleItemIndex <= 1 } }
    val maisNova = state.entries.lastOrNull()

    // Chegou/saiu mensagem: rola ao fim se a pessoa estava no fim ou se a mensagem é dela.
    LaunchedEffect(maisNova?.id) {
        if (maisNova != null && (noFim || maisNova.fromMe)) lista.animateScrollToItem(0)
    }
    // Fim à vista → marca como lida.
    LaunchedEffect(lista, maisNova?.id) {
        snapshotFlow { lista.firstVisibleItemIndex == 0 }.distinctUntilChanged().filter { it }.collect { onLatestVisible() }
    }
    // Topo à vista → anteriores.
    LaunchedEffect(lista, state.hasMoreOlder) {
        snapshotFlow {
            val info = lista.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }.distinctUntilChanged().filter { it && state.hasMoreOlder }.collect { onLoadOlder() }
    }

    RefreshableBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier) {
        when {
            state.isLoadingInitial && state.entries.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.loadError != null && state.entries.isEmpty() ->
                ErrorState(
                    message = state.loadError.ifBlank { texts.loadError },
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
            state.entries.isEmpty() ->
                ScrollableFillBox(Modifier.fillMaxSize().padding(contentPadding)) {
                    EmptyState(icon = Icons.Outlined.ChatBubbleOutline, title = texts.empty, modifier = Modifier.align(Alignment.Center))
                }
            else -> {
                LazyColumn(
                    state = lista,
                    reverseLayout = true,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize().testTag(ChatTestTags.THREAD),
                ) {
                    itemsIndexed(invertidas, key = { _, e -> e.id }) { iInvertido, entry ->
                        val i = state.entries.lastIndex - iInvertido
                        Column(Modifier.fillMaxWidth()) {
                            if (chatNeedsDaySeparator(state.entries, i) { diaLocal(it, timeZone) }) {
                                ChatDaySeparator(rotuloDoDia(entry.createdAtMillis, timeZone, texts))
                            }
                            if (entry.id == state.firstUnreadId) ChatUnreadDivider(texts.unread)
                            ChatBubble(
                                entry = entry,
                                texts = texts,
                                timeText = RegionalFormat.formatTime(entry.createdAtMillis, timeZone),
                                onRetry = { onRetry(entry.id) },
                                onDiscard = { onDiscard(entry.id) },
                                audio = { audio(entry, entry.fromMe) },
                            )
                        }
                    }
                    if (state.isLoadingOlder) {
                        item(key = "carregando-anteriores") {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    Modifier.size(24.dp).semantics { contentDescription = texts.loadingOlder },
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
                if (!noFim) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { lista.animateScrollToItem(0) } },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(contentPadding)
                            .padding(12.dp)
                            .testTag(ChatTestTags.JUMP_TO_LATEST),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = if (state.unreadCount > 0) texts.newMessages else texts.jumpToLatest)
                    }
                }
            }
        }
    }
}

/** Um balão: o meu à direita (cor da marca), o do outro à esquerda; hora + estado de entrega embaixo. */
@Composable
fun ChatBubble(
    entry: ChatEntry,
    texts: ChatTexts,
    timeText: String,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    audio: @Composable () -> Unit = {},
) {
    val meu = entry.fromMe
    val fundo = if (meu) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val cor = if (meu) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val estado = when (entry.delivery) {
        ChatDelivery.SENDING -> texts.sending
        ChatDelivery.FAILED -> texts.failed
        ChatDelivery.SENT -> texts.sent
        ChatDelivery.READ -> texts.read
        null -> null
    }
    val conteudoFalado = when (entry.kind) {
        ChatMessageKind.TEXT -> entry.text.orEmpty()
        ChatMessageKind.AUDIO -> texts.voiceMessage(formatVoiceNoteDuration(entry.audio?.durationMillis ?: 0L))
    }
    val descricao = listOfNotNull(if (meu) texts.you else null, conteudoFalado, timeText, estado).joinToString(". ")
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (meu) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (meu) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(
                modifier = Modifier
                    .testTag(ChatTestTags.bubble(entry.id))
                    .background(
                        fundo,
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (meu) 16.dp else 4.dp,
                            bottomEnd = if (meu) 4.dp else 16.dp,
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = entry.kind == ChatMessageKind.TEXT) {
                        contentDescription = descricao
                        if (entry.delivery == ChatDelivery.FAILED) {
                            customActions = listOf(
                                CustomAccessibilityAction(texts.retry) { onRetry(); true },
                                CustomAccessibilityAction(texts.discard) { onDiscard(); true },
                            )
                        }
                    },
            ) {
                when (entry.kind) {
                    ChatMessageKind.TEXT -> Text(entry.text.orEmpty(), color = cor, style = MaterialTheme.typography.bodyLarge)
                    ChatMessageKind.AUDIO -> audio()
                }
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(timeText, style = MaterialTheme.typography.labelSmall, color = cor.copy(alpha = 0.75f))
                    val icone = when (entry.delivery) {
                        ChatDelivery.SENDING -> Icons.Filled.Schedule
                        ChatDelivery.FAILED -> Icons.Filled.ErrorOutline
                        ChatDelivery.SENT -> Icons.Filled.Done
                        ChatDelivery.READ -> Icons.Filled.DoneAll
                        null -> null
                    }
                    if (icone != null) {
                        Icon(
                            icone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when (entry.delivery) {
                                ChatDelivery.FAILED -> MaterialTheme.colorScheme.error
                                ChatDelivery.READ -> MaterialTheme.colorScheme.primary
                                else -> cor.copy(alpha = 0.75f)
                            },
                        )
                    }
                }
            }
            if (entry.delivery == ChatDelivery.FAILED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.failure?.message?.takeIf { it.isNotBlank() } ?: texts.failed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry, modifier = Modifier.testTag(ChatTestTags.RETRY)) { Text(texts.retry) }
                    TextButton(onClick = onDiscard, modifier = Modifier.testTag(ChatTestTags.DISCARD)) { Text(texts.discard) }
                }
            }
        }
    }
}

@Composable
private fun ChatAudioBubbleContent(
    entry: ChatEntry,
    mine: Boolean,
    texts: ChatTexts,
    resolveAudioUrl: (suspend (String) -> String?)?,
    httpClient: HttpClient?,
) {
    val audio = entry.audio ?: return
    val assetId = audio.assetId
    val cores = if (mine) {
        voiceNotePlayerColors(
            button = MaterialTheme.colorScheme.onPrimaryContainer,
            played = MaterialTheme.colorScheme.onPrimaryContainer,
            unplayed = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
            text = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        voiceNotePlayerColors()
    }
    if (assetId == null || resolveAudioUrl == null) {
        // Ainda subindo (ou o app não deu como ler): a onda e a duração, sem tocar.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            br.com.codecacto.kmplib.media.voicenote.AudioWaveform(
                levels = audio.levels,
                modifier = Modifier.size(width = 160.dp, height = 32.dp),
                activeColor = cores.played,
                inactiveColor = cores.unplayed,
            )
            Text(formatVoiceNoteDuration(audio.durationMillis ?: 0L), style = MaterialTheme.typography.labelMedium, color = cores.text)
        }
        return
    }
    val player = rememberVoiceNotePlayerState(
        source = VoiceNoteSource.Remote(cacheKey = assetId) { resolveAudioUrl(assetId) },
        durationMillis = audio.durationMillis,
        waveform = audio.levels.takeIf { it.isNotEmpty() },
        httpClient = httpClient,
    )
    VoiceNotePlayer(player, modifier = Modifier.widthIn(min = 200.dp), colors = cores, texts = texts.voiceNote)
}

@Composable
private fun ChatDaySeparator(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics { heading() },
        )
    }
}

@Composable
private fun ChatUnreadDivider(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp).testTag(ChatTestTags.UNREAD_DIVIDER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalTime::class)
internal fun diaLocal(millis: Long, timeZone: TimeZone): Long =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date.toEpochDays().toLong()

@OptIn(ExperimentalTime::class)
private fun rotuloDoDia(millis: Long, timeZone: TimeZone, texts: ChatTexts): String {
    val dia = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
    val hoje = Instant.fromEpochMilliseconds(br.com.codecacto.kmplib.core.util.currentTimeMillis()).toLocalDateTime(timeZone).date
    return when (hoje.toEpochDays().toLong() - dia.toEpochDays().toLong()) {
        0L -> texts.today
        1L -> texts.yesterday
        else -> RegionalFormat.formatDate(dia)
    }
}
