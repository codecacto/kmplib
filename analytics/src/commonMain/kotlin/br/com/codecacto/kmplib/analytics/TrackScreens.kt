package br.com.codecacto.kmplib.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination

/**
 * **Rastreio automático de tela** (2.361.0, opt-in): uma visita no Umami a cada destino novo do
 * [navController] — ponha UMA vez, logo depois do `NavHost`, na raiz do app.
 *
 * ```kotlin
 * val navController = rememberNavController()
 * NavHost(navController, startDestination = Route.Home) { … }
 * TrackNavigationScreens(navController)
 * ```
 *
 * - O nome sai da ROTA do destino ([screenNameForRoute]): `Route.ItemDetail` → `/app/item-detail`. O
 *   VALOR dos argumentos nunca sai (`/app/item-detail`, não `/app/item-detail/123`).
 * - Conta uma vez por ENTRADA da pilha: voltar para uma tela conta de novo (é uma visita nova); girar o
 *   aparelho, recompor ou o app voltar do segundo plano não conta (a última entrada contada é guardada
 *   com `rememberSaveable`).
 * - [screenName] troca o nome (ex.: `{ d -> if (d.hasRoute<Route.Debug>()) null else screenNameForRoute(d.route) }`);
 *   `null` = não conta aquele destino.
 * - Sem `Analytics.initialize` (ou desligado) não faz nada.
 */
@Composable
fun TrackNavigationScreens(
    navController: NavController,
    enabled: Boolean = true,
    screenName: (NavDestination) -> String? = { screenNameForRoute(it.route) },
) {
    var ultimaEntrada by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(navController, enabled) {
        if (!enabled) return@LaunchedEffect
        navController.currentBackStackEntryFlow.collect { entry ->
            if (entry.id == ultimaEntrada) return@collect
            ultimaEntrada = entry.id
            val nome = runCatching { screenName(entry.destination) }.getOrNull()?.trim()?.ifEmpty { null }
                ?: return@collect
            Analytics.trackScreen(nome)
        }
    }
}

/**
 * Uma visita de tela quando [route] aparece (ou muda) — para tela que não é destino de navegação
 * (aba interna, passo de assistente). Girar o aparelho não conta de novo.
 */
@Composable
fun TrackScreen(route: String, enabled: Boolean = true) {
    var ultima by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(route, enabled) {
        if (!enabled || route == ultima) return@LaunchedEffect
        ultima = route
        Analytics.trackScreen(route)
    }
}
