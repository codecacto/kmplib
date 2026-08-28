package br.com.codecacto.kmplib.ui.components.video

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

/** O que a fábrica publica como arquivo de mídia — o resto vira [VideoSource.External]. */
private val EXTENSOES_DE_MIDIA = listOf(".mp4", ".m4v", ".mov", ".m3u8", ".webm")
