package br.com.codecacto.kmplib.video

/**
 * Em que ponto a reprodução está — a máquina de estados que a tela desenha.
 *
 * Os seis estados são **exclusivos** de propósito. A tentação é ter `isPlaying` + `isBuffering`
 * como dois booleanos independentes, e o resultado é a tela mostrando o botão de pausa **e** a
 * roda de espera ao mesmo tempo, ou nenhum dos dois. Aqui, quem está esperando dado está em
 * [Buffering], e ponto — [VideoPlayerState.isPlaying] continua existindo como derivação para quem
 * só precisa saber se o relógio anda.
 */
sealed interface VideoStatus {

    /** Nada carregado ainda (nenhuma mídia, ou já liberado). */
    data object Idle : VideoStatus

    /** Preparando a mídia: abrindo o manifesto, escolhendo a variante. Ainda não há primeiro quadro. */
    data object Loading : VideoStatus

    /** Quer tocar, mas está sem dado. É o estado do 3G ruim no meio da aula. */
    data object Buffering : VideoStatus

    /** Tocando. */
    data object Playing : VideoStatus

    /** Pausado pelo usuário, pelo ciclo de vida ou por perda de foco de áudio. */
    data object Paused : VideoStatus

    /** Chegou ao fim. Dar play daqui recomeça do zero. */
    data object Ended : VideoStatus

    /**
     * Falhou. [message] é para o **usuário** — a tela mostra essa frase.
     *
     * @param cause a mensagem técnica do player, para log/crash reporter. Nunca vai para a tela.
     */
    data class Error(
        val kind: VideoErrorKind,
        val message: String,
        val cause: String? = null,
    ) : VideoStatus
}

/**
 * A natureza da falha — o que decide se faz sentido oferecer "tentar de novo".
 *
 * [Expired] existe porque é o erro **esperado** de um curso com URL assinada de curta duração: a
 * pessoa pausa, atende o telefone, volta vinte minutos depois e o link morreu. Tratá-lo como
 * "erro de rede" faria a tela sugerir conferir o wi-fi; o certo é pedir uma URL nova ao servidor.
 */
enum class VideoErrorKind {
    /** Sem rede, tempo esgotado, servidor fora. Recuperável: tentar de novo. */
    Network,

    /** 401/403 — URL assinada vencida ou inválida. Recuperável **renovando a URL**, não repetindo. */
    Expired,

    /** 404 — o vídeo não está lá. */
    NotFound,

    /** Codec/manifesto que o aparelho não toca. Repetir não adianta. */
    Unsupported,

    /** Nenhuma das anteriores. */
    Unknown,
}

/**
 * Traduz o par de sinais que **as duas** plataformas emitem para um [VideoStatus] só.
 *
 * Existe para o Android e o iOS não divergirem no que é "buffering": o ExoPlayer tem
 * `STATE_BUFFERING` explícito, o AVPlayer tem `isPlaybackLikelyToKeepUp` e `timeControlStatus`.
 * Cada `actual` traduz o seu vocabulário para estes três booleanos e a decisão final é **uma só**,
 * aqui — e coberta por teste.
 *
 * @param preparado o player já tem duração e primeiro quadro (`STATE_READY` / `.readyToPlay`).
 * @param querTocar o usuário mandou tocar (`playWhenReady` / `rate > 0` pretendido).
 * @param semDados está esperando rede (`STATE_BUFFERING` / `!isPlaybackLikelyToKeepUp`).
 */
fun videoStatusOf(preparado: Boolean, querTocar: Boolean, semDados: Boolean): VideoStatus = when {
    // Enquanto não há primeiro quadro é Loading, independentemente de já ter sido pedido play — a
    // tela mostra a roda sobre a capa, e não um botão de pausa que ainda não pausa nada.
    !preparado -> VideoStatus.Loading
    // Sem dado e PARADO não é buffering: é pausa. O player só espera rede quando quer andar.
    semDados && querTocar -> VideoStatus.Buffering
    querTocar -> VideoStatus.Playing
    else -> VideoStatus.Paused
}

/**
 * O tipo de falha correspondente a um status HTTP — a tradução que **as duas** plataformas usam
 * quando o erro do player carrega um código de resposta.
 *
 * O `401`/`403` virar [VideoErrorKind.Expired] em vez de "sem permissão" não é chute: numa aula com
 * URL assinada de curta duração, quem recebe 403 é quase sempre o aluno que pausou e voltou vinte
 * minutos depois. O `410 Gone` entra na mesma cesta pelo mesmo motivo.
 */
fun videoErrorKindForHttpStatus(status: Int): VideoErrorKind = when {
    status == 401 || status == 403 || status == 410 -> VideoErrorKind.Expired
    status == 404 -> VideoErrorKind.NotFound
    status >= 500 -> VideoErrorKind.Network
    else -> VideoErrorKind.Unknown
}
