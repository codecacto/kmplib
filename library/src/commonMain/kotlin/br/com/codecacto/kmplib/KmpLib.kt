package br.com.codecacto.kmplib

/**
 * KmpLib - Biblioteca Kotlin Multiplatform da CodeCacto
 *
 * Uma biblioteca completa de utilitários para desenvolvimento Android e iOS.
 *
 * ## Módulos Disponíveis
 *
 * ### Core
 * - `AppLogger` - Logging multiplataforma
 * - `TimeUtils` - Utilitários de data/hora
 *
 * ### Validação
 * - `CpfValidator` - Validação de CPF brasileiro
 * - `CnpjValidator` - Validação de CNPJ (numérico e alfanumérico)
 * - `EmailValidator` - Validação de email
 * - `PhoneValidator` - Validação de telefone brasileiro
 * - `PasswordValidator` - Validação de senha com regras configuráveis
 *
 * ### Máscaras
 * - `PhoneVisualTransformation` - Máscara de telefone (Compose)
 * - `CpfVisualTransformation` - Máscara de CPF (Compose)
 * - `CnpjVisualTransformation` - Máscara de CNPJ (Compose)
 * - `CurrencyVisualTransformation` - Máscara de moeda BRL (Compose)
 * - `CepVisualTransformation` - Máscara de CEP (Compose)
 *
 * ### Dados Brasileiros
 * - `BrazilianStates` - Lista de estados brasileiros
 * - `StringExtensions` - Extensões de String (removeAccents, etc.)
 *
 * ### Firebase
 * - `AuthRepository` - Autenticação (email, Google, Apple)
 * - `StorageService` - Upload/download de arquivos
 *
 * Dados de domínio NÃO usam Firestore: a camada é REST contra o `apps-api` (`core/data`,
 * `core/network`). Firebase fica restrito a Auth e Crashlytics.
 *
 * ### House Ads (anúncios próprios via apps-api)
 * - `CustomAdManager` - Inicialização e observação dos anúncios (por projeto + superfície)
 * - `CustomBannerAd` - Banner retangular com URL de destino
 * - `CustomInterstitialAd` - Anúncio full-screen com botão fechar
 *
 * Sem AdMob: a publicidade é apenas house ads servidos pelo backend central
 * (`GET /public/ads?project={slug}&surface=app`). Cada anúncio traz uma imagem e a URL que abre ao
 * clicar.
 *
 * ### Ad Router (liga/desliga house ads remotamente)
 * - `AdRouter` - Singleton que lê a config (`GET /public/ad-config/{appId}`) no apps-api
 * - `ManagedBannerAd` - Banner dispatcher (despacha pra Custom ou nada)
 * - `ManagedInterstitialAd` - Interstitial dispatcher
 *
 * O app chama `Managed*` sem saber se o formato está ligado; o admin no portal alterna sem precisar
 * de release.
 *
 * ### Ad Stats (impressões / cliques)
 * - `AdStats.initialize(appId, httpClient)` - Liga a coleta no boot
 *
 * Depois disso, CustomBannerAd / CustomInterstitialAd registram automaticamente via
 * `POST /public/ad-stats` no apps-api. O admin lê no portal.
 *
 * ### Feedback
 * - `FeedbackService` - Envio centralizado de feedbacks via Firestore REST API
 * - `FeedbackScreen` - Tela completa de feedback com formulário
 * - `AppReviewDialog` - Dialog de avaliação com persistência automática
 *
 * ### Plataforma
 * - `UrlLauncher` - Abrir URLs, emails, WhatsApp
 * - `ShareHandler` - Compartilhamento de texto e arquivos
 * - `BiometricAuth` - Autenticação biométrica
 * - `NotificationScheduler` - Notificações locais
 *
 * ### Mapa (Google Maps — `map`)
 * - `MapView` / `MapMarker` - Mapa Compose MP (Android: maps-compose; iOS: placeholder/macOS)
 * - `LatLng`, `CameraPosition`, `MapMarkerStatus`, `MapMarkerStatus.color()`
 * - `rememberCameraPositionState()` / `CameraPositionState.animateTo()`
 *
 * Android requer `com.google.android.geo.API_KEY` no AndroidManifest.
 *
 * ### Localização GPS (`location`)
 * - `LocationProvider` - `getCurrentLocation(): LatLng?` (fix único, timeout 10s) + `hasLocationPermission()`
 * - `createLocationProvider()` / `rememberLocationProvider()` (Composable, pede permissão no Android)
 *
 * Android requer permissão `ACCESS_FINE_LOCATION`; iOS `NSLocationWhenInUseUsageDescription`.
 *
 * ## Inicialização (Android)
 *
 * No seu `Application.onCreate()`:
 *
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         KmpLib.init(this)
 *     }
 * }
 * ```
 *
 * Na sua `MainActivity.onResume()` (para biometria):
 *
 * ```kotlin
 * override fun onResume() {
 *     super.onResume()
 *     KmpLib.setActivity(this)
 * }
 * ```
 *
 * ## Inicialização (iOS)
 *
 * No iOS, a maioria dos componentes funciona sem inicialização.
 * Para biometria, a permissão é solicitada automaticamente.
 */
object KmpLib {
    const val VERSION = "2.4.0"
}
