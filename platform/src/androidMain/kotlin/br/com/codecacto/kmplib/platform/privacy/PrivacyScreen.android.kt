package br.com.codecacto.kmplib.platform.privacy

import android.app.Activity
import android.view.WindowManager
import br.com.codecacto.kmplib.core.util.AppLogger
import java.lang.ref.WeakReference

/**
 * **`FLAG_SECURE` na janela da `Activity`** — a API oficial do Android para tirar o app da
 * miniatura de recentes (e de print/gravação de tela).
 *
 * É um `object` porque o estado é da **janela do app**, não de quem pediu: duas telas pedindo
 * ocultação e uma instância por chamada dariam dois estados brigando pelo mesmo flag.
 *
 * A `Activity` chega pelo `kmpLibPlatformOnResume(activity)` e é guardada em [WeakReference] (o
 * mesmo padrão do `BiometricAuthHolder`). Ao trocar de instância — rotação, mudança de tema do
 * sistema, `recreate()` —, o flag é **reaplicado na janela nova**: sem isso, girar o aparelho
 * derrubaria a proteção sem nenhum sinal na tela.
 */
object AndroidPrivacyScreen : PrivacyScreen {

    private const val TAG = "PrivacyScreen"

    private var activityRef: WeakReference<Activity>? = null
    private var hidden: Boolean = false

    override val isSupported: Boolean = true

    override val isHidden: Boolean get() = hidden

    override fun setHidden(hidden: Boolean) {
        if (this.hidden == hidden) return
        this.hidden = hidden
        val activity = activityRef?.get()
        if (activity == null) {
            // Sem Activity o estado fica guardado e é aplicado no próximo onResume. Registrar
            // porque "pedi para esconder e nada aconteceu" precisa deixar rastro.
            AppLogger.w(TAG, "setHidden($hidden) sem Activity — aplicado no próximo onResume")
            return
        }
        apply(activity)
    }

    /** Chamado por `kmpLibPlatformOnResume`. Reaplica o estado na janela da instância atual. */
    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        apply(activity)
    }

    /** Chamado por `kmpLibPlatformOnPause`. */
    fun clearActivity() {
        activityRef = null
    }

    private fun apply(activity: Activity) {
        activity.runOnUiThread {
            if (hidden) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

actual fun getPrivacyScreen(): PrivacyScreen = AndroidPrivacyScreen
