package br.com.codecacto.kmplib.feedback

import br.com.codecacto.kmplib.validation.EmailValidator

/**
 * Campos do formulário da [br.com.codecacto.kmplib.ui.screens.feedback.FeedbackScreen] que podem
 * ser recusados (2.347.0). A ordem em que voltam de [validateFeedbackForm] é a da TELA, e ela depende
 * da [FeedbackContactPolicy]: o primeiro da lista é o que a tela leva à vista depois de um envio recusado.
 */
internal enum class FeedbackField { MOTIVO, MENSAGEM, WHATSAPP, EMAIL }

/**
 * Ordem dos campos do bloco de contato na tela (2.349.0): o obrigatório vem primeiro. Com
 * [FeedbackContactPolicy.WHATSAPP_REQUIRED] (app BR): WhatsApp, e-mail. Com
 * [FeedbackContactPolicy.EMAIL_REQUIRED] (app GLOBAL): e-mail, WhatsApp. O nome fica entre os dois.
 */
internal fun FeedbackContactPolicy.contactFieldOrder(): List<FeedbackField> = when (this) {
    FeedbackContactPolicy.WHATSAPP_REQUIRED -> listOf(FeedbackField.WHATSAPP, FeedbackField.EMAIL)
    FeedbackContactPolicy.EMAIL_REQUIRED -> listOf(FeedbackField.EMAIL, FeedbackField.WHATSAPP)
}

/**
 * Regra pura do envio do feedback (2.347.0; política de contato 2.349.0): devolve os campos
 * recusados, na ordem da tela — vazio quando pode enviar.
 *
 * O botão envia sempre; a tela marca TODOS os campos recusados de uma vez e leva o primeiro à vista.
 *
 * - motivo e mensagem: obrigatórios (espaço em branco não conta);
 * - o canal da [policy] é obrigatório: WhatsApp completo ([whatsappComplete], pelo formato do
 *   aparelho) ou e-mail válido;
 * - o outro canal é opcional, mas se veio precisa estar certo (WhatsApp completo, e-mail válido).
 */
internal fun validateFeedbackForm(
    motivo: FeedbackMotivo?,
    mensagem: String,
    email: String,
    whatsapp: String,
    whatsappComplete: Boolean,
    policy: FeedbackContactPolicy = FeedbackContactPolicy.WHATSAPP_REQUIRED,
): List<FeedbackField> {
    val emailAparado = email.trim()
    val emailRecusado = when {
        emailAparado.isEmpty() -> policy == FeedbackContactPolicy.EMAIL_REQUIRED
        else -> !EmailValidator.isValid(emailAparado)
    }
    val whatsappRecusado = when {
        whatsapp.isBlank() -> policy == FeedbackContactPolicy.WHATSAPP_REQUIRED
        else -> !whatsappComplete
    }
    return buildList {
        if (motivo == null) add(FeedbackField.MOTIVO)
        if (mensagem.isBlank()) add(FeedbackField.MENSAGEM)
        policy.contactFieldOrder().forEach { campo ->
            when (campo) {
                FeedbackField.EMAIL -> if (emailRecusado) add(campo)
                FeedbackField.WHATSAPP -> if (whatsappRecusado) add(campo)
                else -> Unit
            }
        }
    }
}
