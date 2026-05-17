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
14a. [Network (ApiResult / Connectivity)](#14a-network-apiresult-handleapicall-connectivityobserver)
14b. [Push Notifications](#14b-push-notifications)
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
CrefitoValidator.isValid("123456F")            // 6 digitos + F/T (apps medicos)
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

// CREFITO: 123456-F (fisio/TO)
OutlinedTextField(
    value = crefito,
    onValueChange = { crefito = filterCrefitoInput(it) },
    visualTransformation = CrefitoVisualTransformation()
)
```

### Formatadores funcionais (`core.format`)

Helpers sem Compose para uso em logs/exportacoes/PDF. Multi-locale para projetos
fora do Brasil.

```kotlin
// Moeda
formatCurrencyBRL(1234.56)   // "R$ 1.234,56"
formatCurrencyUSD(1234.56)   // "$1,234.56"
formatCurrencyEUR(1234.56)   // "1.234,56 €"
formatCurrency(1234.56, thousandsSeparator = "'", decimalSeparator = ".", prefix = "CHF ")

// Documentos
formatCpf("12345678909")          // "123.456.789-09"
formatCnpj("12345678000195")      // "12.345.678/0001-95"
isValidCpf("12345678909")         // true
isValidCnpj("12345678000195")     // true

// Datas (ISO <-> locale)
formatDateBr("2026-03-05")        // "05/03/2026" (pt-BR / es-ES)
parseDateBrToIso("05/03/2026")    // "2026-03-05" ou null
formatDateUS("2026-03-05")        // "03/05/2026" (en-US)
parseDateUSToIso("03/05/2026")    // "2026-03-05" ou null

// Texto
initialsOf("Joao da Silva")       // "JS"
formatMonthYear(3, 2026)          // "Marco 2026"

// Telefone (uso fora de Compose: logs, PDFs)
formatPhone("11999999999")        // "(11) 99999-9999"  (celular)
formatPhone("1133333333")         // "(11) 3333-3333"   (fixo)

// Datas a partir de millis
formatDateBrFromMillis(1709596800000L)       // "05/03/2024"
formatDateTimeBrFromMillis(1709596800000L)   // "05/03/2024 00:00"
formatIsoDateFromMillis(1709596800000L)      // "2024-03-05"
formatTime(9, 5)                              // "09:05"
parseIsoDateToMillis("2026-03-05")           // Long (epoch UTC)
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

### ImagePicker — Camera + Galeria

```kotlin
// Cria o launcher (funciona em todas as telas que usam)
val picker = rememberImagePickerLauncher { imageBytes: ByteArray ->
    // imageBytes = JPEG, max 1024px, 85% quality
    viewModel.uploadFoto(imageBytes)
}

// Ao clicar, exibe BottomSheet (Android) ou ActionSheet (iOS)
// com opcoes "Tirar foto" e "Escolher da galeria"
Button(onClick = { picker.launch() }) {
    Text("Adicionar foto")
}
```

**Requisitos no app consumidor:**
- Android: adicionar `<uses-permission android:name="android.permission.CAMERA" />` no AndroidManifest
- Android: configurar FileProvider com `<cache-path name="photos" path="photos/" />`
- iOS: adicionar `NSCameraUsageDescription` e `NSPhotoLibraryUsageDescription` no Info.plist
- A permissao de camera e solicitada automaticamente em runtime (Android)

### FullScreenImageViewer — Visualizacao com pinch-to-zoom

```kotlin
var showViewer by remember { mutableStateOf(false) }

// Thumbnail clicavel
AsyncImage(
    model = imageUrl,
    contentDescription = null,
    modifier = Modifier.clickable { showViewer = true }
)

// Viewer fullscreen com zoom
if (showViewer) {
    FullScreenImageViewer(onDismiss = { showViewer = false }) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
```

Gestos suportados:
- Pinch-to-zoom (dois dedos) — zoom de 1x ate 5x
- Pan/arrastar quando zoom > 1x
- Duplo toque para alternar entre 1x e 2.5x
- Botao X para fechar

Tambem disponivel o `ZoomableBox` para uso standalone:

```kotlin
ZoomableBox(minScale = 1f, maxScale = 5f) {
    // qualquer conteudo com suporte a zoom
}
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

### Aliases e variantes

A `BaseViewModel` expoe aliases compativeis com bases MVI locais legadas:
`setState`, `sendEffect`, `dispatch` (alem de `updateState`, `emitEffect`, `launch`,
`onAction`).

Apps que usam a ordem generica `<STATE, EFFECT, ACTION>` podem herdar de
`SimpleMviViewModel` em vez de `BaseViewModel`:

```kotlin
class RestViewModel : SimpleMviViewModel<RestState, RestEffect, RestAction>(RestState()) {
    override fun onAction(action: RestAction) { /* ... */ }
}
```

### Marker interfaces obrigatórios em `SimpleMviViewModel`

`SimpleMviViewModel` exige que State, Effect e Action implementem os markers
`UiState`, `UiEffect` e `UiAction` (de `br.com.codecacto.kmplib.ui.mvi`):

```kotlin
data class RestState(val isResting: Boolean = false) : UiState
sealed interface RestAction : UiAction { /* ... */ }
sealed interface RestEffect : UiEffect { /* ... */ }
```

Os contratos prontos da lib (`LoginContract`, `RegisterContract`) já implementam
os markers, portanto `SimpleMviViewModel<LoginState, LoginEffect, LoginAction>`
compila direto. Para `BaseViewModel<State, Action, Effect>` (ordem clássica)
os markers não são exigidos.

---

## 14a. Network (ApiResult, handleApiCall, ConnectivityObserver)

Pacote `br.com.codecacto.kmplib.core.network` para apps que usam Ktor/HTTP.

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int = -1, val message: String) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}

// Helpers
result.map { it.toDomain() }
result.onSuccess { /* ... */ }.onError { /* ... */ }
result.getOrNull()
result.errorOrNull()

// Wrapper Ktor — converte ResponseException/timeout/serialization para ApiResult.Error
val result: ApiResult<UserDto> = handleApiCall {
    httpClient.get("/v1/me").body()
}
```

### ConnectivityObserver (reativo)

```kotlin
class App {
    val observer: ConnectivityObserver by koinInject()
    LaunchedEffect(Unit) { observer.start() }
    val online by observer.isOnline.collectAsState()
}
```

Targets suportados: Android, iOS. Para JVM/WASM, manter `NetworkChecker` legado.

---

## 14b. Push Notifications

Pacote `br.com.codecacto.kmplib.push`. Abstracao sobre KMPNotifier.

```kotlin
interface PushNotificationService {
    suspend fun getToken(): String?
    suspend fun deleteToken(): Result<Unit>
    suspend fun subscribeToTopic(topic: String): Result<Unit>
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit>
}

interface PushNotificationListener {
    fun onNewToken(token: String) = Unit
    fun onPushNotification(title: String?, body: String?) = Unit
    fun onPayloadData(data: Map<String, String>) = Unit
    fun onNotificationClicked(data: Map<String, String>) = Unit
}

// Implementacao baseada em KMPNotifier
val pushService = KmpPushNotificationService(
    listener = myListener,
    emitCurrentTokenOnStart = true
)
```

Para testes, use `FakePushNotificationService` (em `commonTest`) que rastreia
operacoes sem chamar Firebase real.

Apps continuam responsaveis por: sync de token com backend, escrita em Firestore,
deep links e topicos derivados de dominio (flavor/role/empresa/usuario).

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

### AppPreferences — Preferências chave/valor (DataStore-like)

Wrapper KMP para preferências persistentes. Android usa `SharedPreferences`
(cache `app_prefs`); iOS usa `NSUserDefaults`.

`AppPreferences` é uma **interface**; obtenha a implementação real via
`appPreferences()` ou injete uma fake (`FakeAppPreferences` em commonTest).

```kotlin
val prefs: AppPreferences = appPreferences()

// Suspend get/set
prefs.setString(PrefKeys.THEME, "dark")
val theme = prefs.getString(PrefKeys.THEME, default = "light")

prefs.setBoolean(PrefKeys.ONBOARDING_SEEN, true)
prefs.setInt("session_count", 5)
prefs.has("token")     // Boolean
prefs.remove("token")
prefs.clear()

// Reativo
prefs.observeBoolean(PrefKeys.NOTIFICATIONS_ENABLED, default = true)
    .collect { enabled -> ... }

prefs.observeString(PrefKeys.LANGUAGE, "pt-BR")
    .collect { lang -> ... }
```

Chaves comuns sugeridas em `PrefKeys`: `THEME`, `DARK_MODE`, `LANGUAGE`,
`ONBOARDING_SEEN`, `NOTIFICATIONS_ENABLED`, `FCM_TOKEN_PENDING`, `LAST_SYNC`.

Use como singleton via DI. Múltiplas instâncias compartilham o mesmo flow de
mudanças (companion).

### Crashlytics — Helpers

Além de `getCrashlyticsService()`, há extensões para reduzir try/catch repetitivo:

```kotlin
val crashlytics = getCrashlyticsService()

// runCatching que também reporta a exceção
val result = crashlytics.runCatchingAndReport(
    customKeys = mapOf("user_action" to "save_profile")
) {
    repository.saveProfile(profile)
}
result.onSuccess { ... }.onFailure { ... }

// Suspend
val r = crashlytics.runCatchingAndReportSuspend {
    apiClient.upload(file)
}

// Reportar exceção em catch já existente, sem capturar
try { ... } catch (e: IOException) {
    crashlytics.reportAndRethrow(e, "stage" to "upload")
}

// Reportar sem propagar (erro já tratado)
runCatching { /* ... */ }.onFailure {
    crashlytics.reportSilently(it, "context" to "background")
}
```

`CancellationException` é sempre re-lançada (não é erro de negócio).

### AppReviewManager — Acionar AppReviewDialog após N completions

```kotlin
val reviewManager = AppReviewManager(triggerCount = 3)

// Ao terminar uma ação relevante (completar tarefa, salvar, etc.)
if (reviewManager.onCompletion()) {
    showReviewDialog = true
}

// Quando o dialog for exibido (clique positivo ou negativo)
reviewManager.markShown()

// Checagens passivas
reviewManager.shouldShow()
reviewManager.completionCount()
reviewManager.hasReviewed()
```

Persiste via `ReviewPreferences` (NSUserDefaults / SharedPreferences).

### Avatar — Foto ou iniciais

```kotlin
// Só iniciais (cor de fundo derivada do nome)
Avatar(name = "Joao Silva", size = 48.dp)

// Com imagem (consumer passa AsyncImage/Kamel/Coil)
Avatar(name = user.name, size = 56.dp) {
    AsyncImage(
        model = user.photoUrl,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

// Cor estável para outros usos
val color = colorForName("Maria")
```

### OfflineBanner — Banner reativo quando offline

```kotlin
val observer: ConnectivityObserver by koinInject()

Column {
    OfflineBanner(observer)          // gerencia start/stop automático
    // resto da UI
}

// Customizando
OfflineBanner(
    observer = observer,
    text = "Você está offline",
    backgroundColor = Color(0xFFFEE2E2),
    contentColor = Color(0xFF991B1B)
)
```

### LoadingOverlay — Bloqueio fullscreen com spinner

```kotlin
Box(Modifier.fillMaxSize()) {
    MyScreen(state, onAction)
    LoadingOverlay(show = state.isLoading, text = "Salvando...")
}
```

Bloqueia interação com a UI por trás. Para loading inline (não-bloqueante),
use `CircularProgressIndicator` direto.

### Storage — Upload com progresso reativo

```kotlin
storageService.uploadBytesWithProgress(path, bytes, mimeType = "image/jpeg")
    .collect { progress ->
        when (progress) {
            is UploadProgress.Started -> setState { copy(isUploading = true) }
            is UploadProgress.Uploading -> setState { copy(percent = progress.percent) }
            is UploadProgress.Completed -> {
                setState { copy(isUploading = false, downloadUrl = progress.downloadUrl) }
            }
            is UploadProgress.Failed -> {
                setState { copy(isUploading = false, error = progress.cause.message) }
            }
        }
    }
```

Nota: GitLive Firebase 2.1.0 não expõe progresso intermediário real — o flow
emite `Started → Uploading(0%) → Uploading(100%) → Completed`. Quando GitLive
evoluir, a API permanecerá igual mas o progresso passará a ser real.

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
| AboutScreen template | Padrao recorrente em vários apps | Planejado |
| Multi-step Form wizard | Quando padrao estiver maduro | Planejado |
| LocationService GPS | Quando 2+ apps precisarem | Planejado |
| PlatformMapView | Quando 2+ apps precisarem | Planejado |
| PermissionsHandler Compose | Padrão recorrente (camera, notification, location) | Planejado |
| VibrationManager | Quando 2+ apps precisarem | Planejado |
| Timer/Countdown | Quando 2+ apps precisarem | Planejado |
| Upload com progresso real | Quando GitLive expor `Flow<TaskState>` ou via SDK nativo | Contrato pronto, impl básica |

---

## 17a. Testes

A lib possui suite de testes em `library/src/commonTest/` rodando em Android + iOS via `:library:allTests`. Cobertura medida via [Kover](https://kotlin.github.io/kotlinx-kover/).

### Rodando localmente

```bash
# Android unit tests
./gradlew :library:testDebugUnitTest

# iOS simulator tests (precisa macOS)
./gradlew :library:iosSimulatorArm64Test

# Todos os targets disponíveis
./gradlew :library:allTests

# Relatório de cobertura (HTML em library/build/reports/kover/html/)
./gradlew :library:koverHtmlReport
./gradlew :library:koverXmlReport
```

### CI

Workflow `.github/workflows/tests.yml` roda em cada push/PR para `main`:
- **Android unit tests** + **Kover** em Ubuntu (relatórios HTML/XML como artifacts)
- **iOS simulator tests** em macOS

### Fakes disponíveis em `commonTest`

Para consumidores de testes que querem reutilizar:

| Fake | Substitui | Uso |
|---|---|---|
| `FakeAppPreferences` | `AppPreferences` real | Storage in-memory completo, com `observe*` reativo |
| `FakeReviewStore` | `ReviewStore` / `ReviewPreferences` | Para testar `AppReviewManager` ou lógica de review do app |
| `FakeCrashlyticsService` | `CrashlyticsService` real | Registra `messages`, `customKeys`, `recordedExceptions`, `userId`, `collectionEnabled` |
| `FakePushNotificationService` | `PushNotificationService` real | Rastreia operações sem chamar Firebase |

### O que está coberto hoje

- `brdata/` BrazilianStates, StringExtensions
- `core/format/` BrazilianFormatters, CurrencyFormatters, DateFormatters, PhoneFormatters
- `core/network/` ApiResult, **handleApiCall** (Ktor MockEngine), defaultHttpErrorMessage, mapGenericNetworkMessage
- `core/prefs/` **AppPreferences** (via FakeAppPreferences)
- `core/util/` TimeUtils
- `mask/` filterPhoneInput, filterCpfInput, filterCnpjInput, filterCepInput, filterDateInput, filterCrefitoInput, filterCurrencyInput, `currencyToDouble`, `formatAsCurrency`, CrefitoMask
- `firebase/crashlytics/` **CrashlyticsExtensions** (`runCatchingAndReport`, `runCatchingAndReportSuspend`, `reportAndRethrow`, `reportSilently`)
- `firebase/storage/` **UploadProgress** (fraction, percent, edge cases)
- `platform/` FileData, **AppReviewManager**
- `push/` PushNotificationService
- `ui/` AuthMethods, Badge, LoginColors, LoginContract, LoginTexts, Navigation, NumberField, RegisterContract, RegisterFields, RegisterTexts, Toast, **ContractMarkersTest** (markers MVI)
- `ui/mvi/` BaseViewModel, SimpleMviViewModel
- `validation/` Cpf, Cnpj, Email, Phone, Password, Crefito (todos)

### O que ainda **não** é testado em commonTest

- UI components Compose (Avatar, OfflineBanner, LoadingOverlay etc.) — exige `compose.uiTest` + setup; planejado em fase futura
- `AppLogger` — é `expect object` (não mockável em commonTest); cobertura via testes de plataforma se necessário
- `ConnectivityObserver`, `BiometricAuth`, `NotificationScheduler` — dependem de plataforma real; testar via instrumentation Android/iOS

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
