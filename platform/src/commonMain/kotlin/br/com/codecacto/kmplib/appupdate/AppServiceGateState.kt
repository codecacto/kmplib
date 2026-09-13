package br.com.codecacto.kmplib.appupdate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.cancellation.CancellationException

/**
 * A política dos gates de serviço ([AppServiceGate] e [AppUpdateGate]), fora da composição para
 * poder ser testada.
 *
 * ## Por que o estado não pode morar em `remember(key)` (2.204.0)
 *
 * Até a 2.203.0 o [AppServiceGate] guardava status e "já dispensou" em `remember(key)`. O NeuroCoreX
 * troca o `key` a cada volta ao primeiro plano para reconsultar a manutenção, e cada troca zerava os
 * dois: com atualização OPCIONAL pendente, o gate passava de `Soft` para `None`, e como o `content()`
 * era chamado em ramos diferentes do `when` (cada ramo é um grupo de composição próprio), **a pilha de
 * navegação inteira era destruída** — o app voltava à Splash — e o diálogo já dispensado reaparecia.
 *
 * Aqui o estado vive o tempo da composição do gate. Reconsultar só **substitui** o status quando a
 * resposta chega; até lá vale o último conhecido.
 */
internal class AppServiceGateState(initial: AppServiceStatus = AppServiceStatus()) {

    /** Último status conhecido. Uma reconsulta em andamento NÃO o apaga. */
    var status: AppServiceStatus by mutableStateOf(initial)
        private set

    /**
     * Identidade da atualização opcional que a pessoa dispensou. Guardada por VERSÃO, não como
     * booleano: se o servidor passar a recomendar outra versão, o aviso volta — é outra notícia.
     */
    private var dismissedSoft: Any? by mutableStateOf(null)

    private val inFlight = Mutex()

    /** Manutenção ou atualização obrigatória: o conteúdo fica coberto e não recebe toque nem voltar. */
    val isBlocking: Boolean
        get() = status.maintenance != null || status.update is AppUpdateStatus.Hard

    /** A atualização opcional a oferecer agora, ou `null` (não há, já foi dispensada, ou está bloqueado). */
    val softUpdateToOffer: AppUpdateStatus.Soft?
        get() {
            if (isBlocking) return null
            val soft = status.update as? AppUpdateStatus.Soft ?: return null
            return soft.takeIf { softIdentity(it) != dismissedSoft }
        }

    /** A pessoa dispensou (ou foi à loja): não perguntar de novo por ESTA versão. */
    fun dismissSoft() {
        val soft = status.update as? AppUpdateStatus.Soft ?: return
        dismissedSoft = softIdentity(soft)
    }

    /**
     * Consulta e publica o resultado. Se já houver uma consulta em voo, **não abre outra** e devolve
     * `false`: o pedido que chegou durante ela é atendido pela resposta que está a caminho. É o que
     * impede a consulta dupla na abertura (o gatilho de "voltou ao primeiro plano" dispara colado na
     * primeira consulta).
     *
     * Falha **libera**: exceção de [check] vira status vazio, igual ao contrato best-effort — nunca
     * uma manutenção fantasma e nunca um crash na raiz do app. Cancelamento propaga.
     */
    suspend fun refresh(check: suspend () -> AppServiceStatus): Boolean {
        if (!inFlight.tryLock()) return false
        try {
            publish(
                try {
                    check()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppServiceStatus()
                },
            )
        } finally {
            inFlight.unlock()
        }
        return true
    }

    /** Publica um status obtido por fora (o [AppUpdateGate] consulta pelo próprio serviço). */
    fun publish(newStatus: AppServiceStatus) {
        status = newStatus
    }

    private fun softIdentity(soft: AppUpdateStatus.Soft): Any =
        // Sem nome de versão (backend que só diz "recomendada"), o próprio aviso é a identidade:
        // mudou a mensagem ou a loja, é outra recomendação.
        soft.latestVersionName?.takeIf { it.isNotBlank() } ?: soft
}

/**
 * Decide quando "voltar ao primeiro plano" merece reconsulta.
 *
 * Só conta o `ON_START` que vem **depois de um `ON_STOP`**. O primeiro `ON_START` é o da abertura —
 * o observador o recebe reproduzido ao se registrar — e a abertura já tem a sua consulta. `ON_RESUME`
 * não serve: ele também dispara na volta de um diálogo do sistema (biometria, permissão), que não
 * tirou o app da tela.
 */
internal class ForegroundRecheck {
    private var wentToBackground = false

    fun onStop() {
        wentToBackground = true
    }

    /** `true` quando este `ON_START` é uma volta do segundo plano. */
    fun onStart(): Boolean {
        val back = wentToBackground
        wentToBackground = false
        return back
    }
}
