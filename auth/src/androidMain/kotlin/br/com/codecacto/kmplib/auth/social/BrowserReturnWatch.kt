package br.com.codecacto.kmplib.auth.social

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Quanto se espera, depois de o aplicativo voltar à frente com o login ainda pendente, antes de
 * dar o login por abandonado.
 *
 * O caminho normal não precisa de espera nenhuma: a `AuthCallbackActivity` entrega o *deep link* no
 * `onCreate`/`onNewIntent`, e só depois de ela sair é que a tela de baixo é retomada — quando o
 * `onResume` chega, o pedido já não está pendente. A folga cobre o que foge disso: a tela que volta
 * um instante à frente enquanto o seletor "abrir com" entrega o navegador escolhido, e o aparelho em
 * que a ordem dos dois eventos se inverte. Curta o bastante para a pessoa não ficar olhando um botão
 * girando depois de fechar a aba.
 */
internal const val BROWSER_RETURN_TOLERANCE_MILLIS = 750L

/**
 * Decide quando um login pelo navegador foi **abandonado**: a pessoa fechou a aba (ou voltou ao
 * aplicativo) sem que o *deep link* de volta chegasse.
 *
 * É a regra do AppAuth-Android (`AuthorizationManagementActivity`): quem abriu o navegador voltar a
 * `RESUMED` sem resposta = cancelado. Lá isso é uma Activity intermediária; aqui, que não temos
 * Activity própria, são os eventos de ciclo de vida das telas do aplicativo.
 *
 * Só lógica — não conhece `Activity`, `Handler` nem relógio —, para ser provada em teste de unidade.
 * Não é *thread-safe*: os três eventos chegam na thread principal.
 *
 * @param schedule agenda [action] para daqui a `delayMillis` e devolve como desmarcar.
 * @param isPending se o login que este vigia acompanha ainda espera resposta.
 * @param onAbandoned chamado uma vez, quando o retorno sem resposta se confirma.
 */
internal class BrowserReturnWatch(
    private val toleranceMillis: Long = BROWSER_RETURN_TOLERANCE_MILLIS,
    private val schedule: (delayMillis: Long, action: () -> Unit) -> (() -> Unit),
    private val isPending: () -> Boolean,
    private val onAbandoned: () -> Unit,
) {
    private var leftForeground = false
    private var unschedule: (() -> Unit)? = null

    /** Uma tela do aplicativo saiu da frente — o navegador abriu, ou abriu de novo. */
    fun onActivityPaused() {
        leftForeground = true
        disarm()
    }

    /**
     * Uma tela do aplicativo voltou à frente.
     *
     * @param inMultiWindow em tela dividida o navegador pode estar VIVO ao lado, com a pessoa no meio
     *   do login; tocar no aplicativo não significa que ela desistiu. Ali não se cancela sozinho.
     */
    fun onActivityResumed(inMultiWindow: Boolean = false) {
        // Sem ter saído antes, este `onResume` é o da própria tela que vai abrir o navegador.
        if (!leftForeground || inMultiWindow || !isPending()) return
        disarm()
        unschedule = schedule(toleranceMillis) {
            unschedule = null
            // O redirect pode ter chegado durante a folga: aí não há o que cancelar.
            if (isPending()) onAbandoned()
        }
    }

    /** O login terminou (por qualquer caminho): nada agendado sobrevive a ele. */
    fun dispose() = disarm()

    private fun disarm() {
        unschedule?.invoke()
        unschedule = null
    }
}

/**
 * Liga um [BrowserReturnWatch] aos eventos de ciclo de vida de TODAS as telas do aplicativo.
 *
 * Todas, e não só a que abriu o navegador: girar o aparelho com o navegador na frente faz o Android
 * recriar a tela na volta, e a instância que retoma já não é a que abriu.
 *
 * A `AuthCallbackActivity` não confunde a conta — ela chama `finish()` no `onCreate` e nunca chega a
 * `RESUMED`.
 */
internal class BrowserReturnWatchRegistration(
    private val application: Application,
    isPending: () -> Boolean,
    onAbandoned: () -> Unit,
) : Application.ActivityLifecycleCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val watch = BrowserReturnWatch(
        schedule = { delayMillis, action ->
            val runnable = Runnable(action)
            mainHandler.postDelayed(runnable, delayMillis)
            return@BrowserReturnWatch { mainHandler.removeCallbacks(runnable) }
        },
        isPending = isPending,
        onAbandoned = onAbandoned,
    )

    fun register() = application.registerActivityLifecycleCallbacks(this)

    /** Pode ser chamado de qualquer thread: o desarme vai para a principal, onde os eventos chegam. */
    fun unregister() {
        application.unregisterActivityLifecycleCallbacks(this)
        if (Looper.myLooper() == Looper.getMainLooper()) watch.dispose() else mainHandler.post { watch.dispose() }
    }

    override fun onActivityPaused(activity: Activity) = watch.onActivityPaused()

    override fun onActivityResumed(activity: Activity) = watch.onActivityResumed(activity.isInMultiWindowMode)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
