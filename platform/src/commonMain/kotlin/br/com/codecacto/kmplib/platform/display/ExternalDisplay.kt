package br.com.codecacto.kmplib.platform.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **Segunda tela** (TV, projetor, AirPlay espelhado como tela estendida) com o celular como controle
 * (2.355.0) — o telão da gincana do Quiz, a projeção do Hinário, o modo aula do Atlas.
 *
 * Como usar: na tela de controle, componha [ExternalDisplayContent] com o que vai para a TV. Enquanto
 * ele estiver na composição e houver tela externa, o conteúdo aparece lá; a tela do celular continua
 * sendo a do app. O conteúdo externo é OUTRA composição: leia o estado de fora (ViewModel/StateFlow),
 * não de `remember` da tela de controle. Saiu da tela (ou a TV desconectou) = a TV volta ao espelho.
 *
 * - **Android:** `DisplayManager` (`DISPLAY_CATEGORY_PRESENTATION`) + `android.app.Presentation` com um
 *   `ComposeView` (o caminho oficial para conteúdo distinto na tela secundária). Nada a declarar.
 * - **iOS:** cena de tela externa (`UIWindowSceneSessionRoleExternalDisplayNonInteractive`, iOS 16+). O
 *   app declara a configuração no `Info.plist` e um *scene delegate* Swift de 5 linhas que chama
 *   [ExternalDisplayBridge] ao conectar/desconectar (receita em `references/platform.md` §display).
 */
object ExternalDisplay {
    private val _isConnected = MutableStateFlow(false)

    /** Há uma tela externa disponível agora. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    internal fun setConnected(value: Boolean) {
        _isConnected.value = value
    }

    /** O que a tela externa mostra agora (o último [ExternalDisplayContent] na composição). */
    internal var content: (@Composable () -> Unit)? by mutableStateOf(null)
}

/**
 * Mostra [content] na tela externa enquanto estiver na composição ([enabled]). Ver [ExternalDisplay].
 * Sem tela externa, não faz nada (a tela de controle pode mostrar "Conecte uma TV" lendo
 * `ExternalDisplay.isConnected`).
 */
@Composable
fun ExternalDisplayContent(enabled: Boolean = true, content: @Composable () -> Unit) {
    val current by rememberUpdatedState(content)
    val wrapper: @Composable () -> Unit = { current() }
    DisposableEffect(enabled) {
        if (enabled) ExternalDisplay.content = wrapper
        onDispose { if (ExternalDisplay.content === wrapper) ExternalDisplay.content = null }
    }
    PlatformExternalDisplayHost(enabled)
}

/** Monitora a tela externa e apresenta [ExternalDisplay.content] nela (por plataforma). */
@Composable
internal expect fun PlatformExternalDisplayHost(enabled: Boolean)

/** Orientação pedida por uma tela. */
enum class ScreenOrientation { PORTRAIT, LANDSCAPE, UNSPECIFIED }

/**
 * Pilha de pedidos de orientação: vale o do TOPO (a tela mais recente); ao sair, volta o anterior.
 * Puro — testável.
 */
class OrientationStack {
    private val entries = mutableListOf<Pair<Any, ScreenOrientation>>()

    /** A orientação em vigor. */
    val current: ScreenOrientation get() = entries.lastOrNull()?.second ?: ScreenOrientation.UNSPECIFIED

    fun push(owner: Any, orientation: ScreenOrientation) {
        entries.removeAll { it.first === owner }
        entries += owner to orientation
    }

    fun pop(owner: Any) {
        entries.removeAll { it.first === owner }
    }
}

internal val orientationStack = OrientationStack()

/**
 * Força [orientation] enquanto a tela estiver na composição e devolve a anterior ao sair (2.355.0) —
 * "paisagem só no telão". Aninhável: vale a mais recente.
 *
 * - **Android:** `Activity.requestedOrientation` (`SENSOR_LANDSCAPE`/`SENSOR_PORTRAIT`/o que era).
 * - **iOS 16+:** `UIWindowScene.requestGeometryUpdate` + `setNeedsUpdateOfSupportedInterfaceOrientations`.
 *   As orientações pedidas precisam estar no `Info.plist` (`UISupportedInterfaceOrientations`).
 */
@Composable
fun ScreenOrientationEffect(orientation: ScreenOrientation) {
    val owner = androidx.compose.runtime.remember { Any() }
    DisposableEffect(orientation) {
        orientationStack.push(owner, orientation)
        applyScreenOrientation(orientationStack.current)
        onDispose {
            orientationStack.pop(owner)
            applyScreenOrientation(orientationStack.current)
        }
    }
}

internal expect fun applyScreenOrientation(orientation: ScreenOrientation)
