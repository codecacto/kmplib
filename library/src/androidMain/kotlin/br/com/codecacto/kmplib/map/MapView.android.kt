package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState as rememberGmsCameraPositionState
import com.google.android.gms.maps.model.CameraPosition as GmsCameraPosition
import com.google.android.gms.maps.model.LatLng as GmsLatLng

/**
 * No Android, o conteúdo do mapa é declarado dentro do slot do `GoogleMap` do
 * maps-compose. O [MapScope] carrega o estado de câmera nativo para que
 * [animateTo] funcione, mas marcadores são Composables comuns do maps-compose.
 */
actual class MapScope internal constructor(
    internal val gmsCameraState: com.google.maps.android.compose.CameraPositionState
)

@Composable
actual fun MapView(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    onMapLoaded: () -> Unit,
    onMapLongClick: ((LatLng) -> Unit)?,
    content: @Composable MapScope.() -> Unit
) {
    val gmsCameraState = rememberGmsCameraPositionState {
        position = GmsCameraPosition.fromLatLngZoom(
            cameraPosition.target.toGms(),
            cameraPosition.zoom
        )
    }

    val scope = remember(gmsCameraState) { MapScope(gmsCameraState) }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = gmsCameraState,
        properties = remember { MapProperties(mapType = MapType.NORMAL) },
        onMapLoaded = onMapLoaded,
        onMapLongClick = onMapLongClick?.let { cb -> { latLng -> cb(latLng.toCommon()) } } ?: {},
        content = { scope.content() }
    )
}

@Composable
actual fun MapScope.MapMarker(
    position: LatLng,
    status: MapMarkerStatus,
    title: String,
    onClick: () -> Unit
) {
    val markerState = remember(position) { MarkerState(position = position.toGms()) }
    Marker(
        state = markerState,
        title = title.ifBlank { null },
        icon = BitmapDescriptorFactory.defaultMarker(status.markerHue()),
        onClick = {
            onClick()
            false // false = comportamento padrão (abre info window)
        }
    )
}

actual class CameraPositionState internal constructor(
    internal val gmsState: com.google.maps.android.compose.CameraPositionState
) {
    actual var position: CameraPosition
        get() = gmsState.position.toCommon()
        set(value) {
            gmsState.position = GmsCameraPosition.fromLatLngZoom(
                value.target.toGms(),
                value.zoom
            )
        }

    actual fun animateTo(position: CameraPosition) {
        gmsState.move(
            CameraUpdateFactory.newLatLngZoom(position.target.toGms(), position.zoom)
        )
    }
}

@Composable
actual fun rememberCameraPositionState(initial: CameraPosition): CameraPositionState {
    val gmsState = rememberGmsCameraPositionState {
        position = GmsCameraPosition.fromLatLngZoom(initial.target.toGms(), initial.zoom)
    }
    return remember(gmsState) { CameraPositionState(gmsState) }
}

// -----------------------------------------------------------------------------
// Conversões e mapeamento de cor → hue do Google Maps
// -----------------------------------------------------------------------------

private fun LatLng.toGms(): GmsLatLng = GmsLatLng(latitude, longitude)
private fun GmsLatLng.toCommon(): LatLng = LatLng(latitude, longitude)
private fun GmsCameraPosition.toCommon(): CameraPosition =
    CameraPosition(target = target.toCommon(), zoom = zoom)

/**
 * Hue (0..360) do `BitmapDescriptorFactory` por status. Aproxima as cores
 * semânticas do tema (success/primary/warning/error/info) aos hues padrão do
 * Google Maps, que só aceita matiz (não cor RGB arbitrária) no pin default.
 */
private fun MapMarkerStatus.markerHue(): Float = when (this) {
    MapMarkerStatus.FREE -> BitmapDescriptorFactory.HUE_GREEN      // disponível
    MapMarkerStatus.OCCUPIED -> BitmapDescriptorFactory.HUE_VIOLET // em uso (marca)
    MapMarkerStatus.EXPIRING -> BitmapDescriptorFactory.HUE_ORANGE // vencendo
    MapMarkerStatus.EXPIRED -> BitmapDescriptorFactory.HUE_RED     // vencido
    MapMarkerStatus.INSTALLING -> BitmapDescriptorFactory.HUE_AZURE // instalando
}
