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
 * - **iOS:** **Google Maps pela ponte** — a `GMSMapView` do app, registrada em
 *   `IosMapBridge.factory` (padrão da fábrica: mapa = Google Maps, nenhum outro; roteiro do app com
 *   mapa em `docs/MAP_SETUP.md` da `casca-mobile`). Sem ponte, cai no **MapKit** (`MKMapView`) como fallback de app ainda não
 *   migrado — funciona e avisa no log uma vez por processo; troca quando o app for tocado.
 *
 * Para mapa de carteira (muitos pinos coloridos, agrupamento, rota, posição do usuário, enquadrar
 * pontos), use [NativeMap] — este aqui é o mapa simples de pinos declarados como filhos.
 *
 * @param modifier modificador Compose.
 * @param cameraPosition posição da câmera. Para câmera controlável use
 *   [rememberCameraPositionState] e passe `state.position`: quando ela muda, o mapa vai até lá
 *   (nas duas plataformas desde a 2.241.2 — antes o Android só lia o valor inicial).
 * @param onMapLoaded chamado quando o mapa termina de carregar.
 * @param onMapLongClick chamado em toque longo no mapa (útil para arrastar/
 *   posicionar um pin de cadastro). Recebe a [LatLng] tocada.
 * @param interactive `false` = mapa **só leitura** (2.331.0): sem arrastar, pinçar, girar nem inclinar
 *   — o mapa da ficha, do cartão na lista. No Android vira o **modo lite** do Google Maps (imagem do
 *   mapa, que respeita o recorte de cantos arredondados que a `SurfaceView` do mapa vivo ignora, e não
 *   anima câmera); no iOS chama `setInteractive(false)` na ponte (ou desliga os gestos do MapKit). Toque
 *   no pino continua chegando ao `onClick`.
 * @param content marcadores e demais elementos declarados no [MapScope].
 */
@Composable
expect fun MapView(
    modifier: Modifier = Modifier,
    cameraPosition: CameraPosition,
    onMapLoaded: () -> Unit = {},
    onMapClick: ((LatLng) -> Unit)? = null,
    onMapLongClick: ((LatLng) -> Unit)? = null,
    interactive: Boolean = true,
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
 *
 * É o **pedido** de câmera, não o espelho do mapa: [position] devolve a última posição pedida, e
 * não acompanha o arrasto de quem mexe no mapa com o dedo. Para o estado real da câmera, enquadrar
 * pontos e saber quando o mapa parou, use o [NativeMap] com `rememberMapController()`.
 *
 * Pode ser usado **antes de o mapa existir** (ex.: num `LaunchedEffect` que centra no GPS): o
 * pedido fica guardado e o mapa nasce nele.
 */
@Stable
expect class CameraPositionState {
    var position: CameraPosition

    /**
     * Leva a câmera até [position]: animada se o mapa já carregou; se ainda não, é onde ele abre.
     * Pedir a posição que já é a pedida não faz nada.
     */
    fun animateTo(position: CameraPosition)
}

/**
 * Lembra e retém um [CameraPositionState] entre recomposições.
 *
 * @param initial posição inicial da câmera.
 */
@Composable
expect fun rememberCameraPositionState(initial: CameraPosition): CameraPositionState
