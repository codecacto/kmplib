package br.com.codecacto.kmplib.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.CameraPosition as GmsCameraPosition
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.android.gms.maps.model.LatLngBounds as GmsLatLngBounds
import com.google.maps.android.compose.Marker as ComposeMarker

/**
 * Android do [NativeMap]: Google Maps SDK (`maps-compose`) + `ClusterManager` oficial
 * (`maps-compose-utils` / android-maps-utils) com um renderizador que pinta o pino e o grupo na cor
 * do item — o `DefaultClusterRenderer` puro só conhece o pino vermelho e o círculo por faixa de
 * quantidade ("10+", "20+"), que não servem a um semáforo.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
internal actual fun PlatformNativeMap(
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
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val cameraState = rememberCameraPositionState {
        position = controller.cameraPosition.toGmsCamera()
    }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }

    // Os comandos do controller só são aplicados com o mapa medido e carregado: `newLatLngBounds`
    // antes do layout lança "Map size can't be 0".
    DisposableEffect(controller, cameraState, loaded) {
        if (!loaded) return@DisposableEffect onDispose { }
        val sink = MapCameraSink { command ->
            scope.launch { cameraState.apply(command, density) }
        }
        controller.attach(sink)
        onDispose { controller.detach(sink) }
    }

    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isMoving }
            .filter { moving -> !moving }
            .collect { controller.cameraPosition = cameraState.position.toCommonCamera() }
    }

    val locationGranted = rememberLocationPermission(context)
    val myLocation = showUserLocation && locationGranted

    val markerClick by rememberUpdatedState(onMarkerClick)
    val clusterClick by rememberUpdatedState(onClusterClick)

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(isMyLocationEnabled = myLocation),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = myLocation,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
        ),
        onMapLoaded = { loaded = true },
        onMapClick = { latLng -> onMapClick?.invoke(latLng.toCommon()) },
        onMapLongClick = { latLng -> onMapLongClick?.invoke(latLng.toCommon()) },
    ) {
        polylines.forEach { line ->
            key(line.id) {
                Polyline(
                    points = line.points.map { it.toGms() },
                    color = line.color,
                    width = line.widthDp * density,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap(),
                    zIndex = 0f,
                )
            }
        }

        if (clustering) {
            val clusterItems = remember(markers) { markers.map { MapClusterItem(it) } }
            val clusterManager = rememberClusterManager<MapClusterItem>()
            MapEffect(clusterManager, density, defaultColor) { map ->
                val manager = clusterManager ?: return@MapEffect
                manager.renderer = MapItemClusterRenderer(context, map, manager, density, defaultColor).apply {
                    minClusterSize = 2
                }
            }
            SideEffect {
                val manager = clusterManager ?: return@SideEffect
                manager.setOnClusterClickListener { cluster ->
                    clusterClick(cluster.items.map { it.resolved.item })
                    true
                }
                manager.setOnClusterItemClickListener { item ->
                    markerClick(item.resolved.item)
                    false // false = comportamento padrão: abre o balão do título
                }
            }
            if (clusterManager != null) {
                Clustering(items = clusterItems, clusterManager = clusterManager)
            }
        } else {
            markers.forEach { marker ->
                key(marker.item.id) {
                    ComposeMarker(
                        state = rememberUpdatedMarkerState(marker.item.position.toGms()),
                        icon = PinIcons.pin(marker, density),
                        anchor = Offset(0.5f, 1f),
                        title = marker.item.title.ifBlank { null },
                        snippet = marker.item.snippet,
                        zIndex = marker.item.priority.toFloat(),
                        contentDescription = marker.item.title,
                        onClick = {
                            markerClick(marker.item)
                            false
                        },
                    )
                }
            }
        }
    }
}

/** Item do `ClusterManager`. `data class`: o cache de marcadores da lib compara por igualdade. */
internal data class MapClusterItem(val resolved: ResolvedMarker) : ClusterItem {
    private val gms = resolved.item.position.toGms()
    override fun getPosition(): GmsLatLng = gms
    override fun getTitle(): String? = resolved.item.title.ifBlank { null }
    override fun getSnippet(): String? = resolved.item.snippet
    override fun getZIndex(): Float = resolved.item.priority.toFloat()
}

/** Renderizador: pino na cor do item; grupo na cor do membro mais grave, com o contador exato. */
private class MapItemClusterRenderer(
    context: Context,
    map: GoogleMap,
    manager: ClusterManager<MapClusterItem>,
    private val density: Float,
    private val defaultColor: Color,
) : DefaultClusterRenderer<MapClusterItem>(context, map, manager) {

    override fun onBeforeClusterItemRendered(item: MapClusterItem, markerOptions: MarkerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions)
        markerOptions.icon(PinIcons.pin(item.resolved, density)).anchor(0.5f, 1f)
    }

    override fun onClusterItemUpdated(item: MapClusterItem, marker: Marker) {
        super.onClusterItemUpdated(item, marker)
        marker.setIcon(PinIcons.pin(item.resolved, density))
        marker.setAnchor(0.5f, 1f)
    }

    override fun onBeforeClusterRendered(cluster: Cluster<MapClusterItem>, markerOptions: MarkerOptions) {
        markerOptions.icon(getDescriptorForCluster(cluster)).anchor(0.5f, 0.5f)
    }

    override fun onClusterUpdated(cluster: Cluster<MapClusterItem>, marker: Marker) {
        marker.setIcon(getDescriptorForCluster(cluster))
        marker.setAnchor(0.5f, 0.5f)
    }

    override fun getDescriptorForCluster(cluster: Cluster<MapClusterItem>): BitmapDescriptor =
        PinIcons.cluster(resolveCluster(cluster.items.map { it.resolved }, defaultColor), density)
}

/**
 * Ícones desenhados uma vez e reaproveitados (a carteira tem milhares de pinos, mas poucas
 * combinações de cor + texto). `BitmapDescriptorFactory` só existe depois de o Maps inicializar — por
 * isso só é chamado de dentro do mapa.
 */
private object PinIcons {
    private val cache = LruCache<String, BitmapDescriptor>(256)

    fun pin(marker: ResolvedMarker, density: Float): BitmapDescriptor {
        val key = "p|${marker.color.toArgb()}|${marker.glyph}|${marker.glyphColor.toArgb()}|$density"
        return cache.get(key) ?: BitmapDescriptorFactory
            .fromBitmap(drawPin(marker.color, marker.glyph, marker.glyphColor, density))
            .also { cache.put(key, it) }
    }

    fun cluster(cluster: ResolvedCluster, density: Float): BitmapDescriptor {
        val key = "c|${cluster.color.toArgb()}|${cluster.label}|${cluster.labelColor.toArgb()}|$density"
        return cache.get(key) ?: BitmapDescriptorFactory
            .fromBitmap(drawCluster(cluster, density))
            .also { cache.put(key, it) }
    }

    /** Gota: círculo em cima, ponta embaixo (a ponta é a coordenada). 32 × 42 dp. */
    private fun drawPin(color: Color, glyph: String?, glyphColor: Color, density: Float): Bitmap {
        val w = 32f * density
        val h = 42f * density
        val stroke = 2f * density
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = w / 2f - stroke
        val cx = w / 2f
        val cy = r + stroke
        val path = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * 0.62f, cy + r * 0.78f)
            lineTo(cx, h - stroke)
            lineTo(cx + r * 0.62f, cy + r * 0.78f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = glyphColor.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, border)
        if (glyph != null) {
            drawCenteredText(canvas, glyph, cx, cy, r, glyphColor)
        } else {
            canvas.drawCircle(cx, cy, r * 0.32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = glyphColor.toArgb() })
        }
        return bitmap
    }

    /** Círculo com halo; diâmetro cresce com o número de dígitos. */
    private fun drawCluster(cluster: ResolvedCluster, density: Float): Bitmap {
        val base = when (cluster.label.length) {
            1 -> 36f
            2 -> 40f
            else -> 46f
        } * density
        val halo = 4f * density
        val size = (base + halo * 2).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cluster.color.copy(alpha = 0.35f).toArgb()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cluster.color.toArgb() }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cluster.labelColor.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawCircle(c, c, c, haloPaint)
        canvas.drawCircle(c, c, base / 2f, fill)
        canvas.drawCircle(c, c, base / 2f - density, ring)
        drawCenteredText(canvas, cluster.label, c, c, base / 2f, cluster.labelColor)
        return bitmap
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, radius: Float, color: Color) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = radius * when (text.length) {
                1 -> 1.05f
                2 -> 0.9f
                else -> 0.72f
            }
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, cx, cy - bounds.exactCenterY(), paint)
    }
}

/** Permissão de localização (fina OU aproximada), reconferida a cada volta ao primeiro plano. */
@Composable
private fun rememberLocationPermission(context: Context): Boolean {
    fun check(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(check()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** Executa um comando de câmera. Gesto do usuário no meio da animação a cancela — é o esperado. */
private suspend fun CameraPositionState.apply(command: MapCameraCommand, density: Float) {
    when (command) {
        is MapCameraCommand.Move -> run(
            CameraUpdateFactory.newCameraPosition(command.position.toGmsCamera()),
            command.animate,
        )
        is MapCameraCommand.Fit -> {
            val bounds = GmsLatLngBounds(command.bounds.southwest.toGms(), command.bounds.northeast.toGms())
            val padding = (command.paddingDp * density).toInt()
            try {
                run(CameraUpdateFactory.newLatLngBounds(bounds, padding), command.animate)
            } catch (_: IllegalStateException) {
                // Folga maior que o próprio mapa (mapa muito pequeno): enquadra sem folga.
                run(CameraUpdateFactory.newLatLngBounds(bounds, 0), command.animate)
            }
            if (position.zoom > command.maxZoom) {
                run(CameraUpdateFactory.zoomTo(command.maxZoom), command.animate)
            }
        }
    }
}

private suspend fun CameraPositionState.run(update: CameraUpdate, animate: Boolean) {
    if (animate) animate(update) else move(update)
}

private fun LatLng.toGms(): GmsLatLng = GmsLatLng(latitude, longitude)
private fun GmsLatLng.toCommon(): LatLng = LatLng(latitude, longitude)
private fun CameraPosition.toGmsCamera(): GmsCameraPosition = GmsCameraPosition.fromLatLngZoom(target.toGms(), zoom)
private fun GmsCameraPosition.toCommonCamera(): CameraPosition = CameraPosition(target.toCommon(), zoom)

