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
 * ## Dois tamanhos: cheio e COMPACTO (2.136.0)
 *
 * O padrão (`compact = false`) é a janela cheia: fundo preto, paisagem forçada, imersivo. É o certo
 * quando assistir É a tarefa — um curso, uma aula.
 *
 * `compact = true` abre a MESMA janela do sistema, translúcida: o player aparece em 16:9 no meio da
 * tela, a tela de onde a pessoa veio continua visível por trás, e tocar fora fecha. O botão de tela
 * cheia do próprio player continua lá — quem quiser o modo cheio pede.
 *
 * Ele nasceu de um pedido do fundador sobre o vídeo de apresentação do protocolo (22/ago/2026): um
 * vídeo de dois minutos que explica a tela em que a pessoa está não deveria tomar o aparelho inteiro
 * e virar a orientação. *"Queria que ele abrisse pequeno... pode abrir até uma tela nova, só que
 * pequena. Se a pessoa escolher deixar em tela cheia, deixa."*
 *
 * O que ele **não** é: player embutido na árvore de composição. Isso continua não funcionando, pelo
 * motivo do parágrafo acima — e é justamente por a janela ser do sistema que o modo compacto
 * funciona sem piscar.
 *
 * ## Uso
 *
 * ```kotlin
 * val video = rememberVideoLauncher()
 *
 * CapaDoVideo(onClick = { videoSourceOf(url)?.let { video.play(it, compact = true) } })
 * ```
 *
 * [VideoSource.External] não é tocada aqui: quem chama abre no navegador — ver `videoSourceOf`.
 */
expect class VideoLauncher {
    /**
     * Abre a tela do vídeo. `External` é ignorada de propósito.
     *
     * @param compact `true` = janela translúcida com o player em 16:9 no meio da tela, sobre o
     *   conteúdo de onde se veio. `false` (padrão) = janela cheia, em paisagem.
     */
    fun play(source: VideoSource, compact: Boolean = false)
}

/** O launcher da plataforma corrente, preso à composição atual. */
@Composable
expect fun rememberVideoLauncher(): VideoLauncher
