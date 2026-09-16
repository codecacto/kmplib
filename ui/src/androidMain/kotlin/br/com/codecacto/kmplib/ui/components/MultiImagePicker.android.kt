package br.com.codecacto.kmplib.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

actual class MultiImagePickerLauncher(
    private val onLaunch: () -> Unit,
) {
    actual fun launch() {
        onLaunch()
    }
}

/**
 * `PickMultipleVisualMedia` — o seletor do sistema, o mesmo que o de uma foto só, com teto.
 *
 * ⚠️ O contrato **precisa ser lembrado com o teto**: `PickMultipleVisualMedia(maxItems)` recusa
 * `maxItems <= 1` com `IllegalArgumentException` no construtor, e um contrato recriado a cada
 * recomposição perde o registro do resultado. Por isso o `remember(teto)` e o `coerceAtLeast(2)`.
 */
@Composable
actual fun rememberMultiImagePickerLauncher(
    selectionLimit: Int,
    onImagesPicked: (List<PickedImage>) -> Unit,
    onError: (ImagePickerError) -> Unit,
): MultiImagePickerLauncher {
    val context = LocalContext.current
    val escopo = rememberCoroutineScope()
    val teto = selectionLimit.coerceAtLeast(2)

    val contrato = remember(teto) { ActivityResultContracts.PickMultipleVisualMedia(teto) }

    val seletor = rememberLauncherForActivityResult(contrato) { uris: List<Uri> ->
        // Lista vazia = fechou a galeria sem escolher. Desistir NÃO é erro.
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        escopo.launch {
            // ⚠️ Fora da main thread, e é o motivo de este seletor ter escopo próprio: decodificar,
            // girar e recomprimir vinte JPEGs na thread de UI congela a tela por segundos — o
            // mesmo trabalho que passa despercebido com UMA foto.
            val (fotos, houveFalha) = withContext(Dispatchers.Default) {
                var falhou = false
                val prontas = uris.mapNotNull { uri ->
                    // Uma imagem ilegível no meio da seleção não pode custar as outras dezenove.
                    decodeImageUri(context, uri).also { if (it == null) falhou = true }
                }
                prontas to falhou
            }

            // O erro vem UMA vez, e depois do que deu certo: vinte avisos iguais empilhados é o
            // mesmo que nenhum.
            if (fotos.isNotEmpty()) onImagesPicked(fotos)
            if (houveFalha) onError(ImagePickerError.IMAGE_UNREADABLE)
        }
    }

    return remember(seletor) {
        MultiImagePickerLauncher {
            seletor.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/**
 * Decodifica, gira pelo EXIF, reduz e recodifica — e mede o resultado.
 *
 * É o mesmo trabalho de `processImageUri`, com um contrato diferente: aqui a falha volta como
 * `null` em vez de chamar um `onError`, porque quem chama está no meio de um laço e decide o que
 * fazer com o conjunto.
 */
private fun decodeImageUri(context: Context, uri: Uri): PickedImage? = try {
    val entrada = context.contentResolver.openInputStream(uri)
    val original = entrada?.use { BitmapFactory.decodeStream(it) }
    if (original == null) {
        AppLogger.w(TAG, "Foto da seleção múltipla não pôde ser decodificada.")
        null
    } else {
        val emPe = girarPeloExif(context, uri, original)
        val reduzida = reduzir(emPe, PICKED_IMAGE_MAX_DIMENSION)
        val saida = ByteArrayOutputStream()
        reduzida.compress(Bitmap.CompressFormat.JPEG, 85, saida)
        // Lidos ANTES do recycle: num bitmap reciclado `width`/`height` não valem mais.
        val largura = reduzida.width
        val altura = reduzida.height
        val bytes = saida.toByteArray()

        if (reduzida != emPe) reduzida.recycle()
        if (emPe != original) emPe.recycle()
        original.recycle()

        PickedImage(bytes = bytes, widthPx = largura, heightPx = altura)
    }
} catch (e: Exception) {
    AppLogger.w(TAG, "Foto da seleção múltipla não pôde ser lida: ${e.message}")
    null
}

private fun girarPeloExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val graus = try {
        val entrada = context.contentResolver.openInputStream(uri) ?: return bitmap
        val exif = entrada.use { ExifInterface(it) }
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }
    if (graus == 0f) return bitmap
    val matriz = Matrix().apply { postRotate(graus) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
}

private fun reduzir(bitmap: Bitmap, maior: Int): Bitmap {
    val (largura, altura) = scaledImageSize(bitmap.width, bitmap.height, maior)
    if (largura == bitmap.width && altura == bitmap.height) return bitmap
    return Bitmap.createScaledBitmap(bitmap, largura, altura, true)
}

private const val TAG = "KmpLibMultiImagePicker"
