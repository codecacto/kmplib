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

internal expect fun deleteOriginalPhotoFile(path: String): Boolean

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
