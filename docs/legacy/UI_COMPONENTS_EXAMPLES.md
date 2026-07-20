# Exemplos de Uso - UI Components

Este documento contém exemplos práticos de uso dos componentes de UI da kmplib.

## Índice

1. [GenericLoginScreen - Exemplo Básico](#exemplo-1-loginscreen-básico)
2. [GenericLoginScreen - Com Todas as Features](#exemplo-2-loginscreen-completo)
3. [LoginScreen com Temas Diferentes](#exemplo-3-temas-customizados)
4. [Integração com ViewModel](#exemplo-4-integração-com-viewmodel)
5. [Dialogs de Confirmação](#exemplo-5-dialogs-de-confirmação)
6. [Formulários Customizados](#exemplo-6-formulários-customizados)

---

## Exemplo 1: LoginScreen Básico

Tela de login minimalista com apenas email e senha.

```kotlin
@Composable
fun BasicLoginScreen() {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    GenericLoginScreen(
        onEmailPasswordLogin = { email, password ->
            isLoading = true
            // Implementar lógica de login
            println("Login: $email")
        },
        isLoading = isLoading,
        errorMessage = errorMessage
    )
}
```

---

## Exemplo 2: LoginScreen Completo

Tela de login com todas as funcionalidades habilitadas.

```kotlin
@Composable
fun FullFeaturedLoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Logo do app
    val logo = painterResource(Res.drawable.app_logo)

    // Cores personalizadas
    val colors = LoginColors(
        primary = Color(0xFF6C63FF),
        secondary = Color(0xFF31C4F1),
        background = Color(0xFFF5F5F5),
        textPrimary = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF757575)
    )

    // Textos customizados
    val texts = LoginTexts(
        title = "Bem-vindo de volta!",
        loginButton = "Entrar",
        forgotPassword = "Esqueci minha senha",
        registerPrompt = "Novo por aqui?",
        registerLink = "Criar conta grátis"
    )

    // Métodos de autenticação
    val authMethods = AuthMethods(
        emailPassword = true,
        google = true,
        apple = true  // Disponível apenas no iOS
    )

    // Observar efeitos
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoginEffect.NavigateToHome -> {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
                is LoginEffect.ShowSnackbar -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    GenericLoginScreen(
        logo = logo,
        colors = colors,
        texts = texts,
        authMethods = authMethods,

        // Callbacks
        onEmailPasswordLogin = { email, password ->
            viewModel.loginWithEmail(email, password)
        },
        onGoogleLogin = {
            viewModel.loginWithGoogle()
        },
        onAppleLogin = {
            viewModel.loginWithApple()
        },
        onForgotPassword = { email ->
            viewModel.sendPasswordReset(email)
        },
        onRegister = {
            navController.navigate("register")
        },

        // Links
        termsUrl = "https://myapp.com/terms",
        privacyUrl = "https://myapp.com/privacy",
        onTermsClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://myapp.com/terms"))
            context.startActivity(intent)
        },
        onPrivacyClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://myapp.com/privacy"))
            context.startActivity(intent)
        },

        // Estados
        isLoading = state.isEmailLoading,
        isGoogleLoading = state.isGoogleLoading,
        isAppleLoading = state.isAppleLoading,
        errorMessage = state.errorMessage
    )
}
```

---

## Exemplo 3: Temas Customizados

### Tema Locadora (Laranja)

```kotlin
@Composable
fun LocadoraLoginScreen() {
    val locadoraColors = LoginColors(
        primary = Color(0xFFF97316),      // Laranja
        secondary = Color(0xFF8B5CF6),    // Roxo
        onPrimary = Color.White,
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        error = Color(0xFFEF4444),
        textPrimary = Color(0xFF1F2937),
        textSecondary = Color(0xFF6B7280),
        border = Color(0xFFE5E7EB)
    )

    GenericLoginScreen(
        colors = locadoraColors,
        onEmailPasswordLogin = { email, password ->
            // Login
        }
    )
}
```

### Tema Meu Advogado (Verde)

```kotlin
@Composable
fun MeuAdvogadoLoginScreen() {
    val advogadoColors = LoginColors(
        primary = Color(0xFF10B981),      // Verde Esmeralda
        secondary = Color(0xFFF59E0B),    // Amarelo/Amber
        onPrimary = Color.White,
        background = Color(0xFFF9FAFB),
        textPrimary = Color(0xFF111827),
        textSecondary = Color(0xFF6B7280)
    )

    val advogadoTexts = LoginTexts(
        title = "Advocacia Digital",
        loginButton = "Acessar Conta",
        registerLink = "Criar Nova Conta"
    )

    GenericLoginScreen(
        colors = advogadoColors,
        texts = advogadoTexts,
        onEmailPasswordLogin = { email, password ->
            // Login
        }
    )
}
```

### Tema Dark Mode

```kotlin
@Composable
fun DarkModeLoginScreen() {
    val darkColors = LoginColors(
        primary = Color(0xFF6C63FF),
        onPrimary = Color.White,
        background = Color(0xFF1A1A2E),
        surface = Color(0xFF1A1A2E),
        textPrimary = Color.White,
        textSecondary = Color(0xFFB0B0B0),
        border = Color(0xFF444444),
        error = Color(0xFFFF2D2D)
    )

    GenericLoginScreen(
        colors = darkColors,
        onEmailPasswordLogin = { email, password ->
            // Login
        }
    )
}
```

---

## Exemplo 4: Integração com ViewModel

### State e Effects

```kotlin
data class LoginState(
    val isEmailLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isAppleLoading: Boolean = false,
    val errorMessage: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
)

sealed interface LoginEffect {
    data object NavigateToHome : LoginEffect
    data class ShowSnackbar(val message: String) : LoginEffect
    data class ShowError(val message: String) : LoginEffect
}
```

### ViewModel com AuthRepository

```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _effect = Channel<LoginEffect>()
    val effect = _effect.receiveAsFlow()

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isEmailLoading = true, errorMessage = null) }

            authRepository.signInWithEmail(email, password)
                .onSuccess { user ->
                    _state.update { it.copy(isEmailLoading = false) }
                    _effect.send(LoginEffect.NavigateToHome)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isEmailLoading = false,
                            errorMessage = when (error) {
                                is AuthException.InvalidCredentials -> "Email ou senha incorretos"
                                is AuthException.InvalidEmail -> "Email inválido"
                                is AuthException.NetworkError -> "Erro de conexão. Tente novamente."
                                else -> "Erro ao fazer login"
                            }
                        )
                    }
                }
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            _state.update { it.copy(isGoogleLoading = true) }
            // Implementar login com Google
            // authRepository.signInWithGoogle(idToken)
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            authRepository.sendPasswordResetEmail(email)
                .onSuccess {
                    _effect.send(LoginEffect.ShowSnackbar("Email de recuperação enviado"))
                }
                .onFailure {
                    _effect.send(LoginEffect.ShowError("Erro ao enviar email"))
                }
        }
    }
}
```

---

## Exemplo 5: Dialogs de Confirmação

### Dialog de Exclusão de Conta

```kotlin
@Composable
fun DeleteAccountDialog(
    viewModel: AccountViewModel = koinViewModel()
) {
    var showDialog by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()

    Button(onClick = { showDialog = true }) {
        Text("Excluir Minha Conta")
    }

    ConfirmationDialog(
        show = showDialog,
        title = "Excluir Conta",
        message = "Tem certeza que deseja excluir permanentemente sua conta? Todos os seus dados serão perdidos e esta ação não pode ser desfeita.",
        confirmText = "Sim, Excluir",
        cancelText = "Cancelar",
        onConfirm = {
            viewModel.deleteAccount()
        },
        onDismiss = {
            showDialog = false
        },
        isLoading = state.isDeleting,
        primaryColor = Color(0xFFD32F2F),  // Vermelho para ação destrutiva
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFD32F2F)
            )
        }
    )
}
```

### Dialog de Logout

```kotlin
@Composable
fun LogoutDialog(onLogout: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    TextButton(onClick = { showDialog = true }) {
        Text("Sair")
    }

    ConfirmationDialog(
        show = showDialog,
        title = "Sair da Conta",
        message = "Deseja realmente sair da sua conta?",
        confirmText = "Sair",
        cancelText = "Cancelar",
        onConfirm = {
            onLogout()
            showDialog = false
        },
        onDismiss = {
            showDialog = false
        },
        primaryColor = Color(0xFF6C63FF)
    )
}
```

### InputDialog - Alterar Email

```kotlin
@Composable
fun ChangeEmailDialog(
    viewModel: ProfileViewModel = koinViewModel()
) {
    var showDialog by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    val state by viewModel.state.collectAsState()

    Button(onClick = { showDialog = true }) {
        Text("Alterar Email")
    }

    InputDialog(
        show = showDialog,
        title = "Alterar Email",
        message = "Digite seu novo endereço de email. Você receberá um email de confirmação.",
        textFieldValue = newEmail,
        onTextFieldValueChange = {
            newEmail = it
            emailError = null
        },
        textFieldLabel = "Novo email",
        textFieldPlaceholder = "novo@email.com",
        textFieldError = emailError,
        confirmText = "Alterar",
        cancelText = "Cancelar",
        onConfirm = {
            if (EmailValidator.isValid(newEmail)) {
                viewModel.changeEmail(newEmail)
                showDialog = false
            } else {
                emailError = "Email inválido"
            }
        },
        onDismiss = {
            showDialog = false
            newEmail = ""
            emailError = null
        },
        isLoading = state.isChangingEmail,
        primaryColor = Color(0xFF6C63FF)
    )
}
```

---

## Exemplo 6: Formulários Customizados

### Formulário de Cadastro Completo

```kotlin
@Composable
fun RegisterForm(
    onRegister: (name: String, email: String, phone: String, password: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acceptTerms by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    val canRegister = name.isNotEmpty() &&
            EmailValidator.isValid(email) &&
            PhoneValidator.isValid(phone) &&
            password.length >= 8 &&
            password == confirmPassword &&
            acceptTerms

    FormContainer(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Criar Conta",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Nome
        AppTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = if (it.isEmpty()) "Nome obrigatório" else null
            },
            label = "Nome completo",
            placeholder = "João Silva",
            leadingIcon = Icons.Default.Person,
            errorMessage = nameError,
            imeAction = ImeAction.Next,
            primaryColor = Color(0xFF6C63FF)
        )

        // Email
        AppTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = if (!EmailValidator.isValid(it)) "Email inválido" else null
            },
            label = "Email",
            placeholder = "joao@email.com",
            leadingIcon = Icons.Default.Email,
            keyboardType = KeyboardType.Email,
            errorMessage = emailError,
            imeAction = ImeAction.Next,
            primaryColor = Color(0xFF6C63FF)
        )

        // Telefone
        AppTextField(
            value = phone,
            onValueChange = {
                phone = it
                phoneError = if (!PhoneValidator.isValid(it)) "Telefone inválido" else null
            },
            label = "Telefone",
            placeholder = "(11) 98765-4321",
            leadingIcon = Icons.Default.Phone,
            keyboardType = KeyboardType.Phone,
            visualTransformation = PhoneVisualTransformation(),
            errorMessage = phoneError,
            imeAction = ImeAction.Next,
            primaryColor = Color(0xFF6C63FF)
        )

        // Senha
        AppTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = when {
                    it.length < 8 -> "Mínimo 8 caracteres"
                    else -> null
                }
            },
            label = "Senha",
            placeholder = "••••••••",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            errorMessage = passwordError,
            imeAction = ImeAction.Next,
            primaryColor = Color(0xFF6C63FF)
        )

        // Confirmar senha
        AppTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                confirmPasswordError = if (it != password) "Senhas não conferem" else null
            },
            label = "Confirmar senha",
            placeholder = "••••••••",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            errorMessage = confirmPasswordError,
            imeAction = ImeAction.Done,
            primaryColor = Color(0xFF6C63FF)
        )

        // Termos
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = acceptTerms,
                onCheckedChange = { acceptTerms = it }
            )
            Text(
                text = "Aceito os Termos de Uso e Política de Privacidade",
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Botão de cadastro
        AppButton(
            text = "Cadastrar",
            onClick = {
                onRegister(name, email, phone, password)
            },
            enabled = canRegister,
            primaryColor = Color(0xFF6C63FF)
        )
    }
}
```

### Formulário de Perfil

```kotlin
@Composable
fun ProfileForm(
    initialName: String,
    initialPhone: String,
    onSave: (name: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var isLoading by remember { mutableStateOf(false) }

    FormContainer {
        Text("Meu Perfil", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nome",
            leadingIcon = Icons.Default.Person
        )

        AppTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Telefone",
            keyboardType = KeyboardType.Phone,
            visualTransformation = PhoneVisualTransformation(),
            leadingIcon = Icons.Default.Phone
        )

        AppButton(
            text = "Salvar Alterações",
            onClick = {
                isLoading = true
                onSave(name, phone)
            },
            isLoading = isLoading,
            primaryColor = Color(0xFF6C63FF)
        )
    }
}
```

---

## Dicas de Uso

### 1. Validação em Tempo Real

Use `onValueChange` para validar campos em tempo real:

```kotlin
AppTextField(
    value = email,
    onValueChange = {
        email = it
        emailError = if (EmailValidator.isValid(it)) null else "Email inválido"
    },
    errorMessage = emailError
)
```

### 2. Estados de Loading

Sempre use estados separados para diferentes operações:

```kotlin
data class State(
    val isEmailLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isAppleLoading: Boolean = false
)
```

### 3. Cores Consistentes

Defina as cores do seu app uma vez e reutilize:

```kotlin
object AppTheme {
    val colors = LoginColors(
        primary = Color(0xFF6C63FF),
        secondary = Color(0xFF31C4F1)
    )
}

// Uso
GenericLoginScreen(
    colors = AppTheme.colors,
    ...
)
```

### 4. Internacionalização

Prepare seus textos para múltiplos idiomas:

```kotlin
object LoginStrings {
    val pt = LoginTexts(
        title = "Bem-vindo",
        loginButton = "Entrar"
    )

    val en = LoginTexts(
        title = "Welcome",
        loginButton = "Sign In"
    )

    val currentLanguage get() = if (Locale.current == "pt") pt else en
}
```

### 5. Navegação com Safe Args

```kotlin
LaunchedEffect(Unit) {
    viewModel.effect.collect { effect ->
        when (effect) {
            is LoginEffect.NavigateToHome -> {
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
    }
}
```
