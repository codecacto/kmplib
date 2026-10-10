package br.com.codecacto.kmplib.chat

import kotlinx.serialization.Serializable

/** Tipo de mensagem (o contrato da fábrica só aceita estes dois — sem foto/vídeo). */
@Serializable
enum class ChatMessageKind { TEXT, AUDIO }

/**
 * Uma mensagem **confirmada pelo servidor**.
 *
 * @param fromMe `true` se quem mandou é a conta logada (o app traduz o `senderRole` do contrato).
 * @param readByOtherAtMillis quando a OUTRA ponta leu: na minha mensagem, é o "lida"; na mensagem
 *   que recebi, `null` = eu ainda não li (não lida).
 */
data class ChatMessage(
    val id: String,
    val fromMe: Boolean,
    val kind: ChatMessageKind,
    val text: String? = null,
    val audioAssetId: String? = null,
    val audioDurationMillis: Long? = null,
    val createdAtMillis: Long,
    val readByOtherAtMillis: Long? = null,
) {
    /** Sem o conteúdo: mensagem é dado pessoal. */
    override fun toString(): String = "ChatMessage(id=$id, fromMe=$fromMe, kind=$kind, read=${readByOtherAtMillis != null})"
}

/** Uma página de mensagens (a de `before`/latest vem da mais nova; a de `after`, da mais antiga). */
data class ChatPage(val messages: List<ChatMessage>, val hasMore: Boolean)

/** Por que a mensagem não foi. */
@Serializable
data class ChatSendFailure(val code: Int, val serverCode: String? = null, val message: String? = null)

/**
 * Uma mensagem **ainda não confirmada** — persistida no aparelho até o servidor aceitar. O [id] é
 * gerado no cliente (UUID v7) **uma vez** e reenviado igual a cada tentativa: o servidor devolve a
 * existente em vez de duplicar.
 *
 * @param audioBlobId onde estão os bytes do áudio (no `BlobStore` da fila), até subir.
 * @param audioAssetId o asset depois do upload do áudio — gravado antes de mandar a mensagem, para o
 *   reenvio não subir o áudio de novo.
 * @param autoAttempts falhas retentáveis seguidas (depois do teto, vira [failure] e espera a pessoa).
 */
@Serializable
data class ChatOutgoing(
    val id: String,
    val kind: ChatMessageKind,
    val text: String? = null,
    val audioBlobId: String? = null,
    val audioAssetId: String? = null,
    val audioDurationMillis: Long? = null,
    val audioLevels: List<Float> = emptyList(),
    val createdAtMillis: Long,
    val autoAttempts: Int = 0,
    val nextAttemptAtMillis: Long = 0L,
    val failure: ChatSendFailure? = null,
) {
    override fun toString(): String = "ChatOutgoing(id=$id, kind=$kind, attempts=$autoAttempts, failed=${failure != null})"
}

/** Estado de entrega de uma mensagem minha. */
enum class ChatDelivery {
    /** Na fila do aparelho (tentando, ou esperando rede). */
    SENDING,

    /** Parou — a pessoa escolhe tentar de novo ou descartar. */
    FAILED,

    /** O servidor aceitou. */
    SENT,

    /** A outra ponta leu. */
    READ,
}

/** O áudio de uma entrada: o asset (recebido/enviado) ou o arquivo local (ainda subindo). */
data class ChatAudio(
    val assetId: String?,
    val durationMillis: Long?,
    val levels: List<Float> = emptyList(),
    val localBlobId: String? = null,
)

/**
 * Uma linha da conversa para a tela — mensagem confirmada ou pendente, já com o estado de entrega.
 * [delivery] é `null` nas mensagens recebidas.
 */
data class ChatEntry(
    val id: String,
    val fromMe: Boolean,
    val kind: ChatMessageKind,
    val text: String?,
    val audio: ChatAudio?,
    val createdAtMillis: Long,
    val delivery: ChatDelivery?,
    val failure: ChatSendFailure? = null,
    /** Recebida e ainda não lida por mim. */
    val unread: Boolean = false,
) {
    override fun toString(): String = "ChatEntry(id=$id, fromMe=$fromMe, kind=$kind, delivery=$delivery)"
}

/** O que a tela da conversa desenha. */
data class ChatThreadState(
    /** Da mais antiga para a mais nova. */
    val entries: List<ChatEntry> = emptyList(),
    val isLoadingInitial: Boolean = true,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = false,
    /**
     * Falha da carga (não do envio — esse fica na mensagem). `null` = ok; texto vazio = falhou sem
     * frase do servidor (a tela usa a dela, [ChatTexts.loadError]).
     */
    val loadError: String? = null,
    /** Recebidas não lidas agora. */
    val unreadCount: Int = 0,
    /**
     * Primeira não lida **na abertura** — onde vai o divisor "Não lidas". Fica parado durante a sessão
     * (marcar como lida não faz o divisor pular), e some quando a conversa é reaberta.
     */
    val firstUnreadId: String? = null,
    /** Atualizando pelo gesto de puxar (só o gesto liga). */
    val isRefreshing: Boolean = false,
)
