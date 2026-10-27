package br.com.codecacto.kmplib.map

/**
 * Aviso de que um mapa do iOS (`MapView` ou `NativeMap`) caiu no **MapKit** por falta da ponte do
 * Google (2.331.0).
 *
 * Mapa na fábrica = **Google Maps, nenhum outro** (fundador, 27/out/2026). No iOS o Google entra pela
 * ponte Swift `IosMapBridge` (a `casca-mobile` nasce com ela: `MapBridge.swift` + registro no
 * `AppDelegate`). Sem a ponte, o `MapView` continua funcionando sobre o MapKit — é só o **fallback**
 * de app ainda não migrado, que troca quando o app for tocado — e registra este aviso **uma vez por
 * processo** no log, para o fallback não passar despercebido.
 *
 * Mora em `commonMain` só para a regra "uma vez por processo" ser testável aqui; quem chama é o
 * `actual` do iOS.
 */
internal object IosMapFallbackNotice {
    const val TAG: String = "KmpLibMap"

    const val MESSAGE: String =
        "Mapa no iOS sem a ponte do Google Maps: usando o MapKit como fallback. " +
            "O padrão da fábrica é Google Maps — registre IosMapBridge.shared.register(factory: { GoogleMapController() }) " +
            "no AppDelegate, depois de GMSServices.provideAPIKey (MapBridge.swift e docs/MAP_SETUP.md da casca-mobile)."

    private var warned: Boolean = false

    /** `true` só na primeira chamada do processo; quem recebe `true` registra o aviso. */
    fun claim(): Boolean {
        if (warned) return false
        warned = true
        return true
    }

    /** Volta ao estado inicial — só para teste. */
    internal fun resetForTest() {
        warned = false
    }
}
