package br.com.codecacto.kmplib.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Implementação iOS do [MapView].
 *
 * TODO(GAP-02/iOS): substituir o placeholder por `UIViewControllerRepresentable`
 * wrappando `GMSMapView` do **Google Maps SDK for iOS**. Requer:
 *  1. Adicionar o pod/SPM `GoogleMaps` ao Xcode do app consumidor.
 *  2. Configurar um cinterop `def` para `GoogleMaps` (como já existe para
 *     `GoogleMobileAds` em `src/nativeInterop/cinterop/`).
 *  3. Inicializar a API key via `GMSServices.provideAPIKey(...)` no boot iOS.
 *
 * O SDK iOS não está disponível neste ambiente Windows; o build final iOS
 * exige host macOS. Até lá, exibe um placeholder claro.
 */
@Suppress("UNUSED_PARAMETER")
actual class MapScope internal constructor()

@Composable
actual fun MapView(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    onMapLoaded: () -> Unit,
    onMapLongClick: ((LatLng) -> Unit)?,
    content: @Composable MapScope.() -> Unit
) {
    val scope = remember { MapScope() }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "MapView (Google Maps iOS — requer macOS para build)",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
        // Conteúdo (markers) compõe no escopo, mas é no-op no placeholder.
        scope.content()
    }
    onMapLoaded()
}

@Composable
actual fun MapScope.MapMarker(
    position: LatLng,
    status: MapMarkerStatus,
    title: String,
    onClick: () -> Unit
) {
    // TODO(GAP-02/iOS): renderizar GMSMarker com cor por status no GMSMapView.
    // No placeholder não há nada a desenhar.
}

actual class CameraPositionState internal constructor(initial: CameraPosition) {
    actual var position: CameraPosition by mutableStateOf(initial)

    actual fun animateTo(position: CameraPosition) {
        // TODO(GAP-02/iOS): GMSMapView.animateToCameraPosition(...)
        this.position = position
    }
}

@Composable
actual fun rememberCameraPositionState(initial: CameraPosition): CameraPositionState {
    return remember { CameraPositionState(initial) }
}
