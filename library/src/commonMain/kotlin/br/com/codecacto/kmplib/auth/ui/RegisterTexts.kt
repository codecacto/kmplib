package br.com.codecacto.kmplib.ui.screens.register

import androidx.compose.runtime.Composable

/**
 * Configuração de textos para RegisterScreen
 * Suporta internacionalização via @Composable lambdas
 */
data class RegisterTexts(
    val title: @Composable (() -> String)? = { "Criar Conta" },
    val nameLabel: @Composable () -> String = { "Nome completo" },
    val namePlaceholder: @Composable () -> String = { "João Silva" },
    val emailLabel: @Composable () -> String = { "Email" },
    val emailPlaceholder: @Composable () -> String = { "seu@email.com" },
    val phoneLabel: @Composable () -> String = { "Telefone" },
    val phonePlaceholder: @Composable () -> String = { "(11) 98765-4321" },
    val passwordLabel: @Composable () -> String = { "Senha" },
    val passwordPlaceholder: @Composable () -> String = { "••••••••" },
    val confirmPasswordLabel: @Composable () -> String = { "Confirmar senha" },
    val confirmPasswordPlaceholder: @Composable () -> String = { "••••••••" },
    val registerButton: @Composable () -> String = { "Cadastrar" },
    val loginPrompt: @Composable () -> String = { "Já tem uma conta?" },
    val loginLink: @Composable () -> String = { "Fazer login" },
    val orContinueWith: @Composable () -> String = { "ou continue com" },
    val googleRegister: @Composable () -> String = { "Cadastrar com Google" },
    val appleRegister: @Composable () -> String = { "Cadastrar com Apple" },
    val termsPrefix: @Composable () -> String = { "Eu aceito os " },
    val termsText: @Composable () -> String = { "Termos de Uso" },
    val andText: @Composable () -> String = { " e " },
    val privacyText: @Composable () -> String = { "Política de Privacidade" }
)
