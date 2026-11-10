package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import br.com.codecacto.kmplib.platform.KmpLibPlatformInternalApi
import br.com.codecacto.kmplib.platform.desktopChooseFiles
import br.com.codecacto.kmplib.platform.desktopMimeTypeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Desktop (2.353.0): o vídeo vem do seletor nativo de arquivo (sem câmera). [PickedVideo.reference]
 * é o caminho absoluto; a JVM não tem decodificador de vídeo, então duração, medida e capa
 * ([captureFrame]) ficam `null`.
 */
actual class VideoPickerLauncher(private val onLaunch: () -> Unit) {
    actual fun launch() = onLaunch()
}

@OptIn(KmpLibPlatformInternalApi::class)
@Composable
actual fun rememberVideoPickerLauncher(
    source: VideoPickerSource,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
): VideoPickerLauncher {
    val entregar by rememberUpdatedState(onVideoPicked)
    val falhar by rememberUpdatedState(onError)
    return remember {
        VideoPickerLauncher {
            val path = desktopChooseFiles("Selecionar vídeo", listOf("video/*"), multiple = false).firstOrNull()
                ?: return@VideoPickerLauncher
            val tamanho = runCatching { Files.size(path) }.getOrNull()
            if (tamanho == null) {
                falhar(VideoPickerError.UNREADABLE)
            } else {
                entregar(
                    PickedVideo(
                        reference = path.toAbsolutePath().toString(),
                        name = path.fileName.toString(),
                        mimeType = desktopMimeTypeOf(path),
                        sizeBytes = tamanho,
                    ),
                )
            }
        }
    }
}

actual suspend fun PickedVideo.readChunks(
    chunkSize: Int,
    onChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
) {
    readRange(offset = 0L, length = null, chunkSize = chunkSize, onChunk = onChunk)
}

actual suspend fun PickedVideo.readRange(
    offset: Long,
    length: Long?,
    chunkSize: Int,
    onChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
): Long {
    require(offset >= 0) { "offset negativo" }
    require(length == null || length >= 0) { "length negativo" }
    val tamanho = chunkSize.coerceAtLeast(1)
    return withContext(Dispatchers.IO) {
        FileChannel.open(Paths.get(reference), StandardOpenOption.READ).use { ch ->
            ch.position(offset)
            val buf = ByteArray(tamanho)
            var lidos = 0L
            while (length == null || lidos < length) {
                ensureActive()
                val quer = if (length == null) tamanho else minOf(tamanho.toLong(), length - lidos).toInt()
                val n = ch.read(ByteBuffer.wrap(buf, 0, quer))
                if (n < 0) break
                if (n == 0) continue
                onChunk(buf, n)
                lidos += n
            }
            lidos
        }
    }
}

actual suspend fun PickedVideo.captureFrame(atMillis: Long): ByteArray? = null
