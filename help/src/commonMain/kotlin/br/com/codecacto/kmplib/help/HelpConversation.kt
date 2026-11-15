package br.com.codecacto.kmplib.help

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** Quantos turnos a conversa guarda; o mais antigo sai quando passa disso. */
const val HELP_CONVERSATION_MAX_TURNS: Int = 30

/**
 * Onde a conversa da ajuda mora enquanto o app está aberto: só na MEMÓRIA do processo, nunca em disco.
 *
 * Sair da tela de ajuda e voltar na mesma sessão encontra a conversa como estava; "Nova conversa", o
 * [clearHelpConversations] ou o fim do processo a apagam. Uma conversa por combinação de conteúdo (projeto,
 * perfil, idioma). `MutableStateFlow` só como célula atômica: nenhuma tela observa daqui.
 */
internal class HelpConversationMemory {
    private val turns = MutableStateFlow<List<HelpTurn>>(emptyList())
    private val nextId = MutableStateFlow(1L)

    fun read(): List<HelpTurn> = turns.value

    fun write(value: List<HelpTurn>) {
        turns.value = value.takeLast(HELP_CONVERSATION_MAX_TURNS)
        val highest = value.maxOfOrNull { it.id } ?: return
        nextId.update { maxOf(it, highest + 1) }
    }

    fun newTurnId(): Long = nextId.getAndUpdate { it + 1 }

    fun clear() {
        turns.value = emptyList()
    }
}

internal object HelpConversations {
    private val byKey = MutableStateFlow<Map<String, HelpConversationMemory>>(emptyMap())

    fun forKey(key: String): HelpConversationMemory =
        byKey.updateAndGet { map -> if (key in map) map else map + (key to HelpConversationMemory()) }.getValue(key)

    fun clearAll() {
        byKey.getAndUpdate { emptyMap() }.values.forEach { it.clear() }
    }
}

/**
 * Apaga todas as conversas da ajuda guardadas na memória do processo. Chame no logout: a pergunta é texto livre e
 * a próxima pessoa no mesmo aparelho não deve ver a conversa da anterior.
 */
fun clearHelpConversations() {
    HelpConversations.clearAll()
}
