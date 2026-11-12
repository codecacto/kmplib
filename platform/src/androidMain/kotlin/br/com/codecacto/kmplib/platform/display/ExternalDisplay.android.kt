package br.com.codecacto.kmplib.platform.display

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.ShareHandlerHolder
import java.lang.ref.WeakReference

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** `Presentation` com um `ComposeView` que desenha o conteúdo registrado. */
private class ComposePresentation(private val activity: Activity, display: Display) : Presentation(activity, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(context).apply {
            // A janela da Presentation não herda os donos da árvore da Activity: sem eles o Compose não sobe.
            (activity as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (activity as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
            setContent { ExternalDisplay.content?.invoke() }
        }
        setContentView(view)
    }
}

@Composable
internal actual fun PlatformExternalDisplayHost(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        val activity = context.findActivity()
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        var presentation: ComposePresentation? = null
        fun refresh() {
            val display = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            ExternalDisplay.setConnected(display != null)
            val shown = presentation
            when {
                !enabled || display == null || activity == null -> {
                    shown?.dismiss(); presentation = null
                }
                shown == null || shown.display.displayId != display.displayId -> {
                    shown?.dismiss()
                    presentation = runCatching { ComposePresentation(activity, display).also { it.show() } }
                        .onFailure { AppLogger.w("ExternalDisplay", "não foi possível mostrar na tela externa: ${it::class.simpleName}") }
                        .getOrNull()
                }
            }
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = refresh()
            override fun onDisplayRemoved(displayId: Int) = refresh()
            override fun onDisplayChanged(displayId: Int) = Unit
        }
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        refresh()
        onDispose {
            dm.unregisterDisplayListener(listener)
            presentation?.dismiss()
        }
    }
}

private object OrientationMemory {
    var original: Int? = null
    var activity: WeakReference<Activity>? = null
}

internal actual fun applyScreenOrientation(orientation: ScreenOrientation) {
    val activity = ShareHandlerHolder.currentActivity() ?: return
    if (OrientationMemory.activity?.get() !== activity) {
        OrientationMemory.activity = WeakReference(activity)
        OrientationMemory.original = activity.requestedOrientation
    }
    activity.requestedOrientation = when (orientation) {
        ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ScreenOrientation.UNSPECIFIED -> OrientationMemory.original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
