package br.com.codecacto.kmplib.video

/**
 * O vídeo a tocar — **o quê**, não **como**.
 *
 * A separação importa: o mesmo [VideoMedia] toca no ExoPlayer (Android) e no AVPlayer (iOS) sem
 * que a tela do app saiba de nenhum dos dois.
 *
 * ### Por que NÃO há cabeçalhos HTTP aqui
 *
 * Um player de curso protege o vídeo com **URL assinada de curta duração**, e é assim de propósito.
 * Cabeçalho customizado numa `AVURLAsset` remota **não tem API pública no iOS** (o
 * `AVURLAssetHTTPHeaderFieldsKey` é privado; o caminho oficial seria um
 * `AVAssetResourceLoaderDelegate` reescrevendo o manifesto, o que é outro produto). Aceitar
 * `headers` aqui faria a lib prometer no Android o que ignoraria em silêncio no iOS — o mesmo erro
 * que o `SoundEffectPlayer` recusou cometer com "volume por disparo". Assine a URL.
 *
 * @param url a URL do `.m3u8` (HLS) ou do arquivo progressivo (`.mp4`).
 * @param kind normalmente [VideoStreamKind.Auto] — ver [videoStreamKindOf].
 * @param title o que aparece na tela de bloqueio / central de mídia. `null` = sem metadados.
 * @param artist a linha de baixo da tela de bloqueio (o nome do curso, por exemplo).
 * @param subtitles faixas de legenda em **arquivo externo**. As que já vêm dentro do manifesto HLS
 *   **não se declaram aqui** — a plataforma as descobre sozinha e elas aparecem em
 *   [VideoPlayerState.subtitleOptions] depois que a mídia carrega.
 * @param offlineId liga esta mídia à **cópia baixada** de mesmo id (ver
 *   `MediaDownloadManager.offlineMediaFor`, que devolve um [VideoMedia] já com ele preenchido).
 *   Com ele presente o player toca do disco e **não** toca a rede; sem ele, toca da [url].
 *   É um id, e não um caminho, porque as duas plataformas guardam o baixado de formas diferentes —
 *   no Android é o cache do Media3, endereçado por chave (é isso que faz a URL assinada, que muda a
 *   cada abertura, não virar um item novo no disco); no iOS é um pacote `.movpkg` num caminho que o
 *   sistema escolhe. Um caminho na API funcionaria em um dos dois e mentiria no outro.
 * @param startPositionMillis onde começar. É a retomada ("continuar de onde parou"); `0` = do
 *   início. Aplicada **antes** do primeiro quadro, sem o salto visível de dar play e depois buscar.
 */
data class VideoMedia(
    val url: String,
    val kind: VideoStreamKind = VideoStreamKind.Auto,
    val title: String? = null,
    val artist: String? = null,
    val subtitles: List<VideoSubtitleTrack> = emptyList(),
    val startPositionMillis: Long = 0L,
    val offlineId: String? = null,
)

/** Como a mídia é entregue. [Auto] deixa [videoStreamKindOf] decidir pela URL. */
enum class VideoStreamKind { Auto, Hls, Progressive }

/**
 * Classifica a URL. Função pura — é ela que os testes cobrem e é ela que garante que Android e iOS
 * concordem sobre o que é um HLS.
 *
 * A regra é a extensão do **caminho**, ignorando a query: uma URL assinada termina em
 * `?token=…&expires=…` e olhar a string inteira nunca acharia o `.m3u8`.
 */
fun videoStreamKindOf(url: String): VideoStreamKind {
    val caminho = url.substringBefore('?').substringBefore('#').lowercase()
    return if (caminho.endsWith(".m3u8")) VideoStreamKind.Hls else VideoStreamKind.Progressive
}

/** A forma efetiva de [VideoMedia.kind], resolvendo [VideoStreamKind.Auto] pela URL. */
fun VideoMedia.resolvedKind(): VideoStreamKind =
    if (kind == VideoStreamKind.Auto) videoStreamKindOf(url) else kind
