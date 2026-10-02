package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    content: @Composable MapScope.() -> Unit
) {
    val gmsCameraState = rememberGmsCameraPositionState {
        position = cameraPosition.toGmsCamera()
    }

    val scope = remember(gmsCameraState) { MapScope(gmsCameraState) }

    // O mesmo portão do NativeMap: `CameraUpdateFactory` só existe depois que o SDK inicializou, e
    // quem garante isso é o mapa ter carregado. Antes disso a posição é escrita direto no estado
    // (`position = …` não passa pela fábrica) e o mapa já nasce nela.
    var loaded by remember { mutableStateOf(false) }
    val onLoaded by rememberUpdatedState(onMapLoaded)
    val applied = remember { arrayOf(cameraPosition) }

    LaunchedEffect(cameraPosition, loaded) {
        when (cameraSyncFor(requested = cameraPosition, applied = applied[0], loaded = loaded)) {
            CameraSync.NONE -> Unit
            CameraSync.SET_BEFORE_LOAD -> {
                applied[0] = cameraPosition
                gmsCameraState.position = cameraPosition.toGmsCamera()
            }
            CameraSync.ANIMATE -> {
                applied[0] = cameraPosition
                gmsCameraState.animate(CameraUpdateFactory.newCameraPosition(cameraPosition.toGmsCamera()))
            }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = gmsCameraState,
        properties = remember { MapProperties(mapType = MapType.NORMAL) },
        onMapLoaded = {
            loaded = true
            onLoaded()
        },
        onMapClick = onMapClick?.let { cb -> { latLng -> cb(latLng.toCommon()) } } ?: {},
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

@Composable
actual fun MapScope.MapMarker(
    position: LatLng,
    title: String,
    onClick: () -> Unit
) {
    val markerState = remember(position) { MarkerState(position = position.toGms()) }
    Marker(
        state = markerState,
        title = title.ifBlank { null },
        onClick = {
            onClick()
            false
        }
    )
}

/**
 * Estado da câmera no Android: **só o pedido**, como no iOS — uma posição observável que o
 * [MapView] lê por `cameraPosition = state.position` e aplica no mapa.
 *
 * Até a 2.241.1 esta classe embrulhava um `CameraPositionState` do maps-compose que **nunca era
 * ligado a mapa nenhum** (o [MapView] cria o dele), e o [animateTo] montava o comando com
 * `CameraUpdateFactory` — que só existe depois de o Maps SDK inicializar. Chamado cedo (o
 * `LaunchedEffect` que centra no GPS ou no primeiro pino) derrubava o app com
 * `NullPointerException: CameraUpdateFactory is not initialized`; chamado tarde, não movia nada.
 * Agora não toca no SDK: pode ser chamado a qualquer momento, antes ou depois de o mapa existir.
 */
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

/** O que o [MapView] faz quando a `cameraPosition` pedida muda. */
internal enum class CameraSync {
    /** A posição pedida já é a aplicada. */
    NONE,

    /** O mapa ainda não carregou: escreve a posição no estado, sem `CameraUpdateFactory`. */
    SET_BEFORE_LOAD,

    /** O mapa carregou: anima até a posição pedida. */
    ANIMATE,
}

/**
 * Decide como levar a câmera até [requested].
 *
 * A regra que evita o crash está aqui: **sem mapa carregado nunca se devolve [CameraSync.ANIMATE]**,
 * o único caminho que usa `CameraUpdateFactory`. E posição igual à já aplicada não gera comando —
 * senão o próprio carregamento do mapa animaria até onde ele já está, interrompendo o gesto de quem
 * começou a arrastar.
 */
internal fun cameraSyncFor(requested: CameraPosition, applied: CameraPosition, loaded: Boolean): CameraSync = when {
    requested == applied -> CameraSync.NONE
    loaded -> CameraSync.ANIMATE
    else -> CameraSync.SET_BEFORE_LOAD
}

// -----------------------------------------------------------------------------
// Conversões e mapeamento de cor → hue do Google Maps
// -----------------------------------------------------------------------------

private fun LatLng.toGms(): GmsLatLng = GmsLatLng(latitude, longitude)
private fun CameraPosition.toGmsCamera(): GmsCameraPosition = GmsCameraPosition.fromLatLngZoom(target.toGms(), zoom)
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
