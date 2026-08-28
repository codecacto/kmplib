package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/**
 * Coordenada geográfica — o tipo mora em [br.com.codecacto.kmplib.location.LatLng].
 *
 * Continua exposto aqui como alias porque `map` é onde os apps sempre o importaram; o tipo desceu
 * para `location` para quebrar o ciclo `map` ↔ `location` (ver o KDoc de lá). Alias e tipo são a
 * MESMA classe: `map.LatLng(-15.6, -56.1)` e `location.LatLng(-15.6, -56.1)` são intercambiáveis.
 */
typealias LatLng = br.com.codecacto.kmplib.location.LatLng

/**
 * Posição da câmera do mapa.
 *
 * @param target ponto central da câmera.
 * @param zoom nível de zoom (1 = mundo, ~14 = quadra/rua, ~18 = edifício).
 */
@Immutable
data class CameraPosition(
    val target: LatLng,
    val zoom: Float = 14f
)

/**
 * Status semântico de um marcador no mapa.
 *
 * Usado para colorir o pin via [color] (que resolve a cor a partir de
 * `AppTheme` — sem cores hardcoded na camada de produto).
 *
 * Nasceu para o app Exiba (mapa de outdoors), mas é genérico o bastante
 * para qualquer mapa de recursos com ciclo de disponibilidade.
 */
enum class MapMarkerStatus {
    /** Disponível / livre. */
    FREE,

    /** Ocupado / em uso. */
    OCCUPIED,

    /** Próximo do vencimento. */
    EXPIRING,

    /** Vencido. */
    EXPIRED,

    /** Em instalação / preparação. */
    INSTALLING
}

/**
 * Escopo de conteúdo do [MapView]. Marcadores ([MapMarker]) só podem ser
 * declarados dentro deste escopo.
 *
 * A implementação concreta (`actual`) carrega o slot/contexto nativo do SDK.
 */
@Stable
expect class MapScope

/**
 * Mapa Google Maps multiplataforma (Compose).
 *
 * - **Android:** `com.google.maps.android:maps-compose` (`GoogleMap`).
 * - **iOS:** Google Maps SDK for iOS via `UIViewControllerRepresentable`
 *   (placeholder neste ambiente — requer macOS + SDK para build final).
 *
 * @param modifier modificador Compose.
 * @param cameraPosition posição inicial da câmera. Para câmera controlável
 *   use [rememberCameraPositionState] e passe `state.position`.
 * @param onMapLoaded chamado quando o mapa termina de carregar.
 * @param onMapLongClick chamado em toque longo no mapa (útil para arrastar/
 *   posicionar um pin de cadastro). Recebe a [LatLng] tocada.
 * @param content marcadores e demais elementos declarados no [MapScope].
 */
@Composable
expect fun MapView(
    modifier: Modifier = Modifier,
    cameraPosition: CameraPosition,
    onMapLoaded: () -> Unit = {},
    onMapClick: ((LatLng) -> Unit)? = null,
    onMapLongClick: ((LatLng) -> Unit)? = null,
    content: @Composable MapScope.() -> Unit = {}
)

/**
 * Marcador (pin) colorido por [status] dentro de um [MapView].
 *
 * @param position coordenada do pin.
 * @param status status semântico — define a cor do pin via [MapMarkerStatus.color].
 * @param title título exibido no info window ao tocar.
 * @param onClick chamado ao tocar no pin.
 */
@Composable
expect fun MapScope.MapMarker(
    position: LatLng,
    status: MapMarkerStatus,
    title: String = "",
    onClick: () -> Unit = {}
)

/**
 * Marcador (pin) genérico — cor padrão do SDK (vermelho), sem semântica de status.
 * Para mapas comuns (ex.: clientes de uma agência) onde não há ciclo de disponibilidade.
 *
 * @param position coordenada do pin.
 * @param title título exibido no info window ao tocar.
 * @param onClick chamado ao tocar no pin.
 */
@Composable
expect fun MapScope.MapMarker(
    position: LatLng,
    title: String = "",
    onClick: () -> Unit = {}
)

/**
 * Estado controlável da câmera. Crie via [rememberCameraPositionState].
 *
 * Permite ler/escrever [position] e animar para um novo ponto com [animateTo].
 */
@Stable
expect class CameraPositionState {
    var position: CameraPosition

    /** Anima suavemente a câmera até [position]. */
    fun animateTo(position: CameraPosition)
}

/**
 * Lembra e retém um [CameraPositionState] entre recomposições.
 *
 * @param initial posição inicial da câmera.
 */
@Composable
expect fun rememberCameraPositionState(initial: CameraPosition): CameraPositionState
