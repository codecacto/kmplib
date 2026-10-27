package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents

/**
 * iOS do [NativeMap].
 *
 * - **Padrão: Google Maps pela ponte** (2.331.0) — com `IosMapBridge.register(factory:)` feito no
 *   `AppDelegate`, a `GMSMapView` do app ([IosNativeRichMap]). A lib decide agrupamento
 *   ([planPins], a régua de 100 pt do Android), cores, ícones ([MapPinIcons]) e enquadramento
 *   ([cameraToFit]); o Swift só desenha.
 * - **Fallback: MapKit** ([MapKitMap]) — app sem a ponte nova (ou só com o antigo
 *   `IosMapBridge.factory`, que não sabe desenhar o `NativeMap`). Funciona igual e avisa no log uma
 *   vez por processo ([IosMapFallbackNotice]); troca quando o app for tocado.
 */
@Composable
internal actual fun PlatformNativeMap(
    markers: List<ResolvedMarker>,
    polylines: List<ResolvedPolyline>,
    circles: List<ResolvedCircle>,
    controller: MapController,
    clustering: Boolean,
    showUserLocation: Boolean,
    interactive: Boolean,
    defaultColor: Color,
    onMarkerClick: (MapItem) -> Unit,
    onClusterClick: (List<MapItem>) -> Unit,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    modifier: Modifier,
) {
    val richFactory = IosMapBridge.richFactory
    if (richFactory == null) {
        if (IosMapFallbackNotice.claim()) AppLogger.w(IosMapFallbackNotice.TAG, IosMapFallbackNotice.MESSAGE)
        MapKitMap(
            markers = markers,
            polylines = polylines,
            circles = circles,
            controller = controller,
            clustering = clustering,
            showUserLocation = showUserLocation,
            defaultColor = defaultColor,
            onMarkerClick = onMarkerClick,
            onClusterClick = onClusterClick,
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onLoaded = {},
            modifier = modifier,
            interactive = interactive,
        )
        return
    }

    val host = remember { GoogleBridgeMapHost(richFactory(), controller) }
    host.callbacks = GoogleBridgeCallbacks(onMarkerClick, onClusterClick, onMapClick, onMapLongClick)
    DisposableEffect(host) {
        onDispose { host.dispose() }
    }
    UIKitView(
        factory = { host.map.view },
        modifier = modifier,
        update = {
            host.setInteractive(interactive)
            host.setShowsUserLocation(showUserLocation)
            host.update(markers, polylines, circles, clustering, defaultColor)
        },
    )
}

internal class GoogleBridgeCallbacks(
    val onMarkerClick: (MapItem) -> Unit,
    val onClusterClick: (List<MapItem>) -> Unit,
    val onMapClick: ((LatLng) -> Unit)?,
    val onMapLongClick: ((LatLng) -> Unit)?,
)

/**
 * Liga o [NativeMap] a um [IosNativeRichMap] (a `GMSMapView` do Swift): reconcilia os dados, aplica
 * os comandos de câmera do [MapController] (só depois de a vista ter medida e de a câmera parar uma
 * vez — antes disso o comando fica pendente no controller) e devolve a posição da câmera quando ela
 * para.
 */
@OptIn(ExperimentalForeignApi::class)
internal class GoogleBridgeMapHost(val map: IosNativeRichMap, private val controller: MapController) {

    var callbacks: GoogleBridgeCallbacks? = null

    private var markers: List<ResolvedMarker> = emptyList()
    private var clustering = true
    private var defaultColor: Color = Color.Unspecified
    private var zoom: Float = controller.cameraPosition.zoom
    private var plan: List<PlannedPin>? = null
    private var groups: Map<String, PlannedPin.Group> = emptyMap()
    private var items: Map<String, MapItem> = emptyMap()
    private var sentPolylines: List<ResolvedPolyline>? = null
    private var sentCircles: List<ResolvedCircle>? = null
    private var interactive: Boolean? = null
    private var showsUserLocation: Boolean? = null
    private val sink = MapCameraSink { command -> applyCommand(command) }

    init {
        val start = controller.cameraPosition
        map.moveCamera(start.target.latitude, start.target.longitude, start.zoom, false)
        map.setOnMarkerClick { id -> handleMarkerTap(id) }
        map.setOnMapClick { lat, lng -> callbacks?.onMapClick?.invoke(LatLng(lat, lng)) }
        map.setOnMapLongClick { lat, lng -> callbacks?.onMapLongClick?.invoke(LatLng(lat, lng)) }
        map.setOnCameraIdle { lat, lng, z -> onCameraIdle(lat, lng, z) }
    }

    fun dispose() {
        controller.detach(sink)
        map.setOnMarkerClick { }
        map.clearOnMapClick()
        map.setOnMapLongClick { _, _ -> }
        map.setOnCameraIdle { _, _, _ -> }
    }

    fun setInteractive(value: Boolean) {
        if (interactive == value) return
        interactive = value
        map.setInteractive(value)
    }

    fun setShowsUserLocation(value: Boolean) {
        if (showsUserLocation == value) return
        showsUserLocation = value
        map.setShowsUserLocation(value)
    }

    fun update(
        markers: List<ResolvedMarker>,
        polylines: List<ResolvedPolyline>,
        circles: List<ResolvedCircle>,
        clustering: Boolean,
        defaultColor: Color,
    ) {
        if (circles != sentCircles) {
            sentCircles = circles
            map.setCircles(
                circles.map {
                    IosMapCircleData(
                        id = it.id,
                        lat = it.center.latitude,
                        lng = it.center.longitude,
                        radiusMeters = it.radiusMeters,
                        fillColor = it.fillColor.toUIColor(),
                        strokeColor = it.strokeColor.toUIColor(),
                        strokeWidth = it.strokeWidthDp.toDouble(),
                    )
                },
            )
        }
        if (polylines != sentPolylines) {
            sentPolylines = polylines
            map.setPolylines(
                polylines.mapNotNull { line ->
                    val points = line.points.filter { it.isValidCoordinate() }
                    if (points.size < 2) {
                        null
                    } else {
                        IosMapPolylineData(
                            id = line.id,
                            points = points.map { IosMapCoordinate(it.latitude, it.longitude) },
                            color = line.color.toUIColor(),
                            width = line.widthDp.toDouble(),
                        )
                    }
                },
            )
        }
        this.markers = markers
        this.clustering = clustering
        this.defaultColor = defaultColor
        replan()
        // Reserva: se a vista já tem medida, os comandos de câmera podem ir (o `idleAt` também liga).
        if (!controller.isAttached && viewSize().first > 0.0) controller.attach(sink)
    }

    /** Recalcula os pinos e só fala com o Swift se o plano mudou (recompor não redesenha o mapa). */
    private fun replan() {
        val next = planPins(markers, zoom, clustering)
        if (next == plan) return
        plan = next
        items = markers.associate { it.item.id to it.item }
        groups = next.filterIsInstance<PlannedPin.Group>().associateBy { it.key }
        map.setPins(next.map { it.toPinData() })
    }

    private fun PlannedPin.toPinData(): IosMapPinData = when (this) {
        is PlannedPin.Single -> IosMapPinData(
            id = key,
            lat = position.latitude,
            lng = position.longitude,
            title = marker.item.title.ifBlank { null },
            snippet = marker.item.snippet,
            icon = MapPinIcons.pin(marker),
            anchorX = MapPinIcons.PIN_ANCHOR_X,
            anchorY = MapPinIcons.PIN_ANCHOR_Y,
            zIndex = marker.item.priority,
        )
        is PlannedPin.Group -> IosMapPinData(
            id = key,
            lat = position.latitude,
            lng = position.longitude,
            title = null,
            snippet = null,
            icon = MapPinIcons.cluster(resolveCluster(members, defaultColor)),
            anchorX = 0.5,
            anchorY = 0.5,
            // Grupo por cima dos pinos soltos, e o mais grave por cima dos outros grupos.
            zIndex = (members.maxOf { it.item.priority }).coerceAtMost(Int.MAX_VALUE - 1) + 1,
        )
    }

    private fun handleMarkerTap(id: String) {
        val cb = callbacks ?: return
        val group = groups[id]
        if (group != null) {
            cb.onClusterClick(group.members.map { it.item })
        } else {
            items[id]?.let(cb.onMarkerClick)
        }
    }

    private fun onCameraIdle(lat: Double, lng: Double, zoom: Float) {
        controller.cameraPosition = CameraPosition(LatLng(lat, lng), zoom)
        if (!controller.isAttached && viewSize().first > 0.0) controller.attach(sink)
        if (zoom != this.zoom) {
            this.zoom = zoom
            if (clustering) replan()
        }
    }

    private fun viewSize(): Pair<Double, Double> =
        map.view.bounds.useContents { size.width to size.height }

    private fun applyCommand(command: MapCameraCommand) {
        // Mapa "só leitura" não anima: a câmera vai direto, como no Android (modo lite).
        val animate = command.animate && interactive != false
        when (command) {
            is MapCameraCommand.Move ->
                map.moveCamera(command.position.target.latitude, command.position.target.longitude, command.position.zoom, animate)
            is MapCameraCommand.Fit -> {
                val (w, h) = viewSize()
                val camera = cameraToFit(command.bounds, w, h, command.paddingDp.toDouble(), command.maxZoom)
                map.moveCamera(camera.target.latitude, camera.target.longitude, camera.zoom, animate)
            }
        }
    }
}
