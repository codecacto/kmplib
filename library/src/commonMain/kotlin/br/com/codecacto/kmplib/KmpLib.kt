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
 * ### Plataforma
 * - `UrlLauncher` - Abrir URLs, emails, WhatsApp
 * - `ShareHandler` - Compartilhamento de texto e arquivos
 * - `BiometricAuth` - Autenticação biométrica
 * - `NotificationScheduler` - Notificações locais
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
    const val VERSION = "1.1.0"
}
