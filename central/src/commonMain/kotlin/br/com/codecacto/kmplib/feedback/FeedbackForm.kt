package br.com.codecacto.kmplib.feedback

import br.com.codecacto.kmplib.validation.EmailValidator

/**
 * Campos do formulário da [br.com.codecacto.kmplib.ui.screens.feedback.FeedbackScreen] que podem
 * ser recusados, **na ordem em que aparecem na tela** (2.347.0). A ordem importa: o primeiro da lista
 * é o que a tela leva à vista depois de um envio recusado.
 */
internal enum class FeedbackField { MOTIVO, MENSAGEM, WHATSAPP, EMAIL }

/**
 * Regra pura do envio do feedback (2.347.0): devolve os campos recusados, na ordem da tela — vazio
 * quando pode enviar.
 *
 * Até a 2.346.0 o botão ficava DESLIGADO enquanto faltasse algo, e a pessoa não tinha como saber o
 * quê; agora o botão envia sempre, a tela marca TODOS os campos recusados de uma vez e leva o primeiro
 * à vista (erro de formulário no campo, só depois do envio).
 *
 * - motivo: obrigatório;
 * - mensagem: obrigatória, espaço em branco não conta;
 * - WhatsApp: obrigatório e completo ([whatsappComplete] vem do formato do aparelho) — vem antes do
 *   nome e do e-mail no bloco de contato, por ser o único obrigatório;
 * - e-mail: opcional, mas se veio precisa ser válido.
 */
internal fun validateFeedbackForm(
    motivo: FeedbackMotivo?,
    mensagem: String,
    email: String,
    whatsappComplete: Boolean,
): List<FeedbackField> = buildList {
    if (motivo == null) add(FeedbackField.MOTIVO)
    if (mensagem.isBlank()) add(FeedbackField.MENSAGEM)
    if (!whatsappComplete) add(FeedbackField.WHATSAPP)
    val emailAparado = email.trim()
    if (emailAparado.isNotEmpty() && !EmailValidator.isValid(emailAparado)) add(FeedbackField.EMAIL)
}
