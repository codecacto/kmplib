# CLAUDE.md - KmpLib

Este arquivo fornece orientações para Claude Code (claude.ai/code) ao trabalhar com o código neste repositório.

## Visão Geral

KmpLib é a biblioteca compartilhada Kotlin Multiplatform (KMP) da CodeCacto, usada por todos os projetos mobile (Android + iOS):

- **influencer**
- **locadora**
- **meu-advogado**
- **super8**

## Estrutura do Projeto

```
kmplib/
├── library/
│   └── src/
│       ├── commonMain/       # Código compartilhado Android + iOS
│       ├── androidMain/      # Implementações Android
│       └── iosMain/          # Implementações iOS
├── IOS_INTEGRATION.md        # Guia de integração iOS
└── CLAUDE.md                 # Este arquivo
```

## Comandos de Build

```bash
# Testes (roda em qualquer host — é o gate obrigatório antes de commitar)
./gradlew :kmplib:testDebugUnitTest

# Publicação local (funciona em Linux desde a 2.69.0)
./gradlew publishToMavenLocal

# Build apenas iOS (requer macOS)
./gradlew :kmplib:linkDebugFrameworkIosSimulatorArm64
```

### Guarda de host (2.69.0) — alvos Apple só em macOS

`library/build.gradle.kts` declara `iosX64/iosArm64/iosSimulatorArm64` **apenas quando
`HostManager.hostIsMac`**. iOS não está desativado: é **condicional ao host**.

| Host | O que sai do `publishToMavenLocal` |
|------|-----------------------------------|
| Linux/Windows | `commonMain` + **Android** (AAR). Módulo Gradle coerente. |
| macOS | Tudo, incluindo `kmplib-iosarm64`/`-iosx64`/`-iossimulatorarm64` e o klib de metadata completo. |

Antes disso, os alvos iOS eram sempre declarados e o KGP os desabilitava fora do Mac
(`kotlin.native.ignoreDisabledTargets=true`) — mas o `kmplib-<v>.module` publicado **ainda anunciava**
variantes `iosArm64ApiElements-published` etc. apontando (`available-at`) para artefatos que nunca
eram publicados. Isso quebrava o fallback `mavenLocal` de um clone isolado.

Consequências a respeitar:
- Com **um único alvo**, o KGP não gera klib de metadata → o jar raiz publicado no Linux vem vazio.
  Não afeta o dia a dia (num host Linux o app consumidor também só tem Android habilitado), mas
- **Release para Maven Central exige macOS.** Qualquer tarefa `*MavenCentral*` **falha de propósito**
  em host não-macOS. Para forçar a tentativa de declarar iOS fora do Mac (diagnóstico):
  `./gradlew publishToMavenLocal -Pkmplib.forceAppleTargets=true`.

## Padrões Importantes

### Exportação para iOS

Projetos que usam kmplib **devem** configurar:

```kotlin
// build.gradle.kts do projeto consumidor

// Em binaries.framework:
export(libs.kmplib)

// Em commonMain.dependencies:
api(libs.kmplib)  // NÃO implementation()
```

### Tipos iOS Exportados

| Tipo | Arquivo | Uso |
|------|---------|-----|
| `IosNativeMap` | `map/IosMapBridge.kt` | Protocolo para Google Maps nativo |
| `IosMapMarkerData` | `map/IosMapBridge.kt` | Dados de marcador |
| `IosMapBridge` | `map/IosMapBridge.kt` | Factory do mapa |
| `ImagePickerServiceBridge` | Varia por projeto | Photo picker |

### Anotações ObjC

Para exportar tipos ao Swift com nomes específicos, usar:

```kotlin
@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

import kotlin.native.ObjCName

@ObjCName("IosNativeMap")
interface IosNativeMap { ... }
```

### Callbacks Swift

Kotlin `Unit` vira `KotlinUnit` no Swift. Documentar para consumidores:

```swift
// Swift: usar _ = para ignorar retorno
_ = onSuccess(value)
```

## Geradores de PDF (iOS)

**2.78.0 — os 9 geradores iOS estão IMPLEMENTADOS em código** (ADR-0003). O helper multi-página
`renderIosPdfPaged` + `IosPageFlow` (marca d'água por página) estendeu o `renderIosPdf` de página única
(recibo, 2.77.0) para N páginas, e o `IosPdfCanvas` ganhou `strokeRect`/`strokeRoundRect`/`fillCircle`/
`imageCrop`/`measureWrappedHeight`. Os 7 que eram stub agora renderizam de verdade, espelhando o par
Android (mesmo layout/coordenadas/cores):

- `DocumentPdfGenerator.ios.kt`
- `FinanceReportPdfGenerator.ios.kt`
- `HoursReportPdfGenerator.ios.kt`
- `InspectionPdfGenerator.ios.kt`
- `TableReportPdfGenerator.ios.kt`
- `VaccinationCardPdfGenerator.ios.kt`
- `WorkReportPdfGenerator.ios.kt`

Somados a `OsPdfGenerator.ios`/`ReciboPdf.ios` (2.77.0) e `PdfRasterizer.ios` (`renderPdfPagesToImages`),
os 9 geradores são reais. Técnica: **CoreText** (`CTLine`) dentro de `UIGraphicsPDFRenderer`/`CGContext`
(as categorias de desenho de texto do UIKit `NSString.drawAtPoint` não são exportadas no K/N 2.x).

**PENDÊNCIA — validação em host macOS.** O build Kotlin/Native iOS **não roda em Linux**; o código é
fiel ao Android mas **não foi compilado/validado em macOS**. Por isso
`platform/PlatformCapabilities.pdfGeneration` (e `cameraCapture`) **continuam `false`** — o flip para
`true` é o **passo final em macOS** (compilar os alvos iOS + validação visual dos PDFs; para câmera,
testar num device). Enquanto `false`, o app **não vende/exibe** a feature no iOS:
`"Exportar PDF" requiring PlatformCapability.PdfGeneration` + `List<CapabilityFeature<T>>.availableValues()`
ao montar `PaywallPlan.highlights`/menus, e `CapabilityGate(PlatformCapability.PdfGeneration) { ... }`
para UI pontual. Nenhum app precisa mudar quando o flag virar `true`.

## Câmera / OCR (iOS)

**2.78.0 — `CameraView.ios` e `PlateOcrAnalyzer.ios` implementados** (não mais placeholder).
`PlateOcrAnalyzer.ios` usa **Apple Vision** (`VNRecognizeTextRequest`) para OCR on-device; `CameraView.ios`
usa **AVFoundation** (`AVCaptureSession` + `AVCaptureVideoDataOutput` + `AVCaptureVideoPreviewLayer` via
`UIKitView`) + Vision no `CVPixelBuffer` dos frames (com throttle) e codifica o frame reconhecido em
**JPEG** na variante `onCapture`. Padrão-ouro (APIs oficiais Apple, sem WebView). Info.plist exige
`NSCameraUsageDescription`. **Pendente de validação em host macOS** (não compila em Linux).

## Push own-stack (2.76.0 — sem cerimônia Firebase por app)

O módulo `push/` ganhou o caminho **own-stack** (piloto Meu Barbeiro), ADITIVO e reversível — os apps
legados (`influencer`/`locadora`/`meu-advogado`/`super8`) seguem no fluxo antigo sem mudança. Objetivo:
eliminar `google-services.json`/`GoogleService-Info.plist`/plugin google-services/`processDebugGoogleServices`,
**mantendo o projeto central `code-cacto`**.

- **Android = FCM com init MANUAL do FirebaseApp.** `initFirebaseForPush(context, AndroidFcmAppId)`
  (`FirebaseApp.initializeApp` + `FirebaseOptions.Builder`, idempotente) **antes** de
  `NotifierManager.initialize(...)`. `AndroidFcmAppId.PerApp` (DEFAULT, App ID próprio no console
  `code-cacto`, sem json) ou `.Shared` (atalho opt-in com App ID compartilhado). KMPNotifier-android já
  traz `firebase-messaging` transitivo — **sem plugin google-services**.
- **iOS = APNs-direto** (sem FCM/plist): `@ObjCName("ApplePushBridge") object ApplePushBridge` alimentado
  pelo `AppDelegate` Swift (`onApnsToken`/`onApnsRegistrationFailed`/`onRemoteNotification(userInfo,
  wasTapped)`/`currentToken`) → mesmo `PushNotificationListener`. Passo a passo Swift no KDoc de
  `ApplePushBridge`. Bridge + `UNUserNotificationCenter` **validados por inspeção** (link real no Mac).
- **Comum:** `createPushNotificationService(listener)` (Android⇒KMPNotifier, iOS⇒bridge) e
  `createLocalPushNotifier()` (foreground local: Android⇒KMPNotifier, iOS⇒`UNUserNotificationCenter`).
  Roteamento puro `PushEventRouter`/`PushPayload` (commonMain, testado). Detalhe na skill `kmplib-catalog`.

## Compatibilidade

### Kotlin/Native 2.x

Mudanças importantes:
- `@Volatile` agora requer `import kotlin.concurrent.Volatile`
- `allocArray` em `memScoped` substituído por `usePinned` + `addressOf`
- `CGPDFBox.kCGPDFMediaBox` não acessível, usar valor numérico `0`

### Koin 4.x

- `GlobalContext.get()` → `KoinPlatformTools.defaultContext().get()`

## Dependências Nativas iOS

Projetos consumidores precisam adicionar via SPM:

| Package | URL |
|---------|-----|
| Firebase iOS SDK | `https://github.com/firebase/firebase-ios-sdk` |
| RevenueCat | `https://github.com/RevenueCat/purchases-ios-spm` |
| Google Sign-In | `https://github.com/google/GoogleSignIn-iOS` |

## Documentação Adicional

- **IOS_INTEGRATION.md**: Guia completo de integração iOS
- **library/README.md**: Documentação da biblioteca (se existir)

## Troubleshooting

### "cannot find type 'IosNativeMap' in scope"
Projeto consumidor não está usando `api()` + `export()`.

### "Undefined symbols for architecture arm64"
Faltam dependências SPM no Xcode (Firebase, RevenueCat).

### "cannot convert KotlinUnit to Void"
Usar `_ = callback(...)` no Swift.

---

*Atualizado em 2026-07-01*
