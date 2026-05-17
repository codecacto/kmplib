package br.com.codecacto.kmplib.ui.screens.feedback

/**
 * Textos customizáveis para a FeedbackScreen.
 *
 * Todos os textos possuem valores padrão em português brasileiro.
 */
data class FeedbackTexts(
    val title: String = "Deixe seu Feedback",
    val subtitle: String = "Sua opinião é importante para nós",
    val motivoLabel: String = "Qual o motivo do seu feedback?",
    val motivoSugestao: String = "Sugestão",
    val motivoBug: String = "Reportar Bug",
    val motivoReclamacao: String = "Reclamação",
    val motivoDuvida: String = "Dúvida",
    val motivoElogio: String = "Elogio",
    val motivoOutro: String = "Outro",
    val mensagemLabel: String = "Sua mensagem",
    val mensagemPlaceholder: String = "Descreva sua sugestão, problema ou dúvida...",
    val emailLabel: String = "Seu e-mail (opcional)",
    val emailPlaceholder: String = "email@exemplo.com",
    val whatsappLabel: String = "Seu WhatsApp",
    val whatsappPlaceholder: String = "(00) 00000-0000",
    val submitButton: String = "Enviar Feedback",
    val cancelButton: String = "Cancelar",
    val successTitle: String = "Obrigado!",
    val successMessage: String = "Seu feedback foi enviado com sucesso.",
    val continueButton: String = "Continuar",
    val errorMessage: String = "Erro ao enviar feedback. Tente novamente.",
    val motivoError: String = "Selecione um motivo",
    val mensagemError: String = "Digite sua mensagem",
    val emailError: String = "E-mail inválido",
    val whatsappError: String = "Informe um WhatsApp com 11 dígitos",
    val backContentDescription: String = "Voltar"
)
