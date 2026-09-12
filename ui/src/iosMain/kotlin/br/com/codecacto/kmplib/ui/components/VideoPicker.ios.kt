@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.*
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.abs
import kotlin.math.roundToLong

actual class VideoPickerLauncher(
    private val onLaunch: () -> Unit,
) {
    actual fun launch() {
        onLaunch()
    }
}

/**
 * O delegate em uso, preso a uma referência **forte** de módulo.
 *
 * ⚠️ `PHPickerViewController.delegate` e `UIImagePickerController.delegate` são **weak** — é assim
 * em todo UIKit. Um delegate criado dentro do bloco de lançamento é liberado assim que o bloco sai
 * de escopo, e o seletor abre, a pessoa escolhe o vídeo e **nada acontece**, sem erro nenhum. Só um
 * seletor fica aberto por vez, então uma referência basta.
 */
private var delegateEmUso: NSObject? = null

@Composable
actual fun rememberVideoPickerLauncher(
    source: VideoPickerSource,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
): VideoPickerLauncher = remember(source, onVideoPicked, onError) {
    VideoPickerLauncher {
        val raiz = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (raiz == null) {
            AppLogger.w(TAG, "Sem rootViewController: seletor de vídeo não abriu.")
            onError(VideoPickerError.UNREADABLE)
            return@VideoPickerLauncher
        }
        val temCamera = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )
        when {
            source == VideoPickerSource.GALLERY_ONLY || !temCamera -> abrirGaleria(raiz, onVideoPicked, onError)
            else -> escolherOrigem(raiz, onVideoPicked, onError)
        }
    }
}

private fun escolherOrigem(
    raiz: UIViewController,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
) {
    val folha = UIAlertController.alertControllerWithTitle(
        title = "Adicionar vídeo",
        message = null,
        preferredStyle = UIAlertControllerStyleActionSheet,
    )
    folha.addAction(
        UIAlertAction.actionWithTitle(title = "Gravar vídeo", style = UIAlertActionStyleDefault) {
            abrirCamera(raiz, onVideoPicked, onError)
        },
    )
    folha.addAction(
        UIAlertAction.actionWithTitle(title = "Escolher da galeria", style = UIAlertActionStyleDefault) {
            abrirGaleria(raiz, onVideoPicked, onError)
        },
    )
    folha.addAction(
        UIAlertAction.actionWithTitle(title = "Cancelar", style = UIAlertActionStyleCancel, handler = null),
    )
    raiz.presentViewController(folha, animated = true, completion = null)
}

/**
 * O seletor de fotos do sistema (`PHPickerViewController`) — **sem pedir permissão**: ele roda fora
 * do processo do app, e por isso não exige `NSPhotoLibraryUsageDescription`.
 *
 * ⚠️ **`loadFileRepresentation`, não `loadDataRepresentation`.** O segundo entrega um `NSData` com o
 * arquivo inteiro na memória — 100 MB de uma vez, que é justamente o que esta API existe para não
 * fazer. O primeiro entrega um **arquivo temporário**, que copiamos para a nossa pasta antes de o
 * bloco retornar (o sistema apaga o dele em seguida).
 */
private fun abrirGaleria(
    raiz: UIViewController,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
) {
    val configuracao = PHPickerConfiguration().apply {
        selectionLimit = 1
        filter = PHPickerFilter.videosFilter
    }
    val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)
            // Lista vazia = fechou sem escolher. Desistir NÃO é erro.
            val escolhido = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
            val provedor = escolhido.itemProvider ?: return onError(VideoPickerError.UNREADABLE)
            val tipo = when {
                provedor.hasItemConformingToTypeIdentifier("public.movie") -> "public.movie"
                provedor.hasItemConformingToTypeIdentifier("public.mpeg-4") -> "public.mpeg-4"
                else -> return onError(VideoPickerError.UNREADABLE)
            }
            provedor.loadFileRepresentationForTypeIdentifier(tipo) { url, erro ->
                if (erro != null || url == null) {
                    AppLogger.w(TAG, "Vídeo da galeria não pôde ser lido: ${erro?.localizedDescription}")
                    onError(VideoPickerError.UNREADABLE)
                    return@loadFileRepresentationForTypeIdentifier
                }
                val nome = provedor.suggestedName?.let { nomeComExtensao(it, url) } ?: (url.lastPathComponent ?: "video.mov")
                entregar(url, nome, onVideoPicked, onError)
            }
        }
    }
    delegateEmUso = delegate
    val seletor = PHPickerViewController(configuration = configuracao)
    seletor.delegate = delegate
    raiz.presentViewController(seletor, animated = true, completion = null)
}

private fun abrirCamera(
    raiz: UIViewController,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
) {
    val delegate = object :
        NSObject(),
        UIImagePickerControllerDelegateProtocol,
        UINavigationControllerDelegateProtocol {
        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>,
        ) {
            picker.dismissViewControllerAnimated(true, null)
            val url = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
                ?: return onError(VideoPickerError.UNREADABLE)
            entregar(url, url.lastPathComponent ?: "video.mov", onVideoPicked, onError)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, null)
        }
    }
    delegateEmUso = delegate
    val camera = UIImagePickerController().apply {
        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        mediaTypes = listOf("public.movie")
        this.delegate = delegate
    }
    raiz.presentViewController(camera, animated = true, completion = null)
}

/** Copia para a nossa pasta temporária, lê o cabeçalho e devolve a referência. */
private fun entregar(
    origem: NSURL,
    nome: String,
    onVideoPicked: (PickedVideo) -> Unit,
    onError: (VideoPickerError) -> Unit,
) {
    val caminho = copiarParaTemporario(origem, nome)
    if (caminho == null) {
        onError(VideoPickerError.UNREADABLE)
        return
    }
    val metadados = lerMetadados(caminho)
    onVideoPicked(
        PickedVideo(
            reference = caminho,
            name = nome,
            mimeType = mimeDe(nome),
            sizeBytes = tamanhoDe(caminho),
            durationMillis = metadados.duracaoMillis,
            widthPx = metadados.largura,
            heightPx = metadados.altura,
        ),
    )
}

/**
 * O arquivo que o `PHPickerViewController` entrega vive **só até o bloco retornar** — o sistema o
 * apaga em seguida. A cópia é disco-a-disco (`NSFileManager`), não passa pela memória.
 */
private fun copiarParaTemporario(origem: NSURL, nome: String): String? {
    val gerente = NSFileManager.defaultManager
    val pasta = NSTemporaryDirectory() + PASTA_DE_VIDEOS
    gerente.createDirectoryAtPath(pasta, withIntermediateDirectories = true, attributes = null, error = null)
    val destino = pasta + NSUUID().UUIDString + "-" + nome
    val caminhoDeOrigem = origem.path ?: return null
    gerente.removeItemAtPath(destino, error = null)
    val copiou = gerente.copyItemAtPath(caminhoDeOrigem, toPath = destino, error = null)
    if (!copiou) {
        AppLogger.w(TAG, "Não foi possível copiar o vídeo escolhido para a pasta temporária.")
        return null
    }
    return destino
}

private class MetadadosDeVideo(val duracaoMillis: Long?, val largura: Int?, val altura: Int?)

/**
 * Duração e medida, pelo `AVURLAsset`.
 *
 * ⚠️ A **matriz de transformação** é aplicada: vídeo gravado em pé chega com `naturalSize` de
 * deitado mais um `preferredTransform` de 90°, e quem lê a medida crua conclui que todo vídeo de
 * iPhone é horizontal.
 *
 * As propriedades síncronas (`duration`, `tracksWithMediaType`) estão depreciadas em favor do
 * carregamento assíncrono a partir do iOS 16 — que o Kotlin/Native não expõe de forma utilizável
 * (as APIs `load(_:)` são Swift concurrency). São seguras aqui: o arquivo é **local**, já está no
 * disco, e não há nada a buscar em rede. Mesmo caminho que o `VideoPlayerState.ios` já usa para as
 * faixas de legenda.
 */
@Suppress("DEPRECATION")
private fun lerMetadados(caminho: String): MetadadosDeVideo = try {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(caminho), options = null)
    val segundos = CMTimeGetSeconds(asset.duration)
    val duracao = if (segundos.isNaN() || segundos <= 0.0) null else (segundos * 1000.0).roundToLong()

    val faixa = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
    if (faixa == null) {
        MetadadosDeVideo(duracao, null, null)
    } else {
        val medida = faixa.naturalSize.useContents { width to height }
        // Em 90°/270° a matriz tem `a == 0` e `|b| == 1`; é o caso do vídeo gravado em pé.
        val deitadoNoArquivo = faixa.preferredTransform.useContents { a == 0.0 && abs(b) == 1.0 }
        val largura = medida.first.toInt()
        val altura = medida.second.toInt()
        MetadadosDeVideo(
            duracaoMillis = duracao,
            largura = if (deitadoNoArquivo) altura else largura,
            altura = if (deitadoNoArquivo) largura else altura,
        )
    }
} catch (e: Exception) {
    AppLogger.w(TAG, "Metadados do vídeo não lidos: ${e.message}")
    MetadadosDeVideo(null, null, null)
}

private fun tamanhoDe(caminho: String): Long {
    val atributos = NSFileManager.defaultManager.attributesOfItemAtPath(caminho, error = null)
    return (atributos?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
}

private fun mimeDe(nome: String): String = when {
    nome.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
    nome.endsWith(".m4v", ignoreCase = true) -> "video/mp4"
    else -> "video/quicktime"
}

private fun nomeComExtensao(sugerido: String, url: NSURL): String {
    val extensao = url.pathExtension
    if (extensao.isNullOrBlank()) return sugerido
    return if (sugerido.endsWith(".$extensao", ignoreCase = true)) sugerido else "$sugerido.$extensao"
}

actual suspend fun PickedVideo.readChunks(
    chunkSize: Int,
    onChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
) {
    val arquivo = NSFileHandle.fileHandleForReadingAtPath(reference)
        ?: error("Vídeo indisponível: $reference")
    try {
        val pedaco = chunkSize.coerceAtLeast(1).toULong()
        while (true) {
            val dados = arquivo.readDataOfLength(pedaco)
            val lidos = dados.length.toInt()
            if (lidos <= 0) break
            onChunk(dados.paraByteArray(), lidos)
        }
    } finally {
        arquivo.closeFile()
    }
}

private fun NSData.paraByteArray(): ByteArray {
    val tamanho = length.toInt()
    val destino = ByteArray(tamanho)
    if (tamanho > 0) {
        destino.usePinned { fixado -> memcpy(fixado.addressOf(0), this.bytes, this.length) }
    }
    return destino
}

private const val PASTA_DE_VIDEOS = "kmplib_videos/"
private const val TAG = "KmpLibVideoPicker"
