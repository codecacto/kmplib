package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.core.util.redactMediaUrlsIn
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    maxDimension: Int,
    jpegQuality: Int,
): MultiImagePickerLauncher {
    val context = LocalContext.current
    val currentMaxDimension by rememberUpdatedState(maxDimension)
    val currentJpegQuality by rememberUpdatedState(jpegQuality)
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
                    decodeImageUri(context, uri, currentMaxDimension, currentJpegQuality).also { if (it == null) falhou = true }
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
 * [encodePickedImage] (2.278.0) com a falha devolvida como `null` em vez de `onError`: quem chama
 * está no meio de um laço e decide o que fazer com o conjunto.
 */
private fun decodeImageUri(context: Context, uri: Uri, maxDimension: Int, jpegQuality: Int): PickedImage? = try {
    encodePickedImage(context, uri, maxDimension, jpegQuality)
} catch (e: Exception) {
    AppLogger.w(TAG, "Foto da seleção múltipla não pôde ser lida: ${redactMediaUrlsIn(e.message)}")
    null
} catch (e: OutOfMemoryError) {
    AppLogger.w(TAG, "Foto da seleção múltipla grande demais para a memória disponível.")
    null
}

private const val TAG = "KmpLibMultiImagePicker"
