package br.com.codecacto.kmplib.platform

/**
 * **Originais da câmera que ficaram no disco** (2.218.0).
 *
 * No Android, o `rememberImagePickerLauncher` pede à câmera que grave o JPEG cru em
 * `cacheDir/photos/camera_*.jpg` (é o caminho que o `FileProvider` da lib expõe). Esse original tem
 * resolução cheia e **EXIF completo** (GPS, horário, modelo) — o app recebe só os bytes recodificados.
 * Desde a 2.217.0 o seletor apaga o original depois de processá-lo, mas sobra o de uma captura cujo
 * processo morreu no meio, e a varredura só rodava **na captura seguinte** — que pode nunca vir.
 *
 * Por isso a varredura roda também:
 * - no **início do app** (`initKmpLibPlatform`/`KmpLib.init`), com a folga de
 *   [DEFAULT_CAMERA_CAPTURE_TTL_MILLIS] — o processo pode estar sendo recriado justamente para
 *   receber a foto de uma captura em curso;
 * - na **limpeza da conta** (logout/exclusão, `SyncAccountDataPurger`), sem folga.
 *
 * iOS não grava temporário (a imagem chega em memória): lá a função não faz nada.
 */

/** Subpasta do `cacheDir` onde a câmera grava o original (coberta pelo `kmplib_file_paths`). */
const val CAMERA_CAPTURE_DIRECTORY: String = "photos"

/** Prefixo dos originais da câmera — só esses são varridos; o resto da pasta não é da câmera. */
const val CAMERA_CAPTURE_FILE_PREFIX: String = "camera_"

/** Folga que protege uma captura ainda aberta (10 min). */
const val DEFAULT_CAMERA_CAPTURE_TTL_MILLIS: Long = 10L * 60L * 1000L

/**
 * Decide se um arquivo da pasta da câmera é um original a apagar.
 *
 * @param olderThanMillis `0` (ou negativo) apaga todo original, de qualquer idade.
 */
fun shouldPurgeCameraCapture(
    fileName: String,
    lastModifiedMillis: Long,
    nowMillis: Long,
    olderThanMillis: Long,
): Boolean {
    if (!fileName.startsWith(CAMERA_CAPTURE_FILE_PREFIX)) return false
    if (olderThanMillis <= 0L) return true
    return nowMillis - lastModifiedMillis >= olderThanMillis
}

/**
 * Apaga os originais da câmera com mais de [olderThanMillis]. Devolve quantos saíram. Nunca lança;
 * sem `Context` registrado (lib não inicializada) devolve `0`.
 */
expect fun clearCameraCaptureFiles(olderThanMillis: Long = DEFAULT_CAMERA_CAPTURE_TTL_MILLIS): Int
