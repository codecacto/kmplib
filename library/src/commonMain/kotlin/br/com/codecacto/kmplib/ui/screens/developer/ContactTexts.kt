package br.com.codecacto.kmplib.ui.screens.developer

/**
 * Textos customizáveis para a [ContactScreen] (formulário "Entrar em contato").
 *
 * Todos os textos possuem valores padrão em português brasileiro. Espelha os rótulos do
 * `ContactForm` da weblib (paridade web/app).
 */
data class ContactTexts(
    val title: String = "Entrar em contato",
    val subtitle: String = "Envie sua mensagem e retornaremos em breve.",
    val nameLabel: String = "Nome",
    val namePlaceholder: String = "Seu nome",
    val emailLabel: String = "E-mail",
    val emailPlaceholder: String = "voce@email.com",
    val whatsappLabel: String = "WhatsApp (opcional)",
    val whatsappPlaceholder: String = "(11) 91234-5678",
    val subjectLabel: String = "Assunto (opcional)",
    val subjectPlaceholder: String = "Sobre o que você quer falar",
    val messageLabel: String = "Mensagem",
    val messagePlaceholder: String = "Escreva sua mensagem…",
    val sendButton: String = "Enviar mensagem",
    val cancelButton: String = "Cancelar",
    val nameError: String = "Informe seu nome.",
    val emailError: String = "E-mail inválido.",
    val messageError: String = "Escreva sua mensagem.",
    val whatsappError: String = "Telefone inválido.",
    val errorMessage: String = "Não foi possível enviar agora. Tente novamente em instantes.",
    val successTitle: String = "Mensagem enviada!",
    val successMessage: String = "Em breve entraremos em contato.",
    val continueButton: String = "Voltar",
    val backContentDescription: String = "Voltar",
)
