package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** iOS do [NativeMap]: MapKit ([MapKitMap]). */
@Composable
internal actual fun PlatformNativeMap(
    markers: List<ResolvedMarker>,
    polylines: List<ResolvedPolyline>,
    circles: List<ResolvedCircle>,
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
    )
}
