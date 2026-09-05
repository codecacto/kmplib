package br.com.codecacto.kmplib.ui.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * O estado da trava do app — **um por processo**, de propósito.
 *
 * A trava é do aplicativo inteiro, não de uma tela: se dois [AppLockGate] existissem com estados
 * separados, destravar num lugar deixaria o outro trancado sem motivo.
 *
 * **Vive na memória do processo, e não é salvo em lugar nenhum.** É a parte que segura a
 * segurança:
 * - `rememberSaveable` **não serve** — ele sobrevive à morte do processo, e o app voltaria
 *   destravado depois de o sistema matá-lo em segundo plano, sem ninguém provar quem é.
 * - `remember` puro também não — ele morre na recriação da `Activity`, e **girar o aparelho
 *   trancaria o app na cara do usuário**, que não saiu de perto dele.
 *
 * Objeto de processo acerta os dois: rotação e mudança de tema preservam; processo novo nasce
 * trancado.
 */
internal object AppLockSession {

    /** Começa **trancado**: abrir o app é exatamente o momento em que a trava tem de valer. */
    var isLocked: Boolean by mutableStateOf(true)
        private set

    /** Quando o app foi para segundo plano. `null` = está em uso, ou já voltou. */
    private var backgroundedAtMillis: Long? = null

    fun unlock() {
        isLocked = false
        backgroundedAtMillis = null
    }

    fun lock() {
        isLocked = true
        backgroundedAtMillis = null
    }

    /** App foi para segundo plano. Só interessa quando ele estava destravado. */
    fun onBackground(nowMillis: Long) {
        if (!isLocked) backgroundedAtMillis = nowMillis
    }

    /**
     * App voltou. Tranca se ficou fora por [graceMillis] ou mais.
     *
     * A folga existe para o caso mais comum de sair do app: consultar o valor no banco, copiar um
     * código do SMS, atender uma ligação. Sem ela, o app pediria digital a cada troca — e o usuário
     * desligaria o modo discreto na primeira semana.
     *
     * **Relógio que anda para trás tranca.** Elapsed negativo significa que a hora do aparelho
     * mudou entre a saída e a volta; é o que faria a folga valer para sempre se fosse ignorado.
     */
    fun onForeground(nowMillis: Long, graceMillis: Long) {
        val since = backgroundedAtMillis ?: return
        backgroundedAtMillis = null
        val elapsed = nowMillis - since
        if (elapsed < 0L || elapsed >= graceMillis) lock()
    }
}
