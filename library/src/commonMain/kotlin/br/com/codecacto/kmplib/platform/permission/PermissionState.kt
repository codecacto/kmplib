package br.com.codecacto.kmplib.platform.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.getUrlLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Cria (e lembra) o [PermissionManager] da plataforma dentro de uma composição.
 *
 * Existe desde sempre no KDoc do [PermissionManager] — agora existe de verdade.
 */
@Composable
fun rememberPermissionManager(): PermissionManager = remember { createPermissionManager() }

/**
 * Estado de UMA permissão de runtime, pronto para uma tela Compose.
 *
 * Concentra o ciclo que toda tela que depende de permissão repete: **conferir → pedir → reagir à
 * negação definitiva mandando para as Configurações**. Antes desta peça, cada componente da lib
 * (overlay de ditado, câmera) escrevia o seu, e nenhum tratava a negação permanente — o usuário
 * ficava num beco sem saída, tocando num botão que não abre diálogo nenhum.
 *
 * Obtenha via [rememberPermissionState].
 */
@Stable
class PermissionState internal constructor(
    val permission: AppPermission,
    private val manager: PermissionManager,
    private val scope: CoroutineScope,
) {
    /** Último status conhecido. Começa em [PermissionStatus.NOT_REQUESTED]. */
    var status: PermissionStatus by mutableStateOf(PermissionStatus.NOT_REQUESTED)
        private set

    /** `true` quando a permissão está concedida. */
    val isGranted: Boolean get() = status == PermissionStatus.GRANTED

    /**
     * `true` quando pedir de novo não adianta — o único caminho é [openAppSettings].
     */
    val isPermanentlyDenied: Boolean get() = status == PermissionStatus.PERMANENTLY_DENIED

    /** Reconsulta o status atual sem abrir diálogo (ex.: ao voltar das Configurações). */
    fun refresh() {
        val current = manager.checkPermission(permission)
        // O `checkPermission` do Android não distingue negação permanente; nunca deixamos um
        // PERMANENTLY_DENIED já conhecido regredir para DENIED por causa disso.
        if (status == PermissionStatus.PERMANENTLY_DENIED && current != PermissionStatus.GRANTED) return
        status = current
    }

    /** Solicita a permissão ao usuário (no-op se já concedida). */
    fun request() {
        if (isGranted) return
        scope.launch {
            manager.requestPermission(permission).collectLatest { status = it }
        }
    }

    /**
     * Abre a tela de Configurações do próprio app, onde a permissão pode ser reativada.
     *
     * **Best-effort:** se o `UrlLauncher` não estiver inicializado no app, registra o erro e não
     * faz nada — nunca lança de dentro de um `onClick`.
     */
    fun openAppSettings() {
        runCatching { getUrlLauncher().openAppSettings() }
            .onFailure { AppLogger.e(TAG, "Não foi possível abrir as Configurações do app", it) }
    }

    private companion object {
        const val TAG = "PermissionState"
    }
}

/**
 * Estado observável de [permission], já conferido na entrada da tela.
 *
 * ```kotlin
 * val camera = rememberPermissionState(AppPermission.CAMERA)
 * when {
 *     camera.isGranted -> BarcodeCameraPreview(...)
 *     camera.isPermanentlyDenied -> Button(onClick = camera::openAppSettings) { Text("Abrir Configurações") }
 *     else -> Button(onClick = camera::request) { Text("Permitir câmera") }
 * }
 * ```
 *
 * @param requestOnFirstAppearance pede a permissão automaticamente na primeira composição quando
 *   ela ainda não foi concedida. Deixe `false` quando a tela precisar explicar antes de pedir.
 */
@Composable
fun rememberPermissionState(
    permission: AppPermission,
    manager: PermissionManager = rememberPermissionManager(),
    requestOnFirstAppearance: Boolean = true,
): PermissionState {
    val scope = rememberCoroutineScope()
    val state = remember(permission, manager) { PermissionState(permission, manager, scope) }
    LaunchedEffect(state, requestOnFirstAppearance) {
        state.refresh()
        if (requestOnFirstAppearance && !state.isGranted) state.request()
    }
    return state
}
