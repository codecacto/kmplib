package br.com.codecacto.kmplib.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.ui.components.*
import br.com.codecacto.kmplib.ui.screens.LoginColors
import br.com.codecacto.kmplib.ui.screens.AuthMethods
import br.com.codecacto.kmplib.validation.EmailValidator

/**
 * Configuração de textos para LoginScreen
 * Suporta internacionalização via @Composable lambdas
 */
data class LoginTexts(
    val title: @Composable (() -> String)? = null,
    val emailLabel: @Composable () -> String = { "Email" },
    val emailPlaceholder: @Composable () -> String = { "seu@email.com" },
    val passwordLabel: @Composable () -> String = { "Senha" },
    val passwordPlaceholder: @Composable () -> String = { "••••••••" },
    val loginButton: @Composable () -> String = { "Entrar" },
    val forgotPassword: @Composable () -> String = { "Esqueci minha senha" },
    val registerPrompt: @Composable () -> String = { "Não tem uma conta?" },
    val registerLink: @Composable () -> String = { "Cadastre-se" },
    val orContinueWith: @Composable () -> String = { "ou continue com" },
    val googleLogin: @Composable () -> String = { "Continuar com Google" },
    val appleLogin: @Composable () -> String = { "Continuar com Apple" },
    val termsPrefix: @Composable () -> String = { "Ao continuar, você concorda com os " },
    val termsText: @Composable () -> String = { "Termos de Uso" },
    val privacyText: @Composable () -> String = { "Política de Privacidade" },
    val termsSuffix: @Composable () -> String = { "" },
    // Dialog esqueci senha
    val forgotPasswordTitle: @Composable () -> String = { "Recuperação de Senha" },
    val forgotPasswordMessage: @Composable () -> String = { "Digite seu email para receber o link de recuperação" },
    val sendButton: @Composable () -> String = { "Enviar" },
    val cancelButton: @Composable () -> String = { "Cancelar" }
)

/**
 * Tela de login stateless que aceita estado externo
 *
 * Esta versão permite total controle do estado via ViewModel/StateFlow externo.
 *
 * @param state Estado atual da tela (email, password, loading, erros, etc)
 * @param onAction Callback para todas as ações do usuário
 * @param modifier Modificador customizado
 * @param logo Logo da aplicação (opcional)
 * @param colors Configuração de cores
 * @param texts Configuração de textos (suporta i18n)
 * @param authMethods Métodos de autenticação habilitados
 * @param termsUrl URL dos termos (se null, não mostra)
 * @param privacyUrl URL da política (se null, não mostra)
 * @param onGoogleSignInSuccess Callback quando Google Sign-In retorna sucesso
 * @param onAppleSignInSuccess Callback quando Apple Sign-In retorna sucesso
 */
@Composable
fun LoginScreen(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
    modifier: Modifier = Modifier,
    logo: Painter? = null,
    // Dimensiona a logo. Default = 120dp quadrado (logos-ícone). Logos LARGAS/horizontais passam,
    // ex.: `Modifier.fillMaxWidth(0.9f)` (a `Image` usa ContentScale.Fit, então preserva o aspecto).
    logoModifier: Modifier = Modifier.size(120.dp),
    colors: LoginColors = LoginColors(),
    texts: LoginTexts = LoginTexts(),
    authMethods: AuthMethods = AuthMethods(),
    termsUrl: String? = null,
    privacyUrl: String? = null,
    onGoogleSignInSuccess: ((GoogleSignInResult) -> Unit)? = null,
    onAppleSignInSuccess: ((AppleSignInResult) -> Unit)? = null
) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            background = colors.background,
            surface = colors.surface,
            error = colors.error
        )
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = colors.background
        ) {
            FormContainer(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mais respiro no topo (logo não colada no topo da tela).
                Spacer(modifier = Modifier.height(64.dp))

                // Logo
                if (logo != null) {
                    Image(
                        painter = logo,
                        contentDescription = "Logo",
                        modifier = logoModifier
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Título
                texts.title?.let { titleComposable ->
                    Text(
                        text = titleComposable(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email/Password Login
                if (authMethods.emailPassword) {
                    AppTextField(
                        value = state.email,
                        onValueChange = { onAction(LoginAction.Input.EmailChanged(it)) },
                        label = texts.emailLabel(),
                        placeholder = texts.emailPlaceholder(),
                        leadingIcon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        errorMessage = state.emailError,
                        primaryColor = colors.primary,
                        borderColor = colors.border,
                        labelColor = colors.textSecondary,
                        enabled = !state.isLoading
                    )

                    AppTextField(
                        value = state.password,
                        onValueChange = { onAction(LoginAction.Input.PasswordChanged(it)) },
                        label = texts.passwordLabel(),
                        placeholder = texts.passwordPlaceholder(),
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        errorMessage = state.passwordError,
                        primaryColor = colors.primary,
                        borderColor = colors.border,
                        labelColor = colors.textSecondary,
                        enabled = !state.isLoading
                    )

                    // Esqueci minha senha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onAction(LoginAction.Click.ForgotPassword) },
                            contentPadding = PaddingValues(0.dp),
                            enabled = !state.isLoading
                        ) {
                            Text(
                                text = texts.forgotPassword(),
                                color = colors.primary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Mensagem de erro geral
                    if (state.errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = colors.error.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = state.errorMessage,
                                modifier = Modifier.padding(12.dp),
                                color = colors.error,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Botão de login
                    AppButton(
                        text = texts.loginButton(),
                        onClick = { onAction(LoginAction.Click.Login) },
                        isLoading = state.isLoading,
                        enabled = !state.isLoading,
                        primaryColor = colors.primary,
                        contentColor = colors.onPrimary
                    )

                    // Link para cadastro
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = texts.registerPrompt(),
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { onAction(LoginAction.Click.Register) },
                            contentPadding = PaddingValues(0.dp),
                            enabled = !state.isLoading
                        ) {
                            Text(
                                text = texts.registerLink(),
                                color = colors.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Divider se tiver métodos sociais
                if ((authMethods.google || authMethods.apple) && authMethods.emailPassword) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border)
                        Text(
                            text = texts.orContinueWith(),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.border)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Google Login
                if (authMethods.google) {
                    GoogleLoginButton(
                        text = texts.googleLogin(),
                        onClick = { onAction(LoginAction.Click.GoogleLogin) },
                        isLoading = state.isGoogleLoading,
                        enabled = !state.isLoading && !state.isGoogleLoading,
                        primaryColor = colors.primary
                    )
                }

                // Apple Login
                if (authMethods.apple) {
                    AppleLoginButton(
                        text = texts.appleLogin(),
                        onClick = { onAction(LoginAction.Click.AppleLogin) },
                        isLoading = state.isAppleLoading,
                        enabled = !state.isLoading && !state.isAppleLoading,
                        primaryColor = colors.primary
                    )
                }

                // Links de termos e privacidade — prefixo + links com espaçamento de linha NORMAL.
                // Antes os links eram TextButton (altura mínima ~48dp), o que abria um vão grande
                // entre a linha do prefixo e a dos links; agora são Text clicável (tight, com um
                // padding pequeno só para o alvo de toque).
                if (termsUrl != null || privacyUrl != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = texts.termsPrefix(),
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.Center) {
                            if (termsUrl != null) {
                                Text(
                                    text = texts.termsText(),
                                    fontSize = 12.sp,
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { onAction(LoginAction.Click.Terms) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                            if (termsUrl != null && privacyUrl != null) {
                                Text(
                                    text = " e ",
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            if (privacyUrl != null) {
                                Text(
                                    text = texts.privacyText(),
                                    fontSize = 12.sp,
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { onAction(LoginAction.Click.Privacy) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Dialog esqueci senha
            InputDialog(
                show = state.showForgotPasswordDialog,
                title = texts.forgotPasswordTitle(),
                message = texts.forgotPasswordMessage(),
                textFieldValue = state.forgotPasswordEmail,
                onTextFieldValueChange = { onAction(LoginAction.Input.ForgotPasswordEmailChanged(it)) },
                textFieldLabel = texts.emailLabel(),
                textFieldPlaceholder = texts.emailPlaceholder(),
                textFieldError = state.forgotPasswordError,
                confirmText = texts.sendButton(),
                cancelText = texts.cancelButton(),
                onConfirm = { onAction(LoginAction.Click.SendResetPassword) },
                onDismiss = { onAction(LoginAction.Click.CancelForgotPassword) },
                isLoading = state.isForgotPasswordLoading,
                primaryColor = colors.primary
            )
        }
    }
}
