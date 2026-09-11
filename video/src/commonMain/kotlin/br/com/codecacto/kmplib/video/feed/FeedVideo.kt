package br.com.codecacto.kmplib.video.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.video.MAX_RENOVACOES_DE_URL
import br.com.codecacto.kmplib.video.VideoErrorKind
import br.com.codecacto.kmplib.video.VideoStatus
import br.com.codecacto.kmplib.video.VideoStreamKind
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * **Vídeo de feed**, estilo Instagram — o post de vídeo no meio de uma lista.
 *
 * Não é o [br.com.codecacto.kmplib.video.VideoPlayer] (o de aula, com controles, velocidade e
 * legenda). Aqui não há controle nenhum: o vídeo **toca sozinho e sem som** quando ~60% dele está
 * na tela, **pausa ao sair**, **repete em laço**, e **só um toca por vez** no feed. O único botão é
 * o de **som**, no canto inferior direito, e ele vale para o feed inteiro.
 *
 * Precisa estar dentro de um [FeedVideoHost] — é ele que coordena a vez e empresta os players.
 *
 * ```kotlin
 * FeedVideoHost(Modifier.fillMaxSize()) {
 *     LazyColumn(Modifier.fillMaxSize()) {
 *         items(posts, key = { it.id }, contentType = { it.tipo }) { post ->
 *             FeedVideo(
 *                 url = post.video.playbackUrl,
 *                 posterUrl = post.photoUrl,
 *                 aspectRatio = feedMediaAspectRatio(post.mediaWidth, post.mediaHeight),
 *                 onClick = { abrir(post.id) },
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * ### O que a caixa mostra, em cada momento
 * 1. **A capa** ([posterUrl]) — enquanto o item não tem player, e **até o primeiro quadro** do vídeo
 *    aparecer na superfície. Nunca a tela preta: o quadro chega por cima da capa, sem piscar.
 * 2. **O vídeo.** Tocando se tem a vez; parado no primeiro quadro se foi pré-carregado.
 * 3. **Erro:** a capa **fica**, e o [error] desenha por cima um aviso discreto. Nada de tela de erro no
 *    meio do feed: a pessoa segue rolando. O vídeo tenta de novo quando voltar à vez; [onRenewUrl]
 *    cuida da URL assinada vencida.
 *
 * @param url `.m3u8` (HLS) ou `.mp4`/`.mov` (progressivo). Pode ser assinada: a classificação é pela
 *   extensão do **caminho** ([br.com.codecacto.kmplib.video.videoStreamKindOf]).
 * @param posterUrl a capa. `null` = caixa de [FeedVideoColors.background] até o primeiro quadro.
 * @param aspectRatio largura ÷ altura da caixa. É **do chamador**, que conhece a medida do vídeo antes
 *   de ele carregar — é o que faz a lista não pular de altura quando o vídeo abre. Ver
 *   [feedMediaAspectRatio] para a régua 4:5…1,91:1.
 * @param kind force HLS/progressivo quando a URL não diz (`Auto` resolve pela URL).
 * @param scale [FeedVideoScale.Crop] (preenche e corta, o feed) ou [FeedVideoScale.Fit]. Vale para a
 *   capa também — as duas precisam coincidir, senão a troca capa→vídeo "salta".
 * @param onClick o toque no vídeo (o app abre a publicação). `null` = o vídeo não é clicável. O
 *   botão de som **não** propaga o toque para cá.
 * @param contentDescription o que o leitor de tela diz do vídeo (o texto do post, por exemplo).
 * @param showSoundButton `false` esconde o alto-falante (o app o desenha em outro lugar com
 *   [FeedVideoSoundButton], lendo [FeedVideoController.sound]).
 * @param onRenewUrl a URL assinada venceu (401/403/410): devolva uma nova e o vídeo recarrega. Vale
 *   [MAX_RENOVACOES_DE_URL] vezes por URL de origem — sem teto, um servidor devolvendo sempre a mesma
 *   URL vencida vira laço.
 * @param error o aviso de falha, **por cima da capa**. Recebe o tipo e um `retry`.
 */
@Composable
fun FeedVideo(
    url: String,
    posterUrl: String?,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    kind: VideoStreamKind = VideoStreamKind.Auto,
    scale: FeedVideoScale = FeedVideoScale.Crop,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    colors: FeedVideoColors = FeedVideoColors(),
    texts: FeedVideoTexts = FeedVideoTexts(),
    showSoundButton: Boolean = true,
    onRenewUrl: (suspend () -> String?)? = null,
    error: @Composable BoxScope.(kind: VideoErrorKind, retry: () -> Unit) -> Unit = { _, _ ->
        FeedVideoDefaults.ErrorIndicator(Modifier.align(Alignment.Center), colors, texts)
    },
) {
    // `checkNotNull`, e não `error(...)`: dentro deste corpo `error` é o parâmetro-slot.
    val controller = checkNotNull(LocalFeedVideoController.current) {
        "kmplib-video: FeedVideo precisa estar dentro de um FeedVideoHost — é ele que decide qual " +
            "vídeo toca. Sem ele, cada vídeo tocaria sozinho, todos ao mesmo tempo."
    }

    // A identidade do item para o controller. Não é a URL (dois posts podem ter o mesmo vídeo, e a
    // URL assinada muda na renovação) nem a chave da LazyColumn (que a lib não enxerga): é esta
    // composição. Reúso de composição da LazyColumn zera o `remember` — item novo, chave nova.
    val chave = remember { Any() }

    // A URL em vigor: começa na do post e troca na renovação da assinatura.
    var urlEmVigor by remember(url) { mutableStateOf(url) }
    var renovacoes by remember(url) { mutableIntStateOf(0) }

    SideEffect { controller.setSource(chave, urlEmVigor, kind) }
    DisposableEffect(controller, chave) {
        onDispose { controller.remove(chave) }
    }

    val engine = controller.engineFor(chave)
    val status = engine?.status
    val temAVez = controller.activeKey == chave

    // Se ESTA superfície já mostra um quadro deste player. Zera ao trocar de player ou de fonte.
    var quadroNaTela by remember(engine, urlEmVigor) { mutableStateOf(false) }

    LaunchedEffect(status, onRenewUrl) {
        val falha = status as? VideoStatus.Error ?: return@LaunchedEffect
        if (falha.kind != VideoErrorKind.Expired || onRenewUrl == null) return@LaunchedEffect
        if (renovacoes >= MAX_RENOVACOES_DE_URL) return@LaunchedEffect
        renovacoes++
        val nova = runCatching { onRenewUrl() }.getOrNull()
        if (!nova.isNullOrBlank()) urlEmVigor = nova
    }

    val descricaoDoVideo = contentDescription
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clipToBounds()
            .background(colors.background)
            .onLayoutRectChanged(
                throttleMillis = controller.config.visibilityThrottleMillis,
                debounceMillis = VISIBILITY_DEBOUNCE_MILLIS,
            ) { limites ->
                val area = controller.viewport.bounds
                val fracao = if (area != null) {
                    feedVideoVisibleFraction(limites.boundsInWindow, area.boundsInWindow)
                } else {
                    limites.fractionVisibleInWindow()
                }
                controller.report(chave, fracao, limites.boundsInWindow.top.toFloat())
            }
            .then(
                if (onClick != null) {
                    // Sem indicação visual: ondulação por cima de um vídeo é ruído. A semântica de
                    // botão fica, com o rótulo do que o toque faz.
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = texts.openLabel,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (descricaoDoVideo != null) {
                    Modifier.semantics { this.contentDescription = descricaoDoVideo }
                } else {
                    Modifier
                },
            ),
    ) {
        if (engine != null) {
            FeedVideoSurface(
                engine = engine,
                scale = scale,
                modifier = Modifier.fillMaxSize(),
                onFrameVisibleChange = { quadroNaTela = it },
            )
        }

        val falhou = status is VideoStatus.Error
        val mostrarCapa = engine == null || !quadroNaTela || falhou
        if (mostrarCapa && posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = scale.toContentScale(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (status is VideoStatus.Error) {
            error(status.kind) { controller.retry(chave) }
        } else if (temAVez && (status == VideoStatus.Loading || status == VideoStatus.Buffering)) {
            EsperaDiscreta(colors)
        }

        if (showSoundButton) {
            FeedVideoSoundButton(
                muted = controller.sound.isMuted,
                onToggle = controller::toggleMuted,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                colors = colors,
                texts = texts,
            )
        }
    }
}

/**
 * O botão de **som do feed**: alto-falante (com som) ou alto-falante riscado (mudo).
 *
 * Alvo de toque de **48 dp** (acima dos 44 pt da Apple e no mínimo do Material), com o desenho de
 * 32 dp dentro dele — o círculo pequeno é o que se vê, a área grande é a que o dedo acerta. O toque
 * **não** chega ao vídeo por baixo: o `clickable` daqui consome o evento.
 *
 * O [FeedVideo] já o desenha no canto inferior direito; use este direto só para pôr o botão em outro
 * lugar (com `showSoundButton = false`).
 */
@Composable
fun FeedVideoSoundButton(
    muted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    colors: FeedVideoColors = FeedVideoColors(),
    texts: FeedVideoTexts = FeedVideoTexts(),
) {
    val descricao = if (muted) texts.turnSoundOn else texts.turnSoundOff
    Box(
        modifier = modifier
            .size(FEED_SOUND_BUTTON_TOUCH_TARGET)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = descricao
                stateDescription = if (muted) texts.mutedState else texts.unmutedState
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(colors.soundButtonBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = colors.soundIcon,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Os pedaços default do [FeedVideo], para quem quiser reusar num `error` próprio. */
object FeedVideoDefaults {

    /**
     * O aviso de erro default: um ícone pequeno de câmera riscada, em círculo escuro, **sobre a
     * capa**. Discreto de propósito — o feed não vira tela de erro por causa de um post.
     */
    @Composable
    fun ErrorIndicator(
        modifier: Modifier = Modifier,
        colors: FeedVideoColors = FeedVideoColors(),
        texts: FeedVideoTexts = FeedVideoTexts(),
    ) {
        Box(
            modifier = modifier
                .size(40.dp)
                .background(colors.soundButtonBackground, CircleShape)
                .semantics { contentDescription = texts.playbackError },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.VideocamOff,
                contentDescription = null,
                tint = colors.indicator,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A roda de espera, **só depois de [ESPERA_VISIVEL_APOS_MILLIS]**: numa conexão boa o vídeo abre
 * antes disso, e uma roda que pisca por um décimo de segundo em cada post é ruído.
 */
@Composable
private fun BoxScope.EsperaDiscreta(colors: FeedVideoColors) {
    var visivel by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ESPERA_VISIVEL_APOS_MILLIS)
        visivel = true
    }
    if (visivel) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(28.dp),
            color = colors.indicator,
            strokeWidth = 2.5.dp,
        )
    }
}

private fun FeedVideoScale.toContentScale(): ContentScale = when (this) {
    FeedVideoScale.Crop -> ContentScale.Crop
    FeedVideoScale.Fit -> ContentScale.Fit
}

/** 48 dp: o alvo de toque do botão de som. */
private val FEED_SOUND_BUTTON_TOUCH_TARGET = 48.dp

/** O fim da rolagem sempre reavalia, mesmo entre dois tiques do throttle. */
private const val VISIBILITY_DEBOUNCE_MILLIS = 64L

private const val ESPERA_VISIVEL_APOS_MILLIS = 600L
