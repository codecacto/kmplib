package br.com.codecacto.kmplib.video

/**
 * Os textos do player. Defaults em pt-BR; um app multilíngue passa os seus, vindos de
 * `stringResource` — o idioma segue o do aparelho, como manda a regra da casa.
 *
 * Quase tudo aqui é `contentDescription`: os controles de um player são ícones, e sem rótulo o
 * leitor de tela anuncia "botão" seis vezes.
 */
data class VideoPlayerTexts(
    val play: String = "Reproduzir",
    val pause: String = "Pausar",
    val replay: String = "Assistir de novo",
    val forward: String = "Avançar 10 segundos",
    val rewind: String = "Voltar 10 segundos",
    val enterFullscreen: String = "Tela cheia",
    val exitFullscreen: String = "Sair da tela cheia",
    val speed: String = "Velocidade",
    val subtitles: String = "Legendas",
    val subtitlesOff: String = "Desativadas",
    val progress: String = "Progresso do vídeo",
    val loading: String = "Carregando o vídeo…",
    val retry: String = "Tentar de novo",
    /** O que a tela diz em cada tipo de falha. Ver [VideoErrorKind]. */
    val errorNetwork: String = "Não foi possível carregar o vídeo. Verifique a conexão.",
    val errorExpired: String = "O link deste vídeo expirou. Abra a aula de novo.",
    val errorNotFound: String = "Este vídeo não está disponível.",
    val errorUnsupported: String = "Este aparelho não consegue reproduzir este vídeo.",
    val errorUnknown: String = "Não foi possível reproduzir o vídeo.",
) {
    /** A frase de [kind] — usada pelos `actual` para preencher [VideoStatus.Error.message]. */
    fun messageFor(kind: VideoErrorKind): String = when (kind) {
        VideoErrorKind.Network -> errorNetwork
        VideoErrorKind.Expired -> errorExpired
        VideoErrorKind.NotFound -> errorNotFound
        VideoErrorKind.Unsupported -> errorUnsupported
        VideoErrorKind.Unknown -> errorUnknown
    }
}
