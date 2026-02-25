package br.com.codecacto.kmplib.ui.screens.register

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.mask.PhoneVisualTransformation
import br.com.codecacto.kmplib.ui.components.*
import br.com.codecacto.kmplib.ui.screens.LoginColors
import br.com.codecacto.kmplib.ui.screens.AuthMethods

/**
 * Configuração de campos para RegisterScreen
 */
data class RegisterFields(
    val showNameField: Boolean = true,
    val showPhoneField: Boolean = true,
    val showTermsCheckbox: Boolean = true,
    val phoneMask: VisualTransformation = PhoneVisualTransformation()
)

/**
 * Tela de registro stateless que aceita estado externo
 *
 * Esta versão permite total controle do estado via ViewModel/StateFlow externo.
 *
 * @param state Estado atual da tela (campos, loading, erros, etc)
 * @param onAction Callback para todas as ações do usuário
 * @param modifier Modificador customizado
 * @param logo Logo da aplicação (opcional)
 * @param colors Configuração de cores
 * @param texts Configuração de textos (suporta i18n)
 * @param fields Configuração de campos visíveis
 * @param authMethods Métodos de autenticação habilitados
 * @param termsUrl URL dos termos (se null, não mostra)
 * @param privacyUrl URL da política (se null, não mostra)
 */
@Composable
fun RegisterScreen(
    state: RegisterState,
    onAction: (RegisterAction) -> Unit,
    modifier: Modifier = Modifier,
    logo: Painter? = null,
    colors: LoginColors = LoginColors(),
    texts: RegisterTexts = RegisterTexts(),
    fields: RegisterFields = RegisterFields(),
    authMethods: AuthMethods = AuthMethods(emailPassword = true),
    termsUrl: String? = null,
    privacyUrl: String? = null
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
                Spacer(modifier = Modifier.height(32.dp))

                // Logo
                if (logo != null) {
                    Image(
                        painter = logo,
                        contentDescription = "Logo",
                        modifier = Modifier.size(120.dp)
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

                // Formulário Email/Password
                if (authMethods.emailPassword) {
                    // Nome
                    if (fields.showNameField) {
                        AppTextField(
                            value = state.name,
                            onValueChange = { onAction(RegisterAction.Input.NameChanged(it)) },
                            label = texts.nameLabel(),
                            placeholder = texts.namePlaceholder(),
                            leadingIcon = Icons.Default.Person,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                            errorMessage = state.nameError,
                            primaryColor = colors.primary,
                            borderColor = colors.border,
                            labelColor = colors.textSecondary,
                            enabled = !state.isLoading
                        )
                    }

                    // Email
                    AppTextField(
                        value = state.email,
                        onValueChange = { onAction(RegisterAction.Input.EmailChanged(it)) },
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

                    // Telefone
                    if (fields.showPhoneField) {
                        AppTextField(
                            value = state.phone,
                            onValueChange = { onAction(RegisterAction.Input.PhoneChanged(it)) },
                            label = texts.phoneLabel(),
                            placeholder = texts.phonePlaceholder(),
                            leadingIcon = Icons.Default.Phone,
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                            visualTransformation = fields.phoneMask,
                            errorMessage = state.phoneError,
                            primaryColor = colors.primary,
                            borderColor = colors.border,
                            labelColor = colors.textSecondary,
                            enabled = !state.isLoading
                        )
                    }

                    // Senha
                    AppTextField(
                        value = state.password,
                        onValueChange = { onAction(RegisterAction.Input.PasswordChanged(it)) },
                        label = texts.passwordLabel(),
                        placeholder = texts.passwordPlaceholder(),
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        imeAction = ImeAction.Next,
                        errorMessage = state.passwordError,
                        primaryColor = colors.primary,
                        borderColor = colors.border,
                        labelColor = colors.textSecondary,
                        enabled = !state.isLoading
                    )

                    // Confirmar Senha
                    AppTextField(
                        value = state.confirmPassword,
                        onValueChange = { onAction(RegisterAction.Input.ConfirmPasswordChanged(it)) },
                        label = texts.confirmPasswordLabel(),
                        placeholder = texts.confirmPasswordPlaceholder(),
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        errorMessage = state.confirmPasswordError,
                        primaryColor = colors.primary,
                        borderColor = colors.border,
                        labelColor = colors.textSecondary,
                        enabled = !state.isLoading
                    )

                    // Checkbox Termos
                    if (fields.showTermsCheckbox && (termsUrl != null || privacyUrl != null)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = state.acceptedTerms,
                                onCheckedChange = { onAction(RegisterAction.Input.TermsAcceptedChanged(it)) },
                                enabled = !state.isLoading,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.primary
                                )
                            )
                            Column(
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = texts.termsPrefix(),
                                        fontSize = 14.sp,
                                        color = colors.textSecondary
                                    )
                                    if (termsUrl != null) {
                                        TextButton(
                                            onClick = { onAction(RegisterAction.Click.Terms) },
                                            contentPadding = PaddingValues(0.dp),
                                            enabled = !state.isLoading
                                        ) {
                                            Text(
                                                text = texts.termsText(),
                                                fontSize = 14.sp,
                                                color = colors.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    if (termsUrl != null && privacyUrl != null) {
                                        Text(
                                            text = texts.andText(),
                                            fontSize = 14.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                    if (privacyUrl != null) {
                                        TextButton(
                                            onClick = { onAction(RegisterAction.Click.Privacy) },
                                            contentPadding = PaddingValues(0.dp),
                                            enabled = !state.isLoading
                                        ) {
                                            Text(
                                                text = texts.privacyText(),
                                                fontSize = 14.sp,
                                                color = colors.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
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

                    // Botão de cadastro
                    AppButton(
                        text = texts.registerButton(),
                        onClick = { onAction(RegisterAction.Click.Register) },
                        isLoading = state.isLoading,
                        enabled = !state.isLoading,
                        primaryColor = colors.primary,
                        contentColor = colors.onPrimary
                    )

                    // Link para login
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = texts.loginPrompt(),
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { onAction(RegisterAction.Click.Login) },
                            contentPadding = PaddingValues(0.dp),
                            enabled = !state.isLoading
                        ) {
                            Text(
                                text = texts.loginLink(),
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

                // Google Register
                if (authMethods.google) {
                    GoogleLoginButton(
                        text = texts.googleRegister(),
                        onClick = { onAction(RegisterAction.Click.GoogleRegister) },
                        isLoading = state.isGoogleLoading,
                        enabled = !state.isLoading && !state.isGoogleLoading,
                        primaryColor = colors.primary
                    )
                }

                // Apple Register
                if (authMethods.apple) {
                    AppleLoginButton(
                        text = texts.appleRegister(),
                        onClick = { onAction(RegisterAction.Click.AppleRegister) },
                        isLoading = state.isAppleLoading,
                        enabled = !state.isLoading && !state.isAppleLoading,
                        primaryColor = colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
