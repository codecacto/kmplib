package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * O player **DENTRO da página**, no lugar onde o vídeo está — e rolando junto com ela.
 *
 * ## Isto já falhou duas vezes, e o que muda agora
 *
 * As tentativas anteriores (`VideoPlayer`/`VideoPlayerDialog`, removidos) montavam a view nativa de
 * vídeo **de saída, em toda abertura da tela**, dentro de uma coluna rolável. O resultado era o trio
 * conhecido: pisca, fica preto, o áudio toca por baixo e os controles não respondem. Três coisas
 * eram a causa, e as três estão resolvidas aqui:
 *
 * 1. **A view nasce só depois do PLAY.** Antes disso o que existe é uma capa — a thumbnail oficial
 *    e um ícone. Nenhum processo de renderização, nenhuma conexão ao YouTube, e nada para piscar em
 *    quem abriu a tela e nem vai assistir.
 * 2. **A view é memoizada e liberada explicitamente.** `AndroidView`/`UIKitView` com `factory` presa
 *    ao id do vídeo e um `onRelease` que **destrói** o WebView. Sem isso, sair da tela deixava o
 *    áudio tocando — o WebView sobrevive à composição enquanto o processo de renderização não cai.
 * 3. **Ela NÃO vai dentro de um item de `LazyColumn`.** Lista preguiçosa recicla e descarta itens
 *    fora da tela; um player ali é destruído e recriado ao rolar, o que reinicia o vídeo do zero.
 *    Quem usa este componente numa tela rolável usa `Column` + `verticalScroll`, ou o mantém fora
 *    da área reciclada.
 *
 * ## Quando NÃO usar
 *
 * Quando assistir é a tarefa (um curso, uma aula): ali a tela cheia do [VideoLauncher] é melhor, e
 * o botão de expandir do próprio player leva a ela de qualquer forma.
 *
 * [VideoSource.External] não é tocada aqui — quem chama abre no navegador, como manda `videoSourceOf`.
 *
 * @param source o vídeo. `null` desenha apenas o espaço reservado.
 * @param onExternal chamado quando a fonte é [VideoSource.External] e o toque pede a abertura fora.
 */
@Composable
expect fun VideoPlayerInline(
    source: VideoSource?,
    modifier: Modifier = Modifier,
    onExternal: () -> Unit = {},
    /**
     * A capa antes do play — o slot vem por ÚLTIMO, como manda a convenção do Compose, para caber
     * como lambda de cauda na chamada. Ela recebe o gesto de toque, que é o que monta o player.
     */
    capa: @Composable (aoTocar: () -> Unit) -> Unit,
)
