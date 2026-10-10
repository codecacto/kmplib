package br.com.codecacto.kmplib.chat

/**
 * Regras puras da conversa — sem rede, sem disco, sem relógio implícito. O [ChatController] só
 * orquestra; o que decide ordem, deduplicação e estado de entrega é isto.
 */

/** Ordem de exibição: instante, desempate pelo id (UUID v7 ordena por criação). */
val ChatMessageOrder: Comparator<ChatMessage> = compareBy<ChatMessage> { it.createdAtMillis }.thenBy { it.id }

/**
 * Junta o que já estava na tela com o que chegou. **Mesmo id = mesma mensagem**: a que chegou
 * substitui (traz o "lida" atualizado) — é assim que o eco do servidor de uma mensagem enviada por
 * esta tela, ou a mesma página buscada duas vezes, não duplica nada.
 */
fun mergeChatMessages(existing: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
    if (incoming.isEmpty()) return existing
    val porId = LinkedHashMap<String, ChatMessage>(existing.size + incoming.size)
    existing.forEach { porId[it.id] = it }
    incoming.forEach { nova ->
        val velha = porId[nova.id]
        // "Lida" nunca volta atrás: uma página velha em cache não desfaz o recibo que já chegou.
        porId[nova.id] = if (velha?.readByOtherAtMillis != null && nova.readByOtherAtMillis == null) {
            nova.copy(readByOtherAtMillis = velha.readByOtherAtMillis)
        } else {
            nova
        }
    }
    return porId.values.sortedWith(ChatMessageOrder)
}

/** Estado de entrega de uma mensagem confirmada. */
fun chatDeliveryOf(message: ChatMessage): ChatDelivery? = when {
    !message.fromMe -> null
    message.readByOtherAtMillis != null -> ChatDelivery.READ
    else -> ChatDelivery.SENT
}

/**
 * As linhas da tela: confirmadas em ordem + pendentes no fim (na ordem em que foram escritas).
 * Pendente cujo id **já voltou do servidor** some (é a mesma mensagem — o envio deu certo e a
 * confirmação chegou antes de a fila se limpar).
 */
fun buildChatEntries(confirmed: List<ChatMessage>, pending: List<ChatOutgoing>): List<ChatEntry> {
    val confirmadas = confirmed.map { m ->
        ChatEntry(
            id = m.id,
            fromMe = m.fromMe,
            kind = m.kind,
            text = m.text,
            audio = if (m.kind == ChatMessageKind.AUDIO) ChatAudio(m.audioAssetId, m.audioDurationMillis) else null,
            createdAtMillis = m.createdAtMillis,
            delivery = chatDeliveryOf(m),
            unread = !m.fromMe && m.readByOtherAtMillis == null,
        )
    }
    val ids = confirmed.mapTo(HashSet()) { it.id }
    val pendentes = pending.filter { it.id !in ids }
        .sortedWith(compareBy<ChatOutgoing> { it.createdAtMillis }.thenBy { it.id })
        .map { p ->
            ChatEntry(
                id = p.id,
                fromMe = true,
                kind = p.kind,
                text = p.text,
                audio = if (p.kind == ChatMessageKind.AUDIO) ChatAudio(p.audioAssetId, p.audioDurationMillis, p.audioLevels, p.audioBlobId) else null,
                createdAtMillis = p.createdAtMillis,
                delivery = if (p.failure != null) ChatDelivery.FAILED else ChatDelivery.SENDING,
                failure = p.failure,
            )
        }
    return confirmadas + pendentes
}

/** Recebidas que eu ainda não li. */
fun chatUnreadCount(confirmed: List<ChatMessage>): Int = confirmed.count { !it.fromMe && it.readByOtherAtMillis == null }

/** A primeira recebida não lida (para o divisor). */
fun chatFirstUnreadId(confirmed: List<ChatMessage>): String? =
    confirmed.firstOrNull { !it.fromMe && it.readByOtherAtMillis == null }?.id

/** A recebida mais nova — o `upToMessageId` do "marcar como lida". `null` se não há recebida. */
fun chatLatestIncomingId(confirmed: List<ChatMessage>): String? = confirmed.lastOrNull { !it.fromMe }?.id

/**
 * Marca localmente como lidas por mim as recebidas até [upToId] (inclusive), depois de o servidor
 * aceitar o `read`.
 */
fun markChatReadUpTo(confirmed: List<ChatMessage>, upToId: String, atMillis: Long): List<ChatMessage> {
    val alvo = confirmed.indexOfFirst { it.id == upToId }
    if (alvo < 0) return confirmed
    return confirmed.mapIndexed { i, m ->
        if (i <= alvo && !m.fromMe && m.readByOtherAtMillis == null) m.copy(readByOtherAtMillis = atMillis) else m
    }
}

/** O texto que vai: aparado. Vazio ou acima do teto → `null` (não se envia; o servidor recusaria). */
fun normalizeChatText(raw: String, maxLength: Int = CHAT_TEXT_MAX_LENGTH): String? {
    val t = raw.trim()
    return t.takeIf { it.isNotEmpty() && it.length <= maxLength }
}

/** Teto do texto da mensagem no contrato da fábrica. */
const val CHAT_TEXT_MAX_LENGTH: Int = 2000

/**
 * Próxima espera do polling: [baseMillis] enquanto dá certo; dobra a cada falha seguida até
 * [maxMillis] (sem rede, não adianta martelar o servidor de 5 em 5 s).
 */
fun chatPollDelayMillis(consecutiveFailures: Int, baseMillis: Long, maxMillis: Long): Long {
    if (consecutiveFailures <= 0) return baseMillis
    var d = baseMillis
    repeat(consecutiveFailures) { d = (d * 2).coerceAtMost(maxMillis) }
    return d
}

/** Divisor de dia antes da entrada [index] (primeira do dia), pelo dia local de cada instante. */
fun chatNeedsDaySeparator(entries: List<ChatEntry>, index: Int, dayOf: (Long) -> Long): Boolean =
    index == 0 || dayOf(entries[index].createdAtMillis) != dayOf(entries[index - 1].createdAtMillis)
