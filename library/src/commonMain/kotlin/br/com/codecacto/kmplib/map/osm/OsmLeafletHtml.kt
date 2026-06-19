package br.com.codecacto.kmplib.map.osm

/**
 * Gerador (commonMain puro) do **HTML+JS do Leaflet** renderizado dentro do WebView
 * pelos mapas OSM. Centraliza o template numa única fonte de verdade — testável sem
 * UI — e garante que Android e iOS desenhem exatamente o mesmo mapa, com o **mesmo
 * stack da web** (`leaflet` + tiles `tile.openstreetmap.org`).
 *
 * ## Bridge JS → Kotlin
 * O JS posta mensagens via `window.kmpBridge.postMessage(json)`:
 *  - clique em pin:  `{"type":"marker","id":"<id>"}`
 *  - clique no mapa: `{"type":"map","lat":<num>,"lng":<num>}` (só no modo picker)
 *
 * Cada `actual` injeta um objeto JS chamado `kmpBridge` com um método `postMessage`:
 *  - Android: `WebView.addJavascriptInterface(obj, "kmpBridge")` (o método anotado
 *    com `@JavascriptInterface` chama de volta o Kotlin).
 *  - iOS: `WKUserContentController.addScriptMessageHandler` ouvindo o nome `kmpBridge`;
 *    um pequeno shim define `window.kmpBridge.postMessage = (m) =>
 *    window.webkit.messageHandlers.kmpBridge.postMessage(m)`.
 *
 * O Leaflet é carregado do CDN unpkg (mesma origem usada pelo ecossistema web). Como o
 * mapa exige rede para os tiles de qualquer forma, depender do CDN para o JS/CSS não
 * adiciona uma restrição nova.
 */
internal object OsmLeafletHtml {

    private const val LEAFLET_VERSION = "1.9.4"
    private const val TILE_URL = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    private const val TILE_ATTRIB =
        "&copy; <a href=\\\"https://www.openstreetmap.org/copyright\\\">OpenStreetMap</a> contributors"

    /**
     * Monta o documento HTML completo para [spec].
     *
     * @param withWebkitShim quando `true` (iOS/WKWebView), injeta o shim que mapeia
     *   `window.kmpBridge.postMessage` → `window.webkit.messageHandlers.kmpBridge`.
     *   No Android o objeto `kmpBridge` é injetado nativamente, então fica `false`.
     */
    fun build(spec: OsmMapSpec, withWebkitShim: Boolean): String {
        val markersJson = spec.markers.joinToString(separator = ",") { m ->
            "{\"id\":${jsonString(m.id)},\"lat\":${m.position.latitude}," +
                "\"lng\":${m.position.longitude},\"title\":${jsonString(m.title)}}"
        }

        val shim = if (withWebkitShim) {
            """
            window.kmpBridge = {
              postMessage: function (m) {
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.kmpBridge) {
                  window.webkit.messageHandlers.kmpBridge.postMessage(m);
                }
              }
            };
            """.trimIndent()
        } else {
            ""
        }

        return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="stylesheet" href="https://unpkg.com/leaflet@$LEAFLET_VERSION/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@$LEAFLET_VERSION/dist/leaflet.js"></script>
  <style>
    html, body, #map { height: 100%; margin: 0; padding: 0; }
    #map { width: 100%; }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>
    $shim
    function send(obj) {
      try { if (window.kmpBridge && window.kmpBridge.postMessage) window.kmpBridge.postMessage(JSON.stringify(obj)); } catch (e) {}
    }
    var markers = [$markersJson];
    var map = L.map('map', {
      scrollWheelZoom: ${spec.interactive},
      dragging: ${spec.interactive},
      touchZoom: ${spec.interactive},
      doubleClickZoom: ${spec.interactive},
      boxZoom: ${spec.interactive},
      keyboard: ${spec.interactive},
      zoomControl: ${spec.interactive}
    }).setView([${spec.center.latitude}, ${spec.center.longitude}], ${spec.zoom});
    L.tileLayer('$TILE_URL', { attribution: '$TILE_ATTRIB', maxZoom: 19 }).addTo(map);
    markers.forEach(function (m) {
      var mk = L.marker([m.lat, m.lng]).addTo(map);
      if (m.title) { mk.bindPopup(m.title); }
      mk.on('click', function () { send({ type: 'marker', id: m.id }); });
    });
    ${if (spec.pickable) "map.on('click', function (e) { send({ type: 'map', lat: e.latlng.lat, lng: e.latlng.lng }); });" else ""}
  </script>
</body>
</html>
        """.trimIndent()
    }

    /** Escapa uma string para um literal JSON seguro (aspas, barras, controles). */
    fun jsonString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') {
                    sb.append("\\u")
                    val hex = c.code.toString(16)
                    repeat(4 - hex.length) { sb.append('0') }
                    sb.append(hex)
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Parser leniente da mensagem da bridge (commonMain puro, sem kotlinx-json para não
     * carregar dependência no caminho do WebView). Reconhece os dois formatos emitidos
     * pelo template. Retorna `null` para mensagens irreconhecíveis.
     */
    fun parseBridgeMessage(raw: String): OsmBridgeMessage? {
        val type = extractString(raw, "type") ?: return null
        return when (type) {
            "marker" -> extractString(raw, "id")?.let { OsmBridgeMessage.MarkerClick(it) }
            "map" -> {
                val lat = extractNumber(raw, "lat")
                val lng = extractNumber(raw, "lng")
                if (lat != null && lng != null) OsmBridgeMessage.MapClick(OsmLatLng(lat, lng)) else null
            }
            else -> null
        }
    }

    private fun extractString(json: String, key: String): String? {
        val marker = "\"$key\""
        val k = json.indexOf(marker)
        if (k < 0) return null
        var i = k + marker.length
        while (i < json.length && json[i] != ':') i++
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                val n = json[i + 1]
                sb.append(
                    when (n) {
                        'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> n
                    },
                )
                i += 2
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c); i++
            }
        }
        return null
    }

    private fun extractNumber(json: String, key: String): Double? {
        val marker = "\"$key\""
        val k = json.indexOf(marker)
        if (k < 0) return null
        var i = k + marker.length
        while (i < json.length && json[i] != ':') i++
        i++
        while (i < json.length && json[i].isWhitespace()) i++
        val start = i
        while (i < json.length && (json[i].isDigit() || json[i] == '-' || json[i] == '+' || json[i] == '.' || json[i] == 'e' || json[i] == 'E')) i++
        return json.substring(start, i).toDoubleOrNull()
    }
}

/** Mensagem decodificada da bridge JS → Kotlin. */
internal sealed interface OsmBridgeMessage {
    data class MarkerClick(val id: String) : OsmBridgeMessage
    data class MapClick(val position: OsmLatLng) : OsmBridgeMessage
}
