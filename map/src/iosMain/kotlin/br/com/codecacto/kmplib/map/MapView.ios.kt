package br.com.codecacto.kmplib.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView

/**
 * Implementação iOS do [MapView].
 *
 * - **Padrão (2.220.0): MapKit** ([MapKitMap]) — o SDK oficial da Apple, sem chave e sem ponte
 *   Swift. Até a 2.219.0, sem ponte registrada este mapa mostrava só o texto "Mapa indisponível".
 * - **Com [IosMapBridge.factory] registrada**: a `GMSMapView` do app (Google Maps via Swift), como
 *   antes — quem já tinha a ponte continua exatamente igual.
 *
 * Os marcadores são declarados como filhos `@Composable` ([MapMarker]) e reconciliados
 * imperativamente no mapa nativo (add/remove via [DisposableEffect]) — mesmo modelo do Android.
 */
actual class MapScope internal constructor(
    internal val markers: SnapshotStateList<IosMapMarkerData>,
    internal val clicks: MutableMap<String, () -> Unit>,
    /** Cor de cada pino (id → cor; `null` = cor padrão) — usada pelo caminho MapKit. */
    internal val colors: SnapshotStateMap<String, Color?> = mutableStateMapOf(),
)

@Composable
actual fun MapView(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    onMapLoaded: () -> Unit,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    content: @Composable MapScope.() -> Unit,
) {
    val factory = IosMapBridge.factory
    if (factory == null) {
        MapKitMapView(modifier, cameraPosition, onMapLoaded, onMapClick, onMapLongClick, content)
        return
    }

    val markers = remember { mutableStateListOf<IosMapMarkerData>() }
    val clicks = remember { mutableMapOf<String, () -> Unit>() }
    val scope = remember(markers, clicks) { MapScope(markers, clicks) }
    val nativeMap = remember { factory() }

    LaunchedEffect(nativeMap) {
        nativeMap.setOnMarkerClick { id -> clicks[id]?.invoke() }
        onMapLoaded()
    }
    LaunchedEffect(cameraPosition) {
        nativeMap.setCamera(cameraPosition.target.latitude, cameraPosition.target.longitude, cameraPosition.zoom)
    }
    LaunchedEffect(onMapClick) {
        val cb = onMapClick
        if (cb != null) {
            nativeMap.setOnMapClick { lat, lng -> cb(LatLng(lat, lng)) }
        } else {
            nativeMap.clearOnMapClick()
        }
    }
    // Reconcilia os pins sempre que a lista (preenchida pelos MapMarker filhos) muda.
    SideEffect { nativeMap.setMarkers(markers.toList()) }

    Box(modifier = modifier) {
        UIKitView(factory = { nativeMap.view }, modifier = Modifier.fillMaxSize())
        // Executa os filhos para registrar/desregistrar marcadores no escopo.
        scope.content()
    }
}

@Composable
actual fun MapScope.MapMarker(
    position: LatLng,
    status: MapMarkerStatus,
    title: String,
    onClick: () -> Unit,
) {
    RegisterMarker(position, title, status.color(), onClick)
}

@Composable
actual fun MapScope.MapMarker(
    position: LatLng,
    title: String,
    onClick: () -> Unit,
) {
    RegisterMarker(position, title, null, onClick)
}

/** Adiciona o pin ao escopo (e remove ao sair de composição) — reconciliado na GMSMapView. */
@Composable
private fun MapScope.RegisterMarker(position: LatLng, title: String, color: Color?, onClick: () -> Unit) {
    val id = remember(position.latitude, position.longitude, title) {
        "m_${position.latitude}_${position.longitude}_${title.hashCode()}"
    }
    DisposableEffect(id) {
        markers.add(IosMapMarkerData(id, position.latitude, position.longitude, title))
        clicks[id] = onClick
        onDispose {
            markers.removeAll { it.id == id }
            clicks.remove(id)
            colors.remove(id)
        }
    }
    SideEffect { colors[id] = color }
}

/** O [MapView] sobre MapKit: os filhos registram pinos no escopo e o MapKit os desenha. */
@Composable
private fun MapKitMapView(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    onMapLoaded: () -> Unit,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    content: @Composable MapScope.() -> Unit,
) {
    val scope = remember { MapScope(mutableStateListOf(), mutableMapOf()) }
    val controller = rememberMapController(cameraPosition)
    LaunchedEffect(cameraPosition) { controller.moveTo(cameraPosition, animate = false) }
    val primary = MaterialTheme.colorScheme.primary
    val resolved = scope.markers.map { m ->
        resolveMarker(
            MapItem(
                id = m.id,
                position = LatLng(m.lat, m.lng),
                title = m.title,
                style = scope.colors[m.id]?.let { MapMarkerStyle(it) },
            ),
            primary,
        )
    }
    Box(modifier = modifier) {
        MapKitMap(
            markers = resolved,
            polylines = emptyList(),
            controller = controller,
            clustering = false,
            showUserLocation = false,
            defaultColor = primary,
            onMarkerClick = { item -> scope.clicks[item.id]?.invoke() },
            onClusterClick = {},
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            onLoaded = onMapLoaded,
            modifier = Modifier.fillMaxSize(),
        )
        scope.content()
    }
}

actual class CameraPositionState internal constructor(initial: CameraPosition) {
    actual var position: CameraPosition by mutableStateOf(initial)

    actual fun animateTo(position: CameraPosition) {
        this.position = position
    }
}

@Composable
actual fun rememberCameraPositionState(initial: CameraPosition): CameraPositionState {
    return remember { CameraPositionState(initial) }
}
