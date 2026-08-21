package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.runtime.Composable

/**
 * Abre o vídeo **numa tela do SISTEMA**, fora da árvore do Compose.
 *
 * ## Por que não um `@Composable` que desenha o player
 *
 * Foram duas tentativas, e as duas falharam do mesmo jeito. Player embutido no meio de uma coluna
 * rolável: *"pisca, aparece uma tela toda preta, parece que dá play por baixo, e não dá para
 * parar"*. Player dentro de um `Dialog` do Compose: **o mesmo**, porque o diálogo continua sendo uma
 * janela com árvore de composição, e a view nativa de vídeo continua disputando camada com ela.
 *
 * O que funciona — e está em produção no app de Roteiros desde antes disto existir — é o vídeo ter a
 * **própria janela do sistema**: uma `Activity` no Android, um `UIViewController` apresentado no
 * iOS. Lá dentro não há Compose nenhum para disputar composição, o `WebChromeClient` consegue
 * entregar a tela cheia de verdade, a orientação vira paisagem e o botão de fechar é uma view
 * nativa que sempre responde.
 *
 * ## Três detalhes que decidem se o embed carrega
 *
 * O IFrame API do YouTube valida a origem de quem pede. Copiar o `<iframe>` sem eles dá um quadro
 * preto sem mensagem de erro:
 *
 * 1. **`origin=` na URL do embed** — e igual à base do `loadDataWithBaseURL`.
 * 2. **A base é o pacote do app** (`https://<packageName>`), não o domínio do YouTube.
 * 3. **`referrerpolicy="strict-origin-when-cross-origin"`**, no `<meta>` e no `<iframe>`.
 *
 * ## Uso
 *
 * ```kotlin
 * val video = rememberVideoLauncher()
 *
 * CapaDoVideo(onClick = { videoSourceOf(url)?.let(video::play) })
 * ```
 *
 * [VideoSource.External] não é tocada aqui: quem chama abre no navegador — ver `videoSourceOf`.
 */
expect class VideoLauncher {
    /** Abre a tela do vídeo. `External` é ignorada de propósito. */
    fun play(source: VideoSource)
}

/** O launcher da plataforma corrente, preso à composição atual. */
@Composable
expect fun rememberVideoLauncher(): VideoLauncher
