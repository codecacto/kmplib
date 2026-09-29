package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Comando de câmera que o [MapController] entrega ao mapa nativo. */
internal sealed interface MapCameraCommand {
    val animate: Boolean

    data class Move(val position: CameraPosition, override val animate: Boolean) : MapCameraCommand

    /** Enquadra [bounds] com [paddingDp] de folga, sem passar de [maxZoom]. */
    data class Fit(
        val bounds: LatLngBounds,
        val paddingDp: Float,
        val maxZoom: Float,
        override val animate: Boolean,
    ) : MapCameraCommand
}

/** Quem executa os comandos: a implementação nativa de cada plataforma. */
internal fun interface MapCameraSink {
    fun apply(command: MapCameraCommand)
}

/**
 * Transforma "enquadre estes pontos" num comando concreto. Puro e testado:
 * - nenhum ponto válido → `null` (nada a fazer);
 * - um ponto só (ou todos no mesmo lugar) → [MapCameraCommand.Move] para ele em [maxZoom] —
 *   enquadrar um retângulo de área zero levaria ao zoom MÁXIMO do SDK, dentro de um telhado;
 * - senão → [MapCameraCommand.Fit].
 */
internal fun fitCommandFor(
    points: Iterable<LatLng>,
    padding: Dp,
    maxZoom: Float,
    animate: Boolean,
): MapCameraCommand? {
    val bounds = LatLngBounds.of(points) ?: return null
    return if (bounds.isSinglePoint) {
        MapCameraCommand.Move(CameraPosition(bounds.southwest, maxZoom), animate)
    } else {
        MapCameraCommand.Fit(bounds, padding.value, maxZoom, animate)
    }
}

/**
 * Controle da câmera do [NativeMap] — mover, enquadrar pontos — e leitura da posição atual.
 *
 * Crie com [rememberMapController] e passe ao `NativeMap`. Comandos dados ANTES de o mapa terminar
 * de carregar não se perdem: o último fica guardado e é aplicado assim que o mapa fica pronto (é o
 * caso normal do "enquadrar a carteira ao abrir").
 *
 * ```kotlin
 * val controller = rememberMapController()
 * NativeMap(items, controller = controller)
 * Button(onClick = { controller.fitTo(rota.polyline) }) { Text("Ver rota inteira") }
 * ```
 */
@Stable
class MapController internal constructor(initial: CameraPosition) {

    /** Posição da câmera, atualizada quando o movimento PARA (não a cada quadro). */
    var cameraPosition: CameraPosition by mutableStateOf(initial)
        internal set

    private var sink: MapCameraSink? = null
    internal var pending: MapCameraCommand? = null
        private set

    /** Move a câmera para [position]. */
    fun moveTo(position: CameraPosition, animate: Boolean = true) {
        dispatch(MapCameraCommand.Move(position, animate))
    }

    /** Move a câmera para [target] no [zoom] dado. */
    fun moveTo(target: LatLng, zoom: Float = MapDefaults.STREET_ZOOM, animate: Boolean = true) {
        moveTo(CameraPosition(target, zoom), animate)
    }

    /**
     * Enquadra [points] (pinos, trajeto de rota, os dois juntos) com [padding] de folga nas bordas.
     * Um ponto só vira "centralizar nele em [maxZoom]"; lista vazia não faz nada.
     */
    fun fitTo(
        points: Iterable<LatLng>,
        padding: Dp = 48.dp,
        maxZoom: Float = MapDefaults.STREET_ZOOM,
        animate: Boolean = true,
    ) {
        fitCommandFor(points, padding, maxZoom, animate)?.let(::dispatch)
    }

    private fun dispatch(command: MapCameraCommand) {
        val current = sink
        if (current == null) pending = command else current.apply(command)
    }

    /** Liga o mapa nativo; aplica o comando pendente, se houver. */
    internal fun attach(target: MapCameraSink) {
        sink = target
        pending?.let {
            pending = null
            target.apply(it)
        }
    }

    internal fun detach(target: MapCameraSink) {
        if (sink === target) sink = null
    }

    internal val isAttached: Boolean get() = sink != null
}

/**
 * Lembra um [MapController]. [initial] é onde a câmera nasce antes de qualquer enquadramento
 * (default: o Brasil inteiro).
 */
@Composable
fun rememberMapController(
    initial: CameraPosition = CameraPosition(MapDefaults.BRAZIL_CENTER, MapDefaults.COUNTRY_ZOOM),
): MapController = remember { MapController(initial) }
