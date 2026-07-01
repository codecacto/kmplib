package br.com.codecacto.kmplib.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView

/**
 * Implementação iOS do [MapView] — **Google Maps NATIVO** (`GMSMapView`), em paridade com o
 * Android (`maps-compose`). A `GMSMapView` é criada no Swift (`iosApp/.../MapBridge.swift`) e
 * injetada via [IosMapBridge]; aqui ela é embutida no Compose por `UIKitView`. Os marcadores
 * são declarados como filhos `@Composable` ([MapMarker]) e reconciliados imperativamente na
 * `GMSMapView` (add/remove via [DisposableEffect]) — mesmo modelo do Android.
 *
 * Sem o bridge Swift registrado (`IosMapBridge.factory == null`), exibe um placeholder claro
 * (não quebra). O build/validação final é em host macOS (pod `GoogleMaps` + Xcode).
 */
actual class MapScope internal constructor(
    internal val markers: SnapshotStateList<IosMapMarkerData>,
    internal val clicks: MutableMap<String, () -> Unit>,
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
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Mapa indisponível: registre IosMapBridge.factory (GMSMapView) no app iOS.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LaunchedEffect(Unit) { onMapLoaded() }
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
    RegisterMarker(position, title, onClick)
}

@Composable
actual fun MapScope.MapMarker(
    position: LatLng,
    title: String,
    onClick: () -> Unit,
) {
    RegisterMarker(position, title, onClick)
}

/** Adiciona o pin ao escopo (e remove ao sair de composição) — reconciliado na GMSMapView. */
@Composable
private fun MapScope.RegisterMarker(position: LatLng, title: String, onClick: () -> Unit) {
    val id = remember(position.latitude, position.longitude, title) {
        "m_${position.latitude}_${position.longitude}_${title.hashCode()}"
    }
    DisposableEffect(id) {
        markers.add(IosMapMarkerData(id, position.latitude, position.longitude, title))
        clicks[id] = onClick
        onDispose {
            markers.removeAll { it.id == id }
            clicks.remove(id)
        }
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
