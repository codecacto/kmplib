package br.com.codecacto.kmplib.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * `WindowInsetsControllerCompat` na janela da **Activity** — a forma oficial de escolher a cor dos
 * ícones das barras (é o que o `enableEdgeToEdge()` faz por dentro, uma vez, no `onCreate`).
 *
 * Reaplicado a cada mudança do pedido, porque o `enableEdgeToEdge()` decide pelo modo do APARELHO e
 * só no `onCreate`: tela clara em aparelho escuro, ou troca de tema com o app aberto
 * (`configChanges="uiMode"` não recria a Activity), ficavam com o ícone da cor do fundo.
 *
 * A cor lida da janela (`statusBarColor`/`navigationBarColor`) é o que separa os três casos:
 * transparente (edge-to-edge — Android 15+ força) → decide pelo conteúdo; opaca (Android ≤ 14 sem
 * edge-to-edge) → decide pela barra; translúcida (película da navegação por botões) → pela mistura.
 */
@Composable
internal actual fun ApplySystemBarsAppearance(background: SystemBarsBackground) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        // Dentro de um `Dialog` o `LocalView` é o da janela do diálogo; o contexto dele ainda leva
        // à Activity, e é a janela DELA que tem as barras.
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        @Suppress("DEPRECATION") // leitura: no Android 15+ com edge-to-edge forçado devolve transparente
        val statusBarColor = Color(window.statusBarColor)
        @Suppress("DEPRECATION")
        val navigationBarColor = Color(window.navigationBarColor)
        controller.isAppearanceLightStatusBars =
            needsDarkSystemBarIcons(statusBarColor, background.statusBar)
        controller.isAppearanceLightNavigationBars =
            needsDarkSystemBarIcons(navigationBarColor, background.navigationBar)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
