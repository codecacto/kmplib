package br.com.codecacto.kmplib.platform.photos

import androidx.compose.runtime.Composable

/**
 * Pasta temporária das fotos ORIGINAIS escolhidas em [rememberOriginalPhotoPicker] (2.327.0): dentro do
 * `cacheDir` no Android e do `NSTemporaryDirectory()` no iOS. A foto é temporária por contrato — o app
 * a envia (a `DirectUploadOutbox` a MOVE para a área dela) ou chama [OriginalPhoto.deleteFile]; o
 * `clearKmpLibTemporaryFiles` varre o que sobrou de processo morto.
 */
const val ORIGINAL_PHOTO_TEMP_DIRECTORY: String = "kmplib_original_photos"

/**
 * Uma foto escolhida na fototeca, **byte a byte como está no aparelho** (2.327.0): sem recompressão,
 * sem redimensionar, sem trocar de formato (HEIC continua HEIC) e com o EXIF (câmera, lente,
 * exposição, data) intacto — o que uma venda de foto, uma prova ou um laudo precisam. Já está COPIADA
 * para um arquivo do app em [path]; a referência da fototeca não é guardada.
 *
 * ⚠️ **Android:** o Photo Picker do sistema entrega o arquivo da fototeca, mas a política de
 * privacidade dele **remove a localização (GPS) do EXIF** — o resto dos metadados e os pixels vêm
 * como estão. Não há caminho oficial para o GPS pelo seletor (exigiria `READ_MEDIA_IMAGES` +
 * `ACCESS_MEDIA_LOCATION`, que a Play restringe). No iOS, com `PHPicker` em representação atual, o
 * arquivo vem inteiro.
 *
 * @property path caminho ABSOLUTO do arquivo copiado (pasta [ORIGINAL_PHOTO_TEMP_DIRECTORY]).
 * @property fileName nome sugerido pela fototeca, com a extensão do formato real (`IMG_0042.HEIC`).
 *   Pode ser dado pessoal — não sai em [toString].
 * @property mimeType tipo do arquivo (`image/heic`, `image/jpeg`, `image/png`…).
 * @property sizeBytes tamanho do arquivo copiado.
 */
class OriginalPhoto(
    val path: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
) {
    /** Apaga o arquivo copiado. Não chame depois de entregá-lo a uma fila que o MOVE. Idempotente. */
    fun deleteFile(): Boolean = deleteOriginalPhotoFile(path)

    override fun toString(): String = "OriginalPhoto(mimeType=$mimeType, sizeBytes=$sizeBytes)"
}

/**
 * Resultado de uma escolha em [rememberOriginalPhotoPicker].
 *
 * @property photos as fotos, na ordem escolhida. Vazia com [failedCount] `0` = a pessoa fechou o
 *   seletor (silêncio, não é erro).
 * @property failedCount quantas fotos escolhidas não puderam ser copiadas (iCloud sem rede, disco
 *   cheio, arquivo sumiu) — as outras vêm assim mesmo.
 */
data class OriginalPhotoPickResult(
    val photos: List<OriginalPhoto>,
    val failedCount: Int = 0,
) {
    /** `true` = a pessoa desistiu (nada escolhido, nada falhou). */
    val isCancelled: Boolean get() = photos.isEmpty() && failedCount == 0
}

/**
 * Seletor de **fotos ORIGINAIS** da fototeca, uma ou várias (2.327.0), pela forma oficial de cada
 * plataforma — e **sem permissão de galeria**:
 *
 * - **Android:** o **Photo Picker** do sistema (`ActivityResultContracts.PickMultipleVisualMedia`, só
 *   imagens; Android 4.4+ pelo backport do Google Play). O arquivo é copiado do provedor como está.
 * - **iOS:** `PHPickerViewController` com `preferredAssetRepresentationMode = .current` (sem
 *   transcodificar HEIC para JPEG) e `loadFileRepresentation` no tipo REGISTRADO da foto — é o caminho
 *   da Apple para receber o arquivo original. Live Photo vem como a foto (sem o vídeo).
 *
 * A cópia roda fora da thread principal; [onResult] é chamado na principal, uma vez por escolha.
 *
 * ```kotlin
 * val escolher = rememberOriginalPhotoPicker(maxItems = 0) { r ->
 *     if (!r.isCancelled) vm.onAction(Action.Escolhidas(r.photos, r.failedCount))
 * }
 * AppButton("Escolher fotos", onClick = escolher)
 * // no ViewModel: outbox.enqueue(DirectUploadRequest(sourcePath = foto.path, contentType = foto.mimeType, fileName = foto.fileName, …))
 * ```
 *
 * **Só a galeria, sem folha.** A regra da fábrica (foto = todas as origens) é a
 * `rememberOriginalPhotoPicker(sources = OriginalPhotoSource.Standard) { … }` do `kmplib-ui`
 * (2.340.0), que abre a folha padrão **Galeria · Arquivo**; a origem Arquivos sozinha é
 * [rememberOriginalPhotoFilePicker].
 *
 * Compare: `rememberMultiImagePickerLauncher` (`kmplib-ui`) RECODIFICA em JPEG de até 1024 px — serve a
 * avatar e foto de formulário, não a quem vende ou guarda a foto; `rememberMultiFilePicker` abre o
 * seletor de DOCUMENTOS, não a fototeca.
 *
 * @param maxItems quantas no máximo (`0` = o máximo que o sistema permite; `1` = uma só).
 * @return a função que abre o seletor.
 */
@Composable
expect fun rememberOriginalPhotoPicker(
    maxItems: Int = 0,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit

/**
 * De onde a foto ORIGINAL pode vir (2.340.0). A folha padrão da lib (`Galeria · Arquivo`) é a
 * `rememberOriginalPhotoPicker(sources, …)` do `kmplib-ui` (pacote `ui.components`); aqui ficam as duas
 * origens cruas.
 *
 * **Não existe `CAMERA` — de propósito.** O contrato do original é "o arquivo como ele existe, byte a
 * byte" (hash, HEIC, EXIF da câmera do fotógrafo). A câmera do sistema não entrega isso no iOS — o
 * `UIImagePickerController` devolve um `UIImage` decodificado, e gravá-lo é RECODIFICAR (outro JPEG,
 * outro hash, sem o HEIC); o arquivo de verdade exigiria uma câmera própria
 * (`AVCapturePhotoOutput.fileDataRepresentation`), que é outro produto. Foto tirada agora no celular
 * cai na fototeca e sai pela [GALLERY]; foto para formulário/anexo é o `rememberAttachmentPicker`.
 */
enum class OriginalPhotoSource {
    /** A fototeca do aparelho — [rememberOriginalPhotoPicker]. */
    GALLERY,

    /**
     * O gerenciador de arquivos (Arquivos/Drive/cartão/pendrive) — [rememberOriginalPhotoFilePicker]:
     * é por onde o fotógrafo traz o JPG/RAW exportado da câmera, que nunca passou pela fototeca.
     */
    FILE,
    ;

    companion object {
        /** O padrão da fábrica para foto original: **Galeria · Arquivo**, nesta ordem. */
        val Standard: List<OriginalPhotoSource> = listOf(GALLERY, FILE)
    }
}

/**
 * A origem **Arquivos** das fotos ORIGINAIS (2.340.0): o seletor de documentos do sistema filtrado
 * para imagem, com a MESMA entrega de [rememberOriginalPhotoPicker] — cada arquivo COPIADO por stream
 * (nunca inteiro na memória) para [ORIGINAL_PHOTO_TEMP_DIRECTORY] e devolvido como [OriginalPhoto],
 * pronto para a `DirectUploadOutbox`. Bytes, formato (RAW/HEIC/JPEG) e EXIF como estão — inclusive o
 * GPS, que só o Photo Picker do Android remove.
 *
 * - **Android:** Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `OpenMultipleDocuments`/`OpenDocument`)
 *   com `image/` + `*`; sem permissão. Arquivo que o provedor diz não ser imagem (PDF…) conta em
 *   [OriginalPhotoPickResult.failedCount]; RAW sem tipo declarado é reconhecido pela extensão.
 * - **iOS:** `UIDocumentPickerViewController(forOpeningContentTypes: [.image], asCopy: true)`, escopo
 *   de segurança aberto durante a cópia, cópia para `NSTemporaryDirectory()` fora da thread principal.
 *
 * O sistema não limita a quantidade no seletor de arquivos: com [maxItems] > 0 a lib entrega as
 * primeiras [maxItems] e descarta o resto (sem contar como falha). [onResult] na thread principal.
 *
 * Quase sempre quem chama é a folha padrão do `kmplib-ui` (`rememberOriginalPhotoPicker(sources, …)`);
 * use esta direto só quando a tela já tem um botão "Arquivos" próprio.
 */
@Composable
expect fun rememberOriginalPhotoFilePicker(
    maxItems: Int = 0,
    onResult: (OriginalPhotoPickResult) -> Unit,
): () -> Unit

internal expect fun deleteOriginalPhotoFile(path: String): Boolean

/**
 * O tipo de imagem de um arquivo escolhido (2.340.0), na ordem: o [reported] pelo sistema quando é
 * `image/…`; senão pela extensão do [fileName] (RAW costuma chegar como `application/octet-stream`);
 * senão [fallback]. Tipo declarado que NÃO é imagem nem genérico (`application/pdf`) = `null` — não é foto.
 */
fun resolveOriginalPhotoMimeType(reported: String?, fileName: String?, fallback: String? = null): String? {
    val tipo = reported?.lowercase()?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
    if (tipo != null && tipo.startsWith("image/")) return tipo
    val pelaExtensao = fileName?.let(::imageMimeTypeForFileName)
    if (pelaExtensao != null) return pelaExtensao
    if (tipo != null && tipo !in GENERIC_MIME_TYPES) return null
    return fallback
}

private val GENERIC_MIME_TYPES = setOf("application/octet-stream", "binary/octet-stream", "application/unknown")

/** Extensão de arquivo do [mimeType] de imagem (`image/heic` → `heic`); `null` se desconhecido. */
fun imageExtensionForMimeType(mimeType: String?): String? = when (mimeType?.lowercase()?.substringBefore(';')?.trim()) {
    "image/jpeg", "image/jpg", "image/pjpeg" -> "jpg"
    "image/heic", "image/heic-sequence" -> "heic"
    "image/heif", "image/heif-sequence" -> "heif"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/tiff" -> "tiff"
    "image/avif" -> "avif"
    "image/bmp" -> "bmp"
    "image/x-adobe-dng", "image/dng" -> "dng"
    "image/x-canon-cr2" -> "cr2"
    "image/x-canon-cr3" -> "cr3"
    "image/x-nikon-nef" -> "nef"
    "image/x-sony-arw" -> "arw"
    "image/x-fuji-raf" -> "raf"
    "image/x-olympus-orf" -> "orf"
    "image/x-panasonic-rw2" -> "rw2"
    else -> null
}

/** Tipo MIME pela extensão do [fileName] (`foto.HEIC` → `image/heic`); `null` se desconhecida. */
fun imageMimeTypeForFileName(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "jpe" -> "image/jpeg"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "tif", "tiff" -> "image/tiff"
    "avif" -> "image/avif"
    "bmp" -> "image/bmp"
    "dng" -> "image/x-adobe-dng"
    // RAW das câmeras (o fotógrafo exporta pelo gerenciador de arquivos) — tipos do freedesktop shared-mime-info.
    "cr2" -> "image/x-canon-cr2"
    "cr3" -> "image/x-canon-cr3"
    "nef" -> "image/x-nikon-nef"
    "arw" -> "image/x-sony-arw"
    "raf" -> "image/x-fuji-raf"
    "orf" -> "image/x-olympus-orf"
    "rw2" -> "image/x-panasonic-rw2"
    else -> null
}

/**
 * Nome final da foto copiada: o [suggested] da fototeca, sem caminho nem caractere que quebre um nome de
 * arquivo, com a extensão do formato real ([extension]) quando ele não a traz. Em branco = `foto`.
 */
fun originalPhotoFileName(suggested: String?, extension: String?): String {
    val base = suggested.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\u0000-\\u001F\"*:<>?|]"), "_").trim().trim('.')
        .ifBlank { "foto" }
    val ext = extension?.trim()?.trimStart('.')?.takeIf { it.isNotEmpty() } ?: return base
    return if (base.substringAfterLast('.', "").equals(ext, ignoreCase = true) ||
        (ext.equals("jpg", true) && base.substringAfterLast('.', "").equals("jpeg", true))
    ) base else "$base.$ext"
}
