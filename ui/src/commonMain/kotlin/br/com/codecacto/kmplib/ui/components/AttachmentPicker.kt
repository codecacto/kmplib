package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_attachment_file
import br.com.codecacto.kmplib.generated.resources.kmplib_attachment_gallery
import br.com.codecacto.kmplib.generated.resources.kmplib_attachment_take_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_attachment_title_attachment
import br.com.codecacto.kmplib.generated.resources.kmplib_attachment_title_photo
import br.com.codecacto.kmplib.platform.DEFAULT_MAX_FILE_BYTES
import br.com.codecacto.kmplib.platform.FileData
import br.com.codecacto.kmplib.platform.FilePickFailure
import br.com.codecacto.kmplib.platform.FilePickResult
import br.com.codecacto.kmplib.platform.PickedFile
import br.com.codecacto.kmplib.platform.rememberMultiFilePicker
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

// =================================================================================================
// Seletor de ANEXO — o jeito único de escolher anexo/foto numa tela (2.332.0).
//
// Regra da fábrica (fundador, 27/out/2026): todo lugar do app em que o usuário escolhe um anexo ou
// uma foto abre do MESMO jeito.
//   - o lugar aceita foto  → SEMPRE a folha com as 3 origens: Tirar foto · Galeria · Arquivo;
//   - o lugar NÃO aceita foto (só documento) → nenhuma folha: o seletor de arquivos abre direto.
// Origem: no LocAki, "Anexos do cliente" abria só Arquivos e "Fotos da entrega" só Galeria/Câmera —
// o cliente reclamou que cada lugar abria diferente.
// =================================================================================================

/** O que o lugar aceita, em três formas — decide a folha (ver [AttachmentAccept]). */
enum class AttachmentAcceptKind {
    /** Só foto (ex.: fotos da entrega). Folha com as 3 origens; TODAS devolvem [PickedAttachment.Photo]. */
    IMAGES_ONLY,

    /**
     * Foto **e** outros arquivos (ex.: anexos do cliente). Folha com as 3 origens; câmera e galeria
     * devolvem [PickedAttachment.Photo], "Arquivo" devolve [PickedAttachment.File] (como está).
     */
    IMAGES_AND_FILES,

    /** Só documento, sem foto (ex.: só PDF). Sem folha: o seletor de arquivos abre direto. */
    FILES_ONLY,
}

/** As origens que a folha oferece, na ordem em que aparecem. */
enum class AttachmentSource { CAMERA, GALLERY, FILE }

/**
 * O que o lugar aceita. **A forma é derivada dos tipos MIME, não escolhida à parte** — é isso que
 * garante a regra: se o lugar aceita imagem, a folha com câmera e galeria aparece; não há como
 * declarar "aceito `image/` + `*` mas só pelo seletor de arquivos".
 *
 * ```kotlin
 * AttachmentAccept.ImagesOnly                               // fotos da entrega
 * AttachmentAccept.AnyFile                                  // anexos do cliente (qualquer tipo)
 * AttachmentAccept.forMimeTypes(listOf("application/pdf"))  // só PDF → abre o seletor direto
 * AttachmentAccept.forMimeTypes(listOf("application/pdf", "image/png")) // PDF ou foto → folha
 * ```
 *
 * @property kind a forma derivada (ver [forMimeTypes]).
 * @property fileMimeTypes o filtro do seletor de arquivos ("Arquivo"), já normalizado.
 */
@Immutable
class AttachmentAccept private constructor(
    val kind: AttachmentAcceptKind,
    val fileMimeTypes: List<String>,
) {
    /** `true` quando o lugar aceita foto — e, por isso, abre a folha. */
    val acceptsPhotos: Boolean get() = kind != AttachmentAcceptKind.FILES_ONLY

    override fun equals(other: Any?): Boolean =
        other is AttachmentAccept && other.kind == kind && other.fileMimeTypes == fileMimeTypes

    override fun hashCode(): Int = 31 * kind.hashCode() + fileMimeTypes.hashCode()

    override fun toString(): String = "AttachmentAccept(kind=$kind, fileMimeTypes=$fileMimeTypes)"

    companion object {
        /** Só foto. "Arquivo" abre o seletor filtrado para imagem, e o resultado vira [PickedImage]. */
        val ImagesOnly: AttachmentAccept = AttachmentAccept(AttachmentAcceptKind.IMAGES_ONLY, listOf("image/*"))

        /** Qualquer arquivo — e, portanto, foto também. */
        val AnyFile: AttachmentAccept = AttachmentAccept(AttachmentAcceptKind.IMAGES_AND_FILES, listOf("*/*"))

        /**
         * A forma pelos tipos MIME aceitos (minúsculas, espaços e vazios descartados; lista vazia =
         * qualquer arquivo):
         * - todos `image/…` → [AttachmentAcceptKind.IMAGES_ONLY] (a saída é sempre JPEG — ver [PickedImage]);
         * - algum `image/…` ou `*` + `/` + `*` → [AttachmentAcceptKind.IMAGES_AND_FILES];
         * - nenhum que aceite imagem → [AttachmentAcceptKind.FILES_ONLY].
         */
        fun forMimeTypes(mimeTypes: List<String>): AttachmentAccept {
            val tipos = mimeTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            if (tipos.isEmpty() || ANY_MIME in tipos) return AnyFile
            val imagens = tipos.count { it.startsWith("image/") }
            val kind = when {
                imagens == tipos.size -> AttachmentAcceptKind.IMAGES_ONLY
                imagens > 0 -> AttachmentAcceptKind.IMAGES_AND_FILES
                else -> AttachmentAcceptKind.FILES_ONLY
            }
            return AttachmentAccept(kind, tipos)
        }
    }
}

private const val ANY_MIME = "*/*"

/**
 * O que a tela vai fazer para uma configuração — regra pura, testada no `commonTest`.
 *
 * @property showsSheet folha com as origens (lugar aceita foto) ou seletor de arquivos direto.
 * @property options as origens da folha, na ordem; vazio quando não há folha.
 * @property fileDeliversPhoto `true` = o "Arquivo" passa pelo tratamento de foto (sai [PickedImage]).
 */
internal data class AttachmentPickerPlan(
    val showsSheet: Boolean,
    val options: List<AttachmentSource>,
    val fileMimeTypes: List<String>,
    val fileDeliversPhoto: Boolean,
)

internal fun attachmentPickerPlan(accept: AttachmentAccept): AttachmentPickerPlan = when (accept.kind) {
    AttachmentAcceptKind.FILES_ONLY -> AttachmentPickerPlan(
        showsSheet = false,
        options = emptyList(),
        fileMimeTypes = accept.fileMimeTypes,
        fileDeliversPhoto = false,
    )
    AttachmentAcceptKind.IMAGES_ONLY, AttachmentAcceptKind.IMAGES_AND_FILES -> AttachmentPickerPlan(
        showsSheet = true,
        options = listOf(AttachmentSource.CAMERA, AttachmentSource.GALLERY, AttachmentSource.FILE),
        fileMimeTypes = accept.fileMimeTypes,
        fileDeliversPhoto = accept.kind == AttachmentAcceptKind.IMAGES_ONLY,
    )
}

/**
 * Um anexo escolhido. Duas formas, e o tipo diz qual:
 * - [Photo] — foto já tratada ([PickedImage]: JPEG, em pé, sem EXIF/GPS, HEIC→JPEG, teto), com nome
 *   de arquivo pronto para enviar;
 * - [File] — arquivo **como está**, por referência ([PickedFile]: lido sob demanda, um por vez).
 *
 * Para enviar sem tratar os dois casos à parte: [readForUpload].
 */
sealed interface PickedAttachment {
    /** De onde veio. */
    val origin: AttachmentSource

    /** Nome para o anexo (`foto-AAAAMMDD-HHMMSS.jpg` na foto da câmera/galeria). Pode conter dado pessoal. */
    val fileName: String

    /** Tipo do conteúdo: `image/jpeg` na foto; o informado pelo sistema no arquivo. */
    val mimeType: String

    /**
     * Uma foto tratada. Veio da câmera, da galeria ou — em [AttachmentAcceptKind.IMAGES_ONLY] — do
     * seletor de arquivos, e nos três casos passou pela MESMA codificação do
     * [rememberImagePickerLauncher].
     */
    class Photo(
        val image: PickedImage,
        override val origin: AttachmentSource,
        override val fileName: String,
    ) : PickedAttachment {
        override val mimeType: String get() = image.mimeType

        override fun equals(other: Any?): Boolean =
            other is Photo && other.image == image && other.origin == origin && other.fileName == fileName

        override fun hashCode(): Int = (image.hashCode() * 31 + origin.hashCode()) * 31 + fileName.hashCode()

        /** Sem nome e sem bytes: o nome de arquivo escolhido pode conter dado pessoal. */
        override fun toString(): String =
            "PickedAttachment.Photo(origin=$origin, ${image.widthPx}x${image.heightPx}, bytes=${image.bytes.size})"
    }

    /** Um arquivo como está, por referência (só em [AttachmentAcceptKind.IMAGES_AND_FILES]/[AttachmentAcceptKind.FILES_ONLY]). */
    class File(val file: PickedFile) : PickedAttachment {
        override val origin: AttachmentSource get() = AttachmentSource.FILE
        override val fileName: String get() = file.name
        override val mimeType: String get() = file.mimeType

        override fun toString(): String = "PickedAttachment.File($file)"
    }
}

/**
 * Os bytes para enviar, com o mesmo desfecho tipado do seletor de arquivos — [FilePickResult.Picked]
 * (nome, tipo e conteúdo), [FilePickResult.TooLarge] ou [FilePickResult.Failed]; nunca lança.
 *
 * Foto: os bytes já estão em memória (JPEG reduzido) e só se confere o teto. Arquivo: lido agora,
 * com teto ([PickedFile.read]) — chame um por vez, logo antes de enviar.
 */
suspend fun PickedAttachment.readForUpload(maxBytes: Long = DEFAULT_MAX_FILE_BYTES): FilePickResult =
    when (this) {
        is PickedAttachment.Photo -> photoUploadResult(this, maxBytes)
        is PickedAttachment.File -> file.read(maxBytes)
    }

internal fun photoUploadResult(photo: PickedAttachment.Photo, maxBytes: Long): FilePickResult {
    val tamanho = photo.image.bytes.size.toLong()
    return if (maxBytes > 0 && tamanho > maxBytes) {
        FilePickResult.TooLarge(photo.fileName, tamanho, maxBytes)
    } else {
        FilePickResult.Picked(FileData(photo.fileName, photo.mimeType, photo.image.bytes, tamanho))
    }
}

/** Só as fotos (útil em [AttachmentAcceptKind.IMAGES_ONLY], onde toda entrega é foto). */
fun List<PickedAttachment>.photos(): List<PickedImage> = filterIsInstance<PickedAttachment.Photo>().map { it.image }

/**
 * Nome padrão da foto: `foto-AAAAMMDD-HHMMSS.jpg` na hora local; [sequence] > 1 acrescenta `-N`
 * (várias fotos da galeria no mesmo segundo não repetem nome).
 */
fun photoAttachmentFileName(at: LocalDateTime, sequence: Int = 1): String {
    fun Int.dois() = toString().padStart(2, '0')
    val data = "${at.year.toString().padStart(4, '0')}${at.month.number.dois()}${at.day.dois()}"
    val hora = "${at.hour.dois()}${at.minute.dois()}${at.second.dois()}"
    val sufixo = if (sequence > 1) "-$sequence" else ""
    return "foto-$data-$hora$sufixo.jpg"
}

/**
 * Nome de arquivo para a foto que veio do seletor de arquivos e foi recodificada: a base do nome
 * original com `.jpg` (`IMG_0042.HEIC` → `IMG_0042.jpg`). Base vazia → [fallback].
 */
internal fun jpegFileNameFor(originalName: String, fallback: String): String {
    val base = originalName.trim().let { if ('.' in it) it.substringBeforeLast('.') else it }.trim()
    return if (base.isEmpty()) fallback else "$base.jpg"
}

/** Por que um anexo NÃO veio. Desistir (fechar a folha ou o seletor) não é erro e não chega aqui. */
enum class AttachmentPickerError {
    /** Câmera negada pela pessoa — ou `CAMERA` não declarada no manifest do app. */
    CAMERA_PERMISSION_DENIED,

    /** A câmera não abriu (aparelho sem câmera, sem app de câmera, falha do sistema). */
    CAMERA_UNAVAILABLE,

    /** A foto escolhida não pôde ser lida ou não é imagem (inclusive o "Arquivo" de [AttachmentAcceptKind.IMAGES_ONLY]). */
    IMAGE_UNREADABLE,

    /** Em [AttachmentAcceptKind.IMAGES_ONLY], o arquivo passou do teto `maxImageFileBytes`. */
    FILE_TOO_LARGE,

    /** Em [AttachmentAcceptKind.IMAGES_ONLY], o arquivo não pôde ser lido do provedor. */
    FILE_UNREADABLE,
}

internal fun ImagePickerError.toAttachmentError(): AttachmentPickerError = when (this) {
    ImagePickerError.CAMERA_PERMISSION_DENIED -> AttachmentPickerError.CAMERA_PERMISSION_DENIED
    ImagePickerError.CAMERA_UNAVAILABLE -> AttachmentPickerError.CAMERA_UNAVAILABLE
    ImagePickerError.IMAGE_UNREADABLE -> AttachmentPickerError.IMAGE_UNREADABLE
}

internal fun FilePickResult.toAttachmentError(): AttachmentPickerError? = when (this) {
    is FilePickResult.Picked, FilePickResult.Cancelled -> null
    is FilePickResult.TooLarge -> AttachmentPickerError.FILE_TOO_LARGE
    is FilePickResult.Failed -> if (reason == FilePickFailure.OutOfMemory) {
        AttachmentPickerError.FILE_TOO_LARGE
    } else {
        AttachmentPickerError.FILE_UNREADABLE
    }
}

/**
 * Teto de leitura do arquivo de IMAGEM em [AttachmentAcceptKind.IMAGES_ONLY] (40 MiB): uma foto
 * HEIC/JPEG de 48 MP fica bem abaixo disso; acima, é outra coisa que não uma foto de celular.
 */
const val ATTACHMENT_IMAGE_FILE_MAX_BYTES: Long = 40L * 1024L * 1024L

/** Textos da folha. Default nos 4 idiomas da lib: [rememberAttachmentPickerTexts]. */
@Immutable
data class AttachmentPickerTexts(
    val title: String,
    val takePhoto: String,
    val gallery: String,
    val file: String,
)

/** Os textos da folha no idioma da tela; o título diz "foto" ou "anexo" conforme [accept]. */
@Composable
fun rememberAttachmentPickerTexts(accept: AttachmentAccept): AttachmentPickerTexts = AttachmentPickerTexts(
    title = kmpStringResource(
        if (accept.kind == AttachmentAcceptKind.IMAGES_ONLY) {
            Res.string.kmplib_attachment_title_photo
        } else {
            Res.string.kmplib_attachment_title_attachment
        },
    ),
    takePhoto = kmpStringResource(Res.string.kmplib_attachment_take_photo),
    gallery = kmpStringResource(Res.string.kmplib_attachment_gallery),
    file = kmpStringResource(Res.string.kmplib_attachment_file),
)

/** Ids estáveis da folha, para o Maestro (visíveis como `resource-id` também no Android). */
object AttachmentPickerTestTags {
    const val FOLHA: String = "anexo-folha"
    const val TITULO: String = "anexo-titulo"
    const val CAMERA: String = "anexo-opcao-camera"
    const val GALERIA: String = "anexo-opcao-galeria"
    const val ARQUIVO: String = "anexo-opcao-arquivo"

    fun of(source: AttachmentSource): String = when (source) {
        AttachmentSource.CAMERA -> CAMERA
        AttachmentSource.GALLERY -> GALERIA
        AttachmentSource.FILE -> ARQUIVO
    }
}

/** O disparo do seletor de anexo. Guarde-o e chame [launch] no `onClick`. */
class AttachmentPickerLauncher internal constructor(private val onLaunch: () -> Unit) {
    fun launch() = onLaunch()
}

/**
 * **O seletor de anexo/foto padrão da fábrica** (2.332.0). Toda tela que escolhe anexo ou foto usa
 * este — e assim todo lugar abre do mesmo jeito:
 * - **aceita foto** ([AttachmentAccept.acceptsPhotos]) → folha com **Tirar foto · Galeria · Arquivo**;
 * - **não aceita foto** → o seletor de arquivos do sistema abre **direto**, sem folha.
 *
 * ### (a) Só foto — `AttachmentAccept.ImagesOnly`
 * As três origens devolvem [PickedAttachment.Photo] — inclusive "Arquivo", que abre o seletor de
 * arquivos filtrado para imagem e passa o escolhido pela MESMA codificação da galeria: quem grava foto
 * nunca recebe HEIC, bytes crus ou GPS. Arquivo que não é imagem legível → `onError`
 * ([AttachmentPickerError.IMAGE_UNREADABLE]); acima de [maxImageFileBytes] → [AttachmentPickerError.FILE_TOO_LARGE].
 * ```kotlin
 * val fotos = rememberAttachmentPicker(
 *     accept = AttachmentAccept.ImagesOnly,
 *     onPicked = { anexos -> vm.dispatch(Action.FotosEscolhidas(anexos.photos())) },
 *     onError = { motivo -> vm.dispatch(Action.FalhaNoAnexo(motivo)) },
 *     maxDimension = 2048, jpegQuality = 90,      // foto que vai ser examinada (vistoria)
 * )
 * PhotoStrip(…, onAdd = { fotos.launch() })
 * ```
 *
 * ### (b) Foto e arquivo — `AttachmentAccept.AnyFile` (ou `forMimeTypes` com imagem + documento)
 * Câmera/Galeria devolvem [PickedAttachment.Photo] (`fileName` = `foto-AAAAMMDD-HHMMSS.jpg`,
 * `mimeType` = `image/jpeg`); "Arquivo" devolve [PickedAttachment.File], **como está**, vários de
 * uma vez. Para enviar os dois do mesmo jeito: [readForUpload].
 * ```kotlin
 * val anexar = rememberAttachmentPicker(
 *     accept = AttachmentAccept.AnyFile,
 *     onPicked = { anexos -> vm.dispatch(Action.Anexar(anexos)) },
 *     onError = { motivo -> vm.dispatch(Action.FalhaNoAnexo(motivo)) },
 * )
 * // no ViewModel, um por vez:
 * when (val r = anexo.readForUpload(maxBytes = 20L * 1024 * 1024)) {
 *     is FilePickResult.Picked -> api.enviar(r.file.name, r.file.mimeType, r.file.data)
 *     is FilePickResult.TooLarge, is FilePickResult.Failed -> avisar()
 *     FilePickResult.Cancelled -> Unit
 * }
 * ```
 *
 * ### (c) Só documento — `AttachmentAccept.forMimeTypes(listOf("application/pdf"))`
 * Sem folha: `launch()` abre o seletor de arquivos direto; tudo chega como [PickedAttachment.File].
 *
 * ### Contrato
 * - [onPicked] **nunca recebe lista vazia**; desistir (fechar folha, câmera, galeria ou seletor) é
 *   silêncio — nem [onPicked] nem [onError].
 * - [onError] vem **uma vez** por escolha, mesmo que vários itens falhem; o que deu certo é entregue.
 * - [allowMultiple] = `false` → no máximo um item. A câmera dá sempre uma foto.
 * - [selectionLimit] limita a galeria múltipla (o seletor de arquivos do sistema não tem limite).
 *
 * ### Requisitos do app consumidor
 * - **Android:** `<uses-permission android:name="android.permission.CAMERA" />` no manifest do APP
 *   (sem ela o sistema nega na hora e chega [AttachmentPickerError.CAMERA_PERMISSION_DENIED]). O
 *   `FileProvider` vem do `kmplib-platform` — não redeclarar. Galeria (Photo Picker) e arquivos
 *   (Storage Access Framework) não pedem permissão.
 * - **iOS:** `NSCameraUsageDescription` no `Info.plist` (sem ela o app FECHA ao abrir a câmera). A
 *   galeria (`PHPicker`) e os arquivos (`UIDocumentPicker`) rodam fora do processo e não pedem
 *   `NSPhotoLibraryUsageDescription`.
 *
 * @param accept o que o lugar aceita — decide se há folha e o que "Arquivo" devolve.
 * @param maxDimension/jpegQuality o tratamento da foto, como no [rememberImagePickerLauncher].
 * @param maxImageFileBytes teto de leitura do "Arquivo" em [AttachmentAcceptKind.IMAGES_ONLY].
 * @param texts textos da folha; default nos 4 idiomas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAttachmentPicker(
    accept: AttachmentAccept,
    onPicked: (List<PickedAttachment>) -> Unit,
    onError: (AttachmentPickerError) -> Unit,
    allowMultiple: Boolean = true,
    selectionLimit: Int = MULTI_IMAGE_PICKER_DEFAULT_LIMIT,
    maxDimension: Int = PICKED_IMAGE_MAX_DIMENSION,
    jpegQuality: Int = PICKED_IMAGE_JPEG_QUALITY,
    maxImageFileBytes: Long = ATTACHMENT_IMAGE_FILE_MAX_BYTES,
    texts: AttachmentPickerTexts = rememberAttachmentPickerTexts(accept),
): AttachmentPickerLauncher {
    val plan = remember(accept) { attachmentPickerPlan(accept) }
    val entregar by rememberUpdatedState(onPicked)
    val falhar by rememberUpdatedState(onError)
    val tetoArquivo by rememberUpdatedState(maxImageFileBytes)
    val tetoFoto by rememberUpdatedState(maxDimension)
    val qualidade by rememberUpdatedState(jpegQuality)
    val escopo = rememberCoroutineScope()
    var folhaAberta by remember { mutableStateOf(false) }

    fun agora(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    fun entregarFotos(fotos: List<PickedImage>, origem: AttachmentSource) {
        if (fotos.isEmpty()) return
        val hora = agora()
        entregar(fotos.mapIndexed { i, foto -> PickedAttachment.Photo(foto, origem, photoAttachmentFileName(hora, i + 1)) })
    }

    val camera = rememberCameraPhotoLauncher(
        onImagePicked = { entregarFotos(listOf(it), AttachmentSource.CAMERA) },
        onError = { falhar(it.toAttachmentError()) },
        maxDimension = maxDimension,
        jpegQuality = jpegQuality,
    )
    val galeriaUma = rememberImagePickerLauncher(
        source = ImagePickerSource.GALLERY_ONLY,
        onImagePicked = { entregarFotos(listOf(it), AttachmentSource.GALLERY) },
        onError = { falhar(it.toAttachmentError()) },
        maxDimension = maxDimension,
        jpegQuality = jpegQuality,
    )
    val galeriaVarias = rememberMultiImagePickerLauncher(
        selectionLimit = selectionLimit,
        onImagesPicked = { entregarFotos(it, AttachmentSource.GALLERY) },
        onError = { falhar(it.toAttachmentError()) },
        maxDimension = maxDimension,
        jpegQuality = jpegQuality,
    )

    // "Arquivo" que vira foto (IMAGES_ONLY): lê um por vez, com teto, e codifica fora da main.
    fun arquivosComoFotos(arquivos: List<PickedFile>) {
        val teto = tetoArquivo
        val dimensao = tetoFoto
        val q = qualidade
        escopo.launch {
            val hora = agora()
            val fotos = ArrayList<PickedAttachment.Photo>(arquivos.size)
            var primeiroErro: AttachmentPickerError? = null
            arquivos.forEachIndexed { i, arquivo ->
                val erro: AttachmentPickerError? = try {
                    when (val lido = arquivo.read(teto)) {
                        is FilePickResult.Picked -> {
                            val foto = withContext(Dispatchers.Default) {
                                encodePickedImageFromBytes(lido.file.data, dimensao, q)
                            }
                            if (foto == null) {
                                AttachmentPickerError.IMAGE_UNREADABLE
                            } else {
                                val nome = jpegFileNameFor(arquivo.name, photoAttachmentFileName(hora, i + 1))
                                fotos += PickedAttachment.Photo(foto, AttachmentSource.FILE, nome)
                                null
                            }
                        }
                        else -> lido.toAttachmentError()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Arquivo de imagem não pôde ser tratado: ${e::class.simpleName}")
                    AttachmentPickerError.IMAGE_UNREADABLE
                }
                if (erro != null && primeiroErro == null) primeiroErro = erro
            }
            if (fotos.isNotEmpty()) entregar(fotos)
            primeiroErro?.let(falhar)
        }
    }

    val arquivos = rememberMultiFilePicker(
        mimeTypes = plan.fileMimeTypes,
        allowMultiple = allowMultiple,
    ) { escolhidos ->
        // Lista vazia = fechou o seletor: silêncio.
        if (escolhidos.isEmpty()) return@rememberMultiFilePicker
        if (plan.fileDeliversPhoto) arquivosComoFotos(escolhidos) else entregar(escolhidos.map { PickedAttachment.File(it) })
    }

    fun abrir(origem: AttachmentSource) {
        when (origem) {
            AttachmentSource.CAMERA -> camera.launch()
            AttachmentSource.GALLERY -> if (allowMultiple) galeriaVarias.launch() else galeriaUma.launch()
            AttachmentSource.FILE -> arquivos()
        }
    }

    if (folhaAberta) {
        AppModalBottomSheet(
            onDismissRequest = { folhaAberta = false },
            modifier = Modifier.testTag(AttachmentPickerTestTags.FOLHA),
        ) {
            AttachmentSourceSheetContent(
                title = texts.title,
                options = plan.options,
                texts = texts,
                onSelect = { origem ->
                    folhaAberta = false
                    abrir(origem)
                },
            )
        }
    }

    return remember(plan, allowMultiple, camera, galeriaUma, galeriaVarias, arquivos) {
        AttachmentPickerLauncher {
            if (plan.showsSheet) folhaAberta = true else abrir(AttachmentSource.FILE)
        }
    }
}

@Composable
private fun AttachmentSourceSheetContent(
    title: String,
    options: List<AttachmentSource>,
    texts: AttachmentPickerTexts,
    onSelect: (AttachmentSource) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { heading() }
                .testTag(AttachmentPickerTestTags.TITULO),
        )
        options.forEach { origem ->
            val (rotulo, icone) = when (origem) {
                AttachmentSource.CAMERA -> texts.takePhoto to Icons.Filled.CameraAlt
                AttachmentSource.GALLERY -> texts.gallery to Icons.Filled.PhotoLibrary
                AttachmentSource.FILE -> texts.file to Icons.Filled.AttachFile
            }
            AttachmentSourceRow(rotulo, icone, AttachmentPickerTestTags.of(origem)) { onSelect(origem) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AttachmentSourceRow(label: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        // Decorativo: o rótulo já diz tudo ao leitor de tela.
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
    )
}

/** Só a câmera — permissão, captura e a MESMA codificação do [rememberImagePickerLauncher]. */
@Composable
internal expect fun rememberCameraPhotoLauncher(
    onImagePicked: (PickedImage) -> Unit,
    onError: (ImagePickerError) -> Unit,
    maxDimension: Int,
    jpegQuality: Int,
): ImagePickerLauncher

/** Bytes de imagem (qualquer formato que o sistema leia, HEIC inclusive) → [PickedImage]; `null` = ilegível. */
internal expect fun encodePickedImageFromBytes(bytes: ByteArray, maxDimension: Int, jpegQuality: Int): PickedImage?

private const val TAG = "KmpLibAttachmentPicker"
