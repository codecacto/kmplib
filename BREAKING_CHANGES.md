# BREAKING CHANGES — kmplib

> **Fonte de verdade viva = skill `kmplib-catalog`.** Cada módulo/API no catálogo indica a versão em
> que entrou/mudou. Este arquivo guarda o **histórico curado** dos breaking mais impactantes; o
> `CHANGELOG.md` (raiz) tem a lista completa por versão.

## Resumo de breaking estruturais (2.x)

Mudanças que **exigiram** ação dos consumidores — detalhe/migração no catálogo do módulo citado:

| Versão | Módulo | Breaking | Migração |
|--------|--------|----------|----------|
| 2.90.0 | `monetization/purchase` | `PurchaseErrorCode` ganhou 6 valores (`CONFIGURATION_ERROR`, `PURCHASE_NOT_ALLOWED`, `ALREADY_OWNED_BY_OTHER_USER`, `PURCHASE_IN_PROGRESS`, `INELIGIBLE`, `USER_CANCELLED`). Só quebra quem tem **`when (code)` exaustivo sem `else`** (Super 8, Prospecta). Ordinais dos 7 valores antigos preservados; construir/comparar código não muda. | Apagar o `when` local e usar `code.userMessage()` (i18n via `PurchaseErrorTexts`) — a migração remove código em vez de adicionar. |
| 2.82.0 | `observability` | Só para quem **implementa** `CrashReporter` (fake/duble próprio): a interface ganhou `val isActive` e `captureMessage` ganhou o 3º parâmetro `tags`. **Chamadores não mudam** (`tags` tem default). | Implementar `isActive` (delegar ao estado do `init`) e acrescentar `tags: Map<String, String>` na assinatura de `captureMessage`. |
| 2.78.0 | (nenhum) | Onda de manutenção da auditoria: higiene git, docs, ADRs, `OnboardingPager` (novo), `CrashReporter.initFromBuildConfig` (aditivo), portes iOS de PDF/câmera (aditivos). **Sem breaking.** | — |
| 2.75.0 | `firebase/crashlytics` → `observability` | Módulo Crashlytics **removido**; crashes vão para `CrashReporter` (Sentry/GlitchTip). | Trocar `CrashlyticsService` por `crashReporterModule` + `CrashReporter.init(...)`. |
| 2.57.0 | `monetization` | Assinatura por **Offerings/Packages** (RevenueCat gold-standard); `getProducts()`/`purchase(id)` `@Deprecated`. | Usar `getOfferings()`/`purchasePackage(id)`; `toPaywallPlans(packages=...)`. |
| 2.42.0 | `firebase/firestore` | `FirestoreService` **removido** (SEM Firestore como banco). | CRUD via `core/data` (`RestRepository`) ou local (`sync`). |
| 2.38.0 | `ads`/`monetization` | AdMob/Firebase Ads **removidos**; só house ads via apps-api. `MonetizationConfig` sem `AdConfig`. | `AdRouter`/`CustomAdManager` REST; `AdsOnly` virou `data object`. |
| 2.24.0 | `feedback` | `FeedbackConfig` aponta ao apps-api (removidos `appId`/`firebaseProjectId`/`firebaseApiKey`). | Passar `projectSlug`+`httpClient`+`appsApiBaseUrl`. |
| 2.0.0 | `ui/screens` | `GenericLoginScreen` → `LoginScreen` stateless (MVI). | Ver abaixo. |

---

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

---

## Adendo 2026-05-17 — Refactor para testabilidade (AppPreferences interface + ReviewStore)

**Mudança técnica para permitir testes com fakes em commonTest.** Ninguém em
produção usa essas APIs ainda (adicionadas no mesmo dia), então sem impacto real.

### `AppPreferences` virou interface + factory

Antes (era `expect class`, impossível mockar em commonTest):
```kotlin
val prefs = AppPreferences()
```

Agora:
```kotlin
val prefs: AppPreferences = appPreferences()
```

Razão: `expect class` não pode ter subclasses fake em `commonTest`. Refatorar
para `interface AppPreferences` + `expect fun appPreferences()` torna a API
testável e mantém o uso quase igual. A versão `FakeAppPreferences` (in-memory)
foi adicionada em `commonTest` para reuso entre testes.

### `AppReviewManager` recebe `ReviewStore` em vez de `ReviewPreferences`

Antes:
```kotlin
class AppReviewManager(triggerCount: Int = 3, prefs: ReviewPreferences = ReviewPreferences())
```

Agora:
```kotlin
interface ReviewStore { ... }
class PreferencesReviewStore(prefs: ReviewPreferences = ReviewPreferences()) : ReviewStore
class AppReviewManager(triggerCount: Int = 3, store: ReviewStore = PreferencesReviewStore())
```

Usuários típicos (`AppReviewManager()` ou `AppReviewManager(triggerCount = 5)`)
continuam funcionando idênticos. Apenas quem passava `prefs = ...`
explicitamente precisa trocar para `store = ...` ou passar
`PreferencesReviewStore(customPrefs)`. Em produção ninguém ainda chama assim.

Em testes: injetar `FakeReviewStore` (in-memory) — adicionado em `commonTest`.

---

## Adendo 2026-05-17 — Sprint de aceleradores (Tier 1 + StorageProgress + Crashlytics)

**Não-breaking.** Adições puras. Nenhum app precisa mudar nada para continuar
funcionando; quem quiser usar os novos recursos pode adotar incrementalmente.

### Novos pacotes / componentes

- `br.com.codecacto.kmplib.core.prefs.AppPreferences` — wrapper KMP de preferências
  chave/valor (SharedPreferences / NSUserDefaults) com suporte a `observe*` reativo.
  Suporta `String`, `Boolean`, `Int`, `Long`, `Float`. Chaves comuns em `PrefKeys`.
- `br.com.codecacto.kmplib.platform.AppReviewManager` — helper que decide quando
  mostrar o `AppReviewDialog` baseado em "completions". Persiste via `ReviewPreferences`.
- `br.com.codecacto.kmplib.ui.components.Avatar` — circular com iniciais (cor de
  fundo derivada do nome) ou imagem via slot composable.
- `br.com.codecacto.kmplib.ui.components.OfflineBanner` — banner reativo wireado
  com `ConnectivityObserver`. Gerencia `start()`/`stop()` automaticamente.
- `br.com.codecacto.kmplib.ui.components.LoadingOverlay` — modal fullscreen com
  spinner e texto opcional. Bloqueia interação por trás.

### Novas APIs em pacotes existentes

- `StorageService.uploadBytesWithProgress(path, bytes, mimeType?): Flow<UploadProgress>`
  e o sealed `UploadProgress` (`Started`, `Uploading`, `Completed`, `Failed`).
  Contrato pronto; progresso intermediário real fica para quando GitLive expor
  o `Flow<TaskState>` ou se evoluirmos via SDK nativo Android/iOS.
- Extensões `CrashlyticsService.runCatchingAndReport { }`,
  `runCatchingAndReportSuspend { }`, `reportAndRethrow(e, ...)`, `reportSilently(e, ...)`.
  `CancellationException` é sempre re-lançada.

### Itens "planejados" que valem revisitar

`AboutScreen template`, `PermissionsHandler Compose` foram adicionados à lista de
planejados (README seção 17), justificados por padrão recorrente entre apps.

---

## Adendo 2026-05-17 — Markers em LoginContract / RegisterContract

`LoginState`, `LoginAction`, `LoginEffect`, `RegisterState`, `RegisterAction` e
`RegisterEffect` passam a implementar respectivamente `UiState`, `UiAction` e
`UiEffect` (de `br.com.codecacto.kmplib.ui.mvi`).

**Motivação:** sem isso, `SimpleMviViewModel<LoginState, LoginEffect, LoginAction>`
falhava compilação porque a classe exige os markers. Apps que herdavam de
`SimpleMviViewModel` tinham que criar uma classe wrapper sem bounds.

**Impacto:** zero para apps que herdavam de `BaseViewModel<State, Action, Effect>`
(que não exige markers). Apps que já implementavam wrappers locais
podem deletá-los e usar `SimpleMviViewModel` direto. Ver Casca como referência:

```kotlin
typealias BaseViewModel<STATE, EFFECT, ACTION> =
    br.com.codecacto.kmplib.ui.mvi.SimpleMviViewModel<STATE, EFFECT, ACTION>

class LoginViewModel(
    private val authRepository: AuthRepository
) : BaseViewModel<LoginState, LoginEffect, LoginAction>(LoginState())
```
