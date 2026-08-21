package br.com.codecacto.kmplib.ui.components.video

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * De onde o vídeo vem — e, por consequência, como ele toca.
 *
 * A distinção existe porque **não há um jeito só**: um vídeo do YouTube não se toca com um player de
 * arquivo (a URL da página não é a do mídia, e raspá-la viola os Termos da plataforma), e um `.mp4`
 * hospedado por nós não precisa de player de terceiro nenhum.
 */
sealed interface VideoSource {

    /**
     * Vídeo do YouTube, identificado pelo [videoId].
     *
     * **Toca no IFrame Player API**, que é o caminho oficialmente suportado pelo Google para embutir
     * YouTube em aplicativo — a *YouTube Android Player API* foi descontinuada e não recebe mais
     * correções. Não é WebView "por atalho": é o SDK que o fornecedor mantém, e ele exige um
     * contexto web para rodar. Extrair a URL do arquivo de mídia para tocar num player nativo é
     * violação explícita dos Termos do YouTube, além de quebrar a cada mudança deles.
     */
    data class YouTube(val videoId: String) : VideoSource

    /** Arquivo de mídia servido por nós (`.mp4`, `.m3u8`) — toca no player nativo da plataforma. */
    data class File(val url: String) : VideoSource

    /**
     * URL que não sabemos tocar embutida (Vimeo privado, página de terceiro, link quebrado).
     *
     * Quem recebe isto **abre no navegador** — é honesto, e melhor do que um retângulo preto que
     * nunca carrega.
     */
    data class External(val url: String) : VideoSource
}

/**
 * Classifica uma URL de vídeo. Função pura: é ela que os testes cobrem, e é ela que garante que
 * Android e iOS não divirjam no que consideram "um vídeo do YouTube".
 *
 * Reconhece as quatro formas em que um link do YouTube chega — a da barra de endereços, a do botão
 * "compartilhar", a de embed e a do Shorts:
 *
 * ```
 * https://www.youtube.com/watch?v=ABC123
 * https://youtu.be/ABC123
 * https://www.youtube.com/embed/ABC123
 * https://www.youtube.com/shorts/ABC123
 * ```
 */
fun videoSourceOf(url: String?): VideoSource? {
    val limpa = url?.trim().orEmpty()
    if (limpa.isEmpty() || !limpa.startsWith("http", ignoreCase = true)) return null

    youTubeIdOf(limpa)?.let { return VideoSource.YouTube(it) }

    val semQuery = limpa.substringBefore('?').lowercase()
    val ehArquivo = EXTENSOES_DE_MIDIA.any { semQuery.endsWith(it) }
    return if (ehArquivo) VideoSource.File(limpa) else VideoSource.External(limpa)
}

/** O id de 11 caracteres, quando a URL é do YouTube. `null` para qualquer outra coisa. */
fun youTubeIdOf(url: String?): String? {
    val limpa = url?.trim().orEmpty().ifEmpty { return null }
    val semProtocolo = limpa.substringAfter("://", limpa)
    val host = semProtocolo.substringBefore('/').lowercase().removePrefix("www.")
    val caminho = semProtocolo.substringAfter('/', "")

    val bruto = when {
        host == "youtu.be" -> caminho.substringBefore('?')
        host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> when {
            caminho.startsWith("embed/") -> caminho.removePrefix("embed/").substringBefore('?')
            caminho.startsWith("shorts/") -> caminho.removePrefix("shorts/").substringBefore('?')
            caminho.startsWith("watch") -> caminho
                .substringAfter('?', "")
                .split('&')
                .firstOrNull { it.startsWith("v=") }
                ?.removePrefix("v=")
                .orEmpty()

            else -> ""
        }

        else -> ""
    }.substringBefore('#').trim('/')

    // O id do YouTube tem 11 caracteres do alfabeto de URL. A checagem evita que
    // `youtube.com/results?search_query=…` vire um "vídeo" que abre em preto.
    return bruto.takeIf { it.length == 11 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } }
}

/**
 * **Player embutido** — o vídeo toca DENTRO do app, com botão de tela cheia.
 *
 * ## Por que ele existe
 *
 * A alternativa que os apps usavam era `UrlLauncher.openUrl(url)`: o toque no vídeo jogava a pessoa
 * no navegador ou no app do YouTube, fora do produto, e a volta dependia do botão "voltar" do
 * sistema. Num app cujo vídeo é a peça que explica o instrumento (o protocolo do NeuroCoreX), sair
 * do app para assistir é perder a pessoa no meio da explicação.
 *
 * ## O que ele NÃO faz
 *
 * Não baixa, não armazena e não extrai a mídia. Para YouTube usa o **IFrame Player API oficial**
 * (ver [VideoSource.YouTube]); para arquivo nosso, o player nativo da plataforma. Uma
 * [VideoSource.External] não é tocada aqui — quem chama decide (o normal é abrir no navegador).
 *
 * @param onExternal chamado quando a URL não é reproduzível embutida. Deixe-o abrir o navegador.
 */
@Composable
fun VideoPlayer(
    source: VideoSource,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onExternal: (String) -> Unit = {},
) {
    when (source) {
        is VideoSource.External -> onExternal(source.url)
        else -> VideoPlayerView(
            source = source,
            autoPlay = autoPlay,
            // 16:9 é a proporção do vídeo do YouTube e o default de tudo que a fábrica publica.
            // Sem altura definida, a view nativa mede 0 dentro de uma coluna rolável e o player
            // "não aparece" — sem erro nenhum.
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
    }
}

/** A view nativa de cada plataforma. Interna: quem usa a lib passa por [VideoPlayer]. */
@Composable
internal expect fun VideoPlayerView(
    source: VideoSource,
    autoPlay: Boolean,
    modifier: Modifier,
)

private val EXTENSOES_DE_MIDIA = listOf(".mp4", ".m4v", ".mov", ".m3u8", ".webm")
