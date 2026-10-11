package br.com.codecacto.kmplib.chat

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_discard
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_input_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_jump_to_latest
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_load_error
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_loading_older
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_new_messages
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_read
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_send
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_sending
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_sent
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_today
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_unread
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_voice_message
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_yesterday
import br.com.codecacto.kmplib.generated.resources.kmplib_chat_you
import br.com.codecacto.kmplib.media.voicenote.VoiceNoteTexts
import br.com.codecacto.kmplib.media.voicenote.rememberVoiceNoteTexts
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/** Textos da conversa. Defaults pt-BR; [rememberChatTexts] traz os 4 idiomas no idioma da tela. */
data class ChatTexts(
    val inputPlaceholder: String = "Mensagem",
    val send: String = "Enviar mensagem",
    val sending: String = "Enviando",
    val sent: String = "Enviada",
    val read: String = "Lida",
    val failed: String = "Não enviada",
    val retry: String = "Tentar de novo",
    val discard: String = "Descartar",
    val unread: String = "Não lidas",
    val today: String = "Hoje",
    val yesterday: String = "Ontem",
    val loadingOlder: String = "Carregando mensagens anteriores",
    val empty: String = "Nenhuma mensagem ainda",
    val jumpToLatest: String = "Ir para a mais recente",
    val newMessages: String = "Novas mensagens",
    val voiceMessage: (duration: String) -> String = { "Mensagem de voz, $it" },
    val you: String = "Você",
    val loadError: String = "Não foi possível carregar a conversa.",
    val voiceNote: VoiceNoteTexts = VoiceNoteTexts(),
)

/** Os textos da conversa no idioma da tela (pt-BR, en, es, pt-PT). */
@Composable
fun rememberChatTexts(): ChatTexts {
    val voz = kmpStringResource(Res.string.kmplib_chat_voice_message, "\u0001")
    return ChatTexts(
        inputPlaceholder = kmpStringResource(Res.string.kmplib_chat_input_placeholder),
        send = kmpStringResource(Res.string.kmplib_chat_send),
        sending = kmpStringResource(Res.string.kmplib_chat_sending),
        sent = kmpStringResource(Res.string.kmplib_chat_sent),
        read = kmpStringResource(Res.string.kmplib_chat_read),
        failed = kmpStringResource(Res.string.kmplib_chat_failed),
        retry = kmpStringResource(Res.string.kmplib_chat_retry),
        discard = kmpStringResource(Res.string.kmplib_chat_discard),
        unread = kmpStringResource(Res.string.kmplib_chat_unread),
        today = kmpStringResource(Res.string.kmplib_chat_today),
        yesterday = kmpStringResource(Res.string.kmplib_chat_yesterday),
        loadingOlder = kmpStringResource(Res.string.kmplib_chat_loading_older),
        empty = kmpStringResource(Res.string.kmplib_chat_empty),
        jumpToLatest = kmpStringResource(Res.string.kmplib_chat_jump_to_latest),
        newMessages = kmpStringResource(Res.string.kmplib_chat_new_messages),
        voiceMessage = { voz.replace("\u0001", it) },
        you = kmpStringResource(Res.string.kmplib_chat_you),
        loadError = kmpStringResource(Res.string.kmplib_chat_load_error),
        voiceNote = rememberVoiceNoteTexts(),
    )
}

/** Ids de teste (Maestro) da conversa. */
object ChatTestTags {
    const val THREAD = "conversa-lista"
    const val INPUT = "conversa-input"
    const val SEND = "conversa-btn-enviar"
    const val RETRY = "conversa-btn-tentar"
    const val DISCARD = "conversa-btn-descartar"
    const val JUMP_TO_LATEST = "conversa-btn-recente"
    const val UNREAD_DIVIDER = "conversa-nao-lidas"

    /** O balão da mensagem [id]. */
    fun bubble(id: String): String = "conversa-msg-$id"
}
