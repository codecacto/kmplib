package br.com.codecacto.kmplib.video.download

import br.com.codecacto.kmplib.core.storage.isValidBlobId
import br.com.codecacto.kmplib.video.VideoStreamKind

/**
 * O que baixar — **o quê**, não **como**.
 *
 * O mesmo pedido vale no Media3 (Android) e no `AVAssetDownloadTask` (iOS), e o app não nomeia
 * nenhum dos dois.
 *
 * ### O [id] é do APP, e é uma chave
 * É ele que liga o arquivo baixado à aula: o app passa o id da aula, guarda esse id no seu banco e
 * mais nada precisa ser correlacionado. Ele também vira **nome de arquivo** e **chave de cache**,
 * então só ids seguros são aceitos ([isValidBlobId]) — id inválido é **recusado**, nunca
 * "sanitizado" (dois ids diferentes sanitizados podem virar o mesmo, e uma aula sobrescreveria a
 * outra em silêncio; é a mesma regra do `BlobStore`).
 *
 * ### Por que a URL não é a chave
 * Aula de curso vem por **URL assinada de curta duração**: `…/aula.m3u8?token=…&expires=…`. A URL
 * muda a cada abertura, e a mesma aula viraria um item novo no disco toda vez. Quem identifica é o
 * [id]; a [url] é só por onde se busca **agora**.
 *
 * @param id a identidade estável da mídia no app (o id da aula). Ver acima.
 * @param url de onde baixar agora. Pode mudar entre uma tentativa e outra (renovação do token).
 * @param kind normalmente [VideoStreamKind.Auto] — ver `videoStreamKindOf`. HLS é baixado **como
 *   HLS** (`DownloadHelper`/`AVAssetDownloadTask`); arquivo progressivo, byte a byte.
 * @param title o que a tela de Downloads mostra. `null` = o app resolve pelo [id].
 * @param groupId o agrupador do app — na prática **o curso**. É por ele que
 *   [MediaDownloadManager.removeGroup] apaga tudo de uma vez quando o direito de acesso cai.
 * @param quality qual faixa baixar quando o manifesto oferece várias. Ver [MediaDownloadQuality].
 * @param estimatedBytes o tamanho que o servidor informa, para a **conferência de espaço antes de
 *   começar**. `0` = desconhecido: o download é aceito assim mesmo e, se não couber, falha no meio
 *   com [MediaDownloadErrorKind.NoSpace] — o que é pior para o usuário, e por isso vale mandar o
 *   número quando o app o tem.
 * @param expiresAtMillis até quando a cópia baixada vale (epoch em ms), ou `null` para "não expira".
 *   Ver [isMediaDownloadExpired] — e leia a ressalva sobre o relógio do aparelho.
 */
data class MediaDownloadRequest(
    val id: String,
    val url: String,
    val kind: VideoStreamKind = VideoStreamKind.Auto,
    val title: String? = null,
    val groupId: String? = null,
    val quality: MediaDownloadQuality = MediaDownloadQuality.Standard,
    val estimatedBytes: Long = 0L,
    val expiresAtMillis: Long? = null,
)

/**
 * Qual faixa baixar quando o manifesto HLS oferece várias resoluções.
 *
 * Existe porque **baixar é diferente de tocar**: no streaming a faixa é escolhida a cada segundo
 * pela banda disponível, e no download ela é escolhida **uma vez, para sempre**. Deixar o default
 * do sistema decidir baixa a maior faixa do manifesto — que num curso gravado em 1080p são ~1,5 GB
 * por hora de aula, no celular de quem tem 8 GB livres.
 *
 * A escolha é por **bitrate**, não por altura em pixels: manifesto de verdade nem sempre declara
 * resolução, e bitrate é o que existe em todos.
 */
enum class MediaDownloadQuality {
    /** A menor faixa do manifesto. Economia máxima de espaço e de dados. */
    Low,

    /** A maior faixa **abaixo** de [STANDARD_BITRATE_CEILING] (~2,5 Mbps). O default. */
    Standard,

    /** A maior faixa do manifesto. */
    High,
}

/**
 * O teto de bitrate da qualidade [MediaDownloadQuality.Standard], em bits por segundo.
 *
 * 2,5 Mbps é o patamar de um 720p bem codificado — cerca de **1 GB por hora** de aula. Acima disso
 * o ganho é invisível na tela de um celular e o custo em disco dobra.
 */
const val STANDARD_BITRATE_CEILING: Int = 2_500_000

/**
 * Escolhe o bitrate a baixar entre os [bitrates] que o manifesto oferece.
 *
 * Lógica pura, comum às duas plataformas (no Android alimenta o `DownloadHelper`, no iOS o
 * `AVAssetDownloadTaskMinimumRequiredMediaBitrateKey`). Devolve `null` quando não há por onde
 * escolher — aí cada plataforma fica com o default dela.
 *
 * [MediaDownloadQuality.Standard] cai para a **menor** faixa quando todas passam do teto: é melhor
 * baixar a aula grande do que não baixar.
 */
fun selectDownloadBitrate(bitrates: List<Int>, quality: MediaDownloadQuality): Int? {
    val validos = bitrates.filter { it > 0 }.sorted()
    if (validos.isEmpty()) return null
    return when (quality) {
        MediaDownloadQuality.Low -> validos.first()
        MediaDownloadQuality.High -> validos.last()
        MediaDownloadQuality.Standard ->
            validos.lastOrNull { it <= STANDARD_BITRATE_CEILING } ?: validos.first()
    }
}

/** `true` se o [MediaDownloadRequest.id] pode virar chave e nome de arquivo. Ver [isValidBlobId]. */
fun isValidMediaDownloadId(id: String): Boolean = isValidBlobId(id)
