# Análise de Centralização - KmpLib vs Apps CodeCacto

**Data da análise:** 2026-03-23
**Projetos analisados:** kmplib (v1.1.0), locadora, Meu Advogado, Meu Fisio, Prospecta, Super 8

---

## 1. INVENTÁRIO ATUAL DA KMPLIB (v1.1.0)

A biblioteca já fornece **121 arquivos Kotlin** (72 common, 16 Android, 14 iOS, 19 testes):

| Módulo | O que fornece |
|--------|---------------|
| **Validação** | CPF, CNPJ, Email, Phone, Password validators |
| **Máscaras** | Phone, CPF, CNPJ, Currency, CEP (VisualTransformation) |
| **Dados BR** | Estados (27), Cidades (5.570 municípios), removeAccents |
| **Firebase Auth** | Login email/senha, Google, Apple, reset senha, perfil, deleteAccount, Flow<User?> |
| **Firestore** | CRUD completo, queries com filtros, observação real-time, batch, subcollections |
| **Storage** | Upload, download URL, delete (single/batch) |
| **Remote Config** | AdRemoteConfig para ads |
| **Ads (AdMob)** | BannerAd, InterstitialAd, AppOpenAd com expect/actual |
| **Monetização** | MonetizationManager (AdsOnly/PremiumOnly/Freemium), PurchaseManager (RevenueCat) |
| **Feedback** | FeedbackService + FeedbackScreen (Firestore REST para projeto separado) |
| **Plataforma** | BiometricAuth, UrlLauncher, ShareHandler, FilePicker, NotificationScheduler |
| **UI Components** | AppButton, AppTextField, AppDialog, ConfirmationDialog, AppTopBar, BottomNavBar, Badge, EmptyState, FormContainer, NumberField, Toast, AppReviewDialog |
| **Auth UI** | LoginScreen, RegisterScreen, AuthFields, AuthLinks (Google/Apple) |
| **Feedback UI** | FeedbackScreen com textos customizáveis |
| **MVI** | BaseViewModel<State, Action, Effect> |
| **Theme** | AppTheme, AppColorScheme, AppTypography (light/dark, Material3) |
| **Logging** | AppLogger (d/i/w/e) com expect/actual |
| **Time** | currentTimeMillis() com expect/actual |

---

## 2. ANÁLISE POR APP

---

### 2.1 LOCADORA

**Descrição:** App de gestão de locação de equipamentos
**Arquivos commonMain:** ~120 | **Android:** 13 | **iOS:** 9
**Já usa kmplib:** Sim (AppReviewDialog, FeedbackScreen)

#### O que tem de IGUAL aos outros apps (candidato a kmplib):

| Categoria | O que existe | Já na kmplib? | Status |
|-----------|-------------|---------------|--------|
| **Auth Repository** | Interface + impl Firebase com login email, reset, changePassword, changeEmail, deleteAccount, updateProfile | Parcial | A kmplib tem AuthRepository mas locadora tem impl própria com mapeamento de erros PT-BR |
| **Auth User Model** | `User(id, email, displayName, photoUrl, isEmailVerified, providerId)` | Sim | Já existe na kmplib |
| **MVI Base** | `BaseViewModel<STATE, EFFECT, ACTION>` com ErrorHandler | Parcial | kmplib tem BaseViewModel mas sem ErrorHandler integrado |
| **Error Handler** | `ErrorHandler` + `DefaultErrorHandler` + `GlobalErrorManager` (Firebase errors → PT-BR) | **NÃO** | **CANDIDATO FORTE** |
| **Validadores** | CPF, CNPJ, Email (em InputMasks.kt) com checksum completo | Sim | Já na kmplib |
| **Máscaras** | Phone, CPF, CNPJ, Currency | Sim | Já na kmplib |
| **Estados BR** | 27 estados com abbreviation, code, name | Sim | Já na kmplib |
| **Cidades BR** | Municípios via JSON | Sim | Já na kmplib |
| **Currency Formatting** | `Double.formatAsCurrency()`, `String.currencyToDouble()` | Parcial | kmplib tem CurrencyMask mas locadora tem extensions extras |
| **DateUtils** | `adjustDatePickerTimestamp()`, `toDatePickerMillis()` | **NÃO** | **CANDIDATO** - correção timezone do DatePicker M3 |
| **ImagePicker** | expect/actual com scaling (max 1024px, 85% quality) | **NÃO** | **CANDIDATO FORTE** - Android Bitmap + iOS PHPicker |
| **AppVersion** | expect/actual para obter versão do app | **NÃO** | **CANDIDATO** |
| **Platform** | `openUrl()`, `currentTimeMillis()`, `toFirebaseStorageData()` | Parcial | UrlLauncher já existe |
| **PDF Generation** | Contract + Receipt PDF com expect/actual | **NÃO** | **CANDIDATO MÉDIO** - framework genérico de PDF |
| **Notification Scheduler** | WorkManager (Android) para notificações diárias | Parcial | kmplib tem NotificationScheduler |
| **UserPreferences** | DataStore Preferences com expect/actual | **NÃO** | **CANDIDATO** |
| **Toast/SuccessToast** | Animated toast com auto-dismiss | Parcial | kmplib tem Toast básico |
| **Strings centralizadas** | Objeto com ~612 linhas de strings PT-BR | **NÃO** | App-específico |
| **Koin DI pattern** | AppModule, CoreModule, AuthModule, DataModule, PlatformModule | **NÃO** | Padrão reutilizável |
| **Coil image loading** | coil-compose + coil-network-ktor | **NÃO** | Dependência comum |
| **Navigation pattern** | Auth/Main routing com type-safe routes | **NÃO** | Padrão, não código |

#### Código específico do app (NÃO centralizar):
- Models: Cliente, Equipamento, Locação, Recebimento, Patrimônio, CategoriaEquipamento
- Features: gestão de locações, entregas, recebimentos, dados empresa
- PDF de contrato e recibo (conteúdo específico)
- Workers de verificação de vencimento

---

### 2.2 MEU ADVOGADO

**Descrição:** App de solicitação de serviços jurídicos
**Arquivos commonMain:** ~86 | **Android:** vários | **iOS:** vários
**Já usa kmplib:** Sim (LoginScreen, RegisterScreen, FeedbackScreen, FirestoreService)

#### O que tem de IGUAL aos outros apps:

| Categoria | O que existe | Já na kmplib? | Status |
|-----------|-------------|---------------|--------|
| **Auth Repository** | Interface + impl com Google/Apple OAuth, Firestore user sync | Parcial | Usa kmplib auth + impl própria |
| **Google Auth Provider** | expect/actual com Credential Manager (Android) + Firebase (iOS) | **NÃO** | **CANDIDATO FORTE** - igual em todos os apps |
| **Apple Auth Provider** | expect/actual com ASAuthorizationController | **NÃO** | **CANDIDATO FORTE** - igual em todos os apps |
| **Auth Use Cases** | SignIn, SignUp, SignInWithGoogle, SignInWithApple | **NÃO** | **CANDIDATO** - padrão repetido |
| **Login/Register ViewModels** | Wrappers sobre kmplib screens | **NÃO** | Cada app repete este wrapper |
| **StorageService (custom)** | Wrapper para upload de documentos | Parcial | kmplib tem StorageService básico |
| **AppButton customizado** | 3 variantes: primary, outlined, secondary | Parcial | kmplib tem AppButton, mas app tem variantes |
| **AppTextField + AppTextArea** | TextField com counter de caracteres | Parcial | kmplib tem AppTextField sem TextArea |
| **Theme** | AppColors (Emerald Green primary), AppTypography, AppTheme | Parcial | Cores app-específicas |
| **Platform detection** | `expect val isIOS: Boolean` | **NÃO** | **CANDIDATO** - útil para branching |
| **FilePicker (custom)** | SelectedFile(name, data, mimeType) | Parcial | kmplib tem FilePicker diferente |
| **DataUtils** | `ByteArray.toFirebaseData()` expect/actual | **NÃO** | **CANDIDATO** - necessário para Storage |
| **AppPreferences** | `hasSeenOnboarding` flow com DataStore | **NÃO** | **CANDIDATO** - padrão onboarding |
| **ReviewPreferences** | Track review requests com contagem | **NÃO** | **CANDIDATO FORTE** - igual em todos os apps |
| **BrazilianMunicipalities** | Load cities from JSON | Sim | kmplib tem BrazilianCities |
| **Onboarding pattern** | Screen + ViewModel + hasSeenOnboarding | **NÃO** | **CANDIDATO** - padrão comum |
| **Legal pages** | PrivacyScreen, TermsScreen | **NÃO** | **CANDIDATO** - WebView genérico |
| **Koin DI modules** | Firebase, Repository, UseCase, ViewModel, Platform | **NÃO** | Padrão repetido |

#### Código específico do app (NÃO centralizar):
- Models: Request, Document, Lawyer, LegalCategory, ProblemContext, ProblemSituation
- Features: formulário jurídico multi-step, admin panel, requests
- Categorias jurídicas e seus fluxos

---

### 2.3 MEU FISIO

**Descrição:** App de fisioterapia com prescrição de exercícios
**Arquivos commonMain:** ~181 | **Android:** 14 | **iOS:** 14
**Já usa kmplib:** Sim (FeedbackScreen, FeedbackService, PurchaseManager, MonetizationManager)

#### O que tem de IGUAL aos outros apps:

| Categoria | O que existe | Já na kmplib? | Status |
|-----------|-------------|---------------|--------|
| **Auth Repository** | Interface + impl com Google/Apple, Firestore user sync | Parcial | Padrão repetido |
| **AuthStateManager** | Sealed class (Loading/Authenticated/NotAuthenticated) + StateFlow | **NÃO** | **CANDIDATO FORTE** - igual em Prospecta |
| **Google/Apple Auth Provider** | expect/actual idêntico aos outros apps | **NÃO** | **CANDIDATO FORTE** |
| **Auth Use Cases** | SignIn, SignUp, SignOut, GetCurrentUser, ResetPassword | **NÃO** | **CANDIDATO** |
| **BaseViewModel MVI** | `BaseViewModel<STATE, EFFECT, ACTION>` com setState/sendEffect | Parcial | kmplib tem versão similar |
| **UiState/UiAction/UiEffect** | Marker interfaces | **NÃO** | **CANDIDATO** - padrão MVI |
| **Error Handler** | Interface + DefaultErrorHandler com Crashlytics + mensagens PT-BR | **NÃO** | **CANDIDATO FORTE** - igual locadora |
| **ErrorMessages** | Object com constantes de erro em PT-BR | **NÃO** | **CANDIDATO** |
| **GlobalErrorManager** | Estado global de erros | **NÃO** | **CANDIDATO** |
| **ValidationHelper** | isValidEmail, isValidPassword, isValidPhone, isValidName | Sim | kmplib tem validators separados |
| **DateUtils** | formatDate (DD/MM/YYYY), formatDateTime, formatTime | **NÃO** | **CANDIDATO FORTE** - formato BR |
| **PhoneVisualTransformation** | Máscara (XX) XXXXX-XXXX | Sim | Já na kmplib |
| **DateVisualTransformation** | Máscara XX/XX/XXXX | **NÃO** | **CANDIDATO FORTE** - não existe na kmplib |
| **AppButton** | Com variante acessível (64dp vs 52dp) | Parcial | kmplib tem AppButton sem acessibilidade |
| **AppTextField** | Single/multi-line com erro | Parcial | Já na kmplib |
| **BottomNavBar** | Genérico com items/colors/selected | Sim | Já na kmplib |
| **StatusBadge** | Badge com cores por status | **NÃO** | **CANDIDATO** |
| **ErrorModal** | Dialog de erro padronizado | **NÃO** | **CANDIDATO** |
| **Theme com acessibilidade** | 3 temas (Accessible 18sp, Modern 16sp, Physio) | **NÃO** | **CANDIDATO** - sistema extensível |
| **AppLogger** | expect/actual d/e/i/w | Sim | Já na kmplib |
| **ImagePicker** | expect Composable + GalleryPickerLauncher | **NÃO** | **CANDIDATO FORTE** |
| **VideoPicker** | expect Composable + VideoPickerLauncher | **NÃO** | **CANDIDATO** |
| **VibrationManager** | expect class vibrate/vibratePattern | **NÃO** | **CANDIDATO** |
| **HttpDownloader** | expect suspend fun downloadBytes(url) | **NÃO** | **CANDIDATO** |
| **ByteArrayExtensions** | toImageBitmap(), toStorageData() expect | **NÃO** | **CANDIDATO FORTE** |
| **ReviewPreferences** | hasReviewed, markReviewed, completionCount | **NÃO** | **CANDIDATO FORTE** |
| **DataStore factory** | expect/actual para criar DataStore | **NÃO** | **CANDIDATO** |
| **Room Database** | KMP Room com BundledSQLiteDriver | **NÃO** | Padrão, não centralizar |
| **ThemePreferences** | selectedTheme Flow com DataStore | **NÃO** | **CANDIDATO** |
| **StorageService** | uploadFile, deleteFile, getDownloadUrl customizado | Parcial | kmplib tem versão básica |
| **PremiumPlan enum** | id, displayName, price | **NÃO** | App-específico mas padrão útil |
| **Koin DI modules** | 6 módulos padrão | **NÃO** | Padrão repetido |

#### Código específico do app (NÃO centralizar):
- Models: Prescription, LibraryExercise, ExerciseLog, Appointment, WorkoutMode
- Features: exercícios com timer, prescrições, agenda, teleconsulta
- Roles: PATIENT vs PHYSIOTHERAPIST
- Cache local de exercícios

---

### 2.4 PROSPECTA

**Descrição:** App de prospecção de vendas com geolocalização
**Arquivos commonMain:** ~150+ | **Android:** vários | **iOS:** vários
**Já usa kmplib:** Sim (v1.1.0 - feedback, ads, monetização)

#### O que tem de IGUAL aos outros apps:

| Categoria | O que existe | Já na kmplib? | Status |
|-----------|-------------|---------------|--------|
| **Auth Repository** | Interface + impl com Google/Apple, Firestore sync | Parcial | Padrão repetido |
| **AuthStateManager** | Sealed class Loading/Authenticated/NotAuthenticated | **NÃO** | **CANDIDATO FORTE** - idêntico MeuFisio |
| **Google/Apple Auth Provider** | expect/actual idêntico | **NÃO** | **CANDIDATO FORTE** |
| **BaseViewModel MVI** | `BaseViewModel<STATE, EFFECT, ACTION>` com setState/sendEffect | Parcial | Versão igual ao MeuFisio |
| **Error Handler** | Mapeia exceções → mensagens PT-BR + Crashlytics | **NÃO** | **CANDIDATO FORTE** |
| **PhoneVisualTransformation** | (XX) XXXXX-XXXX | Sim | Já na kmplib |
| **DateVisualTransformation** | XX/XX/XXXX | **NÃO** | **CANDIDATO FORTE** - igual MeuFisio |
| **StatusBadge** | Status com cores | **NÃO** | **CANDIDATO** - igual MeuFisio |
| **NoInternetDialog** | Dialog para rede offline | **NÃO** | **CANDIDATO** |
| **NetworkChecker** | Interface + Android/iOS impl para conectividade | **NÃO** | **CANDIDATO FORTE** |
| **TimeUtils** | now(), currentTimeMillis(), todayDate() expect/actual | Parcial | kmplib tem currentTimeMillis |
| **ReviewPreferences** | hasReviewed, markReviewed, completionCount | **NÃO** | **CANDIDATO FORTE** - idêntico |
| **LocationService** | GPS expect/actual (Android FusedLocation, iOS CLLocation) | **NÃO** | **CANDIDATO** |
| **ReverseGeocodingService** | Endereço a partir de coordenadas | **NÃO** | **CANDIDATO** |
| **LocationUtils** | calculateDistance (Haversine), formatDistance | **NÃO** | **CANDIDATO** |
| **PlatformMapView** | expect/actual para Google Maps / Apple Maps | **NÃO** | **CANDIDATO MÉDIO** |
| **Theme dinâmico** | Primary color customizável via Company settings | **NÃO** | **CANDIDATO** |
| **Strings object** | Todas strings em PT-BR centralizadas | **NÃO** | App-específico |
| **Company system** | Multi-user com invite code | **NÃO** | Poderia ser genérico |
| **PremiumPlan** | enum com id/price | **NÃO** | Padrão repetido |
| **Koin DI modules** | Mesma estrutura dos outros apps | **NÃO** | Padrão repetido |

#### Código específico do app (NÃO centralizar):
- Models: Location, Visit, Lead, DailyPlan, Company, AreaConfig
- Features: mapa de prospecção, registro de visitas, leads, categorias
- 14 áreas de negócio pré-configuradas

---

### 2.5 SUPER 8

**Descrição:** App de torneios esportivos (tipo Super 8/Americano)
**Arquivos commonMain:** ~60+ | **Android:** vários | **iOS:** vários
**Já usa kmplib:** Sim (FeedbackScreen, AdManager, PurchaseManager, MonetizationManager)

#### O que tem de IGUAL aos outros apps:

| Categoria | O que existe | Já na kmplib? | Status |
|-----------|-------------|---------------|--------|
| **AppLogger** | expect/actual w() com Crashlytics | Sim | Já na kmplib (parcial - só w) |
| **BuildInfo** | `isDebug` expect/actual | **NÃO** | **CANDIDATO** |
| **TimeUtils** | `currentInstant()` | Parcial | kmplib tem currentTimeMillis |
| **ShareHandler** | shareText, shareImage com expect/actual | Sim | Já na kmplib |
| **UrlOpener** | openUrl, openEmail, openSubscriptionManagement | Parcial | kmplib tem UrlLauncher |
| **ReviewPreferences** | hasReviewed, markReviewed, completionCount | **NÃO** | **CANDIDATO FORTE** - idêntico |
| **BitmapEncoder** | encodeBitmapToPng expect/actual | **NÃO** | **CANDIDATO** |
| **CrashlyticsService** | Interface + impl para logging de erros | **NÃO** | **CANDIDATO** |
| **Theme** | AppColors + AppTheme Material3 | Parcial | Cores app-específicas |
| **PremiumPlan** | enum com id/price (mensal, trimestral) | **NÃO** | Padrão repetido |
| **Room Database** | KMP Room com BundledSQLiteDriver + DAOs | **NÃO** | Padrão, não centralizar |
| **Koin DI** | 6 módulos | **NÃO** | Padrão repetido |
| **Navigation** | Type-safe routes sealed interface | **NÃO** | Padrão repetido |
| **i18n** | composeResources values/ PT, EN, ES | **NÃO** | Melhor prática |

#### Código específico do app (NÃO centralizar):
- Models: Tournament, Player, Match, Ranking
- Use Cases: DrawPairsUseCase (algoritmo circle method), CanCreateTournamentUseCase
- Features: criação de torneio, ranking, compartilhamento de imagem
- Database local (Room) para torneios offline

---

## 3. MATRIZ DE COMPARAÇÃO CRUZADA

### Legenda:
- ✅ = Presente no app
- ⭐ = Já usa da kmplib
- ❌ = Não possui

| Funcionalidade | Locadora | Meu Advogado | Meu Fisio | Prospecta | Super 8 | Na kmplib? |
|---------------|----------|-------------|-----------|-----------|---------|------------|
| **AUTH - Email/Password** | ✅ | ⭐ | ✅ | ✅ | ❌ | Sim |
| **AUTH - Google Sign-In** | ❌ | ✅ | ✅ | ✅ | ❌ | Parcial |
| **AUTH - Apple Sign-In** | ❌ | ✅ | ✅ | ✅ | ❌ | Parcial |
| **AUTH - GoogleAuthProvider expect/actual** | ❌ | ✅ | ✅ | ✅ | ❌ | **NÃO** |
| **AUTH - AppleAuthProvider expect/actual** | ❌ | ✅ | ✅ | ✅ | ❌ | **NÃO** |
| **AUTH - AuthStateManager** | ❌ | ❌ | ✅ | ✅ | ❌ | **NÃO** |
| **AUTH - Auth Use Cases** | ❌ | ✅ | ✅ | ✅ | ❌ | **NÃO** |
| **AUTH - Login/Register Screen** | ✅ própria | ⭐ | ✅ própria | ✅ própria | ❌ | Sim |
| **AUTH - Login/Register ViewModel** | ✅ | ✅ | ✅ | ✅ | ❌ | **NÃO** |
| | | | | | | |
| **FIREBASE - Firestore CRUD** | ✅ própria | ⭐ | ⭐ | ✅ própria | ❌ | Sim |
| **FIREBASE - Storage** | ✅ | ✅ | ✅ | ❌ | ❌ | Sim |
| **FIREBASE - Crashlytics** | ✅ | ✅ | ✅ | ✅ | ✅ | **NÃO** |
| **FIREBASE - Analytics** | ✅ | ✅ | ✅ | ✅ | ✅ | **NÃO** |
| | | | | | | |
| **MVI - BaseViewModel** | ✅ | ❌ | ✅ | ✅ | ❌ | Parcial |
| **MVI - UiState/Action/Effect** | ✅ | ❌ | ✅ | ✅ | ❌ | **NÃO** |
| **ERROR - ErrorHandler** | ✅ | ❌ | ✅ | ✅ | ❌ | **NÃO** |
| **ERROR - GlobalErrorManager** | ✅ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| **ERROR - Firebase errors PT-BR** | ✅ | ✅ | ✅ | ✅ | ❌ | **NÃO** |
| | | | | | | |
| **VALIDAÇÃO - CPF** | ✅ | ⭐ | ❌ | ❌ | ❌ | Sim |
| **VALIDAÇÃO - CNPJ** | ✅ | ⭐ | ❌ | ❌ | ❌ | Sim |
| **VALIDAÇÃO - Email** | ✅ | ⭐ | ✅ | ✅ | ❌ | Sim |
| **VALIDAÇÃO - Phone** | ✅ | ⭐ | ✅ | ✅ | ❌ | Sim |
| **VALIDAÇÃO - Password** | ✅ | ⭐ | ✅ | ✅ | ❌ | Sim |
| **VALIDAÇÃO - Name** | ❌ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| | | | | | | |
| **MASK - Phone** | ✅ | ⭐ | ✅ | ✅ | ❌ | Sim |
| **MASK - CPF** | ✅ | ⭐ | ❌ | ❌ | ❌ | Sim |
| **MASK - CNPJ** | ✅ | ⭐ | ❌ | ❌ | ❌ | Sim |
| **MASK - Currency** | ✅ | ❌ | ❌ | ❌ | ❌ | Sim |
| **MASK - CEP** | ❌ | ❌ | ❌ | ❌ | ❌ | Sim |
| **MASK - Date (DD/MM/YYYY)** | ❌ | ❌ | ✅ | ✅ | ❌ | **NÃO** |
| | | | | | | |
| **DADOS BR - Estados** | ✅ | ⭐ | ❌ | ❌ | ❌ | Sim |
| **DADOS BR - Cidades** | ✅ | ✅ | ❌ | ❌ | ❌ | Sim |
| | | | | | | |
| **UI - AppButton** | ✅ | ✅ | ✅ | ❌ | ❌ | Sim |
| **UI - AppTextField** | ✅ | ✅ | ✅ | ❌ | ❌ | Sim |
| **UI - AppTextArea** | ❌ | ✅ | ✅ | ❌ | ❌ | **NÃO** |
| **UI - BottomNavBar** | ✅ | ✅ | ✅ | ❌ | ❌ | Sim |
| **UI - AppTopBar** | ✅ | ❌ | ❌ | ❌ | ❌ | Sim |
| **UI - Toast** | ✅ | ❌ | ❌ | ❌ | ❌ | Sim |
| **UI - AppDialog** | ✅ | ❌ | ❌ | ❌ | ❌ | Sim |
| **UI - ConfirmationDialog** | ✅ | ❌ | ❌ | ❌ | ❌ | Sim |
| **UI - StatusBadge** | ❌ | ❌ | ✅ | ✅ | ❌ | **NÃO** |
| **UI - ErrorModal** | ❌ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| **UI - NoInternetDialog** | ❌ | ❌ | ❌ | ✅ | ❌ | **NÃO** |
| **UI - EmptyState** | ✅ | ❌ | ❌ | ❌ | ❌ | Sim |
| **UI - AppReviewDialog** | ⭐ | ❌ | ❌ | ❌ | ❌ | Sim |
| | | | | | | |
| **PLATFORM - UrlLauncher** | ✅ | ❌ | ❌ | ❌ | ✅ | Sim |
| **PLATFORM - ShareHandler** | ❌ | ❌ | ❌ | ❌ | ✅ | Sim |
| **PLATFORM - BiometricAuth** | ❌ | ❌ | ❌ | ❌ | ❌ | Sim |
| **PLATFORM - FilePicker** | ❌ | ✅ | ❌ | ❌ | ❌ | Sim |
| **PLATFORM - NotificationScheduler** | ✅ | ❌ | ❌ | ❌ | ❌ | Sim |
| **PLATFORM - ImagePicker Compose** | ✅ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| **PLATFORM - VideoPicker** | ❌ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| **PLATFORM - VibrationManager** | ❌ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| **PLATFORM - NetworkChecker** | ❌ | ❌ | ❌ | ✅ | ❌ | **NÃO** |
| **PLATFORM - LocationService GPS** | ❌ | ❌ | ❌ | ✅ | ❌ | **NÃO** |
| **PLATFORM - HttpDownloader** | ❌ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| **PLATFORM - BitmapEncoder** | ❌ | ❌ | ❌ | ❌ | ✅ | **NÃO** |
| **PLATFORM - BuildInfo (isDebug)** | ❌ | ❌ | ❌ | ❌ | ✅ | **NÃO** |
| **PLATFORM - AppVersion** | ✅ | ❌ | ❌ | ❌ | ❌ | **NÃO** |
| **PLATFORM - isIOS flag** | ❌ | ✅ | ❌ | ❌ | ❌ | **NÃO** |
| | | | | | | |
| **PREFS - ReviewPreferences** | ❌ | ✅ | ✅ | ✅ | ✅ | **NÃO** |
| **PREFS - DataStore factory** | ✅ | ✅ | ✅ | ❌ | ❌ | **NÃO** |
| **PREFS - ThemePreferences** | ❌ | ❌ | ✅ | ❌ | ❌ | **NÃO** |
| **PREFS - OnboardingPreferences** | ❌ | ✅ | ❌ | ❌ | ❌ | **NÃO** |
| | | | | | | |
| **MONETIZAÇÃO - AdMob** | ❌ | ❌ | ⭐ | ❌ | ⭐ | Sim |
| **MONETIZAÇÃO - RevenueCat** | ❌ | ❌ | ⭐ | ✅ | ⭐ | Sim |
| **MONETIZAÇÃO - PremiumPlan enum** | ❌ | ❌ | ✅ | ✅ | ✅ | **NÃO** |
| **MONETIZAÇÃO - PremiumScreen** | ❌ | ❌ | ✅ | ✅ | ✅ | **NÃO** |
| **MONETIZAÇÃO - PremiumViewModel** | ❌ | ❌ | ✅ | ✅ | ✅ | **NÃO** |
| | | | | | | |
| **FEEDBACK** | ⭐ | ⭐ | ⭐ | ✅ | ⭐ | Sim |
| **CRASHLYTICS Service** | ✅ | ✅ | ✅ | ✅ | ✅ | **NÃO** |
| | | | | | | |
| **DateUtils formatBR** | ✅ | ❌ | ✅ | ✅ | ❌ | **NÃO** |
| **Currency extensions** | ✅ | ❌ | ❌ | ❌ | ❌ | Parcial |
| **ByteArray extensions** | ❌ | ✅ | ✅ | ❌ | ✅ | **NÃO** |
| | | | | | | |
| **Room Database** | ❌ | ❌ | ✅ | ❌ | ✅ | **NÃO** |
| **Coil image loading** | ✅ | ❌ | ❌ | ❌ | ❌ | **NÃO** |
| **i18n (multi-idioma)** | ❌ | ❌ | ❌ | ❌ | ✅ | **NÃO** |

---

## 4. RECOMENDAÇÕES DE CENTRALIZAÇÃO

### 4.1 PRIORIDADE ALTA (presente em 3+ apps, código praticamente idêntico)

#### 4.1.1 GoogleAuthProvider + AppleAuthProvider (expect/actual)
**Apps que têm:** Meu Advogado, Meu Fisio, Prospecta (3 apps)
**Impacto:** Elimina ~8 arquivos duplicados (4 expect + 4 actual por auth provider)
**O que centralizar:**
```
GoogleAuthProvider (expect class)
├── GoogleSignInResult(idToken, accessToken, error)
├── androidMain: CredentialManager + Google Identity API
└── iosMain: Firebase Google Auth

AppleAuthProvider (expect class)
├── AppleSignInResult(idToken, nonce, fullName, email, error)
├── androidMain: Custom OAuth ou Firebase
└── iosMain: ASAuthorizationController
```

#### 4.1.2 AuthStateManager
**Apps que têm:** Meu Fisio, Prospecta (2 apps, mas padrão útil para todos)
**Impacto:** Centraliza gestão global de estado de autenticação
**O que centralizar:**
```kotlin
sealed class AuthState {
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
}

object AuthStateManager {
    val authState: StateFlow<AuthState>
    fun setAuthenticated(user: User)
    fun setNotAuthenticated()
}
```

#### 4.1.3 ReviewPreferences (expect/actual)
**Apps que têm:** Meu Advogado, Meu Fisio, Prospecta, Super 8 (4 apps!)
**Impacto:** Código 100% idêntico em todos os apps
**O que centralizar:**
```kotlin
expect class ReviewPreferences {
    fun hasReviewed(): Boolean
    fun markReviewed()
    fun getCompletionCount(): Int
    fun incrementCompletionCount(): Int
}
```

#### 4.1.4 ErrorHandler + Firebase Error Mapping PT-BR
**Apps que têm:** Locadora, Meu Fisio, Prospecta (3 apps)
**Impacto:** Elimina tradução duplicada de erros Firebase
**O que centralizar:**
```kotlin
interface ErrorHandler {
    fun handleError(throwable: Throwable): String
}

class DefaultErrorHandler(crashlytics: CrashlyticsService) : ErrorHandler {
    // Mapeia FirebaseAuthException → mensagens PT-BR
    // Mapeia network errors → mensagens PT-BR
}

object GlobalErrorManager {
    val currentError: StateFlow<String?>
    fun showError(message: String)
    fun clearError()
}
```

#### 4.1.5 DateVisualTransformation (máscara de data)
**Apps que têm:** Meu Fisio, Prospecta (2 apps)
**Impacto:** Máscara DD/MM/YYYY não existe na kmplib
**O que centralizar:**
```kotlin
class DateVisualTransformation : VisualTransformation
fun filterDateInput(text: String): String
```

#### 4.1.6 DateUtils para formato brasileiro
**Apps que têm:** Locadora, Meu Fisio, Prospecta (3 apps)
**Impacto:** Formatação de datas em padrão BR repetida
**O que centralizar:**
```kotlin
object DateUtils {
    fun formatDate(timestamp: Long): String       // DD/MM/YYYY
    fun formatDateTime(timestamp: Long): String    // DD/MM/YYYY HH:MM
    fun formatTime(hour: Int, minute: Int): String // HH:MM
    fun adjustDatePickerTimestamp(utcMillis: Long): Long // Fix timezone M3
    fun todayDate(): LocalDate
}
```

#### 4.1.7 CrashlyticsService
**Apps que têm:** Todos os 5 apps
**Impacto:** Interface + impl para logging de erros no Crashlytics
**O que centralizar:**
```kotlin
interface CrashlyticsService {
    fun logMessage(message: String)
    fun setCustomKey(key: String, value: String)
    fun setUserId(userId: String)
    fun recordException(throwable: Throwable)
    fun setCrashlyticsCollectionEnabled(enabled: Boolean)
}
// + expect/actual implementations
```

#### 4.1.8 PremiumScreen + PremiumViewModel
**Apps que têm:** Meu Fisio, Prospecta, Super 8 (3 apps)
**Impacto:** Tela de compra de assinatura praticamente idêntica
**O que centralizar:**
```kotlin
// Screen genérica que recebe config
@Composable
fun PremiumScreen(
    config: PremiumScreenConfig,
    onBack: () -> Unit,
    onPurchaseComplete: () -> Unit
)

data class PremiumScreenConfig(
    val title: String,
    val features: List<String>,
    val plans: List<PremiumPlan>,
    val termsUrl: String,
    val privacyUrl: String
)
```

### 4.2 PRIORIDADE MÉDIA (presente em 2 apps ou útil para futuros apps)

#### 4.2.1 ByteArray Extensions
**Apps:** Meu Advogado, Meu Fisio, Super 8
```kotlin
expect fun ByteArray.toImageBitmap(): ImageBitmap
expect fun ByteArray.toStorageData(): Any  // Firebase Storage Data
expect fun ImageBitmap.encodeToPng(): ByteArray
```

#### 4.2.2 ImagePicker Composable
**Apps:** Locadora, Meu Fisio
```kotlin
expect class GalleryPickerLauncher {
    fun launch()
}
@Composable
expect fun rememberGalleryPickerLauncher(onResult: (ByteArray?) -> Unit): GalleryPickerLauncher
```

#### 4.2.3 StatusBadge Component
**Apps:** Meu Fisio, Prospecta
```kotlin
@Composable
fun StatusBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color
)
```

#### 4.2.4 NetworkChecker
**Apps:** Prospecta (mas útil para todos)
```kotlin
interface NetworkChecker {
    fun isConnected(): Boolean
    val isConnectedFlow: Flow<Boolean>
}
// + expect/actual implementations
```

#### 4.2.5 AppTextArea
**Apps:** Meu Advogado, Meu Fisio
```kotlin
@Composable
fun AppTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxCharacters: Int = 500,
    minLines: Int = 3
)
```

#### 4.2.6 BuildInfo / Platform Info
**Apps:** Super 8, Meu Advogado
```kotlin
expect object BuildInfo {
    val isDebug: Boolean
    val platform: String  // "Android" ou "iOS"
}
```

#### 4.2.7 ErrorModal / NoInternetDialog
**Apps:** Meu Fisio, Prospecta
```kotlin
@Composable
fun ErrorModal(title: String, message: String, onDismiss: () -> Unit)

@Composable
fun NoInternetDialog(onRetry: () -> Unit, onDismiss: () -> Unit)
```

#### 4.2.8 Auth Use Cases genéricos
**Apps:** Meu Advogado, Meu Fisio, Prospecta
```kotlin
class SignInUseCase(private val authRepository: AuthRepository)
class SignUpUseCase(private val authRepository: AuthRepository)
class SignInWithGoogleUseCase(private val authRepository: AuthRepository)
class SignInWithAppleUseCase(private val authRepository: AuthRepository)
class ResetPasswordUseCase(private val authRepository: AuthRepository)
```

#### 4.2.9 Onboarding/AppPreferences pattern
**Apps:** Meu Advogado (mas útil para todos)
```kotlin
interface AppPreferences {
    val hasSeenOnboarding: Flow<Boolean>
    suspend fun setHasSeenOnboarding(seen: Boolean)
}
```

#### 4.2.10 DataStore Factory
**Apps:** Locadora, Meu Advogado, Meu Fisio
```kotlin
expect fun createDataStore(name: String): DataStore<Preferences>
```

### 4.3 PRIORIDADE BAIXA (1 app ou muito específico)

| Item | App | Justificativa |
|------|-----|---------------|
| VibrationManager | Meu Fisio | Muito específico, só 1 app usa |
| VideoPicker | Meu Fisio | Só 1 app usa |
| HttpDownloader | Meu Fisio | Só 1 app usa |
| LocationService GPS | Prospecta | Muito específico |
| ReverseGeocodingService | Prospecta | Muito específico |
| PlatformMapView | Prospecta | Muito específico |
| PDF Generation | Locadora | Só 1 app usa, framework muito variável |
| Room Database helpers | Meu Fisio, Super 8 | Cada app tem schema diferente |
| Coil image loading config | Locadora | Configuração, não código |

---

## 5. RESUMO EXECUTIVO

### Números gerais:

| Métrica | Valor |
|---------|-------|
| Total de arquivos analisados (commonMain) | ~597 |
| Funcionalidades já na kmplib | 16 módulos |
| Candidatos ALTA prioridade para kmplib | 8 itens |
| Candidatos MÉDIA prioridade para kmplib | 10 itens |
| Candidatos BAIXA prioridade | 9 itens |

### Top 5 mudanças de maior impacto:

1. **GoogleAuthProvider + AppleAuthProvider** → Elimina ~24 arquivos duplicados (8 por app × 3 apps)
2. **ReviewPreferences** → Elimina ~12 arquivos idênticos (3 por app × 4 apps)
3. **ErrorHandler + Firebase PT-BR** → Elimina ~9 arquivos duplicados (3 por app × 3 apps)
4. **PremiumScreen + ViewModel** → Elimina ~6 arquivos quase idênticos (2 por app × 3 apps)
5. **DateVisualTransformation + DateUtils BR** → Elimina ~6 arquivos duplicados

### Apps que mais se beneficiam:

| App | Código duplicado encontrado | Já usa kmplib? |
|-----|---------------------------|----------------|
| **Meu Fisio** | MUITO ALTO - 14+ funcionalidades duplicadas | Parcialmente |
| **Prospecta** | ALTO - 12+ funcionalidades duplicadas | Parcialmente |
| **Meu Advogado** | MÉDIO - 8+ funcionalidades duplicadas | Bem integrado |
| **Locadora** | MÉDIO - 7+ funcionalidades duplicadas | Pouco integrado |
| **Super 8** | BAIXO - 5+ funcionalidades duplicadas | Bem integrado |

### Estrutura sugerida para kmplib v2.0:

```
kmplib/library/src/commonMain/kotlin/br/com/codecacto/kmplib/
├── auth/                    (EXPANDIR)
│   ├── AuthRepository.kt        ← já existe
│   ├── AuthStateManager.kt      ← NOVO
│   ├── GoogleAuthProvider.kt     ← NOVO (expect)
│   ├── AppleAuthProvider.kt      ← NOVO (expect)
│   ├── User.kt                   ← já existe
│   └── usecase/                  ← NOVO
│       ├── SignInUseCase.kt
│       ├── SignUpUseCase.kt
│       └── ...
├── brdata/                  (MANTER)
├── core/                    (EXPANDIR)
│   ├── error/                    ← NOVO
│   │   ├── ErrorHandler.kt
│   │   ├── DefaultErrorHandler.kt
│   │   └── GlobalErrorManager.kt
│   ├── util/
│   │   ├── AppLogger.kt         ← já existe
│   │   ├── TimeUtils.kt         ← já existe
│   │   ├── DateUtils.kt         ← NOVO (formatBR)
│   │   └── BuildInfo.kt         ← NOVO
│   └── extensions/               ← NOVO
│       └── ByteArrayExtensions.kt
├── feedback/                (MANTER)
├── firebase/                (EXPANDIR)
│   ├── ads/                      ← já existe
│   ├── auth/                     ← já existe
│   ├── crashlytics/              ← NOVO
│   │   └── CrashlyticsService.kt
│   ├── firestore/                ← já existe
│   └── storage/                  ← já existe
├── mask/                    (EXPANDIR)
│   ├── CepMask.kt               ← já existe
│   ├── CnpjMask.kt              ← já existe
│   ├── CpfMask.kt               ← já existe
│   ├── CurrencyMask.kt          ← já existe
│   ├── DateMask.kt              ← NOVO
│   └── PhoneMask.kt             ← já existe
├── monetization/            (EXPANDIR)
│   ├── MonetizationConfig.kt    ← já existe
│   ├── MonetizationManager.kt   ← já existe
│   └── purchase/                 ← já existe
├── platform/                (EXPANDIR)
│   ├── BiometricAuth.kt         ← já existe
│   ├── FilePicker.kt            ← já existe
│   ├── ImagePicker.kt           ← NOVO (Composable)
│   ├── NetworkChecker.kt        ← NOVO
│   ├── NotificationScheduler.kt ← já existe
│   ├── ShareHandler.kt          ← já existe
│   └── UrlLauncher.kt           ← já existe
├── preferences/             (NOVO)
│   ├── ReviewPreferences.kt     ← NOVO
│   ├── AppPreferences.kt        ← NOVO
│   └── DataStoreFactory.kt      ← NOVO
├── ui/                      (EXPANDIR)
│   ├── components/
│   │   ├── AppButton.kt         ← já existe
│   │   ├── AppDialog.kt         ← já existe
│   │   ├── AppTextArea.kt       ← NOVO
│   │   ├── AppTextField.kt      ← já existe
│   │   ├── AppTopBar.kt         ← já existe
│   │   ├── Badge.kt             ← já existe
│   │   ├── BottomNavBar.kt      ← já existe
│   │   ├── ConfirmationDialog.kt ← já existe
│   │   ├── EmptyState.kt        ← já existe
│   │   ├── ErrorModal.kt        ← NOVO
│   │   ├── FormContainer.kt     ← já existe
│   │   ├── NoInternetDialog.kt  ← NOVO
│   │   ├── NumberField.kt       ← já existe
│   │   ├── StatusBadge.kt       ← NOVO
│   │   ├── Toast.kt             ← já existe
│   │   ├── AppReviewDialog.kt   ← já existe
│   │   └── auth/                 ← já existe
│   ├── mvi/
│   │   ├── BaseViewModel.kt     ← já existe (melhorar)
│   │   ├── UiState.kt           ← NOVO
│   │   ├── UiAction.kt          ← NOVO
│   │   └── UiEffect.kt          ← NOVO
│   ├── screens/
│   │   ├── feedback/             ← já existe
│   │   ├── login/                ← já existe
│   │   ├── premium/              ← NOVO
│   │   │   ├── PremiumScreen.kt
│   │   │   └── PremiumViewModel.kt
│   │   └── register/             ← já existe
│   └── theme/                    ← já existe
└── validation/              (EXPANDIR)
    ├── CnpjValidator.kt         ← já existe
    ├── CpfValidator.kt          ← já existe
    ├── EmailValidator.kt        ← já existe
    ├── NameValidator.kt         ← NOVO
    ├── PasswordValidator.kt     ← já existe
    └── PhoneValidator.kt        ← já existe
```

### Itens NOVOS a adicionar: ~25 arquivos/classes
### Itens a MELHORAR: ~5 arquivos existentes
### Estimativa de redução de código duplicado: ~50-70 arquivos nos apps

---

## 6. FUNCIONALIDADES ÚNICAS POR APP (presentes em 1 app, mas genéricas para futuros apps)

Esta seção analisa funcionalidades que existem em apenas um dos apps, mas que são suficientemente genéricas para justificar centralização na kmplib, pois qualquer app futuro pode precisar delas.

---

### 6.1 LOCADORA — Funcionalidades únicas genéricas

#### 6.1.1 PDF Generation & Preview System (ALTA prioridade futura)
**Arquivos:** `core/pdf/` (6 arquivos common + 6 platform)

Sistema completo de geração e visualização de PDF com expect/actual:

**PdfViewer (expect/actual):**
- **Android:** Usa `PdfRenderer` para converter páginas em Bitmaps, suporta pinch-zoom (1x a 5x) e pan com `detectTransformGestures`, renderiza a 2x scale (A4: 595x842)
- **iOS:** Usa `PDFKit` nativo com `PDFView`

**PdfPreviewScreen (common):**
- Tela completa com TopAppBar, botão de voltar e **botão de compartilhar**
- Share via `FileProvider.getUriForFile()` (Android) e `UIActivityViewController` (iOS)

**Geração de PDF (expect/actual):**
- Helpers: `drawText()`, `drawCenteredText()`, `drawWrappedText()`, `drawLine()`
- Download de logo remoto (Firebase Storage URL) e embedding no PDF
- Text wrapping automático para linhas longas
- Suporte a múltiplas páginas (A4)
- Android: `PdfDocument` API | iOS: `UIGraphicsPDFRendererFormat`

**Por que centralizar:** Qualquer app que precise gerar recibos, contratos, relatórios ou compartilhar documentos vai precisar deste sistema. A parte genérica (framework de PDF + preview + share) é totalmente reutilizável.

**O que extrair para kmplib:**
```
pdf/
├── PdfViewer.kt (expect) — renderização com zoom/pan
├── PdfPreviewScreen.kt — tela com share button
├── PdfGenerator.kt (expect) — classe base com helpers de desenho
└── PdfShareUtils.kt — compartilhamento de arquivos PDF
```

#### 6.1.2 SuccessToast — Toast animado com auto-dismiss
**Arquivo:** `core/ui/components/SuccessToast.kt`

```kotlin
@Composable
fun SuccessToast(message: String, visible: Boolean, onDismiss: () -> Unit)
```
- Animação: `fadeIn + slideInVertically` (entra do topo)
- Auto-dismiss após 1.2s via `LaunchedEffect`
- Ícone CheckCircle + texto em fundo verde com shadow
- Muito mais polido que o Toast básico da kmplib

**Por que centralizar:** Feedback visual de sucesso é necessário em praticamente todos os apps. O Toast da kmplib é básico; este é production-ready.

#### 6.1.3 NotificationBadge — Badge numérico com "99+"
**Arquivo:** `core/ui/components/NotificationBadge.kt`

```kotlin
@Composable
fun NotificationBadge(count: Int, onClick: () -> Unit, tint: Color)
```
- Sizing dinâmico: 18dp (1 dígito) vs 20dp (2+ dígitos)
- Cap "99+" para contagens grandes
- Offset position para visual polido

**Por que centralizar:** Qualquer app com notificações precisa disso. Componente 100% genérico.

#### 6.1.4 AppVersion — Acesso à versão do app
**Arquivos:** `AppVersion.kt` (expect) + `.android.kt` + `.ios.kt`

```kotlin
data class AppVersionInfo(val versionName: String, val versionCode: Int) {
    val displayVersion: String get() = "Versão $versionName ($versionCode)"
}
expect fun getAppVersion(): AppVersionInfo
```
- Android: `BuildConfig.VERSION_NAME/CODE`
- iOS: `NSBundle.mainBundle` → `CFBundleShortVersionString/CFBundleVersion`

**Por que centralizar:** Todo app mostra versão na tela de Settings. Trivial mas chato de reimplementar.

#### 6.1.5 Currency Extensions avançadas
**Arquivo:** `core/ui/util/CurrencyVisualTransformation.kt`

Além da CurrencyMask já na kmplib:
- `Double.formatAsCurrency()` → "R$ 1.234,56" (com separador de milhar e sinal negativo)
- `String.currencyToDouble()` → converte "R$ 1.234,56" de volta para Double
- `CurrencyOffsetMapping` para cursor correto no TextField
- 40+ testes unitários cobrindo edge cases

**Por que centralizar:** A kmplib já tem CurrencyMask mas não tem as extension functions de conversão bidirecional. Essas são essenciais para qualquer app com valores monetários.

#### 6.1.6 Valor por Extenso (numberToWords)
**Arquivo:** `core/pdf/ReceiptPdfGenerator.android.kt` (dentro do gerador de recibo)

Converte valores numéricos para texto em português:
- Ex: 1.234,56 → "um mil duzentos e trinta e quatro reais e cinquenta e seis centavos"
- Regras completas do português brasileiro

**Por que centralizar:** Qualquer app que gere recibos ou boletos precisa.

---

### 6.2 MEU ADVOGADO — Funcionalidades únicas genéricas

#### 6.2.1 Multi-Step Form Wizard (ALTA prioridade futura)
**Arquivos:** `features/form/FormScreen.kt` + `FormViewModel.kt`

Framework completo de formulário multi-step:
- **FormState** com `currentStep`, `totalSteps`, `progress` (Float computed)
- Step-specific validation: `isStep1Valid`, `isStep2Valid`, etc → `isCurrentStepValid` routing
- `nextStep()` / `previousStep()` / `goToStep(step)` com animação
- `AnimatedContent` com `slideInHorizontally + fadeIn` baseado na direção
- Barra de progresso com percentual
- Botão "Voltar" que navega entre steps OU sai da tela no step 1

**Tipos de steps implementados:**
1. Seleção única (radio cards)
2. Seleção dupla (2 grupos)
3. Campo de texto com validação de tamanho mínimo
4. Dropdowns cascata (Estado → Cidade)
5. Upload de documentos com lista
6. Tela de revisão com "editar step X"

**Por que centralizar:** Formulários multi-step são extremamente comuns: onboarding, cadastro de produtos, solicitações, etc.

**O que extrair para kmplib:**
```kotlin
// Framework genérico
abstract class StepFormViewModel<STATE>(initialState: STATE) {
    val currentStep: StateFlow<Int>
    val totalSteps: Int
    val progress: Float
    fun nextStep()
    fun previousStep()
    abstract fun isStepValid(step: Int): Boolean
}

@Composable
fun StepFormScreen(
    currentStep: Int,
    totalSteps: Int,
    progress: Float,
    onBack: () -> Unit,
    content: @Composable (step: Int) -> Unit
)
```

#### 6.2.2 Document Upload Flow com staging
**Arquivos:** `features/form/FormViewModel.kt` + `data/remote/StorageService.kt`

Pattern de upload com staging em memória:

```kotlin
data class PendingDocument(
    val id: String,        // UUID gerado localmente
    val name: String,      // Nome original do arquivo
    val data: ByteArray,   // Bytes em memória
    val mimeType: String
)
```

Fluxo:
1. FilePicker → PendingDocument adicionado à lista do state
2. No submit: itera PendingDocuments, faz upload sequencial com progresso
3. "Enviando documento X de Y..." no loading
4. Retorna `Document(storagePath, downloadUrl)` para cada um
5. Se qualquer upload falhar, aborta tudo

**Por que centralizar:** Upload de documentos/imagens com staging é necessário em formulários complexos.

#### 6.2.3 Onboarding Flow genérico
**Arquivos:** `features/onboarding/OnboardingScreen.kt` + `OnboardingViewModel.kt`

```kotlin
data class OnboardingPage(val icon: DrawableResource, val title: String, val description: String)
```
- ViewModel: `currentPage: StateFlow<Int>`, `nextPage()`, `previousPage()`, `isLastPage()`
- Animações: slideIn por direção + fade
- Page indicators: dots expansíveis (24dp current vs 8dp inactive)
- Botão "Pular" que marca `hasSeenOnboarding = true`
- Persistência via `AppPreferences.setHasSeenOnboarding()`

**Por que centralizar:** Quase todo app tem onboarding na primeira execução.

#### 6.2.4 Legal Pages Template (Privacy/Terms)
**Arquivos:** `features/legal/PrivacyScreen.kt` + `TermsScreen.kt`

Pattern reutilizável (não é WebView — é Compose puro):
```
Header com gradiente + botão voltar
  ↓
ScrollableColumn
  ├─ Ícone (Shield/Gavel)
  ├─ Seções com SectionTitle + body + BulletPoints
  └─ Contact Card (email)
```

**Por que centralizar:** Todo app precisa de termos e política. Template genérico economiza tempo.

#### 6.2.5 AppButton com 3 variantes
**Arquivo:** `core/ui/components/AppButton.kt`

3 botões pré-estilizados:
- **AppButton** — Primary filled, 56dp, loading state com spinner
- **AppOutlinedButton** — Bordered, cores customizáveis
- **AppSecondaryButton** — Sutil, 48dp menor, borda leve

**Por que centralizar:** A kmplib tem AppButton mas só 1 variante. Ter 3 é o padrão dos apps.

#### 6.2.6 RequestUpdate Timeline pattern
**Arquivo:** `domain/model/Request.kt`

Pattern de histórico de mudanças:
```kotlin
data class RequestUpdate(val id: String, val message: String, val timestamp: Long)
// Request.updates: List<RequestUpdate> — acumula cronologicamente
```
UI de timeline com dots coloridos e timestamps formatados.

**Por que centralizar:** Qualquer workflow com mudanças de status precisa de timeline (pedidos, tickets, entregas).

---

### 6.3 MEU FISIO — Funcionalidades únicas genéricas

#### 6.3.1 Accessibility Theme System (ALTA prioridade futura)
**Arquivos:** `core/ui/theme/AppColors.kt`, `AppTheme.kt`, `AppTypography.kt`

Sistema de 3 temas com suporte a acessibilidade:
- **Accessible:** Fonte 18sp base, cores claras, contraste alto (ideal para idosos/deficientes visuais)
- **Modern:** Fonte 16sp base, dark mode, cores padrão
- **Role-based:** Tema Physio (emerald) automático para fisioterapeutas

**Implementação:**
- `CompositionLocal` providers: `LocalAppThemeType`, `LocalIsPhysioTheme`
- Helpers: `isAccessibleTheme()`, `isPhysioTheme()`
- Paleta Tailwind-inspired: `Palette.Slate`, `Palette.Blue`, `Palette.Emerald`, etc (escalas 50-900)
- Status badge com transparência: `color.copy(alpha = 0.1f)` para background
- Persistência: `ThemePreferences` com DataStore

**Por que centralizar:** Acessibilidade é requisito legal em muitos contextos. Apps de saúde, financeiros e governamentais precisam.

#### 6.3.2 VibrationManager (expect/actual)
**Arquivos:** `core/platform/VibrationManager.kt` + `.android.kt` + `.ios.kt`

```kotlin
expect class VibrationManager {
    fun vibrate(durationMs: Long = 50)
    fun vibratePattern(pattern: LongArray)
}
```
- Android: `VibratorManager` (API 31+) / `Vibrator` com `VibrationEffect`
- iOS: `UIImpactFeedbackGenerator` com `.medium` style

**Por que centralizar:** Feedback háptico é usado em timers, jogos, confirmações de ação.

#### 6.3.3 VideoPicker Composable (expect/actual)
**Arquivos:** `core/platform/VideoPicker.kt` + `.android.kt` + `.ios.kt`

```kotlin
@Composable
expect fun rememberVideoPickerLauncher(onResult: (ByteArray?) -> Unit): VideoPickerLauncher
```
- Android: `ActivityResultContracts.PickVisualMedia()` com `VideoOnly`, max 50MB
- iOS: `PHPickerViewController` com `"public.movie"` type

**Por que centralizar:** Apps com conteúdo de vídeo (exercícios, tutoriais, reviews) precisam.

#### 6.3.4 HttpDownloader (expect/actual)
**Arquivos:** `core/platform/HttpDownloader.kt` + `.android.kt` + `.ios.kt`

```kotlin
expect suspend fun downloadBytes(url: String): ByteArray?
```
- Android: `URL.openStream()` em `Dispatchers.IO`
- iOS: `NSURLSession.dataTaskWithURL()` em `suspendCancellableCoroutine`

**Por que centralizar:** Download simples de bytes sem Ktor. Útil para thumbnails, assets, exports.

#### 6.3.5 DataStore Factory (expect/actual)
**Arquivos:** `data/datastore/DataStoreFactory.kt` + `.android.kt` + `.ios.kt`

```kotlin
fun createDataStore(producePath: () -> String): DataStore<Preferences>
```
- Android: `context.filesDir.resolve(fileName).absolutePath`
- iOS: `NSFileManager.URLForDirectory(NSDocumentDirectory)`

**Por que centralizar:** Todo app que usa DataStore precisa deste factory. Código idêntico repetido.

#### 6.3.6 Room Database KMP Setup Pattern
**Arquivos:** `data/local/database/` + `di/DatabaseModule.kt`

Pattern completo:
```kotlin
// Common
@Database(entities = [...], version = N)
@ConstructedBy(DatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase()

// DI (common)
val databaseModule = module {
    single { getDatabaseBuilder().setDriver(BundledSQLiteDriver()).build() }
}

// Android
actual fun getDatabaseBuilder() = Room.databaseBuilder(context, name = dbFile.absolutePath)

// iOS
actual fun getDatabaseBuilder() = Room.databaseBuilder(name = NSHomeDirectory() + "/Documents/app.db")
```

**Por que centralizar:** Não o database em si, mas o **boilerplate de setup** (factory, driver, DI) que é idêntico.

#### 6.3.7 Timer/Countdown System
**Arquivos:** `features/exercise/workout/WorkoutViewModel.kt` + `rest/RestViewModel.kt`

Sistema genérico de timer com:
- Countdown com `delay(1000)` em `viewModelScope.launch`
- Pause/Resume via flag `isPaused`
- Progress tracking: `1f - (remaining / total)`
- Vibração no completion de cada repetição
- Cleanup: `timerJob?.cancel()` no `onCleared()`
- Dual mode: MANUAL (tap-to-count) vs AUTOMATIC (timer per rep)

**Por que centralizar:** Timer é útil em apps fitness, produtividade (Pomodoro), culinária, jogos.

**O que extrair para kmplib:**
```kotlin
class CountdownTimer(
    private val scope: CoroutineScope,
    private val onTick: (remaining: Long, progress: Float) -> Unit,
    private val onComplete: () -> Unit
) {
    fun start(durationMs: Long)
    fun pause()
    fun resume()
    fun cancel()
}
```

#### 6.3.8 rememberInject — Koin + Compose utility
**Arquivo:** `core/di/RememberInject.kt`

```kotlin
@Composable
inline fun <reified T : Any> rememberInject(): T {
    val koin = getKoin()
    return remember { koin.get<T>() }
}
```

**Por que centralizar:** Utility simples mas usado em todos os composables. Evita boilerplate.

---

### 6.4 PROSPECTA — Funcionalidades únicas genéricas

#### 6.4.1 NetworkChecker (ALTA prioridade futura)
**Arquivos:** `core/network/NetworkChecker.kt` + `AndroidNetworkChecker.kt` + `IosNetworkChecker.kt`

```kotlin
interface NetworkChecker { fun isAvailable(): Boolean }
```
- Android: `ConnectivityManager.getNetworkCapabilities()` + `NET_CAPABILITY_INTERNET`
- iOS: `SCNetworkReachabilityCreateWithName("firestore.googleapis.com")` + flags bitwise

**Por que centralizar:** Verificação de conectividade é necessária em qualquer app que dependa de rede.

#### 6.4.2 LocationService GPS (expect/actual)
**Arquivos:** `core/location/LocationService.kt` + `AndroidLocationService.kt` + `IosLocationService.kt`

```kotlin
interface LocationService { suspend fun getCurrentLocation(): MapLatLng? }
```
- Android: `FusedLocationProviderClient` com 2-tier (cache → fresh GPS)
- iOS: `CLLocationManager` com request de permissão
- Verifica `ACCESS_FINE_LOCATION` / `kCLAuthorizationStatusAuthorizedWhenInUse`

**Por que centralizar:** Qualquer app com mapas, entregas, check-in precisa de localização.

#### 6.4.3 ReverseGeocodingService
**Arquivos:** `core/location/ReverseGeocodingService.kt` + implementations

```kotlin
interface ReverseGeocodingService { suspend fun getAddress(lat: Double, lng: Double): String? }
```
- Android: `Geocoder` com fallback para `getAddressLine(0)`
- iOS: stub (precisa `CLGeocoder`)

**Por que centralizar:** Apps com mapas frequentemente precisam mostrar endereço a partir de coordenadas.

#### 6.4.4 PlatformMapView — Mapa com markers coloridos
**Arquivos:** `core/ui/components/PlatformMapView.kt` + `.android.kt` + `.ios.kt`

```kotlin
@Composable
expect fun PlatformMapView(
    locations: List<Location>,
    selectedLocation: Location?,
    onLocationClick: (Location) -> Unit,
    userLocation: MapLatLng?,
    centerOnUserTrigger: Int
)
```
- Android: `GoogleMap` + `Marker` com cores por status (RED/YELLOW/AZURE)
- iOS: `MKMapView` via `UIKitView` com `MKPointAnnotation` e delegate para cores
- Camera bounds automático com `LatLngBounds` / `showAnnotations`
- Default center: São Paulo (-23.5505, -46.6333)

**Por que centralizar:** Visualização de mapa com pins é comum em apps de delivery, locadoras, vendas.

#### 6.4.5 PlatformLocationPickerMap — Seletor interativo de localização
**Arquivos:** `core/ui/components/PlatformLocationPickerMap.kt` + implementations

```kotlin
@Composable
expect fun PlatformLocationPickerMap(
    initialPosition: MapLatLng?,
    onCameraIdle: (lat: Double, lng: Double) -> Unit,
    centerOnUserTrigger: Int,
    userLocation: MapLatLng?
)
```
- Detecta movimento da câmera e retorna coordenadas do centro quando para
- Android: `snapshotFlow { cameraPositionState.isMoving }` com debounce
- iOS: `MKMapViewDelegateProtocol.regionDidChangeAnimated`
- Sem markers — puro picker com crosshair no centro

**Por que centralizar:** Seletor de "onde é?" é comum em apps de serviço, delivery, cadastro de endereço.

#### 6.4.6 LocationPermissionEffect — Composable de permissão
**Arquivos:** `core/ui/components/LocationPermissionEffect.kt` + implementations

```kotlin
@Composable
expect fun LocationPermissionEffect(onResult: (granted: Boolean) -> Unit)
```
- Android: `rememberLauncherForActivityResult` + `RequestMultiplePermissions()`
- iOS: `CLLocationManager.authorizationStatus()` + `requestWhenInUseAuthorization()`

**Por que centralizar:** Pattern reutilizável para qualquer permissão de sistema.

#### 6.4.7 OpenInMaps — Deep link para app de mapas nativo
**Arquivos:** `core/ui/components/OpenInMaps.kt` + implementations

```kotlin
@Composable
expect fun rememberOpenInMaps(): (latitude: Double, longitude: Double, label: String) -> Unit
```
- Android: `geo:lat,lng?q=lat,lng(label)` via `Intent.ACTION_VIEW`
- iOS: `http://maps.apple.com/?ll=lat,lng&q=label`

**Por que centralizar:** "Como chegar?" é botão padrão em qualquer app com endereço.

#### 6.4.8 NoInternetDialog
**Arquivo:** `core/ui/components/NoInternetDialog.kt`

Dialog com ícone `WifiOff`, mensagem e botão "Entendi". Material3 aware.

**Por que centralizar:** Todo app offline-aware precisa.

#### 6.4.9 Dynamic Theme from Hex Color
**Arquivo:** `core/ui/theme/ProspectaTheme.kt`

```kotlin
fun hexToColor(hex: String): Color = Color(("FF$hex").toLong(16))

@Composable
fun DynamicTheme(primaryColorHex: String, content: @Composable () -> Unit) {
    val primary = hexToColor(primaryColorHex)
    val colorScheme = lightColorScheme(primary = primary, ...)
    MaterialTheme(colorScheme = colorScheme, ...) { content() }
}
```

**Por que centralizar:** Apps white-label ou com personalização de empresa precisam de tema dinâmico.

#### 6.4.10 LocationUtils — Haversine + formatação de distância
**Arquivo:** `core/utils/LocationUtils.kt`

- `calculateDistance(lat1, lon1, lat2, lon2): Double` — Haversine (raio Terra 6371km)
- `formatDistance(km: Double): String` — "145m" ou "2.3km"
- `formatDateBr(isoDate): String` — "2024-03-23" → "23/03/2024"
- `parseDateBrToIso(brDate): String?` — inverso

**Por que centralizar:** Cálculos de distância e formatação de data BR são necessários em vários contextos.

---

### 6.5 SUPER 8 — Funcionalidades únicas genéricas

#### 6.5.1 BitmapEncoder — ImageBitmap → PNG bytes (ALTA prioridade futura)
**Arquivos:** `features/tournaments/share/BitmapEncoder.kt` + `.android.kt` + `.ios.kt`

```kotlin
expect fun encodeBitmapToPng(bitmap: ImageBitmap): ByteArray
```
- Android: `asAndroidBitmap()` → `Bitmap.compress(PNG, 100, ByteArrayOutputStream)`
- iOS: `asSkiaBitmap()` → Skia `Image.encodeToData(PNG)`

**Por que centralizar:** Qualquer app que precise exportar UI como imagem (rankings, cards, receipts) precisa.

#### 6.5.2 ShareHandler com FileProvider (ALTA prioridade futura)
**Arquivos:** `core/util/ShareHandler.kt` + `.android.kt` + `.ios.kt`

```kotlin
expect class ShareHandler {
    fun shareText(text: String, title: String = "")
    fun shareImage(imageBytes: ByteArray, fileName: String, title: String = "")
}
```
- Android: `shareImage` salva em `cacheDir/shared_images/`, usa `FileProvider.getUriForFile()`, `FLAG_GRANT_READ_URI_PERMISSION`
- iOS: `UIActivityViewController` com `UIImage(data: NSData)`

**Por que centralizar:** A kmplib já tem ShareHandler mas aparentemente sem `shareImage`. O do Super 8 é mais completo com FileProvider.

#### 6.5.3 UrlOpener com openSubscriptionManagement()
**Arquivos:** `core/util/UrlOpener.kt` + `.android.kt` + `.ios.kt`

Além de `openUrl` e `openEmail`:
```kotlin
fun openSubscriptionManagement()
```
- Android: `https://play.google.com/store/account/subscriptions?package=${packageName}`
- iOS: `https://apps.apple.com/account/subscriptions`

**Por que centralizar:** Todo app premium precisa de link para gerenciar assinatura nas stores.

#### 6.5.4 CrashlyticsService — Interface abstrata
**Arquivos:** `data/firebase/CrashlyticsService.kt` + implementations

```kotlin
interface CrashlyticsService {
    fun logMessage(message: String)
    fun setCustomKey(key: String, value: String)
    fun setUserId(userId: String)
    fun recordException(exception: Throwable)
    fun setCrashlyticsCollectionEnabled(enabled: Boolean)
}
```
- Android: `FirebaseCrashlytics.getInstance()` com debug logging adicional
- iOS: `Firebase.crashlytics` (GitLive)

**Por que centralizar:** Todos os 5 apps usam Crashlytics. Interface abstrata permite trocar por Sentry/Bugsnag no futuro.

#### 6.5.5 BuildInfo — isDebug detection
**Arquivos:** `core/util/BuildInfo.kt` + `.android.kt` + `.ios.kt`

```kotlin
expect object BuildInfo { val isDebug: Boolean }
// Android: BuildConfig.DEBUG
// iOS: Platform.isDebugBinary
```

**Por que centralizar:** Necessário para logging condicional, analytics, feature flags.

#### 6.5.6 Shareable Compose UI → Image pattern
**Arquivo:** `features/tournaments/details/ShareableRankingImage.kt`

Fluxo completo para capturar UI Compose como imagem compartilhável:
1. `rememberGraphicsLayer()` → track rendering
2. Wrap content em `drawWithContent { graphicsLayer.record { } }`
3. `graphicsLayer.toImageBitmap()` → captura
4. `encodeBitmapToPng(bitmap)` → bytes
5. `shareHandler.shareImage(bytes)` → share sheet

**Por que centralizar:** Pattern útil para cards sociais, rankings, receipts, tickets — qualquer "compartilhar como imagem".

#### 6.5.7 LocalizedEnums pattern — Enum i18n com Compose
**Arquivo:** `core/ui/LocalizedEnums.kt`

```kotlin
@Composable
fun Gender.localizedName(): String = when (this) {
    Gender.MALE -> stringResource(Res.string.gender_male)
    Gender.FEMALE -> stringResource(Res.string.gender_female)
}
```

**Por que centralizar:** Pattern elegante para traduzir enums. Mais limpo que when() em cada tela.

#### 6.5.8 PremiumViewModel completo com RevenueCat
**Arquivo:** `features/premium/PremiumViewModel.kt`

ViewModel production-ready para fluxo de compra:
- `PremiumState`: selectedPlan, isLoading, isPremium, products, subscriptionInfo, error
- `getStorePrice(plan)`: retorna preço da store
- `observeSubscriptionState()`: Flow reativo do RevenueCat
- `loadProducts()`: sincroniza e carrega produtos
- `onPurchase(plan)`: compra com handling de PurchaseResult (Success/Cancelled/Error)
- `onRestorePurchases()`: restore com RestoreResult
- Error codes mapeados: NETWORK_ERROR, STORE_ERROR, PAYMENT_DECLINED, ALREADY_OWNED, etc.
- `onManageSubscription()`, `onPrivacyPolicy()`, `onTermsOfUse()`, `onContactSupport()`

**Por que centralizar:** 3 apps (Meu Fisio, Prospecta, Super 8) têm PremiumViewModel quase idêntico. Centralizar elimina duplicação imediata e já prepara futuros apps.

#### 6.5.9 i18n com Compose Resources (PT/EN/ES)
**Diretórios:** `composeResources/values/`, `values-en/`, `values-es/`

300+ strings organizadas por feature, com parametrização (`%1$d/%2$d`).

**Por que centralizar:** O pattern (não as strings) é replicável. Templates de `strings.xml` por feature facilitam setup de novos apps.

---

## 7. MATRIZ CONSOLIDADA — Funcionalidades únicas por prioridade

### PRIORIDADE ALTA (genérico + múltiplos apps futuros vão precisar)

| # | Funcionalidade | App de origem | Por que é alta prioridade |
|---|---------------|---------------|--------------------------|
| 1 | **PDF Preview + Share system** | Locadora | Recibos, contratos, relatórios — qualquer app B2B |
| 2 | **Multi-Step Form Wizard** | Meu Advogado | Cadastros complexos, onboarding, solicitações |
| 3 | **PremiumScreen + ViewModel** | Super 8 | 3 apps já precisam, todo app freemium vai precisar |
| 4 | **BitmapEncoder + Shareable UI** | Super 8 | Compartilhar cards/rankings/recibos como imagem |
| 5 | **ShareHandler com shareImage** | Super 8 | kmplib atual não tem shareImage com FileProvider |
| 6 | **NetworkChecker** | Prospecta | Todo app online precisa detectar conectividade |
| 7 | **CrashlyticsService interface** | Super 8 | 5/5 apps usam Crashlytics, interface facilita |
| 8 | **Onboarding Flow** | Meu Advogado | Quase todo app tem onboarding |
| 9 | **Accessibility Theme** | Meu Fisio | Requisito legal crescente, apps de saúde obrigatório |
| 10 | **SuccessToast animado** | Locadora | Toast polido > Toast básico da kmplib |

### PRIORIDADE MÉDIA (útil mas menos apps vão precisar)

| # | Funcionalidade | App de origem | Contexto |
|---|---------------|---------------|----------|
| 11 | **PlatformMapView** | Prospecta | Apps com mapa de pontos (delivery, vendas, serviços) |
| 12 | **LocationPickerMap** | Prospecta | Apps que pedem "selecione no mapa" |
| 13 | **LocationService GPS** | Prospecta | Apps com geolocalização |
| 14 | **LocationPermissionEffect** | Prospecta | Composable de permissão genérico |
| 15 | **OpenInMaps deep link** | Prospecta | Apps com endereço (lojas, entregas) |
| 16 | **Timer/Countdown** | Meu Fisio | Apps fitness, produtividade, jogos |
| 17 | **Document Upload staging** | Meu Advogado | Formulários com anexos |
| 18 | **DataStore Factory** | Meu Fisio | Boilerplate repetido em 3+ apps |
| 19 | **VibrationManager** | Meu Fisio | Feedback háptico (jogos, timers, confirmações) |
| 20 | **Legal Pages Template** | Meu Advogado | Todo app precisa de termos/privacidade |

### PRIORIDADE BAIXA (nicho específico)

| # | Funcionalidade | App de origem | Contexto |
|---|---------------|---------------|----------|
| 21 | **VideoPicker** | Meu Fisio | Só apps com conteúdo de vídeo |
| 22 | **HttpDownloader** | Meu Fisio | Alternativa simples ao Ktor para downloads pontuais |
| 23 | **Dynamic Theme from hex** | Prospecta | Apps white-label ou multi-empresa |
| 24 | **ReverseGeocoding** | Prospecta | Só apps com mapa interativo |
| 25 | **NotificationBadge** | Locadora | Só apps com sistema de notificações |
| 26 | **AppVersion** | Locadora | Trivial mas útil |
| 27 | **BuildInfo isDebug** | Super 8 | Trivial mas útil |
| 28 | **Valor por extenso** | Locadora | Só apps financeiros/recibos |
| 29 | **LocalizedEnums pattern** | Super 8 | Pattern de código, não componente |
| 30 | **rememberInject** | Meu Fisio | 1 linha, mas evita boilerplate |
| 31 | **Room DB setup pattern** | Meu Fisio/Super 8 | Boilerplate, não componente |
| 32 | **RequestUpdate Timeline** | Meu Advogado | Apps com workflow de status |

---

## 8. ESTRUTURA FINAL SUGERIDA — kmplib v2.0 COMPLETA

Incluindo tanto as funcionalidades duplicadas (seção 4) quanto as únicas genéricas (seção 6):

```
kmplib/library/src/commonMain/kotlin/br/com/codecacto/kmplib/
│
├── auth/                         (EXPANDIR)
│   ├── AuthRepository.kt             ← já existe
│   ├── AuthStateManager.kt           ← NOVO (de MeuFisio/Prospecta)
│   ├── GoogleAuthProvider.kt          ← NOVO expect (de 3 apps)
│   ├── AppleAuthProvider.kt           ← NOVO expect (de 3 apps)
│   ├── User.kt                        ← já existe
│   └── usecase/                       ← NOVO
│       ├── SignInUseCase.kt
│       ├── SignUpUseCase.kt
│       ├── SignInWithGoogleUseCase.kt
│       ├── SignInWithAppleUseCase.kt
│       └── ResetPasswordUseCase.kt
│
├── brdata/                       (MANTER)
│   ├── BrazilianStates.kt
│   ├── BrazilianCities.kt
│   └── StringExtensions.kt
│
├── core/                         (EXPANDIR MUITO)
│   ├── error/                         ← NOVO
│   │   ├── ErrorHandler.kt
│   │   ├── DefaultErrorHandler.kt
│   │   ├── GlobalErrorManager.kt
│   │   └── ErrorMessages.kt
│   ├── util/
│   │   ├── AppLogger.kt              ← já existe
│   │   ├── TimeUtils.kt              ← já existe
│   │   ├── DateUtils.kt              ← NOVO (formatBR, adjustDatePicker)
│   │   ├── BuildInfo.kt              ← NOVO expect (de Super8)
│   │   ├── CountdownTimer.kt         ← NOVO (de MeuFisio)
│   │   └── NumberToWords.kt          ← NOVO (de Locadora)
│   └── extensions/                    ← NOVO
│       ├── ByteArrayExtensions.kt     (toImageBitmap, toStorageData)
│       └── CurrencyExtensions.kt      (formatAsCurrency, currencyToDouble)
│
├── feedback/                     (MANTER)
│
├── firebase/                     (EXPANDIR)
│   ├── ads/                           ← já existe
│   ├── auth/                          ← já existe
│   ├── crashlytics/                   ← NOVO (de Super8)
│   │   └── CrashlyticsService.kt
│   ├── firestore/                     ← já existe
│   └── storage/                       ← já existe
│
├── mask/                         (EXPANDIR)
│   ├── CepMask.kt                    ← já existe
│   ├── CnpjMask.kt                   ← já existe
│   ├── CpfMask.kt                    ← já existe
│   ├── CurrencyMask.kt               ← já existe
│   ├── DateMask.kt                   ← NOVO (de MeuFisio/Prospecta)
│   └── PhoneMask.kt                  ← já existe
│
├── monetization/                 (EXPANDIR)
│   ├── MonetizationConfig.kt         ← já existe
│   ├── MonetizationManager.kt        ← já existe
│   └── purchase/                      ← já existe
│
├── pdf/                          (NOVO — de Locadora)
│   ├── PdfGenerator.kt               ← expect (helpers de desenho)
│   ├── PdfViewer.kt                   ← expect (renderização com zoom)
│   ├── PdfPreviewScreen.kt           ← common (tela com share)
│   └── PdfShareUtils.kt              ← compartilhamento
│
├── platform/                     (EXPANDIR MUITO)
│   ├── BiometricAuth.kt              ← já existe
│   ├── BitmapEncoder.kt              ← NOVO expect (de Super8)
│   ├── FilePicker.kt                  ← já existe
│   ├── ImagePicker.kt                ← NOVO expect Composable (de Locadora/MeuFisio)
│   ├── VideoPicker.kt                ← NOVO expect Composable (de MeuFisio)
│   ├── LocationService.kt            ← NOVO (de Prospecta)
│   ├── LocationPermission.kt         ← NOVO expect Composable (de Prospecta)
│   ├── NetworkChecker.kt             ← NOVO (de Prospecta)
│   ├── NotificationScheduler.kt      ← já existe
│   ├── OpenInMaps.kt                 ← NOVO expect (de Prospecta)
│   ├── ShareHandler.kt               ← já existe (MELHORAR: add shareImage)
│   ├── UrlLauncher.kt                ← já existe (MELHORAR: add openSubscriptionManagement)
│   └── VibrationManager.kt           ← NOVO expect (de MeuFisio)
│
├── preferences/                  (NOVO)
│   ├── ReviewPreferences.kt          ← NOVO expect (de 4 apps!)
│   ├── AppPreferences.kt             ← NOVO (onboarding flag)
│   └── DataStoreFactory.kt           ← NOVO expect (de MeuFisio)
│
├── ui/                           (EXPANDIR)
│   ├── components/
│   │   ├── AppButton.kt              ← já existe (MELHORAR: 3 variantes)
│   │   ├── AppDialog.kt              ← já existe
│   │   ├── AppTextArea.kt            ← NOVO (de MeuAdvogado/MeuFisio)
│   │   ├── AppTextField.kt           ← já existe
│   │   ├── AppTopBar.kt              ← já existe
│   │   ├── Badge.kt                  ← já existe
│   │   ├── BottomNavBar.kt           ← já existe
│   │   ├── ConfirmationDialog.kt     ← já existe
│   │   ├── EmptyState.kt             ← já existe
│   │   ├── ErrorModal.kt             ← NOVO (de MeuFisio)
│   │   ├── FormContainer.kt          ← já existe
│   │   ├── NoInternetDialog.kt       ← NOVO (de Prospecta)
│   │   ├── NotificationBadge.kt      ← NOVO (de Locadora)
│   │   ├── NumberField.kt            ← já existe
│   │   ├── StatusBadge.kt            ← NOVO (de MeuFisio/Prospecta)
│   │   ├── SuccessToast.kt           ← NOVO (de Locadora)
│   │   ├── Toast.kt                  ← já existe
│   │   ├── AppReviewDialog.kt        ← já existe
│   │   └── auth/                      ← já existe
│   ├── maps/                          ← NOVO (de Prospecta)
│   │   ├── PlatformMapView.kt        ← expect (mapa com markers)
│   │   └── PlatformLocationPicker.kt ← expect (seletor de local)
│   ├── mvi/
│   │   ├── BaseViewModel.kt          ← já existe (MELHORAR com ErrorHandler)
│   │   ├── UiState.kt                ← NOVO
│   │   ├── UiAction.kt               ← NOVO
│   │   └── UiEffect.kt               ← NOVO
│   ├── screens/
│   │   ├── feedback/                  ← já existe
│   │   ├── legal/                     ← NOVO (de MeuAdvogado)
│   │   │   └── LegalPageTemplate.kt  (privacy/terms template)
│   │   ├── login/                     ← já existe
│   │   ├── onboarding/               ← NOVO (de MeuAdvogado)
│   │   │   ├── OnboardingScreen.kt
│   │   │   └── OnboardingViewModel.kt
│   │   ├── premium/                   ← NOVO (de Super8/MeuFisio/Prospecta)
│   │   │   ├── PremiumScreen.kt
│   │   │   └── PremiumViewModel.kt
│   │   └── register/                  ← já existe
│   ├── stepform/                      ← NOVO (de MeuAdvogado)
│   │   ├── StepFormScreen.kt         (wizard container com progresso)
│   │   └── StepFormViewModel.kt      (base VM com step navigation)
│   └── theme/                         ← já existe (MELHORAR)
│       ├── AppColorScheme.kt
│       ├── AppTheme.kt               (add accessibility support)
│       ├── AppTypography.kt
│       └── DynamicTheme.kt           ← NOVO (hexToColor theme — de Prospecta)
│
└── validation/                   (EXPANDIR)
    ├── CnpjValidator.kt              ← já existe
    ├── CpfValidator.kt               ← já existe
    ├── EmailValidator.kt             ← já existe
    ├── NameValidator.kt              ← NOVO
    ├── PasswordValidator.kt          ← já existe
    └── PhoneValidator.kt             ← já existe
```

### Contagem final v2.0:

| Categoria | Já existe | Novo | Melhorar | Total |
|-----------|-----------|------|----------|-------|
| auth/ | 2 | 8 | 0 | 10 |
| brdata/ | 3 | 0 | 0 | 3 |
| core/ | 2 | 8 | 0 | 10 |
| feedback/ | 3 | 0 | 0 | 3 |
| firebase/ | 8 | 1 | 0 | 9 |
| mask/ | 5 | 1 | 0 | 6 |
| monetization/ | 7 | 0 | 0 | 7 |
| pdf/ | 0 | 4 | 0 | 4 |
| platform/ | 5 | 8 | 2 | 15 |
| preferences/ | 0 | 3 | 0 | 3 |
| ui/components/ | 12 | 6 | 1 | 19 |
| ui/maps/ | 0 | 2 | 0 | 2 |
| ui/mvi/ | 1 | 3 | 1 | 5 |
| ui/screens/ | 5 | 6 | 0 | 11 |
| ui/stepform/ | 0 | 2 | 0 | 2 |
| ui/theme/ | 3 | 1 | 1 | 5 |
| validation/ | 5 | 1 | 0 | 6 |
| **TOTAL** | **61** | **54** | **5** | **120** |

### Impacto estimado:
- **54 novos arquivos** na kmplib
- **Elimina ~80-100 arquivos duplicados** nos 5 apps existentes
- **Economia de ~2-4 semanas** no desenvolvimento de cada novo app futuro
- **Padronização** de UI, auth, monetização e error handling em todos os apps
