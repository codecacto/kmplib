package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * De onde o vídeo pode vir.
 *
 * Existe porque **publicar vídeo não é a mesma coisa que registrar um momento**. Num produto em que
 * o vídeo é conteúdo — feed, aula, portfólio —, ele quase sempre foi editado antes, e oferecer
 * "Gravar vídeo" ali é oferecer um caminho que ninguém usa e que ainda pede a permissão de câmera
 * (que o app talvez nem declare no manifesto).
 */
enum class VideoPickerSource {
    /** Só a galeria. Sem folha de escolha: o toque abre o seletor do sistema direto. */
    GALLERY_ONLY,

    /** Galeria **ou** câmera, com uma folha para escolher. É o comportamento histórico. */
    GALLERY_AND_CAMERA,
}

/**
 * O vídeo escolhido — **uma referência ao arquivo, nunca os bytes dele**.
 *
 * ### Por que referência
 * O limite de upload da casa é 100 MB. Um `ByteArray` de 100 MB não é "pesado": é **`OutOfMemoryError`
 * em aparelho de entrada**, antes de o app ver um único byte — e `OutOfMemoryError` não é `Exception`,
 * então nem o `try/catch` em volta o pega. Com a referência, o vídeo vai do disco para a rede em
 * pedaços ([readChunks]) e a memória do processo nunca guarda mais que um pedaço.
 *
 * @param reference como o arquivo é aberto **nesta plataforma**: no Android, a `content://` URI
 *   (`Uri.toString()`); no iOS, o **caminho absoluto** de uma cópia nossa em `NSTemporaryDirectory`.
 *   Trate como opaco — quem lê é [readChunks].
 *   ⚠️ **Android:** a permissão de leitura da URI é concedida ao processo e vale **enquanto ele
 *   viver**. Suba o vídeo na mesma sessão; guardar a string para o dia seguinte devolve
 *   `SecurityException`. Para guardar de verdade, copie o arquivo primeiro.
 * @param name o nome do arquivo, para o `Content-Disposition` do upload.
 * @param mimeType `video/mp4`, `video/quicktime`…
 * @param sizeBytes o tamanho em bytes. `0` quando o provedor não informa (raro).
 * @param durationMillis a duração, ou `null` se não deu para ler o cabeçalho. **Confira antes de
 *   subir**: o backend recusa acima de 61 s, e descobrir isso depois de 100 MB enviados é o pior
 *   momento possível.
 * @param widthPx largura já **na orientação em que o vídeo é exibido** — vídeo gravado em pé chega
 *   com a matriz de rotação, e a lib já a aplicou. `null` = desconhecida.
 * @param heightPx altura, idem.
 */
data class PickedVideo(
    val reference: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMillis: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
)

/**
 * Por que o vídeo NÃO veio — o que o app precisa dizer na tela.
 *
 * Desistir de escolher **não** está aqui, de propósito: fechar a galeria não é erro e não gera
 * mensagem. O que gera é o que a pessoa não consegue explicar sozinha.
 */
enum class VideoPickerError {
    /** O arquivo escolhido não pôde ser lido (provedor recusou, sumiu no meio, formato quebrado). */
    UNREADABLE,

    /**
     * A câmera foi negada — ou o app **não declarou** `android.permission.CAMERA` no manifesto, caso
     * em que o sistema nega sem nem mostrar o diálogo. Os dois chegam iguais: para quem está na tela
     * a diferença não existe.
     */
    CAMERA_PERMISSION_DENIED,

    /** A câmera não abriu (sem app de câmera, `FileProvider` ausente, falha do sistema). */
    CAMERA_UNAVAILABLE,
}

/** O disparo do seletor. Guarde-o e chame [launch] no `onClick`. */
expect class VideoPickerLauncher {
    fun launch()
}

/**
 * Cria e lembra um seletor de vídeo que devolve uma **referência** ao arquivo.
 *
 * ```kotlin
 * val picker = rememberVideoPickerLauncher(
 *     source = VideoPickerSource.GALLERY_ONLY,       // publicar vídeo: sem câmera
 *     onVideoPicked = { video ->
 *         if ((video.durationMillis ?: 0) > 61_000) { avisar("Máximo de 60 segundos"); return@… }
 *         scope.launch { enviar(video) }             // PUT em fluxo — ver readChunks
 *     },
 *     onError = { motivo -> avisar(motivo) },        // NUNCA silêncio
 * )
 *
 * AppButton("Escolher vídeo") { picker.launch() }
 * ```
 *
 * ## Requisito de manifesto (Android)
 * `VideoPickerSource.GALLERY_AND_CAMERA` exige `<uses-permission android:name="android.permission.CAMERA" />`
 * no manifesto do **app**. O `FileProvider` (authority `${applicationId}.fileprovider`) já vem do
 * `kmplib-platform` — **não redeclarar**, dois `FILE_PROVIDER_PATHS` na mesma authority param o
 * merge. Com `GALLERY_ONLY` não é preciso nada.
 */
@Composable
expect fun rememberVideoPickerLauncher(
    source: VideoPickerSource = VideoPickerSource.GALLERY_AND_CAMERA,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit = {},
): VideoPickerLauncher

/**
 * Lê o vídeo **em pedaços**, do disco, sem nunca ter o arquivo inteiro na memória.
 *
 * É a metade que faz a referência valer: com ela, subir 100 MB custa [chunkSize] de memória, não
 * 100 MB. Para um `PUT` direto no provedor de vídeo, é isto que alimenta o corpo da requisição.
 *
 * ```kotlin
 * client.put(urlAssinada) {
 *     contentType(ContentType.parse(video.mimeType))
 *     setBody(object : OutgoingContent.WriteChannelContent() {
 *         override val contentLength = video.sizeBytes
 *         override suspend fun writeTo(channel: ByteWriteChannel) {
 *             video.readChunks { bytes, count -> channel.writeFully(bytes, 0, count) }
 *         }
 *     })
 * }
 * ```
 *
 * @param onChunk recebe o buffer e **quantos bytes dele valem** — o último pedaço quase nunca é
 *   cheio. Ler `bytes.size` em vez de `count` sobe lixo no fim do arquivo.
 *   ⚠️ O buffer **pode ser reaproveitado** entre chamadas: consuma dentro do bloco, não guarde a
 *   referência.
 * @throws IllegalStateException se o arquivo não puder ser aberto (referência de outra sessão, no
 *   Android; arquivo removido, no iOS).
 */
expect suspend fun PickedVideo.readChunks(
    chunkSize: Int = DEFAULT_VIDEO_CHUNK_BYTES,
    onChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
)

/** 256 KB — pedaço grande o bastante para não picotar a rede e pequeno para caber em qualquer aparelho. */
const val DEFAULT_VIDEO_CHUNK_BYTES: Int = 256 * 1024

/**
 * Extrai **um quadro** do vídeo e devolve como JPEG — a capa, sem pedir foto a ninguém.
 *
 * Sem isto, todo produto com post de vídeo repete o mesmo plano B: uma segunda tela pedindo que a
 * pessoa **escolha uma foto de capa à mão**, para algo que o vídeo já tem. É o que o portal web faz
 * num `<canvas>` aos 0,5 s, e é o que passa a existir aqui nas duas plataformas.
 *
 * ```kotlin
 * val capa = video.captureFrame()            // ~0,5 s, onde já há imagem
 * val bytes = capa ?: pedirCapaAoUsuario()   // vídeo raro em que não dá: caia no caminho manual
 * ```
 *
 * ⚠️ **A rotação é aplicada** (`preferredTransform` no iOS, `METADATA_KEY_VIDEO_ROTATION` no
 * Android): vídeo gravado em pé geraria uma capa deitada, e a capa é justamente o que se olha antes
 * de tocar.
 *
 * @param atMillis onde tirar o quadro. O default de **500 ms** não é arbitrário: no instante `0` é
 *   comum o vídeo ainda estar preto (fade de entrada, autofoco), e uma capa preta parece um vídeo
 *   quebrado. Aproxima-se do quadro-chave mais próximo — o valor exato não é garantido.
 * @return o JPEG do quadro, ou `null` quando não deu para extrair (formato sem quadro no instante
 *   pedido, arquivo ilegível). **Nunca lança**: capa é acessório, e não pode derrubar a publicação.
 */
expect suspend fun PickedVideo.captureFrame(atMillis: Long = 500): ByteArray?

// =================================================================================================
// Legado — a API de bytes. Mantida para não quebrar compilação; não use em código novo.
// =================================================================================================

/**
 * O vídeo escolhido **inteiro na memória**.
 *
 * @deprecated Carrega o arquivo todo num `ByteArray`: com o limite de 100 MB da casa, é
 *   `OutOfMemoryError` em aparelho de entrada — e `OutOfMemoryError` não é `Exception`, então o
 *   `try/catch` da tela não o segura. Use [PickedVideo] + [readChunks].
 */
@Deprecated(
    "Carrega o vídeo INTEIRO na memória (OOM acima de ~100 MB). Use PickedVideo + readChunks.",
    ReplaceWith("PickedVideo"),
)
data class SelectedVideo(
    val bytes: ByteArray,
    val mimeType: String,
    val name: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectedVideo) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

/**
 * Seletor de vídeo que devolve os **bytes**.
 *
 * @deprecated Duas razões, e as duas doem: carrega o arquivo inteiro na memória (ver
 *   [SelectedVideo]) e sempre oferece "Gravar vídeo", que em produto de publicação é um caminho que
 *   ninguém usa e que exige a permissão de câmera. Prefira
 *   `rememberVideoPickerLauncher(source, onVideoPicked, onError)`.
 */
@Deprecated(
    "Carrega o vídeo inteiro na memória e sempre oferece a câmera. " +
        "Use rememberVideoPickerLauncher(source = …, onVideoPicked = …, onError = …).",
    ReplaceWith("rememberVideoPickerLauncher(VideoPickerSource.GALLERY_ONLY, { }, { })"),
)
@Suppress("DEPRECATION")
@Composable
fun rememberVideoPickerLauncher(
    onVideoSelected: (SelectedVideo) -> Unit,
): VideoPickerLauncher {
    val escopo = rememberCoroutineScope()
    return rememberVideoPickerLauncher(
        source = VideoPickerSource.GALLERY_AND_CAMERA,
        onVideoPicked = { video ->
            escopo.launch {
                runCatching { video.paraSelectedVideo() }
                    .onSuccess(onVideoSelected)
            }
        },
        onError = {},
    )
}

/** A ponte do legado: junta os pedaços num `ByteArray` só. Uma alocação, não duas. */
@Suppress("DEPRECATION")
private suspend fun PickedVideo.paraSelectedVideo(): SelectedVideo {
    val total = sizeBytes
    val bytes = if (total in 1..Int.MAX_VALUE.toLong()) {
        val destino = ByteArray(total.toInt())
        var escrito = 0
        readChunks { pedaco, quantos ->
            val cabe = minOf(quantos, destino.size - escrito)
            if (cabe > 0) {
                pedaco.copyInto(destino, escrito, 0, cabe)
                escrito += cabe
            }
        }
        if (escrito == destino.size) destino else destino.copyOf(escrito)
    } else {
        // Tamanho desconhecido: não há como pré-alocar, e este caminho é justamente o que a API
        // nova existe para aposentar.
        val partes = mutableListOf<ByteArray>()
        readChunks { pedaco, quantos -> partes += pedaco.copyOf(quantos) }
        val destino = ByteArray(partes.sumOf { it.size })
        var escrito = 0
        partes.forEach { it.copyInto(destino, escrito); escrito += it.size }
        destino
    }
    return SelectedVideo(bytes = bytes, mimeType = mimeType, name = name)
}
