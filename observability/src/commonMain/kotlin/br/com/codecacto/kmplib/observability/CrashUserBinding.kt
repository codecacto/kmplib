package br.com.codecacto.kmplib.observability

import br.com.codecacto.kmplib.core.identity.SignedInAccount
import br.com.codecacto.kmplib.core.identity.opaqueAccountIdOrNull
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * O usuário do relatório de erro — a regra única que o `SentryCrashReporter` e o
 * [RecordingCrashReporter] seguem (2.364.0).
 *
 * - **Só id opaco sai.** Todo id passa por [opaqueAccountIdOrNull]; e-mail, nome, telefone, CPF/CNPJ
 *   viram anônimo e um aviso vai ao log (sem o valor).
 * - **`setUser` antes do `init` não se perde.** Até a 2.363.0 ele era descartado (o reporter ainda
 *   inativo), e app que restaurava a sessão antes de iniciar o reporter mandava todo erro anônimo.
 *   Agora o pedido fica guardado e é aplicado quando o reporter liga.
 * - **Segue a conta logada sozinho** ([SignedInAccount]) quando [activate] recebe `follow = true`: a
 *   cada login/troca/logout publicado pela sessão, o usuário do escopo muda. Vale o ÚLTIMO evento —
 *   chamada manual ou mudança da sessão.
 */
internal class CrashUserBinding(private val apply: (String?) -> Unit) {

    private var current: String? = null
    private var active = false
    private var unsubscribe: (() -> Unit)? = null

    /** Pedido manual (`setUser`/`clearUser`). Guardado mesmo com o reporter inativo. */
    fun set(id: String?) {
        val safe = opaqueAccountIdOrNull(id)
        if (safe == null && !id.isNullOrBlank()) {
            AppLogger.w(TAG, "setUser recusou um id que não é opaco (e-mail/nome/telefone/documento); evento segue anônimo.")
        }
        current = safe
        if (active) apply(safe)
    }

    /**
     * O reporter ligou de fato. Com [follow], a conta já publicada pela sessão vence o pedido manual
     * anterior (que segue valendo se não há sessão), e as próximas mudanças são seguidas.
     */
    fun activate(follow: Boolean) {
        active = true
        if (follow && unsubscribe == null) {
            unsubscribe = SignedInAccount.addListener { accountId ->
                current = accountId
                if (active) apply(accountId)
            }
            SignedInAccount.id.value?.let { current = it }
        }
        apply(current)
    }

    /** O reporter foi reiniciado como no-op: deixa de seguir a sessão (o pedido manual fica guardado). */
    fun deactivate() {
        active = false
        unsubscribe?.invoke()
        unsubscribe = null
    }

    private companion object {
        const val TAG = "CrashReporter"
    }
}
