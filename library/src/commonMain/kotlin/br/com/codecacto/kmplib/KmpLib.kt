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
 * - `FirestoreService` - Operações CRUD no Firestore
 * - `StorageService` - Upload/download de arquivos
 *
 * ### Custom Ads (anúncios próprios via Firestore)
 * - `CustomAdManager` - Inicialização e observação dos anúncios
 * - `CustomBannerAd` - Banner retangular com URL de destino
 * - `CustomInterstitialAd` - Anúncio full-screen com botão fechar
 *
 * Funcionalidade independente do AdMob (pacote `firebase/ads`). Os anúncios
 * são documentos da coleção `custom_ads` (configurável) no Firestore — cada
 * doc traz uma imagem e a URL que abre ao clicar.
 *
 * ### Ad Router (alterna AdMob ↔ Custom remotamente)
 * - `AdRouter` - Singleton que observa `app_ad_configs/{appId}` no Firestore
 * - `ManagedBannerAd` - Banner dispatcher (despacha pra AdMob, Custom ou nada)
 * - `ManagedInterstitialAd` - Interstitial dispatcher
 *
 * O app chama `Managed*` sem saber qual provider está ativo; o admin no portal
 * (`/anuncios` tab Configuração) alterna sem precisar de release.
 *
 * ### Ad Stats (impressões / cliques)
 * - `AdStats.initialize(appId)` - Liga a coleta no boot
 *
 * Depois disso, BannerAd / InterstitialAd / AppOpenAd (AdMob) e
 * CustomBannerAd / CustomInterstitialAd registram automaticamente em
 * `ad_stats/{provider}__{appId}__{format}__{adId|all}__{YYYY-MM-DD}` via
 * `FieldValue.increment(1)`. O admin lê em `/anuncios` tab Estatísticas.
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
