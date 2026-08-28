package br.com.codecacto.kmplib.map.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.location.LocationProvider
import br.com.codecacto.kmplib.map.CameraPosition
import br.com.codecacto.kmplib.map.LatLng
import br.com.codecacto.kmplib.map.MapMarker
import br.com.codecacto.kmplib.map.MapMarkerStatus
import br.com.codecacto.kmplib.map.MapView
import br.com.codecacto.kmplib.map.rememberCameraPositionState
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.AppTextField
import kotlinx.coroutines.launch

/**
 * Seletor de local (origem/destino) — busca de endereço/POI com autocomplete +
 * soltar pino no mapa por long-press + reverse geocode, retornando um [GeocodeResult]
 * (`{label, position, secondaryLabel}`).
 *
 * Reusa [MapView]/[MapMarker]/[rememberCameraPositionState] (módulo `map`) e o
 * [LocationProvider] (centra no GPS quando [initial] é nulo).
 *
 * **Degradação offline:** se a busca falhar (sem rede), exibe um aviso "rota/busca
 * indisponível offline" e o usuário ainda pode soltar o pino no mapa (cache) e digitar
 * o rótulo manualmente — cobre o cenário G-MF-M-05 (rota indisponível offline).
 *
 * Stateless quanto a navegação — o app exibe num diálogo/tela e trata [onPicked]/[onDismiss].
 *
 * @param geocoding provedor de busca/reverse (ex.: [OrsGeocodingProvider]).
 * @param initial posição inicial (origem conhecida); se nulo, tenta GPS via [locationProvider].
 * @param locationProvider opcional, para centralizar no GPS quando [initial] for nulo.
 * @param onPicked chamado ao confirmar a seleção.
 * @param onDismiss chamado ao cancelar.
 */
@Composable
fun PlacePicker(
    geocoding: GeocodingProvider,
    initial: LatLng? = null,
    modifier: Modifier = Modifier,
    locationProvider: LocationProvider? = null,
    title: String = "Selecionar local",
    searchPlaceholder: String = "Buscar endereço",
    confirmLabel: String = "Confirmar",
    onPicked: (GeocodeResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fallbackCenter = remember { LatLng(-15.7939, -47.8828) } // Brasília (centro do BR)
    val startCenter = initial ?: fallbackCenter
    val cameraState = rememberCameraPositionState(CameraPosition(startCenter, zoom = if (initial != null) 14f else 4f))

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var pinned by remember { mutableStateOf<GeocodeResult?>(initial?.let { GeocodeResult("Local selecionado", it) }) }
    var offlineNotice by remember { mutableStateOf(false) }
    var manualLabel by remember { mutableStateOf("") }

    // Centra no GPS quando não há posição inicial.
    LaunchedEffect(initial) {
        if (initial == null && locationProvider != null) {
            val loc = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            if (loc != null) cameraState.animateTo(CameraPosition(loc, zoom = 14f))
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)

        AppTextField(
            value = query,
            onValueChange = { value ->
                query = value
                scope.launch {
                    if (value.length >= 3) {
                        geocoding.search(value, near = cameraState.position.target)
                            .onSuccess { results = it; offlineNotice = false }
                            .onFailure { results = emptyList(); offlineNotice = true }
                    } else {
                        results = emptyList()
                    }
                }
            },
            label = searchPlaceholder,
            modifier = Modifier.fillMaxWidth(),
        )

        if (offlineNotice) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = "Busca de rota indisponível offline. Solte o pino no mapa e informe o local.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            AppTextField(
                value = manualLabel,
                onValueChange = { manualLabel = it },
                label = "Nome do local (manual)",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (results.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)) {
                items(results, key = { it.label + it.position.latitude }) { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pinned = r
                                query = r.label
                                results = emptyList()
                                scope.launch { cameraState.animateTo(CameraPosition(r.position, zoom = 16f)) }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(r.label, style = MaterialTheme.typography.bodyMedium)
                            r.secondaryLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            MapView(
                modifier = Modifier.fillMaxSize(),
                cameraPosition = cameraState.position,
                onMapLongClick = { latLng ->
                    // Solta o pino e tenta reverse geocode (best-effort).
                    pinned = GeocodeResult(label = pinned?.label ?: "Local selecionado", position = latLng)
                    scope.launch {
                        geocoding.reverse(latLng)
                            .onSuccess { pinned = it; offlineNotice = false }
                            .onFailure { offlineNotice = true }
                    }
                },
            ) {
                pinned?.let { p ->
                    MapMarker(position = p.position, status = MapMarkerStatus.FREE, title = p.label)
                }
            }
        }

        AppButton(
            text = confirmLabel,
            onClick = {
                val result = pinned?.let { p ->
                    if (offlineNotice && manualLabel.isNotBlank()) p.copy(label = manualLabel) else p
                }
                if (result != null) onPicked(result) else onDismiss()
            },
            enabled = pinned != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
