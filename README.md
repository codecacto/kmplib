# KmpLib v2.0.0 — Biblioteca KMP CodeCacto

Biblioteca Kotlin Multiplatform (Android + iOS) que centraliza codigo reutilizavel entre todos os apps da CodeCacto. Usar esta lib ao criar um novo app economiza semanas de desenvolvimento.

**Coordenadas Maven:** `br.com.codecacto:kmplib:2.0.0`

---

## Sumario

1. [Setup e Inicializacao](#1-setup-e-inicializacao)
2. [Autenticacao Firebase](#2-autenticacao-firebase)
3. [Firestore (CRUD)](#3-firestore-crud)
4. [Storage (Arquivos)](#4-storage-arquivos)
5. [Crashlytics](#5-crashlytics)
6. [Monetizacao (Ads + Assinaturas)](#6-monetizacao-ads--assinaturas)
7. [Feedback](#7-feedback)
8. [Validadores](#8-validadores)
9. [Mascaras de Input](#9-mascaras-de-input)
10. [Dados Brasileiros](#10-dados-brasileiros)
11. [Servicos de Plataforma](#11-servicos-de-plataforma)
12. [Componentes UI (Compose)](#12-componentes-ui-compose)
13. [Telas Prontas](#13-telas-prontas)
14. [Arquitetura MVI](#14-arquitetura-mvi)
15. [Tema e Estilo](#15-tema-e-estilo)
16. [Utilitarios](#16-utilitarios)
17. [O que NAO esta na lib (fica no app)](#17-o-que-nao-esta-na-lib-fica-no-app)
18. [Checklist para Novo App](#18-checklist-para-novo-app)

---

## 1. Setup e Inicializacao

### Dependencia (build.gradle.kts do app)

```kotlin
// No gradle/libs.versions.toml
kmplib = "2.0.0"
// Em [libraries]
kmplib = { module = "br.com.codecacto:kmplib", version.ref = "kmplib" }

// No composeApp/build.gradle.kts
commonMain.dependencies {
    implementation(libs.kmplib)
}
```

### Repositorio (settings.gradle.kts)

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal() // para desenvolvimento local
        google()
        mavenCentral()
    }
}
```

### Android — Application.onCreate()

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpLib.init(this) // OBRIGATORIO — inicializa todos os servicos de plataforma

        // Feedback
        FeedbackService.initialize(FeedbackConfig(appId = "br.com.codecacto.meuapp", appVersion = "1.0.0"))

        // Monetizacao (escolha um modo)
        MonetizationManager.initialize(
            MonetizationConfig.Freemium(
                ads = AdConfig(banner = AdUnitIds("ca-app-pub-xxx", "ca-app-pub-yyy"), testMode = BuildConfig.DEBUG),
                purchase = PurchaseConfig(androidApiKey = "goog_xxx", entitlementId = "premium")
            )
        )
    }
}
```

### Android — MainActivity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        KmpLib.setActivity(this) // necessario para biometria e ads
    }
    override fun onPause() {
        super.onPause()
        KmpLib.clearActivity()
    }
}
```

### iOS

No iOS a maioria dos servicos funciona sem inicializacao. Para biometria, a permissao e solicitada automaticamente. Para monetizacao e feedback, inicializar no init() do AppDelegate.

---

## 2. Autenticacao Firebase

### AuthRepository — Login/Cadastro completo

```kotlin
val authRepo = AuthRepository()

// Login
val result = authRepo.signInWithEmail("email@test.com", "senha123")
result.onSuccess { user -> /* User logado */ }
result.onFailure { error -> /* AuthException tipada */ }

// Cadastro
authRepo.signUpWithEmail("email@test.com", "senha123", "Nome")

// Google Sign-In (Android usa CredentialManager internamente)
val googleProvider = GoogleAuthProvider(webClientId = "xxx.apps.googleusercontent.com")
val googleResult = googleProvider.signIn()
if (googleResult.idToken != null) {
    authRepo.signInWithGoogle(googleResult.idToken, googleResult.accessToken)
}

// Apple Sign-In (iOS usa ASAuthorizationController internamente)
val appleProvider = AppleAuthProvider()
val appleResult = appleProvider.signIn()
if (appleResult.idToken != null) {
    authRepo.signInWithApple(appleResult.idToken, appleResult.nonce!!)
}

// Outros
authRepo.sendPasswordResetEmail("email@test.com")
authRepo.changePassword("senhaAtual", "novaSenha")
authRepo.updateProfile(displayName = "Novo Nome")
authRepo.deleteAccount("senhaParaConfirmar")
authRepo.signOut()
authRepo.sendEmailVerification()
val token = authRepo.getIdToken(forceRefresh = true)
```

### AuthStateManager — Estado global de auth

```kotlin
// Observar estado
AuthStateManager.authState.collect { state ->
    when (state) {
        is AuthState.Loading -> showLoading()
        is AuthState.Authenticated -> navigateHome(state.user)
        is AuthState.NotAuthenticated -> navigateLogin()
    }
}

// Definir estado
AuthStateManager.setAuthenticated(user)
AuthStateManager.setNotAuthenticated()

// Acesso sincrono
val user = AuthStateManager.currentUser
val logado = AuthStateManager.isAuthenticated
```

### Observaveis reativos

```kotlin
authRepo.currentUser.collect { user: User? -> }
authRepo.isLoggedIn.collect { logado: Boolean -> }
```

### User Model

```kotlin
data class User(
    val id: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val isEmailVerified: Boolean,
    val providerId: String
)
// Helpers: user.isEmailProvider, user.isGoogleProvider, user.isAppleProvider
```

### AuthException (erros tipados)

`InvalidEmail`, `InvalidCredentials`, `UserNotFound`, `EmailAlreadyInUse`, `WeakPassword`, `TooManyRequests`, `NetworkError`, `RequiresRecentLogin`, `NotAuthenticated`, `UnknownError`

---

## 3. Firestore (CRUD)

```kotlin
val firestore = FirestoreService()

// Criar documento
firestore.setDocument<MyModel>("colecao", "docId", myModel, merge = true)
firestore.addDocument<MyModel>("colecao", myModel) // auto-ID

// Ler
val doc = firestore.getDocument<MyModel>("colecao", "docId")
val list = firestore.getCollection<MyModel>("colecao")

// Query com filtros
val results = firestore.query<MyModel>(
    collection = "colecao",
    filters = listOf(
        QueryFilter.EqualTo("campo", "valor"),
        QueryFilter.GreaterThan("preco", 100.0)
    ),
    orderBy = "criadoEm",
    descending = true,
    limit = 20
)

// Observar em tempo real
firestore.observeDocument<MyModel>("colecao", "docId").collect { doc -> }
firestore.observeCollection<MyModel>("colecao").collect { list -> }
firestore.observeQuery<MyModel>("colecao", filters).collect { results -> }

// Atualizar
firestore.updateDocument("colecao", "docId", mapOf("campo" to "novoValor"))
firestore.updateWithServerTimestamp("colecao", "docId", "atualizadoEm")

// Deletar
firestore.deleteDocument("colecao", "docId")

// Subcollections
firestore.addToSubcollection("parent", "parentId", "sub", data)
firestore.observeSubcollection<MyModel>("parent", "parentId", "sub").collect { }

// Batch (ate 500 operacoes atomicas)
firestore.batch {
    set("colecao", "doc1", model1)
    update("colecao", "doc2", mapOf("x" to 1))
    delete("colecao", "doc3")
}
```

### QueryFilter

`EqualTo`, `NotEqualTo`, `LessThan`, `LessThanOrEqualTo`, `GreaterThan`, `GreaterThanOrEqualTo`, `ArrayContains`, `In`

---

## 4. Storage (Arquivos)

```kotlin
val storage = StorageService()

val url = storage.getDownloadUrl("pasta/arquivo.jpg")
storage.deleteFile("pasta/arquivo.jpg")
val result = storage.deleteFiles(listOf("a.jpg", "b.jpg"))
// result.successCount, result.failedCount, result.failedPaths
val exists = storage.exists("pasta/arquivo.jpg")
```

---

## 5. Crashlytics

```kotlin
val crashlytics = getCrashlyticsService()

crashlytics.logMessage("Usuario abriu tela X")
crashlytics.setUserId("user123")
crashlytics.setCustomKey("plano", "premium")
crashlytics.recordException(exception)
crashlytics.setCrashlyticsCollectionEnabled(true)
```

Android: usa FirebaseCrashlytics nativo com debug logging.
iOS: usa NSLog (o SDK nativo do Firebase no Xcode captura crashes automaticamente).

---

## 6. Monetizacao (Ads + Assinaturas)

### Modos disponiveis

```kotlin
// So ads, sem assinatura
MonetizationConfig.AdsOnly(ads = adConfig)

// So assinatura, sem ads
MonetizationConfig.PremiumOnly(purchase = purchaseConfig)

// Freemium: ads + assinatura (premium remove ads)
MonetizationConfig.Freemium(ads = adConfig, purchase = purchaseConfig)
```

### Ads (AdMob)

```kotlin
// Banner
@Composable fun MyScreen() {
    BannerAd(modifier = Modifier.fillMaxWidth())
}

// Interstitial
AdManager.interstitial?.load()
AdManager.interstitial?.show(onDismissed = { /* continuar */ })

// App Open
AdManager.appOpen?.load()
AdManager.appOpen?.show(onDismissed = { })

// Verificar se ads estao habilitados (Remote Config)
AdManager.adsEnabled.collect { enabled -> }
```

### Assinaturas (RevenueCat)

```kotlin
val repo = PurchaseManager.repository!!

// Verificar status
repo.isPremium()
repo.subscriptionState.collect { info -> }

// Listar produtos
val products = repo.getProducts()

// Comprar
val result = repo.purchase("premium_mensal")
when (result) {
    is PurchaseResult.Success -> { }
    is PurchaseResult.Cancelled -> { }
    is PurchaseResult.Error -> { result.errorCode; result.message }
}

// Restaurar
repo.restorePurchases()

// Estado global
MonetizationManager.isPremium.collect { premium -> }
MonetizationManager.shouldShowAds.collect { showAds -> }
```

---

## 7. Feedback

### Inicializacao

```kotlin
FeedbackService.initialize(FeedbackConfig(appId = "br.com.codecacto.meuapp", appVersion = "1.0.0"))
FeedbackService.updateUser(userId = "abc123", userEmail = "user@email.com")
```

### Enviar feedback programaticamente

```kotlin
FeedbackService.sendFeedback(
    source = FeedbackSource.FEEDBACK_SCREEN,
    motivo = "SUGESTAO",
    mensagem = "Gostaria de uma feature X",
    email = "user@email.com",
    whatsapp = "11999999999"
)
```

### Tela pronta de feedback

```kotlin
FeedbackScreen(
    texts = FeedbackTexts(title = "Feedback", ...),
    onBack = { navController.popBackStack() }
)
```

### Dialog de avaliacao

```kotlin
AppReviewDialog(
    show = showDialog,
    onDismiss = { showDialog = false }
)
// Fluxo: Rating -> Positivo (abre store) / Negativo (formulario de feedback)
```

---

## 8. Validadores

Todos seguem o mesmo padrao: `isValid()` retorna Boolean, `validate()` retorna String? (null = valido).

```kotlin
CpfValidator.isValid("123.456.789-09")      // true/false (com checksum)
CpfValidator.validate("123")                  // "CPF invalido"
CpfValidator.remove("123.456.789-09")         // "12345678909"

CnpjValidator.isValid("12.345.678/0001-95")  // suporta formato alfanumerico (2026)
EmailValidator.isValid("user@email.com")
PhoneValidator.isValid("11999999999")          // 11 digitos
PasswordValidator.isValid("Senha@123")         // regras configuraveis
NameValidator.isValid("Joao", minLength = 2)
NameValidator.validate("", minLength = 2)      // "Nome e obrigatorio"
```

---

## 9. Mascaras de Input

Todas sao `VisualTransformation` para uso direto com TextField/OutlinedTextField.

```kotlin
// Telefone: (11) 99999-9999
OutlinedTextField(
    value = phone,
    onValueChange = { phone = filterPhoneInput(it) },
    visualTransformation = PhoneVisualTransformation()
)

// CPF: 123.456.789-09
CpfVisualTransformation()

// CNPJ: 12.345.678/0001-95
CnpjVisualTransformation()

// Moeda BRL: 1.234,56
CurrencyVisualTransformation()

// CEP: 12345-678
CepVisualTransformation()

// Data: 23/03/2026
OutlinedTextField(
    value = date,
    onValueChange = { date = filterDateInput(it) },
    visualTransformation = DateVisualTransformation()
)
```

---

## 10. Dados Brasileiros

```kotlin
// 27 estados com codigo IBGE, sigla, nome, regiao
BrazilianStates.all                              // List<BrazilianState>
BrazilianStates.findByAbbreviation("SP")         // BrazilianState?
BrazilianStates.findByName("Sao Paulo")
BrazilianStates.abbreviations                    // List<String>

// 5.570 municipios
BrazilianCities.findByStateCode("35")            // cidades de SP

// Remover acentos
"Sao Paulo".removeAccents()                      // "Sao Paulo"
```

---

## 11. Servicos de Plataforma

Todos usam expect/actual. Android e iOS tem implementacoes nativas.

### UrlLauncher — Abrir URLs, apps, mapas

```kotlin
val launcher = getUrlLauncher()

launcher.openUrl("https://example.com")
launcher.openEmail(to = "email@test.com", subject = "Assunto", body = "Corpo")
launcher.openPhone("11999999999")
launcher.openWhatsApp("5511999999999", message = "Ola!")
launcher.openStorePage(androidPackage = "com.example.app", iosAppId = "123456")
launcher.openMap(latitude = -23.5505, longitude = -46.6333, label = "Sao Paulo")
launcher.openMapByAddress("Av Paulista 1000, Sao Paulo")
launcher.openSubscriptionManagement() // Play Store ou App Store
```

### ShareHandler — Compartilhar

```kotlin
val share = getShareHandler()

share.shareText("Texto para compartilhar", title = "Compartilhar via")
share.shareImage(imageBytes, fileName = "ranking.png", title = "Compartilhar imagem")
share.shareFile(pdfBytes, fileName = "contrato.pdf", mimeType = "application/pdf", title = "Enviar PDF")
```

### BiometricAuth — Biometria

```kotlin
val bio = getBiometricAuth()

if (bio.isAvailable()) {
    val type = bio.getBiometricType() // FACE_ID, TOUCH_ID, FINGERPRINT, FACE
    bio.authenticate(
        title = "Autenticacao",
        subtitle = "Use sua biometria",
        onSuccess = { /* autorizado */ },
        onError = { message -> },
        onCancel = { }
    )
}
```

### NotificationScheduler — Notificacoes locais

```kotlin
val scheduler = getNotificationScheduler()

scheduler.requestPermission { granted -> }
scheduler.scheduleNotification(
    id = 1,
    title = "Lembrete",
    body = "Voce tem um vencimento hoje",
    scheduledTime = System.currentTimeMillis() + 3600000
)
scheduler.cancelNotification(1)
scheduler.showNotificationNow(2, "Alerta", "Mensagem imediata")
```

### ReviewPreferences — Controle de avaliacao

```kotlin
val prefs = ReviewPreferences()

prefs.hasReviewed()               // Boolean
prefs.markReviewed()
prefs.getCompletionCount()        // Int
val count = prefs.incrementCompletionCount()
if (count >= 3 && !prefs.hasReviewed()) {
    showReviewDialog = true
}
```

### BitmapEncoder — Capturar UI como imagem

```kotlin
val bitmap: ImageBitmap = graphicsLayer.toImageBitmap()
val pngBytes: ByteArray = encodeBitmapToPng(bitmap)
getShareHandler().shareImage(pngBytes, "captura.png")
```

### NetworkChecker — Verificar conexao

```kotlin
val checker: NetworkChecker = AndroidNetworkChecker() // ou IosNetworkChecker()
if (!checker.isAvailable()) {
    // mostrar NoInternetDialog
}
```

### BuildInfo — Debug detection

```kotlin
if (BuildInfo.isDebug) {
    // modo debug
}
```

---

## 12. Componentes UI (Compose)

### Botoes

```kotlin
AppButton(text = "Salvar", onClick = { }, isLoading = isLoading)
AppOutlinedButton(text = "Cancelar", onClick = { })
AppSecondaryButton(text = "Opcao", onClick = { }) // menor, 48dp, sutil
GoogleLoginButton(text = "Entrar com Google", onClick = { })
AppleLoginButton(text = "Entrar com Apple", onClick = { })
```

### Campos de texto

```kotlin
AppTextField(
    value = email,
    onValueChange = { email = it },
    label = "E-mail",
    error = emailError,
    leadingIcon = { Icon(Icons.Default.Email, null) },
    keyboardType = KeyboardType.Email
)

AppTextArea(
    value = descricao,
    onValueChange = { descricao = it },
    label = "Descricao",
    maxCharacters = 500,
    minLines = 3
)
```

### Dialogos

```kotlin
AppDialog(show = showDialog, onDismiss = { }) { /* conteudo */ }
ConfirmationDialog(show = showConfirm, title = "Excluir?", message = "Tem certeza?",
    onConfirm = { }, onDismiss = { })
ErrorModal(title = "Erro", message = "Algo deu errado", onDismiss = { })
NoInternetDialog(onDismiss = { })
```

### Toast

```kotlin
val toastState = rememberToastState()

ToastHost(toastState)

// Disparar
toastState.showSuccess("Salvo com sucesso!")
toastState.showError("Erro ao salvar")
toastState.showWarning("Atencao!")
toastState.showInfo("Informacao")
```

### Badges

```kotlin
StatusBadge(text = "Ativo", textColor = Color.White, backgroundColor = Color.Green)
NotificationBadge(count = 5, onClick = { navigateToNotifications() })
AppBadge(count = 99) // circular, suporta "99+"
```

### Navegacao

```kotlin
AppTopBar(title = "Minha Tela")
BackTopBar(title = "Detalhes", onBackClick = { navController.popBackStack() })

AppBottomNavBar(
    items = listOf(
        BottomNavItem("Home", route = "home", icon = Icons.Default.Home),
        BottomNavItem("Perfil", route = "perfil", icon = Icons.Default.Person)
    ),
    selectedRoute = currentRoute,
    onItemClick = { item -> navController.navigate(item.route) }
)
```

### Outros

```kotlin
EmptyState(icon = Icons.Default.Search, title = "Nenhum resultado", subtitle = "Tente outro filtro")
FormContainer { /* campos do formulario — gerencia scroll, teclado e foco */ }
NumberField(value = quantidade, onValueChange = { quantidade = it }, label = "Quantidade")
```

### Campos de autenticacao prontos

```kotlin
EmailField(value = email, onValueChange = { }, error = emailError)
PasswordField(value = senha, onValueChange = { }, error = senhaError)
NameField(value = nome, onValueChange = { })
PhoneField(value = telefone, onValueChange = { })
ForgotPasswordLink(onClick = { })
OrDivider() // "— ou —"
TermsCheckbox(checked = accepted, onCheckedChange = { })
```

---

## 13. Telas Prontas

### LoginScreen

```kotlin
LoginScreen(
    state = loginState,
    onAction = { action -> viewModel.onAction(action) },
    texts = LoginTexts(title = "Bem-vindo", emailLabel = "E-mail", ...),
    colors = LoginColors(primary = Color.Blue),
    showGoogleLogin = true,
    showAppleLogin = true,
    showForgotPassword = true,
    showRegisterLink = true
)
```

### RegisterScreen

```kotlin
RegisterScreen(
    state = registerState,
    onAction = { action -> viewModel.onAction(action) },
    texts = RegisterTexts(title = "Criar conta", ...),
    showGoogleLogin = true,
    showAppleLogin = true,
    showPhone = true
)
```

### FeedbackScreen

```kotlin
FeedbackScreen(
    texts = FeedbackTexts(title = "Enviar Feedback"),
    onBack = { navController.popBackStack() }
)
```

---

## 14. Arquitetura MVI

### Marker interfaces

```kotlin
interface UiState   // estado da tela
interface UiAction  // acoes do usuario
interface UiEffect  // eventos one-shot (navegacao, toast)
```

### BaseViewModel

```kotlin
class MyViewModel : BaseViewModel<MyState, MyAction, MyEffect>(MyState()) {

    override fun onAction(action: MyAction) {
        when (action) {
            is MyAction.Load -> loadData()
            is MyAction.Save -> save()
        }
    }

    private fun loadData() {
        launch {
            updateState { copy(isLoading = true) }
            val data = repository.getData()
            updateState { copy(isLoading = false, data = data) }
        }
    }

    private fun save() {
        launch {
            repository.save(currentState.data)
            emitEffect(MyEffect.ShowSuccess("Salvo!"))
            emitEffect(MyEffect.NavigateBack)
        }
    }
}
```

### Uso no Composable

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    viewModel.collectEffect { effect ->
        when (effect) {
            is MyEffect.NavigateBack -> navController.popBackStack()
            is MyEffect.ShowSuccess -> toastState.showSuccess(effect.message)
        }
    }

    // UI usando state...
}
```

---

## 15. Tema e Estilo

```kotlin
AppTheme(
    darkTheme = isSystemInDarkTheme(),
    colorPalette = AppColorPalettes.Default, // ou Orange, Green, Blue, Pink, Red
    fontFamily = FontFamily.Default
) {
    // conteudo do app
}

// Acessar cores customizadas
val primary = AppColors.primary
val success = AppColors.success
```

### Paletas disponiveis

`AppColorPalettes.Default` (roxo), `.Orange`, `.Green`, `.Blue`, `.Pink`, `.Red`

---

## 16. Utilitarios

### TimeUtils — Data/hora

```kotlin
val now = currentTimeMillis()
val instant = currentInstant()

// Formatacao brasileira
instant.formatDateShort()     // "23/03/2026"
instant.formatDateLong()      // "23 de marco de 2026"
instant.formatDateTime()      // "23/03/2026 14:30"

TimeUtils.formatDateBrazilian(timestamp)         // "23/03/2026"
TimeUtils.formatDateTimeBrazilian(timestamp)     // "23/03/2026 14:30"
TimeUtils.getRelativeTime(timestamp)             // "ha 5 minutos"
TimeUtils.isToday(timestamp)                     // Boolean
TimeUtils.isYesterday(timestamp)
TimeUtils.startOfDay(timestamp)                  // 00:00:00.000
TimeUtils.endOfDay(timestamp)                    // 23:59:59.999
TimeUtils.addDays(timestamp, 7)
TimeUtils.parseDate("23/03/2026", "dd/MM/yyyy")  // Long
```

### AppLogger — Logging

```kotlin
AppLogger.d("TAG", "mensagem debug")
AppLogger.i("TAG", "info")
AppLogger.w("TAG", "warning")
AppLogger.e("TAG", "error", throwable)
AppLogger.setMinLevel(Level.WARN) // filtrar logs
```

---

## 17. O que NAO esta na lib (fica no app)

Estes itens sao especificos de cada app e NAO devem ser centralizados:

| Categoria | Exemplos |
|-----------|----------|
| **Models de negocio** | Cliente, Equipamento, Locacao, Prescription, Tournament |
| **Repositories de negocio** | ClienteRepository, PrescriptionRepository |
| **Telas especificas** | Home, Dashboard, detalhes de entidades |
| **Navegacao/Routes** | sealed interface Route (cada app tem rotas diferentes) |
| **Strings/i18n** | Textos especificos do app |
| **Tema/cores customizadas** | Cores do brand do app (use a paleta da lib como base) |
| **Configuracao Firebase** | google-services.json, webClientId |
| **Room Database** | Schema e especifico de cada app |
| **Business logic** | Calculos de aluguel, ranking de torneio, etc. |
| **Workers/Background tasks** | Verificacao de vencimentos, sync |
| **PDF com conteudo especifico** | Layout de contrato, recibo |
| **Maps/Geolocalizacao** | Google Maps, LocationService (especifico de apps com mapa) |
| **Onboarding** | Telas e conteudo de onboarding |
| **Admin panel** | Funcionalidades administrativas |

### Funcionalidades que podem ser adicionadas a lib futuramente

| Item | Quando vale a pena | Status |
|------|-------------------|--------|
| PDF Generation framework | Quando 2+ apps precisarem gerar PDFs | Planejado |
| PremiumScreen + ViewModel | Quando extrair de Super8/MeuFisio/Prospecta | Planejado |
| Onboarding template | Quando padrao estiver maduro | Planejado |
| Legal Pages template | Quando padronizar termos/privacidade | Planejado |
| Multi-step Form wizard | Quando padrao estiver maduro | Planejado |
| LocationService GPS | Quando 2+ apps precisarem | Planejado |
| PlatformMapView | Quando 2+ apps precisarem | Planejado |
| VibrationManager | Quando 2+ apps precisarem | Planejado |
| Timer/Countdown | Quando 2+ apps precisarem | Planejado |

---

## 18. Checklist para Novo App

Ao criar um novo app KMP da CodeCacto, siga esta ordem:

### 1. Estrutura base
- [ ] Criar projeto KMP com Compose Multiplatform
- [ ] Adicionar kmplib como dependencia
- [ ] Configurar settings.gradle.kts com mavenLocal()
- [ ] Chamar KmpLib.init(this) no Application.onCreate()
- [ ] Chamar KmpLib.setActivity(this) / clearActivity() na MainActivity

### 2. Firebase
- [ ] Adicionar google-services.json (Android) e GoogleService-Info.plist (iOS)
- [ ] Usar AuthRepository() para login (nao reimplementar!)
- [ ] Usar GoogleAuthProvider(webClientId) para Google Sign-In
- [ ] Usar AppleAuthProvider() para Apple Sign-In
- [ ] Usar AuthStateManager para estado global
- [ ] Usar FirestoreService() para CRUD
- [ ] Usar StorageService() para uploads
- [ ] Usar getCrashlyticsService() para logs de erro

### 3. Telas de auth
- [ ] Usar LoginScreen da lib (nao criar do zero!)
- [ ] Usar RegisterScreen da lib
- [ ] Customizar via LoginTexts, LoginColors, RegisterTexts

### 4. Monetizacao
- [ ] Escolher modo: AdsOnly, PremiumOnly ou Freemium
- [ ] Configurar MonetizationManager.initialize() no Application
- [ ] Usar BannerAd() onde precisar
- [ ] Usar PurchaseManager.repository para assinaturas

### 5. Feedback
- [ ] Inicializar FeedbackService
- [ ] Adicionar rota para FeedbackScreen
- [ ] Usar AppReviewDialog apos X completions via ReviewPreferences

### 6. UI
- [ ] Usar AppTheme como wrapper
- [ ] Usar AppButton, AppTextField, AppTextArea (nao criar proprios!)
- [ ] Usar ToastHost + rememberToastState() para notificacoes
- [ ] Usar AppTopBar / BackTopBar para navegacao
- [ ] Usar AppBottomNavBar para nav inferior
- [ ] Usar ConfirmationDialog, ErrorModal, NoInternetDialog
- [ ] Usar EmptyState para listas vazias

### 7. Validacao
- [ ] Usar CpfValidator, CnpjValidator, EmailValidator, etc.
- [ ] Usar masks (PhoneVisualTransformation, etc.) nos TextFields
- [ ] NAO reimplementar validacao — se precisar de algo novo, adicionar a lib

### 8. Arquitetura
- [ ] Usar BaseViewModel<State, Action, Effect> para cada tela
- [ ] Implementar UiState, UiAction, UiEffect nos contracts
- [ ] Usar collectState e collectEffect nos composables

### 9. Verificacao final
- [ ] Grep no codigo por imports duplicados que poderiam usar a lib
- [ ] Nenhum validador reimplementado
- [ ] Nenhum componente UI basico reimplementado
- [ ] Firebase usado via kmplib, nao diretamente

---

## Versoes alinhadas (todos os projetos CodeCacto)

| Dependencia | Versao |
|-------------|--------|
| Kotlin | 2.3.0 |
| Compose Multiplatform | 1.10.0 |
| AGP | 8.11.2 |
| Gradle | 8.14.3 |
| kotlinx-serialization | 1.9.0 |
| kotlinx-datetime | 0.7.1 |
| kotlinx-coroutines | 1.10.2 |
| Firebase GitLive | 2.1.0 |
| Koin | 4.1.1 |
| Navigation Compose | 2.9.1 |
| Lifecycle | 2.9.6 |
| kmplib | 2.0.0 |
| compileSdk | 36 |
| minSdk | 24 |
| targetSdk | 36 |
