## 🚨 BREAKING CHANGES - Versão 1.0.0 → 2.0.0

### Refatoração Completa da UI - LoginScreen e RegisterScreen

A GenericLoginScreen foi completamente refatorada para ser stateless e aceitar estado externo via ViewModel.

---

## Mudanças Principais

### 1. **GenericLoginScreen → LoginScreen** (RENOMEADO)

**Antes (1.0.0):**
```kotlin
GenericLoginScreen(
    onEmailPasswordLogin = { email, password ->
        // Login com estado interno
    },
    isLoading = isLoading,
    errorMessage = errorMessage
)
```

**Depois (2.0.0):**
```kotlin
val state by viewModel.state.collectAsState()

LoginScreen(
    state = state, // Estado externo
    onAction = viewModel::onAction, // Todas ações via callback único
    colors = LoginColors(...),
    texts = LoginTexts(...)
)
```

---

### 2. **LoginTexts - Suporte a i18n**

**Antes (1.0.0):**
```kotlin
data class LoginTexts(
    val title: String? = null,
    val emailLabel: String = "Email"
)
```

**Depois (2.0.0):**
```kotlin
data class LoginTexts(
    val title: @Composable (() -> String)? = null,
    val emailLabel: @Composable () -> String = { "Email" }
)

// Uso com i18n:
LoginTexts(
    title = { stringResource(Res.string.login_title) },
    emailLabel = { stringResource(Res.string.email) }
)
```

---

### 3. **Social Login - Callbacks com Tokens**

**Antes (1.0.0):**
```kotlin
onGoogleLogin = {
    // Sem tokens
}
```

**Depois (2.0.0):**
```kotlin
// No ViewModel, observar efeitos:
viewModel.effect.collect { effect ->
    when (effect) {
        is LoginEffect.SocialLogin.LaunchGoogle -> {
            googleHandler.signIn(
                onSuccess = { result ->
                    viewModel.handleGoogleSignIn(result.idToken, result.accessToken)
                },
                onError = { error ->
                    viewModel.showError(error)
                }
            )
        }
    }
}
```

---

### 4. **Arquitetura MVI/Contract**

Agora usa padrão MVI completo:

**Contratos:**
- `LoginState` - Estado completo da tela
- `LoginAction` - Todas as ações possíveis
- `LoginEffect` - Efeitos colaterais

**Exemplo de ViewModel:**
```kotlin
class LoginViewModel : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _effect = Channel<LoginEffect>()
    val effect = _effect.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.Input.EmailChanged -> {
                _state.update { it.copy(email = action.email) }
            }
            is LoginAction.Click.Login -> {
                login()
            }
            // ... outras ações
        }
    }
}
```

---

## Guia de Migração

### Passo 1: Criar ViewModel

```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _effect = Channel<LoginEffect>()
    val effect = _effect.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.Input.EmailChanged -> {
                _state.update { it.copy(
                    email = action.email,
                    emailError = null
                ) }
            }

            is LoginAction.Input.PasswordChanged -> {
                _state.update { it.copy(
                    password = action.password,
                    passwordError = null
                ) }
            }

            is LoginAction.Click.Login -> {
                viewModelScope.launch {
                    _state.update { it.copy(isLoading = true) }

                    authRepository.signInWithEmail(
                        _state.value.email,
                        _state.value.password
                    ).onSuccess {
                        _effect.send(LoginEffect.Navigate.ToHome)
                    }.onFailure { error ->
                        _state.update { it.copy(
                            isLoading = false,
                            errorMessage = error.message
                        ) }
                    }
                }
            }

            is LoginAction.Click.GoogleLogin -> {
                _effect.send(LoginEffect.SocialLogin.LaunchGoogle)
            }

            is LoginAction.Click.Register -> {
                _effect.send(LoginEffect.Navigate.ToRegister)
            }

            // ... outras ações
        }
    }

    fun handleGoogleSignIn(idToken: String, accessToken: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isGoogleLoading = true) }

            authRepository.signInWithGoogle(idToken, accessToken)
                .onSuccess {
                    _effect.send(LoginEffect.Navigate.ToHome)
                }
                .onFailure { error ->
                    _state.update { it.copy(
                        isGoogleLoading = false,
                        errorMessage = error.message
                    ) }
                }
        }
    }
}
```

### Passo 2: Atualizar Screen

```kotlin
@Composable
fun LoginRoute(
    navController: NavController,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Observar efeitos
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoginEffect.Navigate.ToHome -> {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
                is LoginEffect.Navigate.ToRegister -> {
                    navController.navigate("register")
                }
                is LoginEffect.SocialLogin.LaunchGoogle -> {
                    // Lançar Google Sign-In
                }
                is LoginEffect.OpenUrl -> {
                    // Abrir URL
                }
                is LoginEffect.ShowSnackbar -> {
                    // Mostrar snackbar
                }
            }
        }
    }

    LoginScreen(
        state = state,
        onAction = viewModel::onAction,
        colors = LoginColors(
            primary = Color(0xFF6C63FF)
        ),
        texts = LoginTexts(
            title = { stringResource(Res.string.login_title) }
        ),
        authMethods = AuthMethods(
            emailPassword = true,
            google = true,
            apple = true
        )
    )
}
```

---

## Novos Recursos

### 1. RegisterScreen

Nova tela de registro completa:

```kotlin
RegisterScreen(
    state = registerState,
    onAction = viewModel::onAction,
    fields = RegisterFields(
        showNameField = true,
        showPhoneField = true,
        showTermsCheckbox = true
    )
)
```

### 2. Componentes Atômicos

Componentes individuais reutilizáveis:

```kotlin
EmailField(value = email, onValueChange = { email = it })
PasswordField(value = password, onValueChange = { password = it })
NameField(value = name, onValueChange = { name = it })
PhoneField(value = phone, onValueChange = { phone = it })

ForgotPasswordLink(onClick = { /* ... */ })
AuthNavigationLink(
    promptText = "Não tem conta?",
    linkText = "Cadastre-se",
    onClick = { /* ... */ }
)
TermsCheckbox(
    checked = accepted,
    onCheckedChange = { accepted = it },
    onTermsClick = { /* ... */ },
    onPrivacyClick = { /* ... */ }
)
```

### 3. Social Login Handlers

Interfaces para implementação específica de plataforma:

```kotlin
interface GoogleLoginHandler {
    suspend fun signIn(
        onSuccess: (GoogleSignInResult) -> Unit,
        onError: (String) -> Unit
    )
}

interface AppleLoginHandler {
    suspend fun signIn(
        onSuccess: (AppleSignInResult) -> Unit,
        onError: (String) -> Unit
    )
}
```

---

## Vantagens da Nova Arquitetura

✅ **Testável** - Estado separado da UI
✅ **Flexível** - Aceita qualquer ViewModel/StateFlow
✅ **Reusável** - Componentes atômicos podem ser combinados
✅ **i18n** - Suporte nativo a internacionalização
✅ **Type-safe** - Ações tipadas com sealed interfaces
✅ **Social Login** - Callbacks com tokens prontos para backend

---

## Arquivos Removidos

- ❌ `GenericLoginScreen.kt` (renomeado para `LoginScreen.kt`)
- ❌ Data classes movidas para arquivos separados

## Arquivos Novos

- ✅ `login/LoginContract.kt` - Contratos MVI
- ✅ `login/LoginScreen.kt` - Tela refatorada
- ✅ `login/LoginTexts.kt` - Textos i18n
- ✅ `login/SocialLoginHandler.kt` - Interfaces social login
- ✅ `register/RegisterContract.kt` - Contratos registro
- ✅ `register/RegisterScreen.kt` - Tela de registro
- ✅ `register/RegisterTexts.kt` - Textos i18n registro
- ✅ `LoginColors.kt` - Cores compartilhadas
- ✅ `auth/AuthFields.kt` - Componentes atômicos
- ✅ `auth/AuthLinks.kt` - Links e navegação

---

**Data:** 30/01/2026
**Versão:** 2.0.0
