package br.com.codecacto.kmplib.inspection.capture

import br.com.codecacto.kmplib.core.crypto.sha256Hex
import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.core.util.newUuidV7
import br.com.codecacto.kmplib.inspection.model.InspectionMediaKind
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.ui.components.PickedImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Uma posição de GPS (graus decimais, precisão em metros). */
data class InspectionGeo(val latitude: Double, val longitude: Double, val accuracyMeters: Float? = null) {
    /** Coordenada é dado pessoal (onde a pessoa estava): fora de log. */
    override fun toString(): String = "InspectionGeo(…)"
}

/**
 * Uma mídia **já gravada no disco** do aparelho, com a prova calculada (K02): o SHA-256 dos bytes
 * GRAVADOS, a hora do aparelho, o modelo e o GPS (best-effort). É o que o kit devolve ao produto —
 * nunca uma captura que ainda não está no disco.
 *
 * @property blobId a chave no [BlobStore] onde os bytes estão (a fila de envio lê dali).
 * @property origin [InspectionMediaSource.CAMERA] = prova; [InspectionMediaSource.GALLERY] = anexo.
 */
data class CapturedMedia(
    val id: String,
    val kind: InspectionMediaKind,
    val sha256: String,
    val capturedAtDeviceMillis: Long,
    val location: InspectionGeo?,
    val deviceModel: String,
    val origin: InspectionMediaSource,
    val blobId: String,
    val sizeBytes: Long,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val mimeType: String = "image/jpeg",
) {
    /** Sem coordenada no log (o hash e o id bastam para rastrear). */
    override fun toString(): String =
        "CapturedMedia(id=$id, kind=$kind, origin=$origin, sha256=$sha256, size=$sizeBytes, gps=${location != null})"
}

/** Por que a mídia não foi guardada. A captura NÃO deve ser descartada pela tela nesses casos. */
enum class InspectionCaptureError {
    /** Sem espaço ou escrita recusada pelo sistema — "Pouco espaço" + "Liberar espaço". */
    STORAGE,

    /** Conteúdo vazio (a câmera devolveu nada). */
    EMPTY,
}

sealed interface InspectionCaptureResult {
    data class Saved(val media: CapturedMedia) : InspectionCaptureResult
    data class Failed(val error: InspectionCaptureError) : InspectionCaptureResult
}

/**
 * **Grava a captura ANTES de devolvê-la** (K02/LK-M10): bytes no [BlobStore] privado (fora do
 * backup, por padrão da lib), SHA-256 dos MESMOS bytes, hora do aparelho, modelo e GPS em paralelo,
 * com teto ([locationTimeoutMillis]) — GPS negado ou lento **não bloqueia** a foto, ela sai "Sem
 * localização". O original nunca é alterado depois disto: carimbo e marcação são da cópia exibida.
 *
 * O id é um UUID v7 (ordena por captura) e a chave do blob, `insp-<id>.jpg`.
 *
 * @param location fornecedor da posição atual (ex.: o `LocationProvider` do `kmplib-location`);
 *   `null` = o produto não coleta GPS.
 */
class InspectionCaptureWriter(
    private val store: BlobStore,
    private val location: (suspend () -> InspectionGeo?)? = null,
    private val locationTimeoutMillis: Long = DEFAULT_LOCATION_TIMEOUT_MILLIS,
    private val clock: () -> Long = { currentTimeMillis() },
    private val deviceModel: () -> String = { inspectionDeviceModel() },
    private val io: CoroutineDispatcher = Dispatchers.Default,
) {
    /** Grava uma foto. */
    suspend fun persist(
        image: PickedImage,
        origin: InspectionMediaSource = InspectionMediaSource.CAMERA,
    ): InspectionCaptureResult = persistBytes(
        bytes = image.bytes,
        kind = InspectionMediaKind.PHOTO,
        origin = origin,
        mimeType = image.mimeType,
        widthPx = image.widthPx,
        heightPx = image.heightPx,
        extension = "jpg",
    )

    /** Grava bytes de qualquer tipo (nota de voz, vídeo pequeno já em memória). */
    suspend fun persistBytes(
        bytes: ByteArray,
        kind: InspectionMediaKind,
        origin: InspectionMediaSource,
        mimeType: String,
        widthPx: Int? = null,
        heightPx: Int? = null,
        extension: String = "bin",
    ): InspectionCaptureResult {
        if (bytes.isEmpty()) return InspectionCaptureResult.Failed(InspectionCaptureError.EMPTY)
        val agora = clock()
        val id = newUuidV7(agora)
        val blobId = "insp-$id.$extension"
        return coroutineScope {
            // GPS só faz sentido para prova; anexo de galeria não ganha a posição de agora.
            val posicao = if (origin == InspectionMediaSource.CAMERA && location != null) {
                async {
                    withTimeoutOrNull(locationTimeoutMillis) { runCatching { location.invoke() }.getOrNull() }
                }
            } else {
                null
            }
            val hash = withContext(io) { sha256Hex(bytes) }
            val gravou = store.write(blobId, bytes)
            if (!gravou) {
                posicao?.cancel()
                return@coroutineScope InspectionCaptureResult.Failed(InspectionCaptureError.STORAGE)
            }
            InspectionCaptureResult.Saved(
                CapturedMedia(
                    id = id,
                    kind = kind,
                    sha256 = hash,
                    capturedAtDeviceMillis = agora,
                    location = posicao?.await(),
                    deviceModel = deviceModel(),
                    origin = origin,
                    blobId = blobId,
                    sizeBytes = bytes.size.toLong(),
                    widthPx = widthPx,
                    heightPx = heightPx,
                    mimeType = mimeType,
                ),
            )
        }
    }

    companion object {
        const val DEFAULT_LOCATION_TIMEOUT_MILLIS: Long = 3_000L
    }
}

/** Fabricante e modelo do aparelho ("samsung SM-A546E", "iPhone15,2") — vai na prova da mídia. */
expect fun inspectionDeviceModel(): String
