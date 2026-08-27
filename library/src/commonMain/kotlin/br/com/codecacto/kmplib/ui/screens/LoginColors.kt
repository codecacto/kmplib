package br.com.codecacto.kmplib.ui.screens

import androidx.compose.ui.graphics.Color

/**
 * Configuração de cores para telas de autenticação
 *
 * @param primary Cor primária (botões, bordas focadas)
 * @param secondary Cor secundária (opcional, para botões secundários)
 * @param onPrimary Cor do texto/ícone sobre a cor primária
 * @param background Cor de fundo da tela
 * @param surface Cor de superfície (cards, dialogs)
 * @param error Cor de erro
 * @param textPrimary Cor do texto primário
 * @param textSecondary Cor do texto secundário
 * @param border Cor da borda dos campos não focados
 */
data class LoginColors(
    val primary: Color = Color(0xFF6C63FF),
    val secondary: Color? = null,
    val onPrimary: Color = Color.White,
    val background: Color = Color(0xFFF5F5F5),
    val surface: Color = Color.White,
    val error: Color = Color(0xFFD32F2F),
    val textPrimary: Color = Color(0xFF1A1A1A),
    val textSecondary: Color = Color(0xFF757575),
    val border: Color = Color(0xFFE0E0E0)
)

/**
 * Configuração de autenticação disponível
 *
 * @param emailPassword Habilitar login com email e senha
 * @param google Habilitar login com Google
 * @param apple Habilitar login com Apple
 */
data class AuthMethods(
    val emailPassword: Boolean = true,
    val google: Boolean = false,
    val apple: Boolean = false,
    /**
     * Mostra o "Não tem uma conta? Cadastre-se" no fim do formulário. Default `true`.
     *
     * `false` na tela em que **ninguém se cadastra**: a porta corporativa, em que a conta é
     * CONCEDIDA por um RH ou por um profissional. Ali o convite a se cadastrar manda a pessoa
     * exatamente para onde ela não deve ir — e o cadastro que ela criar sozinha não vai estar ligado
     * à empresa nem ao profissional, então o resultado dela não aparece em lugar nenhum.
     */
    val showRegister: Boolean = true
)
