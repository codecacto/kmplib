package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Container com suporte a pinch-to-zoom e pan (arrastar).
 *
 * Envolva qualquer conteudo com este composable para adicionar gestos de zoom:
 * - Pinch-to-zoom (dois dedos)
 * - Pan/arrastar quando zoom > 1x
 * - Duplo toque para alternar entre zoom 1x e 2.5x
 *
 * Uso:
 * ```
 * ZoomableBox {
 *     Image(painter = myPainter, contentDescription = null)
 * }
 * ```
 *
 * @param modifier Modifier para o container
 * @param minScale Escala minima permitida (padrao: 1f)
 * @param maxScale Escala maxima permitida (padrao: 5f)
 * @param onScaleChange avisa a cada mudanca de escala (2.153.0). Existe porque **quem esta por
 *   fora precisa saber se a imagem esta ampliada**: numa galeria com pager, arrastar uma foto
 *   ampliada tem de mover a IMAGEM, nao virar a pagina — e sem este aviso o container nao tem como
 *   descobrir isso. Ver [FullScreenGallery].
 * @param content Conteudo a ser exibido com zoom
 */
@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    onScaleChange: ((Float) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            // ⚠️ **Laco de gesto proprio, e nao `detectTransformGestures`** (2.155.0).
            //
            // O `detectTransformGestures` CONSOME todo arrasto que passa do touch slop — inclusive
            // o de UM dedo, e inclusive com a imagem em escala 1. Num visualizador de imagem
            // sozinha isso nao se nota (nao ha quem receberia o gesto); dentro de um pager, e o
            // defeito inteiro: o dedo arrasta, a foto nao anda (esta em escala 1, o offset e
            // zerado) e o pager nunca ve o evento. Resultado: **deslizar nao faz nada**.
            //
            // Aqui o gesto so e consumido quando ele e NOSSO — ver [gestoEDoZoom]. Fora disso os
            // eventos passam adiante, e quem estiver por fora (o `HorizontalPager`) vira a pagina.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val evento = awaitPointerEvent()
                        val dedos = evento.changes.count { it.pressed }
                        if (gestoEDoZoom(dedos = dedos, escalaAtual = scale)) {
                            val newScale = (scale * evento.calculateZoom()).coerceIn(minScale, maxScale)
                            scale = newScale
                            onScaleChange?.invoke(newScale)

                            if (newScale > 1f) {
                                // Limitar o pan para nao sair da tela
                                val pan = evento.calculatePan()
                                val maxOffsetX = (newScale - 1f) * size.width / 2f
                                val maxOffsetY = (newScale - 1f) * size.height / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            // Consome so o que a gente usou. Sem isto o pager tambem viraria a
                            // pagina durante a pinca, e a foto sairia da tela enquanto amplia.
                            evento.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (evento.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            // Reset zoom
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            // Zoom in
                            scale = 2.5f
                        }
                        onScaleChange?.invoke(scale)
                    }
                )
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Visualizador de imagem em tela cheia com pinch-to-zoom.
 *
 * Exibe um Dialog fullscreen com fundo escuro, suporte a zoom e botao de fechar.
 *
 * Uso:
 * ```
 * var showViewer by remember { mutableStateOf(false) }
 *
 * // Thumbnail clicavel
 * AsyncImage(
 *     model = imageUrl,
 *     modifier = Modifier.clickable { showViewer = true }
 * )
 *
 * // Viewer fullscreen
 * if (showViewer) {
 *     FullScreenImageViewer(onDismiss = { showViewer = false }) {
 *         AsyncImage(
 *             model = imageUrl,
 *             contentDescription = null,
 *             modifier = Modifier.fillMaxSize(),
 *             contentScale = ContentScale.Fit
 *         )
 *     }
 * }
 * ```
 *
 * @param onDismiss Callback chamado ao fechar o viewer (botao X ou toque no fundo)
 * @param content Conteudo da imagem (ex: AsyncImage, Image, etc.)
 */
@Composable
fun FullScreenImageViewer(
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Imagem com zoom
            ZoomableBox(
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }

            // Botao fechar
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fechar"
                )
            }
        }
    }
}

/**
 * **Galeria em TELA CHEIA, passando de uma foto para a outra** (2.153.0).
 *
 * O [FullScreenImageViewer] acima abre **uma** foto: quem tem doze precisa fechar, tocar na
 * seguinte e abrir de novo — doze vezes. Este é o irmão dele para quando há um conjunto: abre na
 * foto tocada, desliza para os lados, e cada uma continua com o pinch-to-zoom e o duplo toque do
 * [ZoomableBox].
 *
 * Nasceu do perfil de **espaço de festa** no Cidade Conectada (fundador, 26/ago/2026: *"queria uma
 * aba, talvez galeria, que aí você clicava, abria em tela cheia e podia passar"*) — mas não tem
 * nada daquele produto: é o gesto que qualquer galeria de app precisa, e por isso mora aqui.
 *
 * ## Duas coisas que este componente resolve e que a versão caseira sempre erra
 *
 * **O zoom briga com o deslizar.** Com a foto ampliada, arrastar tem de mover a IMAGEM, não virar
 * a página — senão é impossível olhar o canto de uma foto. Em escala 1 é o contrário: o arrasto tem
 * de virar a página.
 *
 * ⚠️ Isso NÃO sai de graça, e a 2.153.0 saiu errada por supor que sim: o `detectTransformGestures`
 * que o [ZoomableBox] usava **consumia todo arrasto**, inclusive o de um dedo em escala 1 — o dedo
 * arrastava, a foto não andava e o pager nunca via o evento. Deslizar simplesmente não fazia nada.
 * Desde a 2.155.0 o [ZoomableBox] só consome o gesto que é dele (ver `gestoEDoZoom`), e o
 * `userScrollEnabled` abaixo é a segunda tranca.
 *
 * **O contador não é enfeite.** Sem "3 / 12" ninguém sabe quantas faltam, e a pessoa desliza até
 * bater na última para descobrir que acabou.
 *
 * @param fotos as imagens, na ordem. Aceita o que o Coil aceita (URL, `ByteArray`, path).
 * @param indiceInicial em qual delas abrir — o índice da que foi tocada. Fora da faixa, abre na 1ª.
 * @param onDismiss fechar (X, toque no fundo ou voltar).
 * @param descricao rótulo para leitor de tela; o contador já é anunciado à parte.
 */
@Composable
fun FullScreenGallery(
    fotos: List<Any?>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    indiceInicial: Int = 0,
    descricao: String? = null,
) {
    if (fotos.isEmpty()) return
    val paginas = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = paginaInicialDaGaleria(indiceInicial, fotos.size),
        pageCount = { fotos.size },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Fechar por toque no fundo está DESLIGADO de propósito: numa galeria o dedo passa o
            // tempo todo sobre a imagem, e o toque que "erra" a foto fecharia a tela no meio de
            // quem só queria deslizar. O X e o voltar continuam fechando.
            dismissOnClickOutside = false,
        ),
    ) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            // ⚠️ A escala é do PAGER, não de cada página: é ele que precisa saber se pode
            // receber o arrasto. Guardada por fora, ela zera ao trocar de foto — que é o
            // comportamento certo, porque o zoom da página anterior não vale para a nova.
            var ampliada by remember { mutableStateOf(false) }
            LaunchedEffect(paginas.currentPage) { ampliada = false }

            androidx.compose.foundation.pager.HorizontalPager(
                state = paginas,
                modifier = Modifier.fillMaxSize(),
                // Uma de cada lado já pronta: sem isso a foto seguinte entra cinza e "aparece"
                // depois de a página parar, que é o que faz a galeria parecer travada.
                beyondViewportPageCount = 1,
                // Foto ampliada não vira página: o arrasto é para mover a IMAGEM. Sem isto é
                // impossível olhar o canto de uma foto — o primeiro arrasto some com ela.
                userScrollEnabled = !ampliada,
            ) { pagina ->
                ZoomableBox(
                    modifier = Modifier.fillMaxSize(),
                    onScaleChange = { escala -> ampliada = escala > 1.01f },
                ) {
                    coil3.compose.AsyncImage(
                        model = fotos[pagina],
                        contentDescription = descricao,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (fotos.size > 1) {
                androidx.compose.material3.Text(
                    text = "${paginas.currentPage + 1} / ${fotos.size}",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
            }
        }
    }
}

/**
 * Em qual foto a galeria abre.
 *
 * Existe fora do `@Composable` para poder ser testada — e porque o erro que ela evita é chato: o
 * `rememberPagerState` **estoura** com uma `initialPage` fora da faixa, e o índice vem de uma lista
 * que pode ter encolhido entre o toque e a abertura (uma foto apagada, uma recarga). Ancorar na
 * primeira é o desfecho certo: abrir a galeria vale mais que abrir na foto exata.
 */
internal fun paginaInicialDaGaleria(indiceInicial: Int, total: Int): Int =
    if (total <= 0) 0 else indiceInicial.coerceIn(0, total - 1)

/**
 * Este gesto e do ZOOM (e portanto deve ser consumido), ou de quem esta por fora?
 *
 * ⚠️ **A pergunta que a 2.153.0 nao fazia.** O `detectTransformGestures` consome tudo, e num pager
 * isso significa que deslizar nao faz nada: o dedo arrasta, a foto fica parada (escala 1, offset
 * zerado) e a pagina nunca vira.
 *
 * Duas regras, e as duas sao sobre INTENCAO:
 * - **Dois dedos e sempre pinca.** Ninguem usa dois dedos para virar pagina.
 * - **Um dedo so e nosso com a imagem AMPLIADA** — ai o arrasto move a imagem, que e a unica coisa
 *   que faz sentido: em escala 1 nao ha para onde mover.
 *
 * @param dedos quantos ponteiros estao pressionados neste evento.
 * @param escalaAtual a escala em que a imagem esta agora.
 */
internal fun gestoEDoZoom(dedos: Int, escalaAtual: Float): Boolean =
    dedos > 1 || escalaAtual > ESCALA_NEUTRA

/**
 * Acima disto a imagem conta como ampliada.
 *
 * Nao e `1f` exato de proposito: a pinca deixa residuo de ponto flutuante (1.0000001), e comparar
 * com igualdade faria a imagem "voltar ao normal" continuar capturando o arrasto para sempre.
 */
private const val ESCALA_NEUTRA = 1.01f
