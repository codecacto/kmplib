@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.map

import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIView
import kotlin.native.ObjCName

/** Marcador entregue ao mapa nativo iOS (lado Swift / GMSMarker). */
@ObjCName("IosMapMarkerData")
data class IosMapMarkerData(
    val id: String,
    val lat: Double,
    val lng: Double,
    val title: String,
)

/**
 * Controlador de um mapa **nativo** iOS (Google Maps SDK / `GMSMapView`), IMPLEMENTADO em Swift
 * no app (`iosApp/iosApp/MapBridge.swift`) e injetado via [IosMapBridge.factory].
 *
 * Espelha o padrão já usado neste app para recursos nativos do iOS (ex.: `googleSignInHandler`
 * em `MainViewController`): o Kotlin define o contrato, o Swift implementa com o SDK nativo
 * (o pacote **SPM `googlemaps/ios-maps-sdk`** adicionado no Xcode — SPM é inegociável no
 * ecossistema; o pod `GoogleMaps` foi descontinuado no Q2/2026), e a UI Compose embute a `view`
 * via `UIKitView`. Assim o mapa iOS é Google Maps NATIVO — paridade com o Android (`maps-compose`),
 * sem cinterop na lib.
 */
@ObjCName("IosNativeMap")
interface IosNativeMap {
    /** A `GMSMapView` (como `UIView`) a ser embutida no Compose via `UIKitView`. */
    val view: UIView

    /** Move a câmera para o ponto/zoom. */
    fun setCamera(lat: Double, lng: Double, zoom: Float)

    /** Redesenha o conjunto de pins. */
    fun setMarkers(markers: List<IosMapMarkerData>)

    /** Habilita/desabilita gestos (scroll/zoom). */
    fun setInteractive(interactive: Boolean)

    /** Callback de toque num pin (recebe o id do [IosMapMarkerData]). */
    fun setOnMarkerClick(onClick: (String) -> Unit)

    /** Callback de toque no mapa (lat, lng) — usado no modo picker. */
    fun setOnMapClick(onClick: (Double, Double) -> Unit)

    /** Desabilita o callback de toque no mapa. */
    fun clearOnMapClick()
}

/** Coordenada entregue ao Swift (trajeto de [IosMapPolylineData]). */
@ObjCName("IosMapCoordinate")
data class IosMapCoordinate(
    val lat: Double,
    val lng: Double,
)

/**
 * Pino do [NativeMap] já resolvido pela lib (2.331.0): o ícone vem DESENHADO (cor do item ou do grupo,
 * texto curto, contador), e o Swift só o põe no mapa — `GMSMarker.icon = icon`,
 * `groundAnchor = (anchorX, anchorY)`. [id] é o que volta em `setOnMarkerClick` (pino ou grupo; a
 * lib sabe qual é qual).
 */
@ObjCName("IosMapPinData")
data class IosMapPinData(
    val id: String,
    val lat: Double,
    val lng: Double,
    /** Título do balão; `null` = sem balão (grupos não têm). */
    val title: String?,
    val snippet: String?,
    val icon: UIImage,
    val anchorX: Double,
    val anchorY: Double,
    /** Quem fica por cima quando se sobrepõem (maior = por cima). */
    val zIndex: Int,
)

/** Linha do [NativeMap] (rota): cor e espessura (pt) já resolvidas. */
@ObjCName("IosMapPolylineData")
data class IosMapPolylineData(
    val id: String,
    val points: List<IosMapCoordinate>,
    val color: UIColor,
    val width: Double,
)

/** Círculo de raio em metros do [NativeMap], com as cores já resolvidas. */
@ObjCName("IosMapCircleData")
data class IosMapCircleData(
    val id: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Double,
    val fillColor: UIColor,
    val strokeColor: UIColor,
    val strokeWidth: Double,
)

/**
 * O mapa Google do iPhone com o que o [NativeMap] precisa (2.331.0) — contrato ADITIVO: estende o
 * [IosNativeMap] sem mudar nada nele, então os `MapBridge.swift` antigos continuam compilando (e o
 * `NativeMap` deles segue no MapKit até o app ganhar a ponte nova).
 *
 * A lib decide tudo — agrupamento (mesma régua de 100 pt do Android), cor do grupo, enquadramento,
 * desenho dos ícones — e o Swift só desenha. Cada `set…` substitui o conjunto inteiro daquele tipo, e
 * a lib só chama quando algo mudou. Implementação de referência: `MapBridge.swift` da `casca-mobile`
 * (`GMSMarker`, `GMSPolyline`, `GMSCircle`, `GMSMapViewDelegate.idleAt`/`didLongPressAt`).
 */
@ObjCName("IosNativeRichMap")
interface IosNativeRichMap : IosNativeMap {
    /** Redesenha os pinos do [NativeMap] (itens e grupos) — o conjunto inteiro. */
    fun setPins(pins: List<IosMapPinData>)

    /** Redesenha as rotas — o conjunto inteiro. */
    fun setPolylines(polylines: List<IosMapPolylineData>)

    /** Redesenha os círculos — o conjunto inteiro (por baixo das rotas e dos pinos). */
    fun setCircles(circles: List<IosMapCircleData>)

    /** Move a câmera; [animated] = transição suave (`animate(to:)`) em vez de salto (`camera =`). */
    fun moveCamera(lat: Double, lng: Double, zoom: Float, animated: Boolean)

    /** Liga/desliga o ponto azul do usuário (`isMyLocationEnabled`) — só com a permissão já dada. */
    fun setShowsUserLocation(show: Boolean)

    /** Câmera parou (lat, lng, zoom) — `GMSMapViewDelegate.mapView(_:idleAt:)`. */
    fun setOnCameraIdle(onIdle: (Double, Double, Float) -> Unit)

    /** Toque longo no mapa (lat, lng) — `GMSMapViewDelegate.mapView(_:didLongPressAt:)`. */
    fun setOnMapLongClick(onClick: (Double, Double) -> Unit)
}

/**
 * Ponto de injeção do mapa nativo iOS — **o padrão da fábrica** (mapa = Google Maps, nenhum outro;
 * fundador, 27/out/2026). No `AppDelegate`, depois de `GMSServices.provideAPIKey(...)` e antes de
 * qualquer tela:
 *
 * ```swift
 * IosMapBridge.shared.register(factory: { GoogleMapController() })
 * ```
 *
 * ([register], 2.331.0, liga o [MapView] e o [NativeMap]; a `casca-mobile` já nasce assim, com o
 * `MapBridge.swift`.) O antigo `IosMapBridge.shared.factory = { … }` continua valendo, mas só para o
 * [MapView].
 *
 * Sem ponte, os mapas caem no **MapKit** (fallback de app ainda não migrado; não quebra) e um aviso
 * vai ao log uma vez por processo. Só registre com a chave do Google presente: sem
 * `provideAPIKey`, a `GMSMapView` derruba o app ao abrir.
 */
object IosMapBridge {
    /** Fábrica do mapa simples ([MapView]). Preenchida também por [register]. */
    var factory: (() -> IosNativeMap)? = null

    /** Fábrica do mapa completo ([NativeMap]); `null` = o `NativeMap` usa o MapKit. */
    var richFactory: (() -> IosNativeRichMap)? = null

    /** Registra o mapa Google para o [MapView] **e** o [NativeMap] (2.331.0). */
    fun register(factory: () -> IosNativeRichMap) {
        this.richFactory = factory
        this.factory = { factory() }
    }
}
