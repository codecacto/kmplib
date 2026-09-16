package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable

/**
 * De onde a foto pode vir.
 *
 * Existe pelo mesmo motivo do [VideoPickerSource]: **publicar uma foto não é a mesma coisa que
 * registrar um momento**. Num produto em que a imagem é conteúdo — feed, anúncio, portfólio —, ela
 * quase sempre já está no rolo da câmera, e oferecer "Tirar foto" ali é oferecer um caminho que
 * ninguém usa e que ainda exige `android.permission.CAMERA` no manifesto do app.
 */
enum class ImagePickerSource {
    /** Só a galeria. Sem folha de escolha: o toque abre o seletor do sistema direto. */
    GALLERY_ONLY,

    /** Galeria **ou** câmera, com uma folha para escolher. É o comportamento histórico. */
    GALLERY_AND_CAMERA,
}

/** O disparo do seletor. Guarde-o e chame [launch] no `onClick`. */
expect class ImagePickerLauncher {
    fun launch()
}

/**
 * A foto escolhida: os bytes **e a medida deles**.
 *
 * ### Por que a medida vem junto (e por que a falta dela era um bug ao vivo)
 * Quem publica uma foto precisa gravar a proporção dela. Sem [widthPx]/[heightPx], o app manda a
 * imagem sem `mediaWidth`/`mediaHeight`, o feed cai no padrão (4:5 com corte central) e **a mesma
 * foto publicada pelo site aparece inteira, enquanto a publicada pelo aplicativo aparece cortada** —
 * porque o navegador lê a medida sozinho e o app não tinha como. Foi o que aconteceu no Cidade
 * Conectada (11/set/2026).
 *
 * ### A medida é a da imagem que SAI, não a do original
 * O seletor reduz a foto (JPEG, no máximo [PICKED_IMAGE_MAX_DIMENSION] px no maior lado, qualidade
 * 85) — então devolver a medida do arquivo original erraria a proporção de outro jeito. [widthPx] e
 * [heightPx] descrevem exatamente os [bytes] que vêm nesta mesma instância.
 *
 * ### Orientação já aplicada
 * Foto de celular chega deitada no arquivo, com a orientação num campo EXIF à parte. Quem lê a
 * medida crua conclui que **todo retrato é paisagem**. A lib aplica a rotação antes de medir e
 * **grava os bytes já girados**, sem tag de orientação sobrando: a medida daqui é a mesma que
 * qualquer decodificador vai encontrar nos [bytes], inclusive o do backend e o do navegador.
 *
 * @param bytes o JPEG pronto para subir.
 * @param widthPx largura, em pixels, da imagem contida em [bytes].
 * @param heightPx altura, idem.
 * @param mimeType sempre `image/jpeg` hoje — o seletor recodifica o que escolhem.
 */
data class PickedImage(
    val bytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val mimeType: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedImage) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (widthPx != other.widthPx) return false
        if (heightPx != other.heightPx) return false
        if (mimeType != other.mimeType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + widthPx
        result = 31 * result + heightPx
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/**
 * O maior lado que a foto pode ter depois de reduzida. Mora aqui, e não em cada plataforma, porque
 * é ele que amarra a promessa do [PickedImage]: a medida devolvida é a da imagem reduzida.
 */
const val PICKED_IMAGE_MAX_DIMENSION: Int = 1024

/**
 * A medida que a foto **vai ter** depois da redução — a conta que Android e iOS fazem, escrita uma
 * vez só para as duas darem o mesmo resultado.
 *
 * Reduz proporcionalmente até o maior lado caber em [maxDimension] e **nunca amplia**: imagem menor
 * que o teto sai como está. Medida inválida (zero ou negativa) volta intacta — não há o que reduzir,
 * e inventar um valor aqui esconderia o problema do decodificador.
 */
internal fun scaledImageSize(
    widthPx: Int,
    heightPx: Int,
    maxDimension: Int = PICKED_IMAGE_MAX_DIMENSION,
): Pair<Int, Int> {
    if (widthPx <= 0 || heightPx <= 0 || maxDimension <= 0) return widthPx to heightPx
    if (widthPx <= maxDimension && heightPx <= maxDimension) return widthPx to heightPx

    val ratio = minOf(
        maxDimension.toDouble() / widthPx.toDouble(),
        maxDimension.toDouble() / heightPx.toDouble(),
    )
    // `coerceAtLeast(1)`: o truncamento pode zerar o lado curto de uma imagem muito alongada
    // (panorama de 8000x300 vira 1024x38 — mas 8000x7 viraria 1024x0, que nenhum encoder aceita).
    val novaLargura = (widthPx * ratio).toInt().coerceAtLeast(1)
    val novaAltura = (heightPx * ratio).toInt().coerceAtLeast(1)
    return novaLargura to novaAltura
}

/**
 * Por que a foto NAO veio — o que o app precisa dizer na tela.
 *
 * Sem isto, cada motivo terminava num `printStackTrace()` ou num `if (granted)` sem `else`: o toque
 * em "Tirar foto" nao produzia efeito NENHUM, e a leitura de quem usa e "o botao esta quebrado".
 * Foi o que aconteceu no NeuroCoreX (21/ago/2026), onde o app nao declarava `CAMERA` no manifest —
 * permissao nao declarada e negada pelo sistema na hora, sem nem mostrar o dialogo.
 */
enum class ImagePickerError {
    /**
     * A pessoa negou a camera — ou o app **nao declarou** `android.permission.CAMERA` no manifest,
     * caso em que o sistema nega sem perguntar. Os dois chegam aqui iguais, de proposito: para quem
     * esta na tela a diferenca nao existe, e para quem desenvolve o log da lib nomeia o caso.
     */
    CAMERA_PERMISSION_DENIED,

    /** A camera nao abriu (sem app de camera, `FileProvider` ausente, falha do sistema). */
    CAMERA_UNAVAILABLE,

    /** A imagem escolhida nao pode ser lida ou decodificada. */
    IMAGE_UNREADABLE,
}

/**
 * Cria e lembra um seletor de foto que devolve os bytes **e a medida** ([PickedImage]).
 *
 * ```kotlin
 * val seletor = rememberImagePickerLauncher(
 *     source = ImagePickerSource.GALLERY_ONLY,   // publicar foto: sem câmera
 *     onImagePicked = { foto ->
 *         publicar(foto.bytes, largura = foto.widthPx, altura = foto.heightPx)
 *     },
 *     onError = { motivo -> avisar(motivo) },    // NUNCA silêncio
 * )
 *
 * AppButton("Adicionar foto") { seletor.launch() }
 * ```
 *
 * ## Requisito de manifest (Android)
 *
 * [ImagePickerSource.GALLERY_AND_CAMERA] exige
 * `<uses-permission android:name="android.permission.CAMERA" />` no manifest do **APP**. O
 * `FileProvider` (authority `${applicationId}.fileprovider`) já vem do `kmplib-platform` — **não
 * redeclarar**, dois `FILE_PROVIDER_PATHS` na mesma authority param o merge. Com
 * [ImagePickerSource.GALLERY_ONLY] não é preciso nada.
 */
@Composable
expect fun rememberImagePickerLauncher(
    source: ImagePickerSource = ImagePickerSource.GALLERY_AND_CAMERA,
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit = {},
): ImagePickerLauncher

// =================================================================================================
// Legado — a API que devolve só os bytes. Continua funcionando; não use em código novo.
//
// A assinatura nova começa por `source`, e é isto que mantém as chamadas antigas compilando mesmo
// sem nomear os argumentos: uma lambda posicional não casa com `ImagePickerSource`, então a
// resolução de sobrecarga nunca fica ambígua.
// =================================================================================================

/**
 * Seletor de foto que devolve **só os bytes**.
 *
 * @deprecated Sem a medida, quem publica não tem como gravar a proporção da imagem, e o feed cai no
 *   corte central — a mesma foto sai inteira pelo site e cortada pelo app. Use a sobrecarga com
 *   [PickedImage], que traz `widthPx`/`heightPx` da imagem já reduzida e já girada.
 */
@Deprecated(
    "Devolve os bytes sem a medida, e sem ela o app publica a foto sem proporção (o feed corta). " +
        "Use rememberImagePickerLauncher(source = …, onImagePicked = …, onError = …).",
    ReplaceWith("rememberImagePickerLauncher(onImagePicked = { }, onError = { })"),
)
@Composable
fun rememberImagePickerLauncher(
    onImageSelected: (ByteArray) -> Unit,
    onError: (ImagePickerError) -> Unit,
): ImagePickerLauncher = rememberImagePickerLauncher(
    source = ImagePickerSource.GALLERY_AND_CAMERA,
    onImagePicked = { foto -> onImageSelected(foto.bytes) },
    onError = onError,
)

/**
 * Sobrecarga sem tratamento de erro. **Prefira a de [PickedImage]**: aqui, câmera negada e imagem
 * ilegível são silêncio na tela — e a foto vai sem medida.
 */
@Deprecated(
    "Sem medida e sem tratamento de erro: câmera negada vira silêncio na tela. " +
        "Use rememberImagePickerLauncher(source = …, onImagePicked = …, onError = …).",
    ReplaceWith("rememberImagePickerLauncher(onImagePicked = { }, onError = { })"),
)
@Composable
fun rememberImagePickerLauncher(
    onImageSelected: (ByteArray) -> Unit,
): ImagePickerLauncher = rememberImagePickerLauncher(
    source = ImagePickerSource.GALLERY_AND_CAMERA,
    onImagePicked = { foto -> onImageSelected(foto.bytes) },
    onError = {},
)

// =================================================================================================
// Seletor MÚLTIPLO — escolher várias fotos numa passada só.
// =================================================================================================

/**
 * Quantas fotos o seletor múltiplo aceita de uma vez quando o app não diz outro número.
 *
 * Vinte é o que cabe numa sessão de anúncio sem virar espera: o gargalo não é escolher, é o upload
 * de cada uma. Quem precisar de mais chama o seletor de novo — não há teto acumulado.
 */
const val MULTI_IMAGE_PICKER_DEFAULT_LIMIT: Int = 20

/** O disparo do seletor múltiplo. Guarde-o e chame [launch] no `onClick`. */
expect class MultiImagePickerLauncher {
    fun launch()
}

/**
 * Seletor de **várias** fotos da galeria, numa passada só.
 *
 * ```kotlin
 * val seletor = rememberMultiImagePickerLauncher(
 *     selectionLimit = 20,
 *     onImagesPicked = { fotos -> fotos.forEach(::enviar) },
 *     onError = { motivo -> avisar(motivo) },
 * )
 *
 * PhotoStrip(fotos = state.fotos, onAdd = { seletor.launch() }, …)
 * ```
 *
 * ## Por que existe, e por que NÃO tem câmera
 * Quem monta um anúncio de carro ou de imóvel já fotografou tudo antes de abrir o app: obrigá-lo a
 * entrar na galeria, escolher uma, esperar, e repetir oito vezes é o atrito que fazia o anúncio
 * nascer com duas fotos. E câmera não entra aqui de propósito — ela produz **uma** foto por vez, e
 * oferecer "tirar foto" num seletor múltiplo prometeria algo que o sistema não faz. Para o caminho
 * de uma foto só (avatar, capa, documento) continue em [rememberImagePickerLauncher].
 *
 * ## A ordem da escolha é a ordem entregue
 * A lista chega na ordem em que o sistema devolveu, e o índice 0 é o primeiro item — é o que permite
 * ao app tratar "a primeira é a capa" sem reordenar nada.
 *
 * ## Uma foto ilegível não derruba as outras
 * Cada item é decodificado por conta própria. O que falhar é descartado e reportado em [onError]
 * **uma vez**; o que deu certo é entregue em [onImagesPicked]. Uma imagem corrompida no meio da
 * seleção não pode custar as outras dezenove.
 *
 * ## Desistir não é erro
 * Fechar a galeria sem escolher nada **não** chama [onImagesPicked] nem [onError] — é exatamente o
 * mesmo contrato do seletor de uma foto.
 *
 * @param selectionLimit teto de itens por abertura; é preso à faixa aceita pelo sistema.
 * @param onImagesPicked as fotos já reduzidas, giradas e medidas (ver [PickedImage]). Nunca vazia.
 * @param onError por que ao menos uma foto não veio — NUNCA silêncio na tela.
 */
@Composable
expect fun rememberMultiImagePickerLauncher(
    selectionLimit: Int = MULTI_IMAGE_PICKER_DEFAULT_LIMIT,
    onImagesPicked: (List<PickedImage>) -> Unit,
    onError: (ImagePickerError) -> Unit = {},
): MultiImagePickerLauncher
