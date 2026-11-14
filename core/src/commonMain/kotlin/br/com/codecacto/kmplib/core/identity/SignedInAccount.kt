package br.com.codecacto.kmplib.core.identity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * **A conta logada no aparelho, pelo id INTERNO** (2.364.0) — a ponte leve entre quem CONHECE a
 * sessão (`kmplib-auth`, `kmplib-firebase`) e quem precisa saber DE QUEM é um erro
 * (`kmplib-observability`), sem um módulo depender do outro.
 *
 * Regra da fábrica (fundador, 14/nov/2026): todo evento do GlitchTip leva o id interno da conta
 * (`user.id`) e **nunca** nome, e-mail, telefone ou CPF; logout limpa; sem login fica o anônimo. Para
 * nenhum app precisar lembrar, a sessão PUBLICA aqui e o `CrashReporter` OBSERVA daqui:
 *
 * - o own-auth (`OwnAuthTokenManager`) publica a cada login, restauração do cofre, troca de conta,
 *   logout e sessão derrubada pelo servidor;
 * - o `AuthRepository` do Firebase publica o `uid` enquanto o estado de login é observado;
 * - o `CrashReporter` padrão segue esta conta (`CrashReporterConfig.followSignedInAccount`).
 *
 * App com outro caminho de login (backend próprio sem own-auth, SDK de terceiro) chama [publish] no
 * próprio observador de sessão — uma linha, e o `CrashReporter` passa a seguir sozinho.
 *
 * **Só id opaco passa.** [publish] filtra por [opaqueAccountIdOrNull]: e-mail, nome, telefone ou
 * CPF/CNPJ viram `null` (anônimo) — nunca saem do aparelho. Mais de uma sessão no mesmo app (raro):
 * vale a última que mudou.
 */
object SignedInAccount {

    private val _id = MutableStateFlow<String?>(null)
    private val listeners = MutableStateFlow<List<(String?) -> Unit>>(emptyList())

    /** O id opaco da conta logada, ou `null` (sem login / id recusado). */
    val id: StateFlow<String?> = _id.asStateFlow()

    /**
     * Publica a conta corrente. `null`/branco = deslogado. Id que não é opaco (e-mail, nome,
     * telefone, CPF/CNPJ) é tratado como `null` — ver [opaqueAccountIdOrNull].
     */
    fun publish(accountId: String?) {
        val safe = opaqueAccountIdOrNull(accountId)
        if (_id.value == safe) return
        _id.value = safe
        // Cada ouvinte lê o valor DO MOMENTO da notificação, então duas publicações concorrentes
        // terminam no mesmo estado final em todos.
        listeners.value.forEach { listener -> listener(_id.value) }
    }

    /**
     * Passa a ouvir as MUDANÇAS da conta (não é chamado com o valor atual — leia [id] na hora de
     * registrar). Devolve a função que desfaz o registro.
     */
    fun addListener(listener: (String?) -> Unit): () -> Unit {
        listeners.update { it + listener }
        return { listeners.update { current -> current - listener } }
    }
}

/** Tamanho máximo de um id opaco aceito (UUID tem 36; `sub` de provedor raramente passa de 64). */
private const val MAX_OPAQUE_ID_LENGTH = 128

/**
 * Devolve [raw] aparado **só se ele parece um id opaco** — UUID, id numérico, `uid` do Firebase,
 * `sub` de provedor (`google-oauth2|123`) —, ou `null`.
 *
 * Recusa, de propósito, o que identificaria a PESSOA:
 * - e-mail (qualquer `@`) — o `sub` de um backend mal configurado às vezes é o e-mail;
 * - nome ou texto livre (espaço, acento, qualquer caractere fora de `A-Z a-z 0-9 . _ : | -`);
 * - telefone formatado (`+`, parênteses) e **só algarismos com 10 a 14 dígitos** (descontados
 *   `.`, `-`, `/`) — o tamanho de telefone BR com/sem DDI, CPF (11) e CNPJ (14). Id numérico de
 *   autoincremento (até 9 dígitos) e snowflake (18–19) passam;
 * - mais de [MAX_OPAQUE_ID_LENGTH] caracteres.
 */
fun opaqueAccountIdOrNull(raw: String?): String? {
    val id = raw?.trim().orEmpty()
    if (id.isEmpty() || id.length > MAX_OPAQUE_ID_LENGTH) return null
    if (!id.all { it.isOpaqueIdChar() }) return null
    val digits = id.filterNot { it == '.' || it == '-' || it == '/' }
    if (digits.isNotEmpty() && digits.all { it in '0'..'9' } && digits.length in 10..14) return null
    return id
}

private fun Char.isOpaqueIdChar(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' ||
        this == '-' || this == '_' || this == '.' || this == ':' || this == '|'
