package br.com.codecacto.kmplib.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

actual class VideoPickerLauncher(
    private val onLaunch: () -> Unit,
) {
    actual fun launch() {
        onLaunch()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun rememberVideoPickerLauncher(
    source: VideoPickerSource,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
): VideoPickerLauncher {
    val context = LocalContext.current
    var mostrarEscolha by remember { mutableStateOf(false) }
    var uriDaCaptura by remember { mutableStateOf<Uri?>(null) }

    // `PickVisualMedia` é o seletor de fotos do sistema: NÃO pede permissão de armazenamento (o
    // processo escolhido roda fora do app) e é o que o Android indica desde a API 33.
    val galeria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        // `null` = fechou sem escolher. Desistir NÃO é erro.
        uri?.let { entregar(context, it, onVideoPicked, onError) }
    }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { gravou: Boolean ->
        if (gravou) uriDaCaptura?.let { entregar(context, it, onVideoPicked, onError) }
    }

    fun abrirGaleria() {
        galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    fun abrirCamera() {
        try {
            val pasta = File(context.cacheDir, "videos").apply { mkdirs() }
            val arquivo = File(pasta, "video_${System.currentTimeMillis()}.mp4")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                arquivo,
            )
            uriDaCaptura = uri
            camera.launch(uri)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Câmera de vídeo indisponível: ${e.message}")
            onError(VideoPickerError.CAMERA_UNAVAILABLE)
        }
    }

    val permissaoDaCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedida: Boolean ->
        // Sem `else`, negar a permissão fazia o botão "não fazer nada" — o defeito que o
        // `ImagePickerError` já tinha corrigido no seletor de foto (2.131.0).
        if (concedida) abrirCamera() else onError(VideoPickerError.CAMERA_PERMISSION_DENIED)
    }

    if (mostrarEscolha) {
        val estadoDaFolha = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { mostrarEscolha = false },
            sheetState = estadoDaFolha,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Adicionar vídeo",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )

                OpcaoDoSeletor(Icons.Default.Videocam, "Gravar vídeo") {
                    mostrarEscolha = false
                    val temPermissao = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (temPermissao) abrirCamera() else permissaoDaCamera.launch(Manifest.permission.CAMERA)
                }

                OpcaoDoSeletor(Icons.Default.PhotoLibrary, "Escolher da galeria") {
                    mostrarEscolha = false
                    abrirGaleria()
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    return remember(source, galeria, camera) {
        VideoPickerLauncher {
            when (source) {
                // Sem folha de escolha: uma folha com uma opção só é um toque a mais para nada.
                VideoPickerSource.GALLERY_ONLY -> abrirGaleria()
                VideoPickerSource.GALLERY_AND_CAMERA -> mostrarEscolha = true
            }
        }
    }
}

@Composable
private fun OpcaoDoSeletor(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    rotulo: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icone, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(rotulo, fontSize = 16.sp)
        }
    }
}

/**
 * Monta o [PickedVideo] a partir da URI — **sem abrir o arquivo**.
 *
 * O que se lê aqui é o cabeçalho (nome, tamanho, e os metadados do contêiner). Os bytes só saem do
 * disco em [readChunks], na hora do upload.
 */
private fun entregar(
    context: Context,
    uri: Uri,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
) {
    try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "video/mp4"
        var nome: String? = null
        var tamanho = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) nome = cursor.getString(0)
                    if (!cursor.isNull(1)) tamanho = cursor.getLong(1)
                }
            }
        val metadados = lerMetadados(context, uri)
        onVideoPicked(
            PickedVideo(
                reference = uri.toString(),
                name = nome ?: "video_${System.currentTimeMillis()}.${mime.substringAfter('/', "mp4")}",
                mimeType = mime,
                sizeBytes = tamanho,
                durationMillis = metadados.duracaoMillis,
                widthPx = metadados.largura,
                heightPx = metadados.altura,
            ),
        )
    } catch (e: Exception) {
        AppLogger.w(TAG, "Vídeo escolhido não pôde ser lido: ${e.message}")
        onError(VideoPickerError.UNREADABLE)
    }
}

private class MetadadosDeVideo(val duracaoMillis: Long?, val largura: Int?, val altura: Int?)

/**
 * Duração e medida, do **cabeçalho** do arquivo (`MediaMetadataRetriever`).
 *
 * ⚠️ A rotação é aplicada aqui. Vídeo de celular gravado em pé costuma vir com
 * `naturalSize` de deitado **mais** `METADATA_KEY_VIDEO_ROTATION = 90`: quem lê largura e altura
 * cruas conclui que todo vídeo de celular é horizontal, e a moldura sai errada na tela.
 *
 * Falha aqui **não** derruba a escolha: `null` nos três, e o app segue sem a validação de duração.
 */
private fun lerMetadados(context: Context, uri: Uri): MetadadosDeVideo {
    val leitor = MediaMetadataRetriever()
    return try {
        leitor.setDataSource(context, uri)
        val duracao = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val largura = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val altura = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        val rotacao = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val deitadoNoArquivo = rotacao == 90 || rotacao == 270
        MetadadosDeVideo(
            duracaoMillis = duracao,
            largura = if (deitadoNoArquivo) altura else largura,
            altura = if (deitadoNoArquivo) largura else altura,
        )
    } catch (e: Exception) {
        AppLogger.w(TAG, "Metadados do vídeo não lidos: ${e.message}")
        MetadadosDeVideo(null, null, null)
    } finally {
        runCatching { leitor.release() }
    }
}

actual suspend fun PickedVideo.readChunks(
    chunkSize: Int,
    onChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
) {
    val context = AndroidAppContext.get()
        ?: error("kmplib-ui: chame KmpLib.init(context) (ou initKmpLibCore) antes de ler o vídeo.")
    val uri = Uri.parse(reference)
    withContext(Dispatchers.IO) {
        val entrada = context.contentResolver.openInputStream(uri)
            ?: error("Vídeo indisponível: $reference")
        entrada.use { fluxo ->
            val buffer = ByteArray(chunkSize.coerceAtLeast(1))
            while (true) {
                val lidos = fluxo.read(buffer)
                if (lidos <= 0) break
                onChunk(buffer, lidos)
            }
        }
    }
}

/**
 * O quadro da capa, via `MediaMetadataRetriever`.
 *
 * ⚠️ **A rotação é conferida, não presumida.** O `getFrameAtTime` devolve o quadro **já girado** na
 * maioria dos aparelhos — mas não em todos, e um `postRotate` cego giraria duas vezes o que já veio
 * certo. A comparação com a medida crua do cabeçalho diz qual dos dois casos é este: se o quadro
 * saiu com a mesma medida do arquivo (sem troca de lados) num vídeo marcado como 90°/270°, então
 * ninguém girou nada e a matriz é nossa.
 */
actual suspend fun PickedVideo.captureFrame(atMillis: Long): ByteArray? {
    val context = AndroidAppContext.get() ?: run {
        AppLogger.w(TAG, "Sem contexto: chame KmpLib.init(context) antes de extrair a capa.")
        return null
    }
    return withContext(Dispatchers.IO) {
        val leitor = MediaMetadataRetriever()
        try {
            leitor.setDataSource(context, Uri.parse(reference))
            val quadro = leitor.getFrameAtTime(
                atMillis.coerceAtLeast(0) * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: leitor.frameAtTime
            if (quadro == null) {
                AppLogger.w(TAG, "Nenhum quadro extraído do vídeo para a capa.")
                return@withContext null
            }

            val rotacao = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val larguraCrua = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val alturaCrua = leitor.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val deitadoNoArquivo = rotacao == 90 || rotacao == 270
            val aindaNaoGirado = deitadoNoArquivo &&
                larguraCrua != null && alturaCrua != null &&
                quadro.width == larguraCrua && quadro.height == alturaCrua

            val emPe = if (aindaNaoGirado) {
                val matriz = Matrix().apply { postRotate(rotacao.toFloat()) }
                Bitmap.createBitmap(quadro, 0, 0, quadro.width, quadro.height, matriz, true)
            } else {
                quadro
            }

            val saida = ByteArrayOutputStream()
            emPe.compress(Bitmap.CompressFormat.JPEG, 85, saida)
            if (emPe != quadro) emPe.recycle()
            quadro.recycle()
            saida.toByteArray()
        } catch (e: Exception) {
            // Capa é acessório: falhar aqui não pode derrubar a publicação do vídeo.
            AppLogger.w(TAG, "Capa não extraída do vídeo: ${e.message}")
            null
        } finally {
            runCatching { leitor.release() }
        }
    }
}

private const val TAG = "KmpLibVideoPicker"
