@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.map

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

/**
 * Ponto de injeção do mapa nativo iOS. O `ContentView.swift` deve setar [factory] no boot
 * (antes de exibir qualquer mapa), retornando uma `GMSMapView` encapsulada num [IosNativeMap].
 * Sem a fábrica registrada, o [MapView] exibe um placeholder claro (não quebra).
 */
object IosMapBridge {
    var factory: (() -> IosNativeMap)? = null
}
