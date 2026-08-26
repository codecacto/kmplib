package br.com.codecacto.kmplib.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

private const val TAG = "ScreenBrightness"

/**
 * Escala do `Settings.System.SCREEN_BRIGHTNESS`.
 *
 * O máximo real é um recurso interno da plataforma (`config_screenBrightnessSettingMaximum`), sem
 * API pública; 255 é a escala usada por praticamente todo aparelho. Como a leitura é só
 * **referência** (`ScreenBrightnessState.systemLevel`) e nunca decide o que é escrito na janela, um
 * aparelho com escala diferente erra a porcentagem exibida, não o brilho aplicado.
 */
private const val SYSTEM_BRIGHTNESS_SCALE = 255f

/**
 * Holder da Activity para o [ScreenBrightnessController] imperativo. Alimentado por
 * `KmpLib.setActivity(activity)` / `KmpLib.clearActivity()`.
 *
 * O caminho declarativo ([rememberScreenBrightnessController]) **não** depende dele: lá a Activity
 * sai da própria composição.
 */
object ScreenBrightnessHolder {
    private var activityRef: WeakReference<Activity>? = null

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivity() {
        activityRef = null
    }

    internal fun getActivity(): Activity? = activityRef?.get()
}

/**
 * Cria o controlador de brilho do Android a partir da Activity registrada em `KmpLib.setActivity`.
 * Sem Activity, devolve [UnavailableScreenBrightnessController] — nunca um controlador que aceita
 * comandos e não muda luz nenhuma.
 */
actual fun createScreenBrightnessController(): ScreenBrightnessController {
    val activity = ScreenBrightnessHolder.getActivity() ?: run {
        AppLogger.e(TAG, "KmpLib.setActivity(activity) não foi chamado — brilho indisponível")
        return UnavailableScreenBrightnessController
    }
    return AndroidScreenBrightnessController(activity)
}

@Composable
actual fun rememberScreenBrightnessController(): ScreenBrightnessController {
    val view = LocalView.current
    val controller = remember(view) {
        view.context.findActivity()?.let { AndroidScreenBrightnessController(it) }
            ?: UnavailableScreenBrightnessController.also {
                AppLogger.w(TAG, "Composição sem Activity — brilho indisponível")
            }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/**
 * **Padrão-ouro do Android: `WindowManager.LayoutParams.screenBrightness`.**
 *
 * O override vive na **janela da Activity** — vale enquanto o app está à frente e some quando a
 * janela sai, o que é exatamente o escopo que um app tem direito de controlar.
 * `BRIGHTNESS_OVERRIDE_NONE` devolve o comando ao sistema (inclusive ao brilho automático).
 *
 * **`Settings.System.SCREEN_BRIGHTNESS` é usado SÓ para LER.** Escrever ali mudaria o brilho do
 * **aparelho inteiro**, exigiria a permissão `WRITE_SETTINGS` (concedida numa tela de sistema, não
 * num diálogo) e deixaria o aparelho alterado depois que o app fosse fechado. A leitura não pede
 * permissão nenhuma — e é por isso que este módulo não acrescenta uma linha ao manifesto.
 *
 * A escrita é postada na thread de UI (`runOnUiThread`): `window.attributes` só pode ser alterado
 * lá, e o chamador pode ser um ViewModel em background.
 */
internal class AndroidScreenBrightnessController(activity: Activity) : ScreenBrightnessController {

    private val activityRef = WeakReference(activity)
    private val appContext: Context = activity.applicationContext

    private val session = ScreenBrightnessSession(
        restoreMode = BrightnessRestoreMode.ReleaseToSystem,
        readPlatform = ::readSystemBrightness,
        writePlatform = ::applyToWindow,
    )

    override val state: StateFlow<ScreenBrightnessState> = session.state

    init {
        session.refresh()
    }

    override fun current(): Float = session.current()

    override fun setBrightness(level: Float) = session.set(level)

    override fun restore() = session.restore()

    override fun release() = session.release()

    private fun applyToWindow(level: Float) {
        val activity = activityRef.get() ?: run {
            AppLogger.w(TAG, "Activity já foi descartada — brilho não aplicado")
            return
        }
        activity.runOnUiThread {
            runCatching {
                val window = activity.window ?: return@runCatching
                window.attributes = window.attributes.apply {
                    screenBrightness = if (ScreenBrightnessLevel.isOverride(level)) {
                        level.coerceIn(ScreenBrightnessLevel.MIN, ScreenBrightnessLevel.MAX)
                    } else {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }.onFailure { AppLogger.w(TAG, "Falha ao aplicar o brilho na janela: ${it.message}") }
        }
    }

    private fun readSystemBrightness(): Float = runCatching {
        val raw = Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        (raw / SYSTEM_BRIGHTNESS_SCALE).coerceIn(ScreenBrightnessLevel.MIN, ScreenBrightnessLevel.MAX)
    }.getOrElse { ScreenBrightnessLevel.UNKNOWN }
}

/** Desembrulha os `ContextWrapper` até achar a Activity que hospeda a composição. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
