package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGLineJoin
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKClusterAnnotation
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKFeatureDisplayPriorityDefaultHigh
import platform.MapKit.MKFeatureDisplayPriorityRequired
import platform.MapKit.MKFeatureVisibility
import platform.MapKit.MKMapPointForCoordinate
import platform.MapKit.MKMapRectMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPolyline
import platform.MapKit.MKPolylineRenderer
import platform.MapKit.MKUserLocation
import platform.MapKit.addOverlay
import platform.MapKit.removeOverlay
import platform.UIKit.UIColor
import platform.UIKit.accessibilityLabel
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UILongPressGestureRecognizer
import platform.UIKit.UIScreen
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.objc.sel_registerName
import kotlin.math.abs
import kotlin.math.pow

/**
 * **MapKit** no Compose (2.220.0 — GAP-ER-01): o `MKMapView` oficial da Apple, sem chave, sem
 * pacote SPM e sem ponte Swift. É o motor do [NativeMap] no iOS e, desde a mesma versão, também do
 * [MapView] quando o app não registrou [IosMapBridge.factory] (antes: um texto "Mapa indisponível").
 *
 * Tudo que é decisão (cor, contador, enquadramento, zoom ↔ extensão) vem das funções puras do
 * `commonMain`; aqui só se traduz para MapKit.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun MapKitMap(
    markers: List<ResolvedMarker>,
    polylines: List<ResolvedPolyline>,
    controller: MapController,
    clustering: Boolean,
    showUserLocation: Boolean,
    defaultColor: Color,
    onMarkerClick: (MapItem) -> Unit,
    onClusterClick: (List<MapItem>) -> Unit,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    onLoaded: () -> Unit,
    modifier: Modifier,
) {
    val host = remember { MapKitHost(controller) }
    host.callbacks = MapKitCallbacks(onMarkerClick, onClusterClick, onMapClick, onMapLongClick, onLoaded)
    host.defaultColor = defaultColor

    DisposableEffect(host) {
        onDispose { host.dispose() }
    }

    UIKitView(
        factory = { host.mapView },
        modifier = modifier,
        update = {
            host.setClustering(clustering)
            host.mapView.showsUserLocation = showUserLocation
            host.updatePolylines(polylines)
            host.updateMarkers(markers)
        },
    )
}

internal class MapKitCallbacks(
    val onMarkerClick: (MapItem) -> Unit,
    val onClusterClick: (List<MapItem>) -> Unit,
    val onMapClick: ((LatLng) -> Unit)?,
    val onMapLongClick: ((LatLng) -> Unit)?,
    val onLoaded: () -> Unit,
)

/** Pino de um [MapItem] no MapKit; carrega o id para voltar ao item no toque e no agrupamento. */
@OptIn(ExperimentalForeignApi::class)
internal class ItemAnnotation(val marker: ResolvedMarker) : MKPointAnnotation() {
    init {
        setCoordinate(CLLocationCoordinate2DMake(marker.item.position.latitude, marker.item.position.longitude))
        setTitle(marker.item.title.ifBlank { null })
        setSubtitle(marker.item.snippet)
    }
}

private const val CLUSTER_ID = "br.com.codecacto.kmplib.map.cluster"
private const val ITEM_REUSE_ID = "br.com.codecacto.kmplib.map.item"
private const val CLUSTER_REUSE_ID = "br.com.codecacto.kmplib.map.group"

/** Tamanho do mundo em `MKMapPoint` (2^28), a base para converter zoom ↔ retângulo. */
private val MK_WORLD_WIDTH: Double = 2.0.pow(28)

@OptIn(ExperimentalForeignApi::class)
internal class MapKitHost(private val controller: MapController) : NSObject(), MKMapViewDelegateProtocol {

    val mapView: MKMapView = MKMapView()
    var callbacks: MapKitCallbacks? = null
    var defaultColor: Color = Color.Unspecified

    private val annotations = mutableMapOf<String, ItemAnnotation>()
    private val overlays = mutableMapOf<String, Pair<MKPolyline, ResolvedPolyline>>()
    private var clustering = true
    private var loadedNotified = false
    private val gestures = MapKitGestureTarget(this)
    private val sink = MapCameraSink { command -> applyCommand(command) }

    init {
        mapView.delegate = this
        mapView.showsCompass = true
        val tap = UITapGestureRecognizer(target = gestures, action = sel_registerName("onTap:"))
        tap.cancelsTouchesInView = false
        tap.delegate = gestures
        mapView.addGestureRecognizer(tap)
        val longPress = UILongPressGestureRecognizer(target = gestures, action = sel_registerName("onLongPress:"))
        longPress.delegate = gestures
        mapView.addGestureRecognizer(longPress)
        // Câmera inicial (antes do layout a largura é 0; a da tela serve de estimativa).
        applyMove(controller.cameraPosition, animate = false)
    }

    fun dispose() {
        controller.detach(sink)
        mapView.delegate = null
    }

    // --- dados ---------------------------------------------------------------------------------

    fun setClustering(enabled: Boolean) {
        if (enabled == clustering) return
        clustering = enabled
        // A view de cada pino guarda o `clusteringIdentifier`: re-adicionar recria as views.
        val all = annotations.values.toList()
        if (all.isNotEmpty()) {
            mapView.removeAnnotations(all)
            mapView.addAnnotations(all)
        }
    }

    fun updateMarkers(markers: List<ResolvedMarker>) {
        val incoming = markers.associateBy { it.item.id }
        val toRemove = mutableListOf<ItemAnnotation>()
        val toAdd = mutableListOf<ItemAnnotation>()
        val iterator = annotations.entries.iterator()
        while (iterator.hasNext()) {
            val (id, annotation) = iterator.next()
            val next = incoming[id]
            if (next == null || next != annotation.marker) {
                toRemove += annotation
                iterator.remove()
            }
        }
        for ((id, marker) in incoming) {
            if (id !in annotations && marker.item.position.isValidCoordinate()) {
                val annotation = ItemAnnotation(marker)
                annotations[id] = annotation
                toAdd += annotation
            }
        }
        if (toRemove.isNotEmpty()) mapView.removeAnnotations(toRemove)
        if (toAdd.isNotEmpty()) mapView.addAnnotations(toAdd)
    }

    fun updatePolylines(lines: List<ResolvedPolyline>) {
        val incoming = lines.associateBy { it.id }
        val iterator = overlays.entries.iterator()
        while (iterator.hasNext()) {
            val (id, pair) = iterator.next()
            if (incoming[id] != pair.second) {
                mapView.removeOverlay(pair.first)
                iterator.remove()
            }
        }
        for ((id, line) in incoming) {
            if (id in overlays) continue
            val valid = line.points.filter { it.isValidCoordinate() }
            if (valid.size < 2) continue
            val polyline = memScoped {
                val coords = allocArray<CLLocationCoordinate2D>(valid.size)
                valid.forEachIndexed { i, p ->
                    coords[i].latitude = p.latitude
                    coords[i].longitude = p.longitude
                }
                MKPolyline.polylineWithCoordinates(coords, valid.size.toULong())
            }
            polyline.setTitle(id)
            overlays[id] = polyline to line
            mapView.addOverlay(polyline)
        }
    }

    // --- câmera --------------------------------------------------------------------------------

    private fun viewWidth(): Double {
        val w = mapView.bounds.useContents { size.width }
        return if (w > 0.0) w else UIScreen.mainScreen.bounds.useContents { size.width }
    }

    private fun attachIfReady() {
        if (!controller.isAttached && mapView.bounds.useContents { size.width } > 0.0) {
            controller.attach(sink)
        }
    }

    private fun applyCommand(command: MapCameraCommand) {
        when (command) {
            is MapCameraCommand.Move -> applyMove(command.position, command.animate)
            is MapCameraCommand.Fit -> applyFit(command)
        }
    }

    private fun applyMove(position: CameraPosition, animate: Boolean) {
        val lngSpan = longitudeSpanForZoom(position.zoom, viewWidth()).coerceIn(0.0001, 360.0)
        val latSpan = latitudeSpanFor(lngSpan, position.target.latitude).coerceAtLeast(0.0001)
        val region = MKCoordinateRegionMake(
            CLLocationCoordinate2DMake(position.target.latitude, position.target.longitude),
            MKCoordinateSpanMake(latSpan, lngSpan),
        )
        mapView.setRegion(region, animated = animate)
    }

    private fun applyFit(command: MapCameraCommand.Fit) {
        val sw = MKMapPointForCoordinate(
            CLLocationCoordinate2DMake(command.bounds.southwest.latitude, command.bounds.southwest.longitude),
        )
        val ne = MKMapPointForCoordinate(
            CLLocationCoordinate2DMake(command.bounds.northeast.latitude, command.bounds.northeast.longitude),
        )
        val x1 = sw.useContents { x }
        val y1 = sw.useContents { y }
        val x2 = ne.useContents { x }
        val y2 = ne.useContents { y }
        var x = minOf(x1, x2)
        var y = minOf(y1, y2)
        var w = abs(x2 - x1)
        var h = abs(y2 - y1)
        val padding = command.paddingDp.toDouble()
        // Não passar de maxZoom: o retângulo visível tem, no mínimo, a largura desse zoom.
        val usable = (viewWidth() - 2 * padding).coerceAtLeast(1.0)
        val minWidth = MK_WORLD_WIDTH * usable / (WORLD_TILE_POINTS * 2.0.pow(command.maxZoom.toDouble()))
        if (w < minWidth) {
            x -= (minWidth - w) / 2
            w = minWidth
        }
        if (h < minWidth) {
            y -= (minWidth - h) / 2
            h = minWidth
        }
        mapView.setVisibleMapRect(
            MKMapRectMake(x, y, w, h),
            edgePadding = UIEdgeInsetsMake(padding, padding, padding, padding),
            animated = command.animate,
        )
    }

    // --- MKMapViewDelegate ---------------------------------------------------------------------

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        if (viewForAnnotation is MKUserLocation) return null
        if (viewForAnnotation is MKClusterAnnotation) {
            val members = viewForAnnotation.memberAnnotations.filterIsInstance<ItemAnnotation>().map { it.marker }
            val cluster = resolveCluster(members, defaultColor)
            val view = (mapView.dequeueReusableAnnotationViewWithIdentifier(CLUSTER_REUSE_ID) as? MKMarkerAnnotationView)
                ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = CLUSTER_REUSE_ID)
            view.annotation = viewForAnnotation
            view.markerTintColor = cluster.color.toUIColor()
            view.glyphText = cluster.label
            view.glyphTintColor = cluster.labelColor.toUIColor()
            view.canShowCallout = false
            view.titleVisibility = MKFeatureVisibility.MKFeatureVisibilityHidden
            view.subtitleVisibility = MKFeatureVisibility.MKFeatureVisibilityHidden
            view.displayPriority = MKFeatureDisplayPriorityRequired
            return view
        }
        val item = viewForAnnotation as? ItemAnnotation ?: return null
        val marker = item.marker
        val view = (mapView.dequeueReusableAnnotationViewWithIdentifier(ITEM_REUSE_ID) as? MKMarkerAnnotationView)
            ?: MKMarkerAnnotationView(annotation = viewForAnnotation, reuseIdentifier = ITEM_REUSE_ID)
        view.annotation = viewForAnnotation
        view.markerTintColor = marker.color.toUIColor()
        view.glyphText = marker.glyph
        view.glyphTintColor = marker.glyphColor.toUIColor()
        view.canShowCallout = marker.item.title.isNotBlank()
        // Título só no balão, como no Android — milhares de rótulos no chão do mapa viram ruído.
        view.titleVisibility = MKFeatureVisibility.MKFeatureVisibilityHidden
        view.subtitleVisibility = MKFeatureVisibility.MKFeatureVisibilityHidden
        view.clusteringIdentifier = if (clustering) CLUSTER_ID else null
        // Sem agrupamento, nenhum pino pode sumir por colisão; com ele, a colisão é que agrupa.
        view.displayPriority = if (clustering) MKFeatureDisplayPriorityDefaultHigh else MKFeatureDisplayPriorityRequired
        view.zPriority = marker.item.priority.toFloat()
        view.accessibilityLabel = marker.item.title.ifBlank { null }
        return view
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        when (val annotation = didSelectAnnotationView.annotation) {
            is MKClusterAnnotation -> {
                val members = annotation.memberAnnotations.filterIsInstance<ItemAnnotation>().map { it.marker.item }
                mapView.deselectAnnotation(annotation, animated = false)
                callbacks?.onClusterClick?.invoke(members)
            }
            is ItemAnnotation -> callbacks?.onMarkerClick?.invoke(annotation.marker.item)
            else -> Unit
        }
    }

    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
        val polyline = rendererForOverlay as? MKPolyline
        val style = polyline?.title?.let { overlays[it]?.second }
        if (polyline == null || style == null) return MKOverlayRenderer(overlay = rendererForOverlay)
        return MKPolylineRenderer(polyline = polyline).apply {
            strokeColor = style.color.toUIColor()
            lineWidth = style.widthDp.toDouble()
            lineCap = CGLineCap.kCGLineCapRound
            lineJoin = CGLineJoin.kCGLineJoinRound
        }
    }

    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        attachIfReady()
        val center = mapView.region.useContents { LatLng(center.latitude, center.longitude) }
        val lngSpan = mapView.region.useContents { span.longitudeDelta }
        controller.cameraPosition = CameraPosition(center, zoomForLongitudeSpan(lngSpan, viewWidth()))
    }

    override fun mapViewDidFinishLoadingMap(mapView: MKMapView) {
        attachIfReady()
        notifyLoaded()
    }

    override fun mapViewDidFailLoadingMap(mapView: MKMapView, withError: platform.Foundation.NSError) {
        // Sem rede os ladrilhos não vêm, mas pinos, rota e câmera continuam funcionando.
        attachIfReady()
        notifyLoaded()
    }

    private fun notifyLoaded() {
        if (!loadedNotified) {
            loadedNotified = true
            callbacks?.onLoaded?.invoke()
        }
    }

    // --- gestos --------------------------------------------------------------------------------

    internal fun handleTap(recognizer: UIGestureRecognizer, long: Boolean) {
        val cb = (if (long) callbacks?.onMapLongClick else callbacks?.onMapClick) ?: return
        val point = recognizer.locationInView(mapView)
        // Toque num pino é do pino (didSelect), não do mapa.
        var hit: UIView? = mapView.hitTest(point, withEvent = null)
        while (hit != null) {
            if (hit is MKAnnotationView) return
            hit = hit.superview
        }
        val coordinate = mapView.convertPoint(point, toCoordinateFromView = mapView)
        cb(coordinate.useContents { LatLng(latitude, longitude) })
    }
}

/** Alvo dos gestos (o UIKit guarda o alvo de forma fraca — por isso ele vive no host). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class MapKitGestureTarget(private val host: MapKitHost) : NSObject(), UIGestureRecognizerDelegateProtocol {

    @ObjCAction
    fun onTap(recognizer: UITapGestureRecognizer) {
        host.handleTap(recognizer, long = false)
    }

    @ObjCAction
    fun onLongPress(recognizer: UILongPressGestureRecognizer) {
        if (recognizer.state == UIGestureRecognizerStateBegan) host.handleTap(recognizer, long = true)
    }

    /** Convive com o arrasto, a pinça e o duplo toque do próprio MapKit. */
    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
    ): Boolean = true
}

internal fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())
