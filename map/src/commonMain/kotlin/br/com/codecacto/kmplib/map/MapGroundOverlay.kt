package br.com.codecacto.kmplib.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.LayoutDirection
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ---------------------------------------------------------------------------------------------
// Imagem georreferenciada (ground overlay) e folga do mapa no NativeMap (2.382.0 — pedido do
// CampoRaiz, mapa da chuva). Tudo que é decisão é puro e testado aqui; as plataformas só desenham:
// Android `GroundOverlay` do maps-compose, iOS `GMSGroundOverlay` pela ponte
// ([IosNativeMapGroundOverlays]) ou um renderizador de imagem do MapKit no fallback.
// ---------------------------------------------------------------------------------------------

/**
 * A imagem de um [MapGroundOverlay]: bytes que o app já tem (o PNG guardado em cache, para o modo sem
 * sinal) ou uma URL que a lib baixa. PNG e JPEG; a transparência do PNG é respeitada.
 */
sealed interface MapOverlayImage {

    /**
     * Bytes da imagem (PNG/JPEG). [cacheKey] identifica a imagem (ex.: `"chuva-7d-2026-11-26T06"`):
     * com ele a lib compara e guarda a imagem decodificada pela chave, sem varrer os bytes; sem ele,
     * pelo conteúdo. Trocar de imagem = passar outros bytes (ou outra chave) no mesmo [MapGroundOverlay.id].
     */
    class Bytes(val bytes: ByteArray, val cacheKey: String? = null) : MapOverlayImage {
        private val contentHash: Int by lazy { bytes.contentHashCode() }

        /** Chave interna da imagem (decodificação reaproveitada entre composições e telas). */
        internal val key: String
            get() = cacheKey?.let { "k:$it" } ?: "b:${bytes.size}:$contentHash"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Bytes) return false
            return if (cacheKey != null || other.cacheKey != null) {
                cacheKey == other.cacheKey
            } else {
                bytes.size == other.bytes.size && contentHash == other.contentHash && bytes.contentEquals(other.bytes)
            }
        }

        override fun hashCode(): Int = cacheKey?.hashCode() ?: contentHash

        override fun toString(): String = "MapOverlayImage.Bytes(${bytes.size} bytes, cacheKey=${cacheKey != null})"
    }

    /**
     * Imagem baixada pela lib (GET, cliente próprio do módulo com nova tentativa e gzip da
     * `kmplib-core`). Guardada em memória pela URL inteira: voltar à mesma aba não baixa de novo.
     * [headers] vai na requisição (ex.: `Authorization` de uma rota privada). URL assinada que muda a
     * cada abertura é uma imagem nova — prefira baixar e passar [Bytes] com `cacheKey` estável.
     *
     * O `toString` não leva a query nem os valores dos cabeçalhos (pode haver assinatura ou token).
     */
    class Url(val url: String, val headers: Map<String, String> = emptyMap()) : MapOverlayImage {
        internal val key: String get() = "u:$url"

        override fun equals(other: Any?): Boolean =
            this === other || (other is Url && other.url == url && other.headers == headers)

        override fun hashCode(): Int = 31 * url.hashCode() + headers.hashCode()

        override fun toString(): String =
            "MapOverlayImage.Url(${redactOverlayUrl(url)}, headers=${headers.keys})"
    }
}

/** A URL sem query nem fragmento (para log e `toString`). */
internal fun redactOverlayUrl(url: String): String = url.substringBefore('#').substringBefore('?')

/**
 * Imagem presa a um retângulo geográfico no [NativeMap] (2.382.0) — o mapa de chuva acumulada, um
 * mapa de calor, uma planta sobre o terreno. Acompanha zoom e arraste como o próprio mapa.
 *
 * - **Projeção:** a imagem é esticada em linha reta entre [LatLngBounds.southwest] e
 *   [LatLngBounds.northeast] no plano do mapa (Web Mercator, EPSG:3857) — o que o Google Maps SDK faz
 *   nas duas plataformas. Gere a imagem nessa projeção; uma grade "lat/lng reta" (equirretangular)
 *   desalinha alguns pixels nas bordas norte/sul de áreas grandes.
 * - **Ordem:** todos os overlays ficam **por baixo** de polígonos, círculos, rotas e pinos (talhão e
 *   pino da fazenda leem por cima da chuva). Entre eles, maior [zIndex] por cima; empate = o que vem
 *   depois na lista.
 * - **Atualizar sem recriar:** mudar [image], [opacity], [bounds], [zIndex] ou [visible] de um [id]
 *   atualiza só aquele overlay, no lugar — o mapa não é refeito. Enquanto a imagem NOVA carrega, a
 *   anterior continua (sem piscar); se a nova falhar, o overlay some (nunca fica a imagem antiga
 *   como se fosse a nova) e o motivo vai ao log, sem a URL inteira.
 * - **Enquadramento:** overlays **não** entram no `fitOnFirstLoad` (a chuva cobre a região inteira e
 *   arrancaria a câmera da fazenda) — enquadre com o `MapController` se quiser.
 * - **Toque:** o overlay não é tocável; o toque passa para o `onMapClick` do [NativeMap], que devolve
 *   a coordenada — é assim que o app mostra o valor exato do ponto.
 *
 * @param bounds sul-oeste → nordeste. Inválido (coordenada fora da faixa, sul ≥ norte, oeste ≥ leste —
 *   não cruza o antimeridiano) = não desenha.
 * @param opacity 0 (invisível) a 1 (opaca); fora da faixa é limitada, `NaN` vira 1.
 * @param visible `false` esconde sem descartar a imagem (voltar a `true` é instantâneo).
 */
@Immutable
data class MapGroundOverlay(
    val id: String,
    val image: MapOverlayImage,
    val bounds: LatLngBounds,
    val opacity: Float = 1f,
    val zIndex: Float = 0f,
    val visible: Boolean = true,
)

/** Overlay validado, com a opacidade limitada e o `zIndex` inteiro já decidido para o SDK. */
@Immutable
internal data class ResolvedGroundOverlay(
    val id: String,
    val image: MapOverlayImage,
    val imageKey: String,
    val bounds: LatLngBounds,
    val opacity: Float,
    val visible: Boolean,
    /** `zIndex` no SDK: abaixo do polígono mais baixo (`-polygonCount-1`). */
    val sdkZIndex: Int,
)

internal val MapOverlayImage.key: String
    get() = when (this) {
        is MapOverlayImage.Bytes -> key
        is MapOverlayImage.Url -> key
    }

/** Retângulo desenhável: coordenadas válidas, sul < norte e oeste < leste. */
internal fun LatLngBounds.isDrawableOverlayArea(): Boolean =
    southwest.isValidCoordinate() && northeast.isValidCoordinate() &&
        southwest.latitude < northeast.latitude && southwest.longitude < northeast.longitude

/** Opacidade entre 0 e 1; `NaN` = 1. */
internal fun overlayOpacity(value: Float): Float = if (value.isNaN()) 1f else value.coerceIn(0f, 1f)

/**
 * Overlays prontos para desenhar — pura e testada. Descarta área inválida e `id` repetido (fica o
 * primeiro) e decide o `zIndex` do SDK: ordenados por [MapGroundOverlay.zIndex] (empate = ordem da
 * lista), todos abaixo dos [polygonCount] polígonos (que ocupam `-polygonCount-1 … -2`) — o de baixo
 * recebe `-n-polygonCount-1`, o de cima `-polygonCount-2`.
 */
internal fun resolveGroundOverlays(overlays: List<MapGroundOverlay>, polygonCount: Int): List<ResolvedGroundOverlay> {
    val seen = HashSet<String>()
    val valid = overlays.withIndex().filter { (_, o) -> o.bounds.isDrawableOverlayArea() && seen.add(o.id) }
    val ranked = valid.sortedWith(compareBy({ it.value.zIndex.takeUnless { z -> z.isNaN() } ?: 0f }, { it.index }))
    val n = ranked.size
    val floor = -polygonCount.coerceAtLeast(0) - 1
    return ranked.mapIndexed { rank, (_, o) ->
        ResolvedGroundOverlay(
            id = o.id,
            image = o.image,
            imageKey = o.image.key,
            bounds = o.bounds,
            opacity = overlayOpacity(o.opacity),
            visible = o.visible,
            sdkZIndex = floor - n + rank,
        )
    }
}

/** Imagem decodificada pela plataforma (Android `Bitmap`, iOS `UIImage`). */
internal interface DecodedOverlayImage

/** Decodifica PNG/JPEG fora da main thread; `null` = bytes que não são imagem. */
internal expect suspend fun decodeOverlayImage(bytes: ByteArray): DecodedOverlayImage?

/** Overlay com a imagem já decodificada — o que a plataforma desenha. */
@Immutable
internal data class ReadyGroundOverlay(
    val overlay: ResolvedGroundOverlay,
    /** Chave da imagem que está em [image] (pode ser a anterior enquanto a nova carrega). */
    val imageKey: String,
    val image: DecodedOverlayImage,
)

/**
 * Baixa (se for URL), decodifica e guarda as imagens dos overlays. Memória limitada a [maxEntries]
 * imagens decodificadas (LRU — as três abas de um mapa de chuva cabem), e dois pedidos da mesma
 * imagem ao mesmo tempo viram um só. [fetch] e [decode] trocam a rede e a plataforma no teste.
 */
internal class GroundOverlayImageLoader(
    private val fetch: suspend (MapOverlayImage.Url) -> ByteArray?,
    private val decode: suspend (ByteArray) -> DecodedOverlayImage?,
    private val maxEntries: Int = 6,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, DecodedOverlayImage>()
    private val inFlight = HashMap<String, CompletableDeferred<DecodedOverlayImage?>>()

    /** A imagem já carregada, sem rede nem decodificação; `null` = ainda não. */
    suspend fun cached(key: String): DecodedOverlayImage? = mutex.withLock { touch(key) }

    suspend fun load(image: MapOverlayImage): DecodedOverlayImage? {
        val key = image.key
        val (deferred, owner) = mutex.withLock {
            touch(key)?.let { return it }
            val running = inFlight[key]
            if (running != null) {
                running to false
            } else {
                CompletableDeferred<DecodedOverlayImage?>().also { inFlight[key] = it } to true
            }
        }
        if (!owner) {
            return try {
                deferred.await()
            } catch (e: CancellationException) {
                // Quem baixava desistiu (saiu da tela); se ESTE pedido segue vivo, assume o download.
                currentCoroutineContext().ensureActive()
                load(image)
            }
        }
        val result = try {
            val bytes = when (image) {
                is MapOverlayImage.Bytes -> image.bytes
                is MapOverlayImage.Url -> fetch(image)
            }
            bytes?.takeIf { it.isNotEmpty() }?.let { decode(it) }
        } catch (e: CancellationException) {
            mutex.withLock { inFlight.remove(key) }
            deferred.completeExceptionally(e)
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Imagem do overlay não carregou (${e::class.simpleName}).")
            null
        }
        mutex.withLock {
            inFlight.remove(key)
            if (result != null) {
                cache.remove(key)
                cache[key] = result
                while (cache.size > maxEntries) cache.remove(cache.keys.first())
            }
        }
        deferred.complete(result)
        return result
    }

    private fun touch(key: String): DecodedOverlayImage? {
        val hit = cache.remove(key) ?: return null
        cache[key] = hit
        return hit
    }

    internal companion object {
        const val TAG = "NativeMap"

        /** O carregador do processo: um cliente HTTP só do módulo, criado na primeira URL. */
        val shared: GroundOverlayImageLoader by lazy {
            val client: HttpClient by lazy { createHttpClient() }
            GroundOverlayImageLoader(
                fetch = { image ->
                    val response = client.get(image.url) { image.headers.forEach { (k, v) -> header(k, v) } }
                    if (response.status.isSuccess()) {
                        response.readRawBytes()
                    } else {
                        AppLogger.w(TAG, "Imagem do overlay: HTTP ${response.status.value} em ${redactOverlayUrl(image.url)}.")
                        null
                    }
                },
                decode = ::decodeOverlayImage,
            )
        }
    }
}

/**
 * Os overlays prontos para desenhar, carregando as imagens em segundo plano. Enquanto a imagem nova de
 * um `id` carrega, devolve a anterior; se a nova falhar, o `id` sai da lista.
 */
@Composable
internal fun rememberReadyGroundOverlays(
    overlays: List<ResolvedGroundOverlay>,
    loader: GroundOverlayImageLoader = GroundOverlayImageLoader.shared,
): List<ReadyGroundOverlay> {
    if (overlays.isEmpty()) return emptyList()
    val loaded = remember(loader) { mutableStateMapOf<String, Pair<String, DecodedOverlayImage>>() }
    overlays.forEach { overlay ->
        key(overlay.id) {
            LaunchedEffect(overlay.imageKey) {
                val current = loaded[overlay.id]
                if (current?.first == overlay.imageKey) return@LaunchedEffect
                val image = loader.load(overlay.image)
                if (image != null) {
                    loaded[overlay.id] = overlay.imageKey to image
                } else {
                    loaded.remove(overlay.id)
                    AppLogger.w(GroundOverlayImageLoader.TAG, "Overlay '${overlay.id}' sem imagem: ${overlay.image}.")
                }
            }
        }
    }
    return readyGroundOverlays(overlays, loaded)
}

/** Junta cada overlay à imagem carregada do seu `id` (a nova ou, enquanto carrega, a anterior). Pura. */
internal fun readyGroundOverlays(
    overlays: List<ResolvedGroundOverlay>,
    loaded: Map<String, Pair<String, DecodedOverlayImage>>,
): List<ReadyGroundOverlay> =
    overlays.mapNotNull { o -> loaded[o.id]?.let { (key, image) -> ReadyGroundOverlay(o, key, image) } }

/** Folga do mapa em pontos/dp, já sem `start`/`end` (resolvidos pela direção do texto). */
@Immutable
internal data class MapPadding(val top: Float, val left: Float, val bottom: Float, val right: Float) {
    val isZero: Boolean get() = top == 0f && left == 0f && bottom == 0f && right == 0f

    companion object {
        val Zero = MapPadding(0f, 0f, 0f, 0f)
    }
}

/** [PaddingValues] → [MapPadding] (negativo vira 0). Pura e testada. */
internal fun PaddingValues.toMapPadding(direction: LayoutDirection): MapPadding = MapPadding(
    top = calculateTopPadding().value.coerceAtLeast(0f),
    left = calculateLeftPadding(direction).value.coerceAtLeast(0f),
    bottom = calculateBottomPadding().value.coerceAtLeast(0f),
    right = calculateRightPadding(direction).value.coerceAtLeast(0f),
)

/** Área útil do mapa depois da folga (nunca negativa) — o enquadramento do iOS usa esta medida. */
internal fun MapPadding.innerSize(width: Double, height: Double): Pair<Double, Double> {
    if (width <= 0.0 || height <= 0.0) return width to height // vista ainda sem medida
    return (width - left - right).coerceAtLeast(1.0) to (height - top - bottom).coerceAtLeast(1.0)
}
